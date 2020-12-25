package ru.falseteam.rsub

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect

class RSubServer : RSub() {
    suspend fun handleNewConnection(connection: RSubConnection): Unit = coroutineScope {
        println("Handle new connection")
        connection.receive.collect {
            println(it)
        }
        println("Connection closed")
    }
}
