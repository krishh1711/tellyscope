// SPDX-License-Identifier: GPL-3.0-only

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "app.tellyscope.helper"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.tellyscope.helper"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
