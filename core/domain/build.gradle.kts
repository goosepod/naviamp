import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

val generatedNaviampVersionSources = layout.buildDirectory.dir("generated/naviampVersion/commonMain")
val generateNaviampVersion by tasks.registering {
    val versionFile = rootProject.layout.projectDirectory.file("VERSION")
    val outputDir = generatedNaviampVersionSources
    inputs.file(versionFile)
    outputs.dir(outputDir)
    doLast {
        val version = versionFile.asFile.readText().trim()
        val escapedVersion = version.replace("\\", "\\\\").replace("\"", "\\\"")
        val packageDir = outputDir.get().file("app/naviamp/domain/network").asFile
        packageDir.mkdirs()
        packageDir.resolve("NaviampClientIdentity.kt").writeText(
            """
            package app.naviamp.domain.network

            const val NaviampAppVersion = "$escapedVersion"
            const val NaviampClientName = "Naviamp/$escapedVersion"
            const val NaviampUserAgent = "Naviamp/$escapedVersion"
            const val NaviampProjectUserAgent = "Naviamp/$escapedVersion (https://github.com/jbmcmichael/Naviamp)"
            """.trimIndent(),
        )
    }
}

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    jvm()

    sourceSets {
        commonMain {
            kotlin.srcDir(generatedNaviampVersionSources)
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.client.core)
            }
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.cio)
        }

        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
    }
}

android {
    namespace = "app.naviamp.domain"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    dependsOn(generateNaviampVersion)
}
