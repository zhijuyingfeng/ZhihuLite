plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation(libs.napier.log)
    implementation(libs.symbol.processing.api)
    implementation(libs.kotlinpoet)
}