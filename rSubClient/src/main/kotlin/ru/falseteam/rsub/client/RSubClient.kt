package ru.falseteam.rsub.client

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ru.falseteam.rsub.RSub
import ru.falseteam.rsub.RSubConnection

class RSubClient(
    private val connector: RSubConnector
) : RSub() {
    private val connectionObservable = channelFlow {
        var connection: RSubConnection? = null
        try {
            send(RSubConnectionStatus.DISCONNECTED)
            connection = connector.connect()
            send(RSubConnectionStatus.CONNECTED)
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


