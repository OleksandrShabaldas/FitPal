plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.fitpal.shared"
    compileSdk = 35

    defaultConfig {
        // Must be <= the lowest minSdk of any consumer (phone = 28).
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // DataMap lives in the wearable client — both phone and watch already depend on it, so the
    // shared snapshot mappers can speak DataMap directly (no extra JSON layer). `api` so the
    // DataMap types in StatsSnapshot's public methods are visible to consumers.
    api(libs.play.services.wearable)
}
