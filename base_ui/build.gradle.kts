plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "org.nigao.zhihuLite.base_ui"

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
}

// Compose-level building blocks with no business meaning: modifier/string/color helpers and the
// shared image loader.
dependencies {
    api(libs.compose.ui)
    api(libs.compose.foundation)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // `toColorInt` from ColorExtender.
    implementation(libs.androidx.core.ktx)
    // The image loader type appears in signatures below this module, so Coil is part of the API.
    api(libs.coil.compose)
    implementation(libs.coil.network.ktor)
}
