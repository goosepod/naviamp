import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Disposable test protocol and JDK server only: no application/provider dependency graph.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.kover)
}

kotlin {
    android {
        namespace = "app.naviamp.core.tls.testkit"
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
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}
