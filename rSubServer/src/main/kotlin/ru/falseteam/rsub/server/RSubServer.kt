package ru.falseteam.rsub.server

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.serializer
import org.slf4j.LoggerFactory
import ru.falseteam.rsub.RSub
import ru.falseteam.rsub.RSubConnection
import ru.falseteam.rsub.RSubMessage
import ru.falseteam.rsub.RSubSubscribeMessage
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.reflect.full.functions

class RSubServer : RSub() {
    private val log = LoggerFactory.getLogger("rSub.server")
    private val impls = mutableMapOf<String, Any>()

    suspend fun handleNewConnection(connection: RSubConnection): Unit = coroutineScope {
        ConnectionHandler(connection).handle()
    }

    //TODO name auto
    fun registerImpl(impl: Any, name: String) {
        impls[name] = impl
    }

    private inner class ConnectionHandler(
        private val connection: RSubConnection
    ) {
        private val activeSubscriptions = mutableMapOf<Int, Job>()

        suspend fun handle() {
            coroutineScope {
                log.debug("Handle new connection")
                connection.receive.collect {
                    val request = RSubMessage.fromJson(it)
                    when (request.type) {
                        RSubMessage.Type.SUBSCRIBE -> processSubscribe(request, this)
                        RSubMessage.Type.UNSUBSCRIBE -> processUnsubscribe(request)
                    }
                }
                activeSubscriptions.forEach { (_, v) -> v.cancel() }
            }
            log.debug("Connection closed")
        }

        private suspend fun send(message: RSubMessage) {
            connection.send(message.toJson())
        }

        //TODO check possible data corrupt on id error from client (add sync?)
        //TODO add error handling
        //TODO make cancelable
        private suspend fun processSubscribe(request: RSubMessage, scope: CoroutineScope) {
            val job = scope.launch(start = CoroutineStart.LAZY) {
                val subscribeRequest = Json.decodeFromJsonElement<RSubSubscribeMessage>(request.payload!!)
                log.trace("Subscribe id=${request.id} to ${subscribeRequest.interfaceName}::${subscribeRequest.functionName}")
                val impl = impls[subscribeRequest.interfaceName]!!
                val kFunction = impl::class.functions.find { it.name == subscribeRequest.functionName }!!

                val response = try {
                    suspendCoroutine<Any?> {
                        it.resume(kFunction.call(impl, it))
                    }
                } catch (e: Exception) {
                    send(RSubMessage(request.id, RSubMessage.Type.ERROR))
                    activeSubscriptions.remove(request.id)

                    if (e is CancellationException) throw e
                    log.trace(
                        "Error on subscription id=${request.id} to ${subscribeRequest.interfaceName}::${subscribeRequest.functionName}",
                        e
                    )
                    return@launch
                }

                val responsePayload =
                    Json.encodeToJsonElement(
                        Json.serializersModule.serializer(kFunction.returnType),
                        response!!
                    )

                send(
                    RSubMessage(
                        request.id,
                        RSubMessage.Type.DATA,
                        responsePayload
                    )
                )

                log.trace("Complete subscription id=${request.id} to ${subscribeRequest.interfaceName}::${subscribeRequest.functionName}")
                activeSubscriptions.remove(request.id)
            }
            activeSubscriptions[request.id] = job
            job.start()
        }

        private fun processUnsubscribe(request: RSubMessage) {
            log.trace("Cancel subscription id=${request.id}")
            activeSubscriptions.remove(request.id)?.cancel()
        }
    }
}
