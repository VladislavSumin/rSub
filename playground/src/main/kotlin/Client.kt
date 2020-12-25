import io.ktor.client.*
import io.ktor.client.features.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import ru.falseteam.rsub.RSubClient
import ru.falseteam.rsub.RSubConnection
import ru.falseteam.rsub.RSubConnector
import ru.falseteam.rsub.connector.ktorwebsocket.core.RSubConnectionKtorWebSocket

fun startClient(): RSubClient {
    println("Creating client")
    val client = HttpClient {
        install(WebSockets)
    }
    println("Client created")

    val connector = object : RSubConnector {
        override suspend fun connect(): RSubConnection {
            val session = client.webSocketSession(method = HttpMethod.Get, port = 8888, path = "/ws") {
                this.setAttributes {
                    header(HttpHeaders.SecWebSocketProtocol, "rSub")
                }
            }
            return RSubConnectionKtorWebSocket(session)
        }
    }

    val rSubClient = RSubClient(connector)
    println("Client function finish")

    return rSubClient
}
