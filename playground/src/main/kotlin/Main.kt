import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import ru.falseteam.rsub.RSubOld
import kotlin.concurrent.thread
import kotlin.coroutines.coroutineContext

fun main() {
    runClientSever()
}


private fun runClientSever() {
    val srvThread = thread(name = "srv") { startServer() }
    Thread.sleep(1000)
    val clientThread = thread(name = "client") {
        val client = startClient()
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
