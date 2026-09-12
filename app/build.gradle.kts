import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import org.nigao.zhihulite.buildlogic.BusinessTraceClassVisitorFactory

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.serialization)
    id("com.google.devtools.ksp")
    alias(libs.plugins.room)
}

// Release signing, driven by CI environment variables (see .github/workflows/release.yml).
// Local builds stay unsigned when no keystore is provided.
val releaseKeystore = System.getenv("RELEASE_KEYSTORE_PATH")
    ?.let { file(it) }
    ?.takeIf { it.isFile }

room {
    // Exported schemas are committed so schema changes can be diffed and migrations tested;
    // exportSchema = true on the @Database class requires this location.
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "org.nigao.zhihuLite"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.nigao.zhihuLite"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = (project.findProperty("versionCode") as? String)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("versionName") as? String) ?: "1.0"
    }

    kotlinOptions {
        jvmTarget = "11"  // Match Java version
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("build/generated/ksp/src/main/kotlin")
        }
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD").orEmpty()
                keyAlias = System.getenv("RELEASE_KEY_ALIAS").orEmpty()
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD").orEmpty()
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        // Profiling-only variant (docs/perfetto-business-method-tracing.md): the same code, signed
        // with the debug key, under its own application id so it never overwrites the real install,
        // and without minification so trace names stay readable. Nothing in the app UI knows about
        // it — capture is driven from the shell by scripts/capture-perfetto.sh.
        create("perfetto") {
            initWith(getByName("release"))
            applicationIdSuffix = ".perfetto"
            versionNameSuffix = "-perfetto"
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        disable.add("NullSafeMutableLiveData")
    }

    testOptions {
        unitTests {
            // Robolectric needs the merged resources to build an Android Context in a JVM test.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.runtime)
    implementation(libs.compose.navigation)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.androidx.activity.compose)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    // Keep the KMP lifecycle wrappers pulled in by Coil 3 aligned with androidx lifecycle.
    implementation(libs.jetbrains.lifecycle.viewmodel)
    implementation(libs.jetbrains.lifecycle.runtimeCompose)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.napier.log)
    implementation(libs.multiplatform.settings)
    implementation(libs.multiplatform.settings.no.arg)
    implementation(libs.androidx.material3)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    testImplementation(libs.room.testing)

    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit.ktx)

    constraints {
        // Pin the transitive concurrent-futures pulled in via profileinstaller to a stable version.
        implementation(libs.androidx.concurrent.futures)
    }
}

android.sourceSets.all {
    java.srcDirs("build/generated/$name/kotlin")
}

// Adds android.os.Trace slices around every concrete method in the app package. The sections are
// compiled only into the dedicated perfetto build and appear in Perfetto as "BM:...".
// Disable temporarily with: ./gradlew :app:assemblePerfetto -PbusinessTraceEnabled=false
val businessTraceEnabled = providers.gradleProperty("businessTraceEnabled")
    .map { it.toBoolean() }
    .orElse(true)

androidComponents {
    onVariants(selector().withBuildType("perfetto")) { variant ->
        if (!businessTraceEnabled.get()) {
            return@onVariants
        }

        variant.instrumentation.transformClassesWith(
            BusinessTraceClassVisitorFactory::class.java,
            InstrumentationScope.PROJECT
        ) { parameters ->
            parameters.includedClassPrefix.set("org.nigao.zhihuLite")
            parameters.maxSectionNameLength.set(127)
        }
        variant.instrumentation.setAsmFramesComputationMode(
            FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS
        )
    }
}
