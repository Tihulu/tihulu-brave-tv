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
        versionCode = 9
        versionName = "0.3.4-brave-adblock-debug"
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
