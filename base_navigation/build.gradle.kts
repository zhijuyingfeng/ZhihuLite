import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.serialization)
}

// Cross-layer navigation vocabulary: the route objects and the AppNavigator contract. Depends on
// nothing of ours so both business_ui (which raises routes) and assemble (which registers the graph)
// can use it without either depending on the other.
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    api(libs.kotlinx.serialization.core)
}
