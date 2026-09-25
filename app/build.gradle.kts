plugins {
    id("com.android.application")
}

android {
    namespace = "com.tihulu.tvlite"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tihulu.tvlite"
        minSdk = 23
        targetSdk = 35
        versionCode = 3
        versionName = "0.2.1"
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
}
