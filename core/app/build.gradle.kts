import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.kover)
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
    android {
        namespace = "app.naviamp.core.app"
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

    applyHierarchyTemplate {
        common {
            group("jvmAndAndroid") {
                withJvm()
                // The Android-KMP plugin implements KotlinTarget directly, rather than the
                // legacy KotlinAndroidTarget selected by withAndroidTarget().
                withCompilations {
                    it.target.platformType == org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.androidJvm
                }
            }
            group("ios") {
                withIos()
            }
            group("tvos") {
                withTvos()
            }
        }
    }

    sourceSets {
        val jvmAndAndroidMain by getting {
            dependencies {
                implementation(libs.bouncycastle.provider)
            }
        }

        commonMain.dependencies {
            implementation(libs.ktor.network)
            implementation(project(":core:domain"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
