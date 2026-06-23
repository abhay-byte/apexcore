import java.util.Properties

plugins {
    id("com.android.application")
}

android {
    namespace = "com.apexcore.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.apexcore.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
