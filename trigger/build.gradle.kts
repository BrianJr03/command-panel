import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
}

// TCP Save Clip (separate APK, jr.brian.tcp.saveclip). Its launcher activity asks Command Panel to save or arm the
// Replay Buffer, then closes. Platform only: no dependencies.
android {
    namespace = "jr.brian.tcp.saveclip"
    compileSdk = 37

    defaultConfig {
        applicationId = "jr.brian.tcp.saveclip"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        // No applicationIdSuffix on debug: one trigger targets both Command Panel builds.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
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
