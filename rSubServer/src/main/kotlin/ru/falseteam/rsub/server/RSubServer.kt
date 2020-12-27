package ru.falseteam.rsub.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.serializer
import org.slf4j.LoggerFactory
import ru.falseteam.rsub.RSub
import ru.falseteam.rsub.RSubConnection
import ru.falseteam.rsub.RSubMessage
import ru.falseteam.rsub.RSubSubscribeMessage
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.reflect.KClass
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
        suspend fun handle() = coroutineScope {
            log.debug("Handle new connection")
            connection.receive.collect {
                val request = RSubMessage.fromJson(it)
                when (request.type) {
                    RSubMessage.Type.SUBSCRIBE -> processSubscribe(request, this)
                }
            }
            log.debug("Connection closed")
        }

        private suspend fun send(message: RSubMessage) {
            connection.send(message.toJson())
        }

        //TODO check possible data corrupt on id error from client
        //TODO add error handling
        //TODO make cancelable
        private suspend fun processSubscribe(request: RSubMessage, scope: CoroutineScope) {
            scope.launch {
                val subscribeRequest = Json.decodeFromJsonElement<RSubSubscribeMessage>(request.payload)
                val impl = impls[subscribeRequest.interfaceName]!!
                val kFunction = impl::class.functions.find { it.name == subscribeRequest.functionName }!!
                val response = suspendCoroutine<Any?> {
                    it.resume(kFunction.call(impl, it))
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

                send(request)
            }
        }
    }
}
