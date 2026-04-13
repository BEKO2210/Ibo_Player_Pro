plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.premiumtvplayer.app"
    compileSdk = 34

    defaultConfig {
        // ⚠ Locked product decision (Run 11):
        // applicationId MUST match BILLING_ANDROID_PACKAGE_NAME in services/api.
        applicationId = "com.premiumtvplayer.app"
        // minSdk = 26 keeps us on Adaptive Icons (no PNG launcher fallback
        // matrix needed) and matches the Google TV install base — the
        // sub-26 Android TV population is functionally zero in 2026.
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // ── BuildConfig fields read by NetworkModule + FirebaseModule ──
        // Defaults are dev placeholders; override via local.properties or
        // CI env (-PapiBaseUrl=... -PfirebaseApiKey=... etc).
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"" + (project.findProperty("apiBaseUrl") as String? ?: "http://10.0.2.2:3000/v1/") + "\""
        )
        buildConfigField(
            "String",
            "FIREBASE_API_KEY",
            "\"" + (project.findProperty("firebaseApiKey") as String? ?: "REPLACE_ME_FIREBASE_WEB_API_KEY") + "\""
        )
        buildConfigField(
            "String",
            "FIREBASE_PROJECT_ID",
            "\"" + (project.findProperty("firebaseProjectId") as String? ?: "REPLACE_ME_FIREBASE_PROJECT_ID") + "\""
        )
        buildConfigField(
            "String",
            "FIREBASE_APPLICATION_ID",
            "\"" + (project.findProperty("firebaseApplicationId") as String? ?: "1:000000000000:android:0000000000000000000000") + "\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api",
            "-opt-in=androidx.tv.foundation.ExperimentalTvFoundationApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
        )
    }

    buildFeatures {
        compose = true
        // BuildConfig generation needs to be opted in on AGP 8+; we use
        // it for API_BASE_URL + Firebase Options (see defaultConfig).
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core + lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Activity + Navigation
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    // Compose for TV
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Media3 / ExoPlayer (wired here so playback in Run 16 doesn't need a build change)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)

    // Networking + serialization (used by API client in Run 13)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.logging)

    // Storage (used in Run 14 home / Run 15 sources)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Firebase Auth (used in Run 13 onboarding)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
