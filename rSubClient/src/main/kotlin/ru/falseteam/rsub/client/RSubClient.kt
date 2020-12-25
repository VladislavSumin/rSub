package ru.falseteam.rsub.client

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ru.falseteam.rsub.RSub
import ru.falseteam.rsub.RSubConnection
import java.net.SocketTimeoutException

class RSubClient(
    private val connector: RSubConnector
) : RSub() {
    private val connectionObservable = channelFlow {
        var connection: RSubConnection? = null
        try {
            while (true) {
                try {
                    send(RSubConnectionStatus.DISCONNECTED)
                    connection = connector.connect()
                    send(RSubConnectionStatus.CONNECTED)
                    delay(5000)
                    connection.close()
                } catch (e: SocketTimeoutException) {
                    send(RSubConnectionStatus.DISCONNECTED)
                    println("Failed!")
                    delay(1000)
                }
            }
        } finally {
            withContext(NonCancellable) {
                connection?.close()
            }
        }
    }
        .distinctUntilChanged()
        .shareIn(GlobalScope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), 1)

    fun observeConnection() = connectionObservable

    private suspend fun processInputMessages(messages: Flow<String>) {

    }
}


