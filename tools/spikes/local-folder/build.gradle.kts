import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Throwaway #662 spike. Included only with -Pnaviamp.spikes.localFolder=true; nothing depends on it.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
}

kotlin {
    android {
        namespace = "app.naviamp.spike.localfolder"
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        withHostTest {}
    }
    jvm()
    // Klib compilation only on Linux; proves the common contracts and tag reader build for Apple.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

tasks.named<Test>("jvmTest") {
    maxHeapSize = providers.systemProperty("naviamp.spike.heap").orElse("512m").get()
    // Without a UTF-8 locale the JVM cannot map non-ASCII file names to paths on Linux.
    environment("LC_ALL", "C.UTF-8")
    listOf("naviamp.spike.corpus", "naviamp.spike.threads").forEach { key ->
        providers.systemProperty(key).orNull?.let { systemProperty(key, it) }
    }
    testLogging { showStandardStreams = true }
    outputs.upToDateWhen { false }
}
