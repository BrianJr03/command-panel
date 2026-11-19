import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
}

// Fake Panel plugin (#21): a debug-only test tool for scripts/verify-plugin.sh. It speaks the Panel plugin contract
// with behavior modes set over adb, so the swap can be checked without ThorKBM. Platform only: no dependencies. Never
// shipped: the release variant is switched off below, and neither workflow uploads it.
android {
    namespace = "jr.brian.commandpanel.fakeplugin"
    compileSdk = 37

    defaultConfig {
        applicationId = "jr.brian.commandpanel.fakeplugin"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Debug only: no release variant, so `assembleRelease` (or a bare `assemble`) never builds it.
androidComponents {
    beforeVariants(selector().withBuildType("release")) { it.enable = false }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
