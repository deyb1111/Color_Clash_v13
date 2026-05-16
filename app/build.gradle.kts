plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.colorclash"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.colorclash"
        // Lowered from 35 so the app installs on much older devices.
        // 23 (Android 6.0 Marshmallow) is the lowest the current AndroidX
        // (activity 1.13.0) dependencies allow; still covers >99% of devices.
        minSdk = 23
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}