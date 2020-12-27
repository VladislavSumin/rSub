package ru.falseteam.rsub

import kotlinx.serialization.Serializable

@Serializable
data class RSubSubscribeMessage(
    val interfaceName: String,
    val functionName: String,
)
