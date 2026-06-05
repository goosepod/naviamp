import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.zip.ZipFile

val composeVersion = libs.versions.compose.get()
val composeMaterial3Version = "1.8.2"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "app.naviamp.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.naviamp.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.9.0"
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDir(project.layout.projectDirectory.dir("../../native/bass-jni/vendor/android"))
        }
    }

    externalNativeBuild {
        cmake {
            path = project.layout.projectDirectory.file("../../native/bass-jni/CMakeLists.txt").asFile
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:storage"))
    implementation(project(":core:ui"))
    implementation(project(":providers:navidrome"))
    implementation(libs.activity.compose)
    implementation(libs.androidx.media)
    implementation("org.jetbrains.compose.foundation:foundation:$composeVersion")
    implementation("org.jetbrains.compose.material3:material3:$composeMaterial3Version")
    implementation("org.jetbrains.compose.runtime:runtime:$composeVersion")
    implementation("org.jetbrains.compose.ui:ui:$composeVersion")
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.cio)
    implementation(libs.sqldelight.android.driver)
}

tasks.register("verifyDebugBassNativePackage") {
    group = "verification"
    description = "Verifies that the debug APK contains Naviamp JNI plus BASS native libraries for each packaged ABI."
    dependsOn("assembleDebug")

    val apkFile = layout.buildDirectory.file("outputs/apk/debug/android-debug.apk")
    inputs.file(apkFile)

    doLast {
        val apk = apkFile.get().asFile
        check(apk.isFile) { "Debug APK was not found at ${apk.absolutePath}" }

        val requiredAbis = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        val requiredLibraries = listOf(
            "libnaviamp_bass.so",
            "libbass.so",
            "libbassmix.so",
            "libc++_shared.so",
        )
        val entries = ZipFile(apk).use { zip ->
            zip.entries().asSequence().map { it.name }.toSet()
        }
        val missing = requiredAbis.flatMap { abi ->
            requiredLibraries.mapNotNull { library ->
                val path = "lib/$abi/$library"
                path.takeUnless { entries.contains(it) }
            }
        }
        check(missing.isEmpty()) {
            "Debug APK is missing native playback libraries: ${missing.joinToString()}"
        }
    }
}
