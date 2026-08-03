plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.fitpal.wear"
    compileSdk = 35

    defaultConfig {
        // Same applicationId as the phone app — required so the Wearable Data Layer routes between
        // them and so Google Play auto-delivers this watch app alongside the phone install.
        applicationId = "com.fitpal.app"
        // Wear OS 3+ (Galaxy Watch 5 Pro ships with Wear OS 3.5+).
        minSdk = 30
        targetSdk = 35
        // Must increase every release, same as the phone app — see :app's build file.
        versionCode = 8
        versionName = "1.5.2"
    }

    buildTypes {
        release {
            // Keep R8 off: the Tiles / complications / Health Services entry points are resolved
            // reflectively by the system, and stripping them silently breaks the tile.
            isMinifyEnabled = false
            // Sign with the debug key so a sideloaded release build still pairs over the Data
            // Layer (phone + watch must share a signing key, and the phone is a debug build).
            signingConfig = signingConfigs.getByName("debug")
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
}

dependencies {
    // Shared wire contract + StatsSnapshot (with its DataMap mappers).
    implementation(project(":shared"))

    // Compose (managed by the same BOM the phone uses for the base UI artifacts).
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // Wear Compose (watch-specific UI: ScalingLazyColumn, Chip, TimeText, CircularProgressIndicator).
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)

    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)

    // Wearable Data Layer (send commands to the phone, read the stats snapshot).
    implementation(libs.play.services.wearable)

    // Voice / keyboard text capture on the watch.
    implementation(libs.wear.input)

    // Tiles + ProtoLayout (water quick-log tile).
    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)
    implementation(libs.wear.protolayout.material)

    // Complications (water quick-log + read-only calories-left).
    implementation(libs.wear.complications.datasource)

    // Bridges the tile's suspend snapshot read into the ListenableFuture the Tiles API expects.
    implementation(libs.androidx.concurrent.futures)

    // Health Services — the watch's own daily step count, reported to the phone so step totals
    // don't depend on Samsung Health writing watch data into Health Connect.
    implementation(libs.health.services.client)

    // health-services-client transitively pulls full Guava (runtime-scoped, so it wasn't visible
    // on the compile classpath on its own) while androidx.concurrent:concurrent-futures-ktx (used
    // by the water tile's CallbackToFutureAdapter) depends on the separate small
    // "com.google.guava:listenablefuture" stub for the same ListenableFuture class. Declaring full
    // Guava directly puts the real class on BOTH classpaths; excluding the stub everywhere (below)
    // leaves exactly one copy, avoiding a duplicate-class error at merge time.
    implementation("com.google.guava:guava:31.1-android")
}

configurations.all {
    exclude(group = "com.google.guava", module = "listenablefuture")
}
