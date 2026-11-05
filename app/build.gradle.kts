plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.djkfc.pistoletik"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.djkfc.pistoletik"
        minSdk = 24
        targetSdk = 34
        versionCode = runNumber
        versionName = "0.1.$runNumber"
    }

    // Постоянный ключ: новые сборки ставятся поверх старых без удаления
    signingConfigs {
        create("fixed") {
            storeFile = file("pistoletik.keystore")
            storePassword = "pistoletik"
            keyAlias = "pistoletik"
            keyPassword = "pistoletik"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("fixed")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}
