plugins { id("com.android.application") }

android {
    namespace = "ru.offline100.games"
    compileSdk = 36
    defaultConfig {
        applicationId = "ru.offline100.games"
        minSdk = 23
        targetSdk = 36
        versionCode = 13
        versionName = "1.2.0"
    }
    buildTypes {
        debug { isMinifyEnabled = false }
        release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
