package ru.falseteam.rsub.client

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.slf4j.LoggerFactory
import ru.falseteam.rsub.*
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.kotlinFunction

class RSubClient(
    private val connector: RSubConnector
) : RSub() {
    private val log = LoggerFactory.getLogger("rSub.client")

    /**
     * Contains currently existed proxies
     * @key proxy name
     * @value proxy original class and proxy object
     */
    private val proxies = mutableMapOf<String, Pair<KClass<*>, Any>>()

    /**
     * Contains next id, using to create new subscription with uncial id
     */
    private val nextId = AtomicInteger(0)

    /**
     * This shared flow keeps the connection open and automatically reconnects in case of errors.
     * The connection will be maintained as long as there are active subscriptions
     */
    private val connection = channelFlow {
        log.debug("Start observe connection")
        send(ConnectionState.Connecting)

        var connectionGlobal: RSubConnection? = null
        try {
            while (true) {
                try {
                    coroutineScope {
                        val connection = connector.connect()
                        connectionGlobal = connection
                        val state = crateConnectedState(connection, this)
                        send(state)
                    }
                } catch (e: Exception) {
                    when (e) {
                        is SocketTimeoutException,
                        is SocketException -> {
                            log.debug("Connection failed by socket exception: ${e.message}")
                            send(ConnectionState.Disconnected)
                            connectionGlobal?.close()
                            delay(2000)
                            log.debug("Reconnecting...")
                        }
                        is CancellationException -> throw e
                        else -> {
                            log.error("Unknown exception on connection", e)
                            throw e
                        }
                    }
                }
            }
        } finally {
            log.debug("Stopping observe connection")
            withContext(NonCancellable) {
                connectionGlobal?.close()
                log.debug("Stop observe connection")
            }
        }
    }
        .distinctUntilChanged()
        .onEach { log.debug("New connection status: ${it.status}") }
        .shareIn(GlobalScope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), 1)

    /**
     * Create wrapped connection, with shared receive flow
     * all received messages reply to that flow, flow haven`t buffer!
     *
     * @param connection raw connection from connector
     * @param scope coroutine scope of current connection session
     */
    private fun crateConnectedState(
        connection: RSubConnection,
        scope: CoroutineScope
    ): ConnectionState.Connected {
        return ConnectionState.Connected(
            { connection.send(Json.encodeToString(it)) },
            connection.receive
                .map { Json.decodeFromString<RSubMessage>(it) }
                // Hot observable, subscribe immediately, shared, no buffer, connection scoped
                .shareIn(scope, SharingStarted.Eagerly)
        )
    }

    /**
     * Keep connection active while subscribed, return actual connection status
     */
    fun observeConnectionStatus(): Flow<RSubConnectionStatus> = connection.map { it.status }

    /**
     * Try to subscribe to [connection], wait connected state and execute given block with [ConnectionState.Connected]
     * If connection failed throw [RSubException]
     */
    private suspend fun <T> withConnection(
        throwOnDisconnect: Boolean = true,
        block: suspend (connection: ConnectionState.Connected) -> T
    ): T {
        return connection.filter {
            when (it) {
                is ConnectionState.Connecting -> false
                is ConnectionState.Connected -> true
                is ConnectionState.Disconnected ->
                    if (throwOnDisconnect) throw RSubException("Connection in state DISCONNECTED")
                    else false
            }
        }
            .map { it as ConnectionState.Connected }
            // Hack, use map to prevent closing connection.
            // Connection subscription active all time while block executing.
            .mapLatest(block)
            .retry { !throwOnDisconnect && (it is SocketException) }
            .first()
    }

    inline fun <reified T : Any> getProxy(): T = getProxy(T::class)

    fun <T : Any> getProxy(kClass: KClass<T>): T {
        val annotation = kClass.findAnnotation<RSubInterface>()
            ?: throw Exception("Proxy interface must have @RSubInterface annotation")
        val name = annotation.name
        @Suppress("UNCHECKED_CAST")
        return if (proxies.containsKey(name)) {
            log.debug("Found proxy instance for name $name")
            val pair = proxies[name]!!
            if (kClass != pair.first) {
                throw Exception(
                    "Found proxies name collision, requestedProxy: ${kClass.qualifiedName}, " +
                            "cached proxy: ${pair.first.qualifiedName}"
                )
            }
            pair.second
        } else {
            log.debug("Creating new proxy for name $name")
            val proxy = createNewProxy(name, kClass)
            proxies[name] = Pair(kClass, proxy)
            proxy
        } as T
    }

    private fun createNewProxy(name: String, kClass: KClass<*>) =
        Proxy.newProxyInstance(
            this::class.java.classLoader,
            arrayOf(kClass.java)
        ) { _, method, arguments ->
            return@newProxyInstance processProxyCall(name, method, arguments)
        }

    private fun processProxyCall(name: String, method: Method, arguments: Array<Any?>?): Any? {
        val kMethod = method.kotlinFunction!!
        return if (kMethod.isSuspend) processSuspendFunction(name, kMethod, arguments!!)
        else processNonSuspendFunction(name, kMethod, arguments)
    }

    private fun processSuspendFunction(
        name: String,
        method: KFunction<*>,
        arguments: Array<Any?>
    ): Any? {
        val continuation = arguments.last() as Continuation<*>
        val argumentsWithoutContinuation = arguments.sliceArray(0 until arguments.size - 1)
        return SuspendCaller(continuation, object : SuspendFunction {
            override suspend fun invoke(): Any? =
                processSuspend(name, method, argumentsWithoutContinuation)
        })
    }

    private fun processNonSuspendFunction(
        name: String,
        method: KFunction<*>,
        arguments: Array<Any?>?
    ): Flow<*> {
        if (method.returnType.classifier != Flow::class) {
            throw Exception("For non suspend function only flow return type supported")
        }
        return processFlow(name, method, arguments)
    }

    private suspend fun processSuspend(
        name: String,
        method: KFunction<*>,
        arguments: Array<Any?>
    ): Any? {
        return withConnection { connection ->
            val id = nextId.getAndIncrement()
            try {
                coroutineScope {
                    val responseDeferred =
                        async { connection.incoming.filter { it.id == id }.first() }

                    connection.subscribe(id, name, method, arguments)

                    val response = responseDeferred.await()

                    parseServerMessage(response, method.returnType)
                }
            } catch (e: Exception) {
                withContext(NonCancellable) {
                    connection.unsubscribe(id)
                }
                throw e
            }
        }
    }

    private fun processFlow(
        name: String,
        method: KFunction<*>,
        arguments: Array<Any?>?
    ): Flow<Any?> = channelFlow {
        withConnection { connection ->
            val id = nextId.getAndIncrement()
            try {
                coroutineScope {
                    launch {
                        connection.incoming
                            .filter { it.id == id }
                            .collect {
                                val item = parseServerMessage(
                                    it,
                                    method.returnType.arguments[0].type!!
                                )
                                send(item)
                            }
                    }
                    connection.subscribe(id, name, method, arguments)
                }
            } catch (e: FlowCompleted) {
                // suppress
            } catch (e: Exception) {
                withContext(NonCancellable) {
                    connection.unsubscribe(id)
                }
                throw e
            }
        }
    }


    private fun parseServerMessage(message: RSubMessage, castType: KType): Any? {
        return when (message) {
            is RSubMessage.Data -> {
                val data = message.data
                if (data != null)
                    Json.decodeFromJsonElement(
                        Json.serializersModule.serializer(castType),
                        data
                    )
                else null
            }
            is RSubMessage.FlowComplete -> throw FlowCompleted()
            is RSubMessage.Error -> throw RSubException("Server return error")
            is RSubMessage.Subscribe, is RSubMessage.Unsubscribe -> throw RSubException("Unexpected server data")
        }
    }

    private suspend fun ConnectionState.Connected.subscribe(
        id: Int,
        name: String,
        method: KFunction<*>,
        arguments: Array<Any?>?
    ) {
        send(RSubMessage.Subscribe(id, name, method.name))
    }

    private suspend fun ConnectionState.Connected.unsubscribe(id: Int) {
        this.send(RSubMessage.Unsubscribe(id))
    }


    companion object {
        private val SuspendCaller = { cont: Continuation<*>, obj: SuspendFunction ->
            try {
                SuspendRemover.invoke(obj, cont)
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        }
        private val SuspendRemover = SuspendFunction::class.java.methods[0]

        private interface SuspendFunction {
            suspend fun invoke(): Any?
        }
    }

    private sealed class ConnectionState(val status: RSubConnectionStatus) {
        object Connecting : ConnectionState(RSubConnectionStatus.CONNECTING)
        class Connected(
            val send: suspend (message: RSubMessage) -> Unit,
            val incoming: Flow<RSubMessage>
        ) : ConnectionState(RSubConnectionStatus.CONNECTED)

        object Disconnected : ConnectionState(RSubConnectionStatus.DISCONNECTED)
    }

    private class FlowCompleted : Exception()
}


