import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun startServer() {
    println("Starting server")
    embeddedServer(Netty, port = 8888) {

    }.start(true)
    println("Server function end")
}
