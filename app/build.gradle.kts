
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.serialization)
    id("com.google.devtools.ksp")
}
android {
    namespace = "org.nigao.zhihuLite"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.nigao.zhihuLite"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
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

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
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
}

dependencies {
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.junit.ktx)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.runtime)
    implementation(libs.compose.navigation)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.androidx.activity.compose)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlin.testJunit)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.navigation.compose)
    implementation(libs.napier.log)
    implementation(libs.multiplatform.settings)
    implementation(libs.multiplatform.settings.no.arg)
    implementation(libs.datetime)
    implementation(libs.androidx.material3)
    implementation(project(":app:gaia"))
    ksp(project(":app:gaia"))
}

android.sourceSets.all {
    java.srcDirs("build/generated/$name/kotlin")
}
