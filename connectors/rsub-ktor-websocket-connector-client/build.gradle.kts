import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import ru.falseteam.config.RSubConfiguration.Dependencies

plugins {
    kotlin("jvm") version ru.falseteam.config.RSubConfiguration.Versions.kotlin
    kotlin("plugin.serialization") version ru.falseteam.config.RSubConfiguration.Versions.kotlin
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    api(project(":rsub-client"))
    api(project(":connectors:rsub-ktor-websocket-connector-core"))
    with(Dependencies) {
        api(ktorClientWebSocket)
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "1.8"
    }
}
