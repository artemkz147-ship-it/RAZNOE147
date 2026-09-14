import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.openai.bodyrunner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.openai.bodyrunner"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"

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

val poseModel = layout.projectDirectory.file("src/main/assets/pose_landmarker_lite.task").asFile
val downloadPoseModel by tasks.registering {
    outputs.file(poseModel)
    doLast {
        if (!poseModel.exists() || poseModel.length() < 1_000_000) {
            poseModel.parentFile.mkdirs()
            val modelUrl = URI(
                "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task"
            ).toURL()
            modelUrl.openStream().use { input ->
                poseModel.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(downloadPoseModel)
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
