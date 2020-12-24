import ru.falseteam.rsub.annotation.RSubFunction
import ru.falseteam.rsub.annotation.RSubInterface

@RSubInterface
class TestInterfaceImpl : TestInterface {
    @RSubFunction
    override fun testSimple(): String {
        return "Hello world"
    }
}
