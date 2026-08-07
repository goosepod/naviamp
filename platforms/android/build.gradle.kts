import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "app.naviamp.platform.android"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":core:app"))
    api(project(":core:domain"))
    api(project(":core:presentation"))
    api(project(":core:storage"))
    api(project(":core:ui"))
    api(project(":providers:navidrome"))
    api(project(":providers:jellyfin"))
    implementation("org.jetbrains.compose.runtime:runtime:${libs.versions.compose.get()}")
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.cio)
    implementation(libs.sqldelight.android.driver)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
