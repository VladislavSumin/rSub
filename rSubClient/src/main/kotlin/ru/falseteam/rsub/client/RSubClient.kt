package ru.falseteam.rsub.client

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.serializer
import org.slf4j.LoggerFactory
import ru.falseteam.rsub.RSub
import ru.falseteam.rsub.RSubConnection
import ru.falseteam.rsub.RSubMessage
import ru.falseteam.rsub.RSubSubscribeMessage
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
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

        var connection: RSubConnection? = null
        try {
            while (true) {
                try {
                    connection = connector.connect()
                    val state = crateConnectedState(connection, this)
                    send(state)
                    state.incoming.count() // block current coroutine while connection running
                } catch (e: SocketTimeoutException) {
                    log.debug("Connection failed by socket exception: ${e.message}")
                    send(ConnectionState.Disconnected)
                    connection?.close()
                    delay(1000)
                    log.debug("Reconnecting...")
                }
            }
        } finally {
            log.debug("Stopping observe connection")
            withContext(NonCancellable) {
                connection?.close()
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
    private fun crateConnectedState(connection: RSubConnection, scope: CoroutineScope): ConnectionState.Connected {
        return ConnectionState.Connected(
            { connection.send(it.toJson()) },
            connection.receive
                .map { RSubMessage.fromJson(it) }
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
    private suspend fun <T> withConnection(block: suspend (connection: ConnectionState.Connected) -> T): T {
        return connection.filter {
            when (it) {
                is ConnectionState.Connecting -> false
                is ConnectionState.Connected -> true
                is ConnectionState.Disconnected -> throw RSubException("Connection in state DISCONNECTED")
            }
        }
            .map { it as ConnectionState.Connected }
            // Hack, use map to prevent closing connection.
            // Connection subscription active all time while block executing.
            .map(block)
            .first()
    }

    inline fun <reified T> getProxy(): T = getProxy(T::class)

    fun <T> getProxy(kClass: KClass<*>): T {
        val name = kClass.simpleName!!
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
        Proxy.newProxyInstance(this::class.java.classLoader, arrayOf(kClass.java)) { _, method, arguments ->
            return@newProxyInstance processProxyCall(name, method, arguments)
        }

    private fun processProxyCall(name: String, method: Method, arguments: Array<Any?>?): Any? {
        val kMethod = method.kotlinFunction!!
        return if (kMethod.isSuspend) processSuspendFunction(name, kMethod, arguments!!)
        else processNonSuspendFunction(name, kMethod, arguments)
    }

    private fun processSuspendFunction(name: String, method: KFunction<*>, arguments: Array<Any?>): Any? {
        val continuation = arguments.last() as Continuation<*>
        val argumentsWithoutContinuation = arguments.sliceArray(0 until arguments.size - 1)
        return SuspendCaller(continuation, object : SuspendFunction {
            override suspend fun invoke(): Any? = processSuspend(name, method, argumentsWithoutContinuation)
        })
    }

    private fun processNonSuspendFunction(name: String, method: KFunction<*>, arguments: Array<Any?>?): Flow<*> {
        if (method.returnType.classifier != Flow::class) {
            throw Exception("For non suspend function only flow return type supported")
        }
        return processFlow(name, method, arguments)
    }

    private suspend fun processSuspend(name: String, method: KFunction<*>, arguments: Array<Any?>): Any? {
        return withConnection { connection ->
            val id = nextId.getAndIncrement()
            try {
                coroutineScope {
                    val responseDeferred = async { connection.incoming.filter { it.id == id }.first() }

                    connection.subscribe(id, name, method, arguments)

                    val response = responseDeferred.await()

                    when (response.type) {
                        RSubMessage.Type.DATA -> {
                            Json.decodeFromJsonElement(
                                Json.serializersModule.serializer(method.returnType),
                                response.payload!!
                            )
                        }
                        RSubMessage.Type.ERROR -> throw RSubException("Server return error")
                        RSubMessage.Type.SUBSCRIBE, RSubMessage.Type.UNSUBSCRIBE -> throw RSubException("Unexpected server data")
                    }
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    connection.send(getUnsubscribeMessage(id))
                }
                throw e
            }
        }
    }

    private fun processFlow(name: String, method: KFunction<*>, arguments: Array<Any?>?): Flow<Any?> {
        return flow<Any?> {
            withConnection {
                delay(1000)
                emit("Hello 1")
                delay(500)
                emit("Hello 2")
            }
        }
    }

    private suspend fun ConnectionState.Connected.subscribe(
        id: Int,
        name: String,
        method: KFunction<*>,
        arguments: Array<Any?>?
    ) {
        send(getSubscribeMessage(id, name, method))
    }

    private fun getSubscribeMessage(id: Int, name: String, method: KFunction<*>): RSubMessage {
        val payload = RSubSubscribeMessage(
            name,
            method.name
        )
        return RSubMessage(
            id,
            RSubMessage.Type.SUBSCRIBE,
            Json.encodeToJsonElement(payload)
        )
    }

    private fun getUnsubscribeMessage(id: Int): RSubMessage {
        return RSubMessage(id, RSubMessage.Type.UNSUBSCRIBE)
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
}


