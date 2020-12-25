package ru.falseteam.rsub

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class RSubClient(
    private val connector: RSubConnector
) : RSub() {
    private val connectionObservable = flow {
        emit(ConnectionStatus.DISCONNECTED)
    }.shareIn(GlobalScope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), 1)

    fun observeConnection() = connectionObservable
}

interface RSubConnector {
    suspend fun connect(): RSubConnection
}


enum class ConnectionStatus {
    CONNECTED,
    DISCONNECTED,
}
