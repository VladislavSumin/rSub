import ru.falseteam.rsub.annotation.RSubFunction
import ru.falseteam.rsub.annotation.RSubInterface

@RSubInterface
interface TestInterface {
    @RSubFunction
    suspend fun testSimple(): String
}
