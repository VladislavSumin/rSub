import io.ktor.client.*
import io.ktor.client.features.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ru.falseteam.rsub.RSubClient
import ru.falseteam.rsub.RSubConnection
import ru.falseteam.rsub.RSubConnector

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

            return object : RSubConnection {
                override val receive: Flow<String>
                    get() = session.incoming.receiveAsFlow()
                        .map { it as Frame.Text }
                        .map { it.readText() }

                override suspend fun send(data: String) = session.send(Frame.Text(data))

                override suspend fun close() = session.close()
            }
        }
    }

    val rSubClient = RSubClient(connector)
    println("Client function finish")

    return rSubClient
}
