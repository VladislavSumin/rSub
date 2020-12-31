import kotlinx.coroutines.flow.Flow
import ru.falseteam.rsub.RSubInterface

@RSubInterface("test")
interface TestInterface {
    suspend fun testSimple(): String

    fun testFlow(): Flow<String>
}
