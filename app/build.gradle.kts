plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.intent.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.intent.launcher"
        minSdk = 26
        targetSdk = 34
        versionCode = 22
        versionName = "0.0.22"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Stable sideload key so GitHub/raw artifacts update via native Package Installer.
    // Ephemeral ~/.android/debug.keystore differs per machine and causes
    // INSTALL_FAILED_UPDATE_INCOMPATIBLE ("App not installed") on upgrade.
    signingConfigs {
        create("sideload") {
            storeFile = rootProject.file("keystore/intent-sideload.jks")
            storePassword = "intent-sideload"
            keyAlias = "intent-sideload"
            keyPassword = "intent-sideload"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        // Published artifacts use the "debug" variant name historically, but must NOT set
        // android:debuggable — PackageInstaller rejects debuggable APKs with
        // INSTALL_FAILED_TEST_ONLY while ApkMirror Installer still succeeds.
        debug {
            isDebuggable = false
            signingConfig = signingConfigs.getByName("sideload")
        }
        // Local engineering builds that need a debugger (adb Studio attach).
        create("engineering") {
            initWith(getByName("debug"))
            isDebuggable = true
            matchingFallbacks += listOf("debug")
        }
        release {
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sideload")
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
