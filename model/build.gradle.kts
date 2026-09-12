import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.serialization)
}

// Bottom layer: serializable DTOs only. It depends on nothing in this project — that is the rule the
// module boundary now enforces, and the reason every other module can depend on it.
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    // The DTOs are @Serializable and callers decode them, so the serialization runtime is part of
    // this module's API rather than an implementation detail.
    api(libs.kotlinx.serialization.core)
    api(libs.kotlinx.serialization.json)
}
