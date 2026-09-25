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
        versionCode = 11
        versionName = "0.3.6-brave-adblock"
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


dependencies {
    implementation("androidx.webkit:webkit:1.14.0")
}
