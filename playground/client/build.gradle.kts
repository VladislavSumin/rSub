import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import ru.falseteam.config.Configuration.Dependencies

plugins {
    java
    kotlin("jvm")
    kotlin("plugin.serialization")
}

repositories {
    mavenCentral()
    jcenter()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

group = "ru.falseteam.myhome"
version = "0.1.0"

dependencies {
    with(Dependencies.Kotlin) {
        implementation(stdLibJdk8)
        implementation(reflect)
    }
    with(Dependencies) {
        implementation(ktorClient)
        implementation(ktorWebSocket)
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
