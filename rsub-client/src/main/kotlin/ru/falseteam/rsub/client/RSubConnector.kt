package ru.falseteam.rsub.client

import ru.falseteam.rsub.RSubConnection

interface RSubConnector {
    suspend fun connect(): RSubConnection
}
