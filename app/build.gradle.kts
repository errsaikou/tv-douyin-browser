plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.tvdouyin"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.tvdouyin"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.leanback:leanback:1.0.0")

    // NanoHTTPD - Lightweight embedded HTTP server (~80KB)
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // Java-WebSocket - WebSocket server for real-time phone communication
    implementation("org.java-websocket:Java-WebSocket:1.5.6")

    // ZXing - QR code generation for phone scanning
    implementation("com.google.zxing:core:3.5.3")
}
