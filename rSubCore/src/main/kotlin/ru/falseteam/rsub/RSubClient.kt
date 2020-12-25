package ru.falseteam.rsub

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class RSubClient(
    private val connector: RSubConnector
) : RSub() {
    private val connectionObservable = channelFlow {
        var connection: RSubConnection? = null
        try {
            send(ConnectionStatus.DISCONNECTED)
            connection = connector.connect()
            send(ConnectionStatus.CONNECTED)
            delay(5000)
            connection.close()
        } finally {
            withContext(NonCancellable) {
                connection?.close()
            }
        }
    }
        .distinctUntilChanged()
        .shareIn(GlobalScope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), 1)

    fun observeConnection() = connectionObservable
}


enum class ConnectionStatus {
    CONNECTED,
    DISCONNECTED,
}
