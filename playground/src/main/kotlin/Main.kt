import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.features.websocket.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import ru.falseteam.rsub.RSubClient
import ru.falseteam.rsub.connector.ktorwebsocket.client.RSubConnectorKtorWebSocket
import ru.falseteam.rsub.connector.ktorwebsocket.server.rSubWebSocket
import ru.falseteam.rsub.server.RSubServer
import kotlin.concurrent.thread

fun main() {
    runClientSever()
}

private fun runClientSever() {
    val srvThread = thread(name = "srv") { startServer() }
    Thread.sleep(1000)
    val clientThread = thread(name = "client") {
        val client = createRSubClient()
        runBlocking {
            val job = launch {
                client.observeConnection().collect {
                    println("New status $it")
                }
            }
            delay(1000)
            job.cancel()
        }
    }

    srvThread.join()
    clientThread.join()
}

private fun startServer() {
    val rSubServer = RSubServer()
    embeddedServer(Netty, port = 8080) {
        install(io.ktor.websocket.WebSockets)
        routing {
            rSubWebSocket(rSubServer)
        }
    }.start(true)
}

private fun createRSubClient(): RSubClient {
    val client = HttpClient {
        install(WebSockets)
    }
    return RSubClient(RSubConnectorKtorWebSocket(client))
}
