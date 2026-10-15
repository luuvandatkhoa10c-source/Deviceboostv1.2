plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.garen.deviceboost"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.garen.deviceboost"
        minSdk = 30
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"
    }

    // One fixed key for every build, so a new APK installs over the old one.
    // Personal sideload tool: keep the repo private.
    signingConfigs {
        create("personal") {
            storeFile = file("personal.jks")
            storePassword = "deviceboost"
            keyAlias = "deviceboost"
            keyPassword = "deviceboost"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("personal")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("personal")
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
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
