import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.lang.RuntimeException

class TestInterfaceImpl : TestInterface {
    override suspend fun testSimple(): String {
        delay(6000)
        throw RuntimeException("Whoooops!")
        return "Hello world"
    }

    override fun testFlow(): Flow<String> = flowOf("Test")
}
