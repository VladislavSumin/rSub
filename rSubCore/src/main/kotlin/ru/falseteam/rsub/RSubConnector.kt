package ru.falseteam.rsub

interface RSubConnector {
    suspend fun connect(): RSubConnection
}
