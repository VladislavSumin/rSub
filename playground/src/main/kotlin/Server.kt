import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import ru.falseteam.rsub.connector.ktorwebsocket.core.RSubConnectionKtorWebSocket
import ru.falseteam.rsub.server.RSubServer

fun startServer() {
    println("Starting server")

    val rSubServer = RSubServer()

    embeddedServer(Netty, port = 8888) {
        install(WebSockets)
        routing {
            webSocket(protocol = "rSub", path = "/ws") session@{
                rSubServer.handleNewConnection(RSubConnectionKtorWebSocket(this))
            }
        }
    }.start(true)
    println("Server function end")
}
