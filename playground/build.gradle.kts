import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import ru.falseteam.config.RSubConfiguration.Dependencies

plugins {
    java
    kotlin("jvm") version ru.falseteam.config.RSubConfiguration.Versions.kotlin
    kotlin("plugin.serialization") version ru.falseteam.config.RSubConfiguration.Versions.kotlin
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

group = "ru.falseteam.myhome"
version = "0.1.0"

dependencies {
    implementation(project(":connectors:rsub-ktor-websocket-connector-server"))
    implementation(project(":connectors:rsub-ktor-websocket-connector-client"))

    with(Dependencies) {
        implementation(ktorServer)
        implementation(ktorClient)
        implementation(ktorSerialization)

        implementation(log4j2Api)
        implementation(log4j2Core)
        implementation(log4jSlf4jImpl)
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "1.8"
    }
}
