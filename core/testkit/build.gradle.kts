import org.gradle.api.attributes.Usage
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.kover)
}

kotlin {
    android {
        namespace = "app.naviamp.core.testkit"
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
        commonMain.dependencies {
            api(project(":core:tls-testkit"))
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

// Resolve the server independently: preparing it must not build this module's application graph.
val mobileTlsFixtureRuntime by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
    }
}
dependencies { add(mobileTlsFixtureRuntime.name, project(":core:tls-testkit")) }

tasks.register<JavaExec>("runMobileMutualTlsFixture") {
    group = "verification"
    description = "Serve disposable certificates and mTLS audio to mobile integration tests on loopback"
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
