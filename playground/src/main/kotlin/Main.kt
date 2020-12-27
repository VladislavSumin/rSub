import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.features.websocket.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import ru.falseteam.rsub.client.RSubClient
import ru.falseteam.rsub.connector.ktorwebsocket.client.RSubConnectorKtorWebSocket
import ru.falseteam.rsub.connector.ktorwebsocket.server.rSubWebSocket
import ru.falseteam.rsub.server.RSubServer
import kotlin.reflect.typeOf

fun main() {
    runClientSever()
}

private fun runClientSever() {
    val server = startServer()
    val httpClient = createHttpClient()
    val rSubClient = createRSubClient(httpClient)

    val proxy = rSubClient.getProxy<TestInterface>()
    val proxy2 = rSubClient.getProxy<TestInterface>()

    runBlocking {
//        val job = launch {
//            rSubClient.observeConnection().collect {
//                println("New status $it")
//            }
//        }
        launch { println(proxy.testSimple()) }
        println(proxy.testSimple())
    }

    httpClient.close()
    server.stop(100L, 100L)
}

private fun startServer(): NettyApplicationEngine {
    val rSubServer = RSubServer()
    rSubServer.registerImpl(TestInterfaceImpl(), "TestInterface")
    return embeddedServer(Netty, port = 8080) {
        install(io.ktor.websocket.WebSockets)
        routing {
            rSubWebSocket(rSubServer)
        }
    }.start(false)
}

private fun createHttpClient(): HttpClient {
    return HttpClient {
        install(WebSockets)
    }
}

private fun createRSubClient(client: HttpClient): RSubClient {
    return RSubClient(RSubConnectorKtorWebSocket(client))
}
