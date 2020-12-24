import kotlin.concurrent.thread

fun main() {
    val srvThread = thread(name = "srv") { startServer() }
    val clientThread = thread(name = "client") { startClient() }

    srvThread.join()
    clientThread.join()
}
