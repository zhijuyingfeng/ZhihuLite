plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    id("com.google.devtools.ksp")
    alias(libs.plugins.room)
}

android {
    namespace = "org.nigao.zhihuLite.business_logic"

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

    sourceSets {
        getByName("main") {
            java.srcDirs("build/generated/ksp/src/main/kotlin")
        }
    }
}

android.sourceSets.all {
    java.srcDirs("build/generated/$name/kotlin")
}

room {
    // Exported schemas are committed so schema changes can be diffed and migrations tested;
    // exportSchema = true on the @Database class requires this location.
    schemaDirectory("$projectDir/schemas")
}

// Business rules, data access and the Zhihu protocol. It may use model + base_logic and nothing
// above it — in particular it cannot see business_ui, which is where the session/credential contract
// used to be declared (that was a real violation, now impossible).
dependencies {
    api(project(":model"))
    // `ZhihuApi.client` and `EventReporter(HttpClient)` are public, so Ktor is part of the API; the
    // engine only has to be present at runtime.
    implementation(libs.androidx.core.ktx)

    api(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.napier.log)
    implementation(libs.multiplatform.settings)
    implementation(libs.multiplatform.settings.no.arg)
    // `HtmlNode.Element.attributes` is an ImmutableMap, i.e. this type is part of the module's API.
    api(libs.kotlinx.collections.immutable)
    // ZhihuDatabase, the DAOs and the entities are public API of this module.
    api(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
}
