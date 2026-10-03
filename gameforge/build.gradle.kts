plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.llgl.gameforge"
    // Compose 1.12 (BOM 2026.09.00) requires compiling against API 37; the SDK ships it as 37.2.
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "com.llgl.gameforge"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // The LLM runtime ships ~100 MB of native code for four ABIs; phones that can run Gemma are all arm64.
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    signingConfigs {
        // The same checked-in debug key as the other two apps, so debug builds from any machine update each other.
        getByName("debug") {
            storeFile = file("../app/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            excludes += listOf("**/x86/**", "**/x86_64/**", "**/armeabi-v7a/**")
            // Store the 27 MB inference library compressed and let the installer extract it: the APK
            // drops from ~38 MB to well under 30 MB, which keeps sideloading and artifact limits happy.
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.mediapipe.tasks.genai)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
