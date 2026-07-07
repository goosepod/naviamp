import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.zip.ZipFile

val composeVersion = libs.versions.compose.get()
val composeMaterial3Version = "1.8.2"
val naviampVersionName = rootProject.file("VERSION").readText().trim()
val naviampVersionCode = rootProject.file("VERSION_CODE").readText().trim().toInt()
val androidReleaseKeystore = providers.environmentVariable("NAVIAMP_ANDROID_KEYSTORE")
val androidReleaseKeystorePassword = providers.environmentVariable("NAVIAMP_ANDROID_KEYSTORE_PASSWORD")
val androidReleaseKeyAlias = providers.environmentVariable("NAVIAMP_ANDROID_KEY_ALIAS")
val androidReleaseKeyPassword = providers.environmentVariable("NAVIAMP_ANDROID_KEY_PASSWORD")
val signDebugWithReleaseKey = providers.gradleProperty("naviamp.android.signDebugWithReleaseKey")
    .map(String::toBoolean)
    .orElse(false)
val hasAndroidReleaseSigning = listOf(
    androidReleaseKeystore,
    androidReleaseKeystorePassword,
    androidReleaseKeyAlias,
    androidReleaseKeyPassword,
).all { it.isPresent }

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
        versionCode = naviampVersionCode
        versionName = naviampVersionName
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }

    signingConfigs {
        if (hasAndroidReleaseSigning) {
            create("release") {
                storeFile = file(androidReleaseKeystore.get())
                storePassword = androidReleaseKeystorePassword.get()
                keyAlias = androidReleaseKeyAlias.get()
                keyPassword = androidReleaseKeyPassword.get()
            }
        }
    }

    buildTypes {
        debug {
            if (hasAndroidReleaseSigning && signDebugWithReleaseKey.get()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            if (hasAndroidReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
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
    implementation(libs.androidx.profileinstaller)
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
