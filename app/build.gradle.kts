plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun envQuoted(name: String): String {
    val v = (System.getenv(name) ?: "").replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"$v\""
}

android {
    namespace = "ru.garickbox.gor4akovnet"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.garickbox.gor4akovnet"
        minSdk = 26
        targetSdk = 35
        val runNum = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = runNum
        versionName = "1.$runNum"

        buildConfigField("String", "STATS_GIST_ID", envQuoted("GBOX_STATS_GIST_ID"))
        buildConfigField("String", "STATS_TOKEN", envQuoted("GBOX_STATS_TOKEN"))
        buildConfigField("String", "STATS_URL", envQuoted("GBOX_STATS_URL"))
        buildConfigField("String", "DONATE_URL", envQuoted("GBOX_DONATE_URL"))

        ndk {
            // Только реальные телефоны: arm64 (POCO, новые Samsung/Xiaomi) + armeabi-v7a (старые 32-bit).
            // x86/x86_64 убираем — они нужны только эмуляторам, на телефоне не запускаются.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        val ksPath = System.getenv("GBOX_KEYSTORE_PATH")
        if (!ksPath.isNullOrBlank() && file(ksPath).exists()) {
            create("gbox") {
                storeFile = file(ksPath)
                storePassword = System.getenv("GBOX_STORE_PASS")?.ifBlank { null } ?: "gor4akov123"
                keyAlias = System.getenv("GBOX_KEY_ALIAS")?.ifBlank { null } ?: "gor4akovnet"
                keyPassword = System.getenv("GBOX_KEY_PASS")?.ifBlank { null } ?: "gor4akov123"
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfigs.findByName("gbox")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.findByName("gbox")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        noCompress += listOf("gz", "tar", "ipk")
    }
}

dependencies {
    implementation(files("libs/gboxcore.aar"))
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("com.google.zxing:core:3.5.3")
}
