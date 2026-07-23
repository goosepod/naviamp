import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "app.naviamp.android.v2"
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
    implementation(project(":core:domain"))
    implementation(project(":core:presentation"))
    implementation(project(":core:ui"))
    implementation(libs.activity.compose)
    implementation("org.jetbrains.compose.runtime:runtime:${libs.versions.compose.get()}")
    implementation("org.jetbrains.compose.ui:ui:${libs.versions.compose.get()}")

    testImplementation(project(":core:testkit"))
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
