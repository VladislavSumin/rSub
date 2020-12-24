import kotlin.concurrent.thread

fun main() {
    val srvThread = thread(name = "srv") { startServer() }
    Thread.sleep(1000)
    val clientThread = thread(name = "client") { startClient() }

    srvThread.join()
    clientThread.join()
}
