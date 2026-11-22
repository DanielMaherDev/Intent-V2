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
        versionCode = 170
        versionName = "0.0.170"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Live OpenAI key wiring — never commit secrets or print values.
        // Canonical Cursor / CI secret name: OPENAPI_GENERAL
        // Resolution order (first non-blank wins for presence):
        //   1) env OPENAPI_GENERAL (Cloud Agent / CI)
        //   2) gitignored local.properties OPENAPI_GENERAL=...
        //   3) -Popenapi.general=...
        // Raw key is embedded into BuildConfig ONLY when
        //   -Pintent.embed.openai.key=true
        // Public / PR APKs omit embed (empty string). On-device key also via
        // EncryptedSharedPreferences after Settings opt-in.
        val localPropsFile = rootProject.file("local.properties")
        val localProps: Map<String, String> = if (localPropsFile.exists()) {
            localPropsFile.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
                .associate { line ->
                    val key = line.substringBefore("=").trim()
                    val value = line.substringAfter("=").trim()
                    key to value
                }
        } else {
            emptyMap()
        }
        fun escapeBuildConfigString(raw: String): String =
            raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "").replace("\r", "")

        val fromEnv = System.getenv("OPENAPI_GENERAL").orEmpty().trim()
        val fromLocal = localProps["OPENAPI_GENERAL"].orEmpty().trim()
        val fromProp = (project.findProperty("openapi.general") as? String).orEmpty().trim()
        val resolvedSecret = when {
            fromEnv.isNotEmpty() -> fromEnv
            fromLocal.isNotEmpty() -> fromLocal
            fromProp.isNotEmpty() -> fromProp
            else -> ""
        }
        val embedKey =
            (project.findProperty("intent.embed.openai.key") as? String)
                ?.equals("true", ignoreCase = true) == true
        val openAiApiKey = if (embedKey) resolvedSecret else ""
        val localKeyFlag = localProps["llm.api.key.configured"]
        // Presence flag follows the *embedded* key (or an explicit local/CI flag).
        // Do not flip true merely because OPENAPI_GENERAL exists in the build env —
        // public APKs must stay honest empty/NOT_SUPPORTED without embed or runtime key.
        val llmKeyConfigured =
            openAiApiKey.isNotEmpty() ||
                (project.findProperty("llm.api.key.configured") as? String)
                    ?.equals("true", ignoreCase = true) == true ||
                localKeyFlag?.equals("true", ignoreCase = true) == true
        buildConfigField("boolean", "LLM_API_KEY_CONFIGURED", llmKeyConfigured.toString())
        // Empty in public builds. Never commit a non-empty value in source.
        buildConfigField(
            "String",
            "OPENAI_API_KEY",
            "\"${escapeBuildConfigString(openAiApiKey)}\""
        )
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
        buildConfig = true
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
    implementation("androidx.security:security-crypto:1.0.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
