import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

class TestInterfaceImpl : TestInterface {
    override suspend fun testSimple(): String {
        return "Hello world"
    }

    override fun testFlow(): Flow<String> = flowOf("Test")
}
