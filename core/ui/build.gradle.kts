import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val composeVersion = libs.versions.compose.get()
val composeMaterial3Version = "1.8.2"

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
            implementation("org.jetbrains.compose.foundation:foundation:$composeVersion")
            implementation("org.jetbrains.compose.material3:material3:$composeMaterial3Version")
            implementation("org.jetbrains.compose.runtime:runtime:$composeVersion")
            implementation("org.jetbrains.compose.ui:ui:$composeVersion")
        }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "app.naviamp.ui"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
