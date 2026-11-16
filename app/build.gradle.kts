import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val signingPropertiesFile = rootProject.file("signing.properties")
val signingProperties = Properties().apply {
    if (signingPropertiesFile.isFile) {
        signingPropertiesFile.inputStream().use(::load)
    }
}

fun signingValue(propertyName: String, environmentName: String): String? =
    signingProperties.getProperty(propertyName)?.takeIf(String::isNotBlank)
        ?: providers.environmentVariable(environmentName).orNull?.takeIf(String::isNotBlank)

val releaseStorePath = signingValue("storeFile", "AEROVECTOR_STORE_FILE")
val releaseStorePassword = signingValue("storePassword", "AEROVECTOR_STORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "AEROVECTOR_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "AEROVECTOR_KEY_PASSWORD")

android {
    namespace = "com.example.aerovector"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.aerovector"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"
    }

    signingConfigs {
        create("release") {
            if (releaseStorePath != null) storeFile = rootProject.file(releaseStorePath)
            if (releaseStorePassword != null) storePassword = releaseStorePassword
            if (releaseKeyAlias != null) keyAlias = releaseKeyAlias
            if (releaseKeyPassword != null) keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        release {
            // Release output uses this config; validation below prevents packaging if it is incomplete.
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}

val validateReleaseSigning = tasks.register("validateReleaseSigning") {
    group = "verification"
    description = "Checks that release signing credentials and keystore are configured."
    doLast {
        val missing = buildList {
            if (releaseStorePath.isNullOrBlank()) add("storeFile / AEROVECTOR_STORE_FILE")
            if (releaseStorePassword.isNullOrBlank()) add("storePassword / AEROVECTOR_STORE_PASSWORD")
            if (releaseKeyAlias.isNullOrBlank()) add("keyAlias / AEROVECTOR_KEY_ALIAS")
            if (releaseKeyPassword.isNullOrBlank()) add("keyPassword / AEROVECTOR_KEY_PASSWORD")
            if (!releaseStorePath.isNullOrBlank() && !rootProject.file(releaseStorePath).isFile) {
                add("keystore file not found: ${rootProject.file(releaseStorePath)}")
            }
        }
        check(missing.isEmpty()) {
            "Release signing is not configured. See README.md and signing.properties.example. Missing/invalid: ${missing.joinToString()}"
        }
    }
}

tasks.configureEach {
    if (name.startsWith("package") && name.contains("Release")) {
        dependsOn(validateReleaseSigning)
    }
}
