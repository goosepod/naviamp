import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kover)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":core:presentation"))
            api(project(":providers:navidrome"))
            implementation(libs.ktor.network)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "app.naviamp.core.testkit"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val mobileTlsFixtureTarget = kotlin.targets.getByName("jvm") as org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
val mobileTlsFixtureRuntime = files(tasks.named("jvmJar"), mobileTlsFixtureTarget.compilations.getByName("main").runtimeDependencyFiles)

tasks.register<JavaExec>("runMobileMutualTlsFixture") {
    group = "verification"
    description = "Serve disposable certificates and mTLS audio to mobile integration tests on loopback"
    dependsOn("jvmJar")
    classpath = mobileTlsFixtureRuntime
    mainClass.set("app.naviamp.testkit.NaviampMobileMutualTlsFixtureKt")
}

tasks.register("prepareMobileMutualTlsFixture") {
    group = "verification"
    description = "Prepare a standalone fixture JVM without holding Gradle's execution locks during mobile tests"
    inputs.files(mobileTlsFixtureRuntime)
    val arguments = rootProject.layout.buildDirectory.file("mobile-mtls-fixture.args")
    outputs.file(arguments)
    doLast {
        val classpath = mobileTlsFixtureRuntime.asPath.replace('\\', '/')
        arguments.get().asFile.apply { parentFile.mkdirs() }.writeText(
            "--class-path\n\"$classpath\"\napp.naviamp.testkit.NaviampMobileMutualTlsFixtureKt\n",
        )
    }
}
