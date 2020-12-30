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
    with(Dependencies.Kotlin) {
        api(stdLibJdk8)
        api(reflect)
        api(coroutines)
        api(serialization)
    }

    with(Dependencies) {
        api(slf4j)
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "1.8"
    }
}
