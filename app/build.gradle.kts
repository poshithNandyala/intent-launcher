// Same sources and settings as ./build.sh (keep the versions in sync).
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.poshith.intent"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.poshith.intent"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "1.5.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // Usage access is granted by the user in Settings, which lint can't know.
        disable += "ProtectedPermissions"
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
