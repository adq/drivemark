import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kover)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

val configPropertiesFile = rootProject.file("config.dev.properties")
val configProperties = Properties()
if (configPropertiesFile.exists()) {
    configProperties.load(FileInputStream(configPropertiesFile))
}

val prodConfigPropertiesFile = rootProject.file("config.prod.properties")
val prodConfigProperties = Properties()
if (prodConfigPropertiesFile.exists()) {
    prodConfigProperties.load(FileInputStream(prodConfigPropertiesFile))
}

android {
    namespace = "com.drivemark.app"
    compileSdk = 37

    if (keystorePropertiesFile.exists()) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.drivemark.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        // Dev OAuth client ID loaded from config.dev.properties (prod override in release block)
        buildConfigField("String", "WEB_CLIENT_ID", "\"${configProperties.getProperty("webClientId", "MISSING_WEB_CLIENT_ID")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            // Production OAuth client ID from config.prod.properties
            buildConfigField("String", "WEB_CLIENT_ID", "\"${prodConfigProperties.getProperty("webClientId", "MISSING_PROD_WEB_CLIENT_ID")}\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/INDEX.LIST"
        }
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.icons.extended)
    implementation(libs.activity.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Google Sign-In via Credential Manager
    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.googleid)

    // Google API Client (Sheets + Drive)
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.sheets)
    implementation(libs.google.api.drive)
    implementation(libs.google.http.gson)

    // OkHttp
    implementation(libs.okhttp)

    // Coil (image loading) — Coil 3 needs an explicit network fetcher artifact
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Jsoup (HTML parsing)
    implementation(libs.jsoup)

    // WorkManager
    implementation(libs.work.runtime)

    // Coroutines
    implementation(libs.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
}
