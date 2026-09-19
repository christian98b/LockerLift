plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.lockerlift.core.sync"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(libs.androidx.room.runtime)

    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

tasks.withType<Test>().configureEach {
    val runE2e = (project.hasProperty("runE2eSync") && project.property("runE2eSync") == "true") ||
                 System.getProperty("runE2eSync") == "true" ||
                 System.getenv("RUN_E2E_SYNC") == "true"

    if (runE2e) {
        systemProperty("runE2eSync", "true")
    } else {
        exclude("**/e2e/**")
        exclude("**/*E2eSyncTest*")
    }
}
