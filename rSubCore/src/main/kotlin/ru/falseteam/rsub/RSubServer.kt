package ru.falseteam.rsub

import kotlinx.coroutines.coroutineScope

class RSubServer : RSub() {
    suspend fun handleNewConnection(connection: RSubConnection): Unit = coroutineScope {
        println("Handle new connection")
    }
}
