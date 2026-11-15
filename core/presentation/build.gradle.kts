import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kover)
}

kotlin {
    android {
        namespace = "app.naviamp.core.presentation"
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        withHostTest {}
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()
    if (providers.gradleProperty("naviamp.tvos.sceneHost").orNull == "true") {
        tvosArm64()
        tvosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:app"))
            api(project(":core:domain"))
            api(project(":core:ui"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
            implementation("org.jetbrains.compose.ui:ui-test:${libs.versions.compose.get()}")
        }
    }
}
