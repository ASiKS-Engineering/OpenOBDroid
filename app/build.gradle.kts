/**
 * This build file defines the configuration for the Android application module.
 * It includes plugin applications, Android-specific configurations, and dependency management.
 */

plugins {
    // Standard plugin for building Android applications
    id("com.android.application")
    // Kotlin plugin for Android development
    id("org.jetbrains.kotlin.android")
    // Compose Compiler plugin for Kotlin 2.0+
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    /**
     * The namespace is used as a unique identifier for generated classes like R and BuildConfig.
     * It should match your package name.
     */
    namespace = "com.openobdroid.app"

    /**
     * compileSdk specifies the Android API level that Gradle should use to compile your app.
     * This means your app can use the features of this API level and lower.
     */
    compileSdk = 35

    defaultConfig {
        // Unique identifier for the application on the Google Play Store
        applicationId = "com.openobdroid.app"
        // Minimum API level required to run the app
        minSdk = 24
        // Target API level used for testing and optimizing the app
        targetSdk = 35
        // Internal version number used to determine which version is more recent
        versionCode = 2
        // Publicly visible version string
        versionName = "1.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        // Enables support for Jetpack Compose
        compose = true
        // Enables generation of BuildConfig class
        buildConfig = true
    }

    packaging {
        jniLibs {
            /**
             * useLegacyPackaging = true ensures that native libraries (.so files) 
             * are stored uncompressed in the APK, which is sometimes required for 
             * certain hardware-specific libraries like FTDI d2xx.
             */
            useLegacyPackaging = true
        }
    }

    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "OpenOBDroid.apk"
        }
    }
}

dependencies {
    // Core Android KTX for Kotlin-friendly APIs
    implementation("androidx.core:core-ktx:1.15.0")
    // Integration with Activity for Compose
    implementation("androidx.activity:activity-compose:1.10.0")
    // ViewModel support for Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    // Material Design 3 components for Compose
    implementation("androidx.compose.material3:material3:1.3.1")
    // Extended icons for Compose
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    // Material components for Android (required for XML-based themes)
    implementation("com.google.android.material:material:1.12.0")

    // Kotlin Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    /**
     * Local library dependency for FTDI D2XX driver.
     * Ensure the d2xx.jar file is present in the 'libs' directory.
     */
    implementation(files("libs/d2xx.jar"))
}
