import java.util.zip.ZipFile
import org.gradle.api.tasks.Sync
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val composeVersion = libs.versions.compose.get()
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
        resValue("string", "app_name", "Naviamp")
        externalNativeBuild {
            cmake { arguments += "-DANDROID_STL=c++_shared" }
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
            applicationIdSuffix = ".v2test"
            versionNameSuffix = "-v2test"
            resValue("string", "app_name", "Naviamp v2 Test")
            if (hasAndroidReleaseSigning && signDebugWithReleaseKey.get()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            if (hasAndroidReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
        create("benchmark") {
            initWith(getByName("release"))
            applicationIdSuffix = ".benchmark"
            versionNameSuffix = "-benchmark"
            resValue("string", "app_name", "Naviamp v2 Benchmark")
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isProfileable = true
            matchingFallbacks += listOf("release")
        }
        create("profileable") {
            initWith(getByName("release"))
            applicationIdSuffix = ".v2test"
            versionNameSuffix = "-v2test-profileable"
            resValue("string", "app_name", "Naviamp v2 Profileable")
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isProfileable = true
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets.getByName("main").jniLibs.srcDir(
        project.layout.projectDirectory.dir("../../native/bass-jni/vendor/android"),
    )
    externalNativeBuild {
        cmake { path = project.layout.projectDirectory.file("../../native/bass-jni/CMakeLists.txt").asFile }
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":platforms:android"))
    implementation(project(":core:domain"))
    implementation(project(":core:presentation"))
    implementation(project(":core:ui"))
    implementation(libs.activity.compose)
    implementation(libs.androidx.media)
    implementation(libs.androidx.profileinstaller)
    implementation("org.jetbrains.compose.foundation:foundation:$composeVersion")
    implementation(libs.compose.material3)
    implementation("org.jetbrains.compose.runtime:runtime:$composeVersion")
    implementation("org.jetbrains.compose.ui:ui:$composeVersion")
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(project(":core:testkit"))
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
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
            "libbass_ssl.so",
            "libbassmix.so",
            "libbass_aac.so",
            "libbass_ac3.so",
            "libbass_mpc.so",
            "libbassalac.so",
            "libbassape.so",
            "libbassdsd.so",
            "libbassflac.so",
            "libbasshls.so",
            "libbassmidi.so",
            "libbassopus.so",
            "libbasswebm.so",
            "libbasswv.so",
            "libc++_shared.so",
        )
        val entries = ZipFile(apk).use { zip -> zip.entries().asSequence().map { it.name }.toSet() }
        val missing = requiredAbis.flatMap { abi ->
            requiredLibraries.mapNotNull { library -> "lib/$abi/$library".takeUnless(entries::contains) }
        }
        check(missing.isEmpty()) { "Debug APK is missing native playback libraries: ${missing.joinToString()}" }
        val expectedBassLibraries = requiredLibraries.filter { it.startsWith("libbass") }.toSet()
        val unexpected = requiredAbis.flatMap { abi ->
            entries.asSequence()
                .filter { it.startsWith("lib/$abi/libbass") && it.endsWith(".so") }
                .map { it.substringAfterLast('/') }
                .filterNot(expectedBassLibraries::contains)
                .map { "lib/$abi/$it" }
                .toList()
        }
        check(unexpected.isEmpty()) {
            "Debug APK contains unexpected BASS libraries: ${unexpected.joinToString()}"
        }
    }
}

tasks.register<Sync>("stageReleaseApk") {
    group = "distribution"
    description = "Builds and stages the signed Android APK with the complete semantic version in its filename."
    dependsOn("assembleRelease")
    from(layout.buildDirectory.dir("outputs/apk/release")) {
        include("*.apk")
        rename { "Naviamp-$naviampVersionName-android.apk" }
    }
    into(layout.buildDirectory.dir("release-artifacts"))
    doLast {
        val artifact = layout.buildDirectory.file("release-artifacts/Naviamp-$naviampVersionName-android.apk").get().asFile
        check(artifact.isFile) { "Versioned Android release APK was not produced: ${artifact.absolutePath}" }
    }
}
