package ru.falseteam.rsub.connector.ktorwebsocket.core

import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import ru.falseteam.rsub.RSubConnection

class RSubConnectionKtorWebSocket(
    private val session: DefaultWebSocketSession
) : RSubConnection {
    override val receive: Flow<String>
        get() = session.incoming.receiveAsFlow()
            .map { it as Frame.Text }
            .map { it.readText() }

    override suspend fun send(data: String) = session.send(Frame.Text(data))

    override suspend fun close() = session.close()
}
