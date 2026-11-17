import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
}

// Thor capability probe (separate APK, jr.brian.thorprobe). Debug tool only; never shipped with the app.
// targetSdk stays at 35, the value the recorded results in docs/reference/PROBE_RESULTS.md were measured with.
android {
    namespace = "jr.brian.thorprobe"
    compileSdk = 37

    defaultConfig {
        applicationId = "jr.brian.thorprobe"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
}
