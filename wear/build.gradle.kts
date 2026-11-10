plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.aboutlibraries)
}

android {
    namespace = "com.lockerlift.wear"
    compileSdk = providers.gradleProperty("lockerlift.compileSdk").get().toInt()

    defaultConfig {
        applicationId = "com.lockerlift"
        minSdk = providers.gradleProperty("lockerlift.wearMinSdk").get().toInt()
        targetSdk = providers.gradleProperty("lockerlift.targetSdk").get().toInt()
        // Play rejects two artifacts of package com.lockerlift with the same
        // versionCode. The watch uses a stable offset above the phone code.
        versionCode = providers.gradleProperty("lockerlift.versionCode").get().toInt() +
            providers.gradleProperty("lockerlift.wearVersionCodeOffset").get().toInt()
        versionName = providers.gradleProperty("lockerlift.versionName").get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                rootProject.file("proguard/lockerlift-release.pro")
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        compose = true
    }
    lint {
        // NullSafeMutableLiveData crashes lint under Kotlin 2.0 (KaCallableMemberCall
        // analysis-api mismatch); disable the broken detector until AGP/lint is upgraded.
        disable += "NullSafeMutableLiveData"
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:sync"))
    implementation(libs.aboutlibraries.core)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.play.services.wearable)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.navigation)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)

    implementation(libs.kotlinx.serialization.json)
}
