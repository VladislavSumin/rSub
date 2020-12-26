import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import ru.falseteam.rsub.annotation.RSubFunction
import ru.falseteam.rsub.annotation.RSubInterface

@RSubInterface
class TestInterfaceImpl : TestInterface {
    @RSubFunction
    override suspend fun testSimple(): String {
        return "Hello world"
    }

    override fun testFlow(): Flow<String> = flowOf("Test")
}
