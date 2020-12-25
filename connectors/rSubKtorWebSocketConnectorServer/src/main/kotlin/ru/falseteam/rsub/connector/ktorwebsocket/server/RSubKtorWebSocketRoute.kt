package ru.falseteam.rsub.connector.ktorwebsocket.server

import io.ktor.routing.*
import io.ktor.websocket.*
import ru.falseteam.rsub.connector.ktorwebsocket.core.RSubConnectionKtorWebSocket
import ru.falseteam.rsub.server.RSubServer

fun Route.rSubWebSocket(server: RSubServer, path: String = "/rSub") {
    webSocket(path = path, protocol = "rSub") {
        server.handleNewConnection(RSubConnectionKtorWebSocket(this))
    }
}
