import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kover)
}

kotlin {
    android {
        namespace = "app.naviamp.storage"
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
            api(project(":core:host-api"))
            implementation(project(":core:domain"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.sqldelight.runtime)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        named("androidHostTest") { dependencies { implementation(libs.sqldelight.sqlite.driver) } }
        nativeMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
    }
}

sqldelight {
    databases {
        create("NaviampStorageDatabase") {
            packageName.set("app.naviamp.storage")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/app/naviamp/storage"))
            verifyMigrations.set(true)
        }
    }
}
