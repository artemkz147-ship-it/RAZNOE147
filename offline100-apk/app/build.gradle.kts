plugins {
    id("com.android.application")
}

android {
    namespace = "ru.offline100.games"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.offline100.games"
        minSdk = 23
        targetSdk = 36
        versionCode = 11
        versionName = "1.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
