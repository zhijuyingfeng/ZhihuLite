plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
}

android {
    namespace = "org.nigao.zhihuLite.performance"
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

// Support module, not a layer: the annotation lower layers may use to opt out of business tracing,
// and the runtime bridge the perfetto variant's instrumented bytecode calls. It depends on nothing of
// ours and holds no logic.
dependencies {
}
