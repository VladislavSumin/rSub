package ru.falseteam.rsub

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Serializable
data class RSubMessage(
    val id: Int,
    val type: Type,
    val payload: JsonElement? = null
) {
    companion object {
        private val mapper = Json
        fun fromJson(json: String): RSubMessage = mapper.decodeFromString(json)
    }

    fun toJson(): String = mapper.encodeToString(this)

    enum class Type {
        SUBSCRIBE,
        UNSUBSCRIBE,
        DATA,
        ERROR,
    }
}
