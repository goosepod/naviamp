import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Disposable test protocol and JDK server only: no application/provider dependency graph.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kover)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}

android {
    namespace = "app.naviamp.core.tls.testkit"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
