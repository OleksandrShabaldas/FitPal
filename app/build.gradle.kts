plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.fitpal.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fitpal.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // Bundling MediaPipe + TFLite Task Library can ship duplicate native/license
    // files. Pick one copy so the build doesn't fail on collisions.
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
        }
        jniLibs {
            pickFirsts += "**/libc++_shared.so"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // Navigation
    implementation(libs.navigation.compose)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Room — local SQLite database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Hilt — dependency injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // CameraX — camera access
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // MediaPipe — on-device multimodal LLM (Gemma 3n: vision + text)
    implementation(libs.mediapipe.genai)
    // Provides the MPImage / BitmapImageBuilder classes for sending photos to the model
    implementation(libs.mediapipe.vision)

    // ML Kit — offline barcode scanning
    implementation(libs.mlkit.barcode)

    // Health Connect — read steps that Samsung Health writes (all on-device, offline)
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07")

    // Coroutines
    implementation(libs.coroutines.android)

    // Coil — image loading for Compose
    implementation(libs.coil.compose)

    // Haze — real frosted-glass blur behind the floating nav (offline rendering)
    implementation(libs.haze)

    // Android core
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)

    // Wear OS companion: the shared wire contract, the Data Layer client (receive watch
    // commands / push the stats snapshot), and WorkManager (run watch-triggered AI jobs as
    // expedited work, since a wearable message can't legally start a foreground service on 12+).
    implementation(project(":shared"))
    implementation(libs.play.services.wearable)
    implementation(libs.coroutines.play.services)
    implementation(libs.androidx.work.runtime)
}
