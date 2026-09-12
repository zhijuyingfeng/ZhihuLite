import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import org.nigao.zhihulite.buildlogic.BusinessTraceClassVisitorFactory

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
}

// Release signing, driven by CI environment variables (see .github/workflows/release.yml).
// Local builds stay unsigned when no keystore is provided.
val releaseKeystore = System.getenv("RELEASE_KEYSTORE_PATH")
    ?.let { file(it) }
    ?.takeIf { it.isFile }

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

// The application module is the only place allowed to know every layer: it assembles the container
// and the navigation graph. Everything it *uses* comes in through the module boundary, so a wrong
// dependency direction is a compile error instead of a convention.
dependencies {
    implementation(project(":business_ui"))
    implementation(project(":business_logic"))
    implementation(project(":performance"))

    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.androidx.material3)
    implementation(libs.compose.navigation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.napier.log)

    // One unit-test suite, in the module that can see everything. It drives the real layers (Room,
    // the repositories, the ViewModels), which is why the few test seams it needs are public rather
    // than internal.
    testImplementation(project(":model"))
    testImplementation(project(":base_logic"))
    testImplementation(project(":base_navigation"))
    testImplementation(project(":base_ui"))
    testImplementation(project(":business_logic"))
    testImplementation(project(":business_ui"))
    // The tests build in-memory Room databases and decode payloads directly.
    testImplementation(libs.room.runtime)
    testImplementation(libs.room.testing)
    testImplementation(libs.kotlinx.serialization.json)

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

// Adds android.os.Trace slices around every concrete method in the app package. The sections are
// compiled only into the dedicated perfetto build and appear in Perfetto as "BM:...".
// Disable temporarily with: ./gradlew :assemble:assemblePerfetto -PbusinessTraceEnabled=false
val businessTraceEnabled = providers.gradleProperty("businessTraceEnabled")
    .map { it.toBoolean() }
    .orElse(true)

androidComponents {
    onVariants(selector().withBuildType("perfetto")) { variant ->
        if (!businessTraceEnabled.get()) {
            return@onVariants
        }

        // ALL, not PROJECT: the code being traced now lives in library modules, so the transform has
        // to cover the dependencies too. (With a single module, PROJECT happened to mean "all of it".)
        variant.instrumentation.transformClassesWith(
            BusinessTraceClassVisitorFactory::class.java,
            InstrumentationScope.ALL
        ) { parameters ->
            parameters.includedClassPrefix.set("org.nigao.zhihuLite")
            parameters.maxSectionNameLength.set(127)
        }
        variant.instrumentation.setAsmFramesComputationMode(
            FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS
        )
    }
}
