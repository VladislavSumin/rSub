package ru.falseteam.rsub.client

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import ru.falseteam.rsub.RSub
import ru.falseteam.rsub.RSubConnection
import ru.falseteam.rsub.RSubMessage
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.net.SocketTimeoutException
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.kotlinFunction

class RSubClient(
    private val connector: RSubConnector
) : RSub() {
    private val log = LoggerFactory.getLogger("rSub.client")
    private val proxies = mutableMapOf<String, Any>()
    private val connectionObservable = channelFlow {
        log.debug("Start observe connection")
        send(RSubConnectionStatus.DISCONNECTED)

        var connection: RSubConnection? = null
        try {
            while (true) {
                try {
                    connection = connector.connect()
                    send(RSubConnectionStatus.CONNECTED)
                    processInputMessages(connection.receive)
                } catch (e: SocketTimeoutException) {
                    send(RSubConnectionStatus.DISCONNECTED)
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
        .onEach { log.debug("New connection status: $it") }
        .shareIn(GlobalScope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), 1)

    fun observeConnection() = connectionObservable

    private suspend fun processInputMessages(messages: Flow<String>) {
        messages.collect { json ->
            processInputMessage(RSubMessage.fromJson(json))
        }
    }

    private suspend fun processInputMessage(message: RSubMessage) {
        println(message)
    }

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
            processProxyCall(name, method, arguments)
        }

    private fun processProxyCall(name: String, method: Method, arguments: Array<Any>) {
        val kMethod = method.kotlinFunction!!
        if (kMethod.isSuspend) processSuspendFunction(name, kMethod, arguments)
        else processNonSuspendFunction(name, kMethod, arguments)
    }

    private fun processSuspendFunction(name: String, method: KFunction<*>, arguments: Array<Any>) {
        TODO("not implemented")
    }

    private fun processNonSuspendFunction(name: String, method: KFunction<*>, arguments: Array<Any>) {
        TODO("not implemented")
    }

//    class RSubProxyNameCollisionException(name: String, kClassRequest: KClass<*>, kClassProxy: KClass<*>) :
//        Exception("For name $name request proxy ${kClassRequest.qualifiedName!!}, but find in cache ${kClassProxy.qualifiedName!!}")
}


