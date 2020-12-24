import ru.falseteam.rsub.RSub
import kotlin.concurrent.thread

fun main() {
createRSub()
}

private fun createRSub() {
    RSub(listOf(TestInterfaceImpl()))
}

private fun runClientSever() {
    val srvThread = thread(name = "srv") { startServer() }
    Thread.sleep(1000)
    val clientThread = thread(name = "client") { startClient() }

    srvThread.join()
    clientThread.join()
}
