import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import kotlinx.coroutines.launch

fun startServer() {
    println("Starting server")
    embeddedServer(Netty, port = 8888) {
        install(WebSockets)
        routing {
            webSocket(protocol = "rSub", path = "/ws") {
                launch {
                    send(Frame.Text("Hello from server"))
                }
                for (frame in incoming) {
                    frame as Frame.Text
                    println("Received from client: ${frame.readText()}")
                }
            }
        }
    }.start(true)
    println("Server function end")
}
