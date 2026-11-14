plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.densitytaskbar"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.densitytaskbar"
        minSdk = 26
        targetSdk = 33   // 33 để không cần khai báo foregroundServiceType
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies { }
