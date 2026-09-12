rootProject.name = "ZhihuLite"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// One Gradle module per layer. The module boundary *is* the layering rule: a forbidden reference
// (e.g. business_logic -> business_ui) is not on the compile classpath, so it does not build at all.
// modules: model -> base_logic -> base_ui -> business_logic -> business_ui -> assemble, plus
// base_navigation for the route vocabulary and performance for the tracing bridge/annotation.
include(":model")
include(":base_logic")
include(":base_navigation")
include(":base_ui")
include(":business_logic")
include(":business_ui")
include(":performance")
include(":assemble")
