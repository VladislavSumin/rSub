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
    private val proxies = mutableMapOf<String, Any>()
    private val nextId = AtomicInteger(0)
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
                    send(ConnectionState.Disconnected)
                    connection?.close()
                    delay(1000)
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

    private fun crateConnectedState(connection: RSubConnection, scope: CoroutineScope): ConnectionState.Connected {
        return ConnectionState.Connected(
            { connection.send(it.toJson()) },
            connection.receive
                .map { RSubMessage.fromJson(it) }
                .shareIn(scope, SharingStarted.Eagerly)
        )
    }

    fun observeConnection(): Flow<RSubConnectionStatus> = connection.map { it.status }

    inline fun <reified T> getProxy(): T = getProxy(T::class)

    fun <T> getProxy(kClass: KClass<*>): T {
        val name = kClass.simpleName!!
        return if (proxies.containsKey(name)) {
            log.debug("Find proxy instance for name $name")
            //TODO check proxy class to prevent name collision
            proxies[name]!!
        } else {
            log.debug("Creating new proxy for name $name")
            val proxy = createNewProxy(name, kClass)
            proxies[name] = proxy
            proxy
        } as T
    }

    private fun createNewProxy(name: String, kClass: KClass<*>) =
        Proxy.newProxyInstance(this::class.java.classLoader, arrayOf(kClass.java)) { _, method, arguments ->
            return@newProxyInstance processProxyCall(name, method, arguments)
        }

    private fun processProxyCall(name: String, method: Method, arguments: Array<Any>?): Any? {
        val kMethod = method.kotlinFunction!!
        return if (kMethod.isSuspend) processSuspendFunction(name, kMethod, arguments!!)
        else processNonSuspendFunction(name, kMethod, arguments)
    }

    private fun processSuspendFunction(name: String, method: KFunction<*>, arguments: Array<Any>): Any? {
        val continuation = arguments.last() as Continuation<*>
        val argumentsWithoutContinuation = arguments.take(arguments.size - 1)
        return try {
            SuspendRemover.invoke(object : SuspendFunction {
                override suspend fun invoke(): Any? = processSuspend(name, method, argumentsWithoutContinuation)
            }, continuation)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    private suspend fun processSuspend(name: String, method: KFunction<*>, arguments: List<Any>?): Any? {
        return connection.filter {
            when (it) {
                is ConnectionState.Connecting -> false
                is ConnectionState.Connected -> true
                is ConnectionState.Disconnected -> throw Exception("Connection in state DISCONNECTED")//TODO make custom exception
            }
        }
            .map { it as ConnectionState.Connected }
            .map { connection ->
                val id = nextId.getAndIncrement()
                try {
                    coroutineScope {
                        val responseDeferred = async { connection.incoming.filter { it.id == id }.first() }

                        val request = getSubscribeMessage(id, name, method)
                        connection.send(request)

                        val response = responseDeferred.await()

                        Json.decodeFromJsonElement(
                            Json.serializersModule.serializer(method.returnType),
                            response.payload!!
                        )
                    }
                } catch (e: CancellationException) {
                    withContext(NonCancellable) {
                        connection.send(getUnsubscribeMessage(id))
                    }
                    throw e
                }
            }.first()
    }

    private fun processNonSuspendFunction(name: String, method: KFunction<*>, arguments: Array<Any>?): Flow<*> {
        if (method.returnType.classifier != Flow::class) {
            throw Exception("For non suspend function only flow return type supported")
        }
        return processFlow(name, method, arguments)
    }

    private fun processFlow(name: String, method: KFunction<*>, arguments: Array<Any>?): Flow<Any?> {
        return flow<String> {
            delay(1000)
            emit("Hello 1")
            delay(500)
            emit("Hello 2")
        }
    }

    companion object {
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

//    class RSubProxyNameCollisionException(name: String, kClassRequest: KClass<*>, kClassProxy: KClass<*>) :
//        Exception("For name $name request proxy ${kClassRequest.qualifiedName!!}, but find in cache ${kClassProxy.qualifiedName!!}")
}


