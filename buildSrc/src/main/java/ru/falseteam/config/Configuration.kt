package ru.falseteam.config

object Configuration {
    object Versions {
        const val kotlin = "1.4.20"
        const val ktor = "1.5.0"
        const val log4j2 = "2.13.3"
    }

    object Dependencies {
        object Kotlin {
            const val stdLibJdk8 = "org.jetbrains.kotlin:kotlin-stdlib-jdk8:${Versions.kotlin}"
            const val reflect = "org.jetbrains.kotlin:kotlin-reflect:${Versions.kotlin}"
        }

        const val ktorServer = "io.ktor:ktor-server-netty:${Versions.ktor}"
        const val ktorClient = "io.ktor:ktor-client-okhttp:${Versions.ktor}"
        const val ktorWebSocket = "io.ktor:ktor-websockets:${Versions.ktor}"
        const val ktorSerialization = "io.ktor:ktor-serialization:${Versions.ktor}"

        const val log4j2Api = "org.apache.logging.log4j:log4j-api:${Versions.log4j2}"
        const val log4j2Core = "org.apache.logging.log4j:log4j-core:${Versions.log4j2}"
    }
}