import io.ktor.client.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlin.concurrent.thread

fun main() {
    val srvThread = thread(name = "srv") { server() }
    val clientThread = thread(name = "client") { client() }

    srvThread.join()
    clientThread.join()
}

private fun server() {
    println("Starting server")
    embeddedServer(Netty, port = 8888) {

    }.start(true)
    println("Server function end")
}

private fun client() {
    println("Creating client")
    HttpClient() {

    }
    println("Client created")
}
