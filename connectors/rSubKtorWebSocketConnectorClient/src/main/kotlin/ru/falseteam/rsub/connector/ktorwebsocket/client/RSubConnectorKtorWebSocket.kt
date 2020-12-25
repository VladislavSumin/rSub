package ru.falseteam.rsub.connector.ktorwebsocket.client

import io.ktor.client.*
import io.ktor.client.features.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import ru.falseteam.rsub.RSubConnection
import ru.falseteam.rsub.RSubConnector
import ru.falseteam.rsub.connector.ktorwebsocket.core.RSubConnectionKtorWebSocket

class RSubConnectorKtorWebSocket(
    private val client: HttpClient,
    private val host: String = "localhost",
    private val path: String = "/rSub",
    private val port: Int = 8080
) : RSubConnector {
    override suspend fun connect(): RSubConnection {
        val session = client.webSocketSession(
            method = HttpMethod.Get,
            host = host,
            port = port,
            path = path,
        ) {
            setAttributes {
                header(HttpHeaders.SecWebSocketProtocol, "rSub")
            }
        }
        return RSubConnectionKtorWebSocket(session)
    }
}
