import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(project(":core:presentation"))
                implementation(project(":core:ui"))
                implementation(project(":platforms:desktop"))
                implementation(compose.runtime)
                implementation(compose.ui)
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(project(":core:testkit"))
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "app.naviamp.desktop.DesktopV2MainKt"

        nativeDistributions {
            packageName = "Naviamp"
            packageVersion = "2.0.0"
            description = "Naviamp 2.0.0-alpha shared-Core test host"
            vendor = "Naviamp"
            appResourcesRootDir.set(
                rootProject.layout.projectDirectory.dir("apps/desktop/build/generated/desktopBassApp"),
            )
            modules("java.net.http", "java.sql")
            targetFormats(TargetFormat.Dmg)
        }
    }
}

tasks.matching {
    it.name in setOf("run", "prepareAppResources", "createDistributable", "packageDmg")
}.configureEach {
    dependsOn(":apps:desktop:copyDesktopBassAppResources", ":apps:desktop:copyDesktopBassJniAppResources")
}
