package ru.falseteam.rsub.server

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.slf4j.LoggerFactory
import ru.falseteam.rsub.RSub
import ru.falseteam.rsub.RSubConnection
import ru.falseteam.rsub.RSubInterface
import ru.falseteam.rsub.RSubMessage
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.superclasses

class RSubServer : RSub() {
    private val log = LoggerFactory.getLogger("rSub.server")
    private val impls = mutableMapOf<String, Pair<KClass<*>, Any>>()

    suspend fun handleNewConnection(connection: RSubConnection): Unit = coroutineScope {
        ConnectionHandler(connection).handle()
    }

    fun registerImpl(impl: Any) {
        val rSubInterface = impl::class.superclasses.find { it.hasAnnotation<RSubInterface>() }
            ?: throw Exception("impl must be subtype of interface with @RSubInterface annotation")

        val name = rSubInterface.findAnnotation<RSubInterface>()!!.name

        impls[name] = Pair(rSubInterface, impl)
    }


    private inner class ConnectionHandler(
        private val connection: RSubConnection
    ) {
        private val activeSubscriptions = mutableMapOf<Int, Job>()

        suspend fun handle() {
            coroutineScope {
                log.debug("Handle new connection")
                connection.receive.collect {
                    when (val request = Json.decodeFromString<RSubMessage>(it)) {
                        is RSubMessage.Subscribe -> processSubscribe(request, this)
                        is RSubMessage.Unsubscribe -> processUnsubscribe(request)
                        else -> {
                            // TODO!!
                        }
                    }
                }
                activeSubscriptions.forEach { (_, v) -> v.cancel() }
            }
            log.debug("Connection closed")
        }

        private suspend fun send(message: RSubMessage) {
            connection.send(Json.encodeToString(message))
        }

        //TODO check possible data corrupt on id error from client (add sync?)
        //TODO add error handling
        //TODO make cancelable
        private suspend fun processSubscribe(request: RSubMessage.Subscribe, scope: CoroutineScope) {
            val job = scope.launch(start = CoroutineStart.LAZY) {
                log.trace("Subscribe id=${request.id} to ${request.interfaceName}::${request.functionName}")
                val rSubInterface = impls[request.interfaceName]!!.first
                val instance = impls[request.interfaceName]!!.second
                val kFunction = rSubInterface.functions.find { it.name == request.functionName }!!

                try {
                    if (kFunction.isSuspend) {
                        val response = suspendCoroutine<Any?> {
                            it.resume(kFunction.call(instance, it))
                        }
                        sendData(request.id, kFunction.returnType, response)
                    } else {
                        val flow = kFunction.call(instance) as Flow<*>
                        flow.collect {
                            sendData(request.id, kFunction.returnType.arguments[0].type!!, it)
                        }
                        send(RSubMessage.FlowComplete(request.id))
                    }
                } catch (e: Exception) {
                    send(RSubMessage.Error(request.id))
                    activeSubscriptions.remove(request.id)

                    if (e is CancellationException) throw e
                    log.trace(
                        "Error on subscription id=${request.id} to ${request.interfaceName}::${request.functionName}",
                        e
                    )
                    return@launch
                }


                log.trace("Complete subscription id=${request.id} to ${request.interfaceName}::${request.functionName}")
                activeSubscriptions.remove(request.id)
            }
            activeSubscriptions[request.id] = job
            job.start()
        }

        private fun processUnsubscribe(request: RSubMessage) {
            log.trace("Cancel subscription id=${request.id}")
            activeSubscriptions.remove(request.id)?.cancel()
        }

        private suspend fun sendData(id: Int, type: KType, data: Any?) {
            val responsePayload =
                Json.encodeToJsonElement(
                    Json.serializersModule.serializer(type),
                    data
                )
            val message = RSubMessage.Data(
                id,
                responsePayload
            )
            send(message)
        }

    }
}
