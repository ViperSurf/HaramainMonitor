plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.haramain.monitor"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    ndkVersion = "26.3.11579264"

    defaultConfig {
        applicationId = "com.haramain.monitor"
        minSdk = 26          // AAudio low-latency path is used on API 27+; Oboe falls back to OpenSL ES below that.
        targetSdk = 35
        versionCode = 25
        versionName = "3.7"

        // Build native code only for real device ABIs (+ x86_64 for emulators).
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-O3", "-Wall")
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
    }

    // Release signing reads optional environment variables (for Play Store /
    // private CI signing); otherwise falls back to the committed dev-only
    // keystore so local and dev-latest builds install over each other.
    val devKeystore = rootProject.file("keystore/debug.keystore")
    val relStoreFile = System.getenv("RELEASE_STORE_FILE")?.takeIf { it.isNotBlank() }?.let { file(it) }
    signingConfigs {
        if (devKeystore.exists()) {
            create("devDebug") {
                storeFile = devKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        if (relStoreFile != null && relStoreFile.exists()) {
            create("releaseEnv") {
                storeFile = relStoreFile
                storePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: ""
                keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: ""
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            // v2.7: dev-latest ships THIS build. A non-debuggable build is
            // dramatically smoother for Compose UI than the debug build
            // (no JDWP, full ART optimization). Minify stays OFF for now:
            // the JNI surface makes R8 keep-rules a separate careful step.
            isMinifyEnabled = false
            signingConfig = when {
                signingConfigs.findByName("releaseEnv") != null -> signingConfigs.getByName("releaseEnv")
                signingConfigs.findByName("devDebug") != null -> signingConfigs.getByName("devDebug")
                else -> null
            }
        }
        debug {
            isMinifyEnabled = false
            if (signingConfigs.findByName("devDebug") != null) {
                signingConfig = signingConfigs.getByName("devDebug")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        compose = true
        prefab = true        // Required for Oboe Prefab (find_package(oboe) in CMake).
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Google Oboe — low-latency native audio (AAudio / OpenSL ES abstraction).
    implementation("com.google.oboe:oboe:1.9.0")

    // Jetpack Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // AndroidX core / lifecycle
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    // DataStore — custom preset + last-used parameter persistence.
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // JVM unit tests
    testImplementation("junit:junit:4.13.2")
}
