import kotlinx.coroutines.flow.Flow

interface TestInterface {
    suspend fun testSimple(): String

    fun testFlow(): Flow<String>
}
