import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class TestInterfaceImpl : TestInterface {
    override suspend fun testSimple(): String {
        delay(6000)
        return "Hello world"
    }

    override fun testFlow(): Flow<String> = flowOf("Test")
}
