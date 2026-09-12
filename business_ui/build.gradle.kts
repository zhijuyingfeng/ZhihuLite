plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "org.nigao.zhihuLite.business_ui"

    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    kotlinOptions { jvmTarget = "11" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    lint {
        // The bundled lifecycle detector (`NonNullableMutableLiveDataDetector`) crashes lint on
        // Kotlin 2.1 + AGP 8.7 (`IncompatibleClassChangeError`). Disabled in every module now that
        // libraries run lintVitalAppRelease-class checks too.
        disable.add("NullSafeMutableLiveData")
    }

    buildFeatures {
        // HtmlRenderer logs parser diagnostics behind `BuildConfig.DEBUG`.
        buildConfig = true
    }
}

// Screens, ViewModels and their state. It may use every layer below it; it deliberately cannot see
// `assemble`, so a screen cannot reach for the container or the navigation graph directly (the
// *Wiring contracts in `Wiring.kt` are the only door).
dependencies {
    api(project(":business_logic"))
    api(project(":base_ui"))
    api(project(":base_logic"))
    api(project(":base_navigation"))

    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    // Keep the KMP lifecycle wrappers pulled in by Coil 3 aligned with androidx lifecycle.
    implementation(libs.jetbrains.lifecycle.viewmodel)
    implementation(libs.jetbrains.lifecycle.runtimeCompose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor)
    implementation(libs.napier.log)
    // HtmlRenderer builds persistent maps for its memoised style lookups.
    implementation(libs.kotlinx.collections.immutable)
}
