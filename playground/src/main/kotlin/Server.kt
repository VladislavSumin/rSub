import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import ru.falseteam.rsub.RSubConnection
import ru.falseteam.rsub.server.RSubServer

fun startServer() {
    println("Starting server")

    val rSubServer = RSubServer()

    embeddedServer(Netty, port = 8888) {
        install(WebSockets)
        routing {
            webSocket(protocol = "rSub", path = "/ws") session@{
                val session = this
                rSubServer.handleNewConnection(object : RSubConnection {
                    override val receive: Flow<String>
                        get() = session.incoming.receiveAsFlow()
                            .map { it as Frame.Text }
                            .map { it.readText() }

                    override suspend fun send(data: String) = session.send(Frame.Text(data))

                    override suspend fun close() = session.close()
                })
            }
        }
    }.start(true)
    println("Server function end")
}
