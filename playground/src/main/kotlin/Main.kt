import kotlinx.coroutines.runBlocking
import ru.falseteam.rsub.RSubOld
import kotlin.concurrent.thread

fun main() {
    createRSub()
}

private fun createRSub() {
    runBlocking {
        RSubOld(listOf(TestInterfaceImpl())).getInterface<TestInterface>().testSimple()
    }
}

private fun runClientSever() {
    val srvThread = thread(name = "srv") { startServer() }
    Thread.sleep(1000)
    val clientThread = thread(name = "client") { startClient() }

    srvThread.join()
    clientThread.join()
}
