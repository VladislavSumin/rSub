package ru.falseteam.rsub.client

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import ru.falseteam.rsub.RSub
import ru.falseteam.rsub.RSubConnection
import ru.falseteam.rsub.RSubMessage
import java.net.SocketTimeoutException

class RSubClient(
    private val connector: RSubConnector
) : RSub() {
    private val log = LoggerFactory.getLogger("rSub.client")
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
}


