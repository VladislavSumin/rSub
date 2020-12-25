import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ru.falseteam.rsub.RSubOld
import kotlin.concurrent.thread
import kotlin.coroutines.coroutineContext

fun main() {
    runClientSever()
}


private fun runClientSever() {
    val srvThread = thread(name = "srv") { startServer() }
    Thread.sleep(1000)
    val clientThread = thread(name = "client") { startClient() }

    srvThread.join()
    clientThread.join()
}
