plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.proximi.proximiiowebandroid"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.proximi.proximiiowebandroid"
        minSdk = 26  // Updated to support adaptive icons
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

// Force dependency versions to prevent conflicts across the entire project
configurations.all {
    resolutionStrategy.force("androidx.core:core:1.12.0")
    resolutionStrategy.force("androidx.core:core-ktx:1.12.0")
    resolutionStrategy.eachDependency {
        if (requested.group == "androidx.core") {
            when (requested.name) {
                "core", "core-ktx" -> useVersion("1.12.0")
            }
        }
        if (requested.group == "androidx.lifecycle" && requested.name.startsWith("lifecycle")) {
            useVersion("2.8.6")
        }
        if (requested.group == "androidx.activity" && requested.name.startsWith("activity")) {
            useVersion("1.8.2")
        }
    }
}

dependencies {
    implementation("io.proximi.library:core:5.3.0")

    // Explicit androidx.core dependencies to ensure version alignment
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
