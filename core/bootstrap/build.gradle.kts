import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.kover)
}

kotlin {
    android {
        namespace = "app.naviamp.core.bootstrap"
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        withHostTest {}
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()
    if (providers.gradleProperty("naviamp.tvos.enabled").orNull == "true") {
        tvosArm64()
        tvosSimulatorArm64()
    }
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.configureEach { linkerOpts("-lsqlite3") }
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":core:presentation"))
            api(project(":core:storage"))
            implementation(project(":providers:navidrome"))
            implementation(project(":providers:jellyfin"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(compose.runtime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies { implementation(libs.sqldelight.sqlite.driver) }
        named("androidHostTest") { dependencies { implementation(libs.sqldelight.sqlite.driver) } }
        nativeTest.dependencies { implementation(libs.sqldelight.native.driver) }
    }
}
