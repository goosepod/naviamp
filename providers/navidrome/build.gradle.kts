import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.kover)
}

kotlin {
    android {
        namespace = "app.naviamp.provider.navidrome"
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        withHostTest {}
        optimization { consumerKeepRules.file("consumer-rules.pro") }
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()
    if (providers.gradleProperty("naviamp.tvos.enabled").orNull == "true") {
        tvosArm64()
        tvosSimulatorArm64()
    }
    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:app"))
            implementation(project(":core:domain"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation("io.ktor:ktor-client-mock:${libs.versions.ktor.get()}")
        }

        val jvmAndAndroidMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.ktor.client.okhttp)
                implementation(libs.bouncycastle.provider)
            }
        }
        androidMain.get().dependsOn(jvmAndAndroidMain)
        jvmMain.get().dependsOn(jvmAndAndroidMain)

        jvmTest.dependencies {
            implementation(project(":core:testkit"))
        }

        appleMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}
