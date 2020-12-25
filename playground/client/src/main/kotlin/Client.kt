import io.ktor.client.*
import io.ktor.client.features.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun startClient() {
    println("Creating client")
    val client = HttpClient {
        install(WebSockets)
    }
    println("Client created")

    runBlocking {
        val session = client.webSocketSession(method = HttpMethod.Get, port = 8888, path = "/ws") {
            this.setAttributes {
                header(HttpHeaders.SecWebSocketProtocol, "rSub")
            }
        }

        with(session) {
            launch {
                send(Frame.Text("Hello from client"))
            }
            for (frame in incoming) {
                frame as Frame.Text
                println("Received from server: ${frame.readText()}")
            }
        }
    }

    println("Client function finish")

}
