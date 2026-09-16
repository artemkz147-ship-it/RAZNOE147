import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.openai.bodyrunner"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.openai.bodyrunner"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

val litePoseModel = layout.projectDirectory.file("src/main/assets/pose_landmarker_lite.task").asFile
val fullPoseModel = layout.projectDirectory.file("src/main/assets/pose_landmarker_full.task").asFile
val threeModule = layout.projectDirectory.file("src/main/assets/web/vendor/three.module.js").asFile
val threeCore = layout.projectDirectory.file("src/main/assets/web/vendor/three.core.js").asFile

val downloadPoseModels by tasks.registering {
    outputs.files(litePoseModel, fullPoseModel)
    doLast {
        val models = listOf(
            litePoseModel to "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task",
            fullPoseModel to "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/1/pose_landmarker_full.task",
        )
        for ((file, url) in models) {
            if (!file.exists() || file.length() < 1_000_000) {
                file.parentFile.mkdirs()
                URI(url).toURL().openStream().use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }
}

val downloadThreeJs by tasks.registering {
    outputs.files(threeModule, threeCore)
    doLast {
        val files = listOf(
            threeModule to "https://cdn.jsdelivr.net/npm/three@0.186.0/build/three.module.js",
            threeCore to "https://cdn.jsdelivr.net/npm/three@0.186.0/build/three.core.js",
        )
        for ((file, url) in files) {
            if (!file.exists() || file.length() < 100_000) {
                file.parentFile.mkdirs()
                URI(url).toURL().openStream().use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(downloadPoseModels, downloadThreeJs)
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.webkit:webkit:1.12.1")

    val cameraX = "1.6.2"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")

    implementation("com.google.mediapipe:tasks-vision:0.10.35")

    testImplementation("junit:junit:4.13.2")
}
