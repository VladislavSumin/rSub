package ru.falseteam.rsub.server

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import ru.falseteam.rsub.RSub
import ru.falseteam.rsub.RSubConnection

class RSubServer : RSub() {
    suspend fun handleNewConnection(connection: RSubConnection): Unit = coroutineScope {
        println("Handle new connection")
        connection.receive.collect {
            println(it)
        }
        println("Connection closed")
    }
}
