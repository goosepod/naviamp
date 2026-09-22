import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val composeVersion = libs.versions.compose.get()
val animationProbe = providers.gradleProperty("naviamp.animationProbe").orNull == "true"

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
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

    if (animationProbe) {
        targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
            binaries.framework { baseName = "NaviampAnimationProbe"; isStatic = true }
        }
    }

    sourceSets {
        val renderedUiTests = "src/renderedTest/kotlin"
        jvmTest.get().kotlin.srcDir(renderedUiTests)
        androidInstrumentedTest.get().kotlin.srcDir(renderedUiTests)
        if (animationProbe) {
            commonMain.get().kotlin.srcDir("src/animationProbe/kotlin")
            iosMain.get().kotlin.srcDir("src/animationProbeIos/kotlin")
        }
        androidInstrumentedTest.dependencies {
            implementation(libs.activity.compose)
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.ext.junit)
            implementation("org.jetbrains.compose.ui:ui-test:$composeVersion")
            // Espresso 3.7 uses the public input service on Android 17.
            implementation("androidx.test.espresso:espresso-core:3.7.0")
            implementation(kotlin("test-junit"))
        }
        commonMain.dependencies {
            implementation(project(":core:domain"))
            implementation(libs.kotlinx.serialization.json)
            implementation("org.jetbrains.compose.foundation:foundation:$composeVersion")
            implementation(libs.compose.material3)
            implementation("org.jetbrains.compose.runtime:runtime:$composeVersion")
            implementation("org.jetbrains.compose.ui:ui:$composeVersion")
            implementation("org.jetbrains.compose.components:components-resources:$composeVersion")
            implementation(libs.androidx.lifecycle.runtime.compose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
            implementation("org.jetbrains.compose.ui:ui-test:$composeVersion")
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotlin.test)
        }
    }
}

compose.resources {
    generateResClass = always
    packageOfResClass = "app.naviamp.ui.generated.resources"
}

android {
    namespace = "app.naviamp.ui"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        // Keep the standalone native-boundary fixture on the pre-enforced-edge-to-edge model.
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        if (animationProbe) {
            testInstrumentationRunnerArguments["class"] = listOf(
                "app.naviamp.ui.AndroidAnimationProbeTest",
                "app.naviamp.ui.AndroidRasterPlacementTest",
            ).joinToString(",")
        }
    }
    sourceSets.getByName("androidTest").manifest.srcFile("src/androidInstrumentedTest/AndroidManifest.xml")
    if (animationProbe) {
        sourceSets.getByName("androidTest").java.srcDir("src/animationProbeAndroid/kotlin")
        sourceSets.getByName("androidTest").manifest.srcFile("src/animationProbeAndroid/AndroidManifest.xml")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Opt-in real-window probe; included only in explicit device CI and excluded from release packaging.
val compositorProbeLibrary = layout.buildDirectory.file("animation-probe/libnaviamp_probe_layers.dylib")
val buildAnimationCompositorProbe by tasks.registering(Exec::class) {
    val source = file("src/jvmTest/native/animation_compositor_probe.mm")
    inputs.file(source)
    outputs.file(compositorProbeLibrary)
    doFirst {
        check(System.getProperty("os.name").startsWith("Mac")) { "The Core Animation probe requires macOS." }
        val javaHome = System.getProperty("java.home")
        val output = compositorProbeLibrary.get().asFile
        output.parentFile.mkdirs()
        commandLine("/usr/bin/clang++", "-std=c++17", "-fobjc-arc", "-dynamiclib",
            "-I$javaHome/include", "-I$javaHome/include/darwin", source.absolutePath,
            "-L$javaHome/lib", "-ljawt", "-framework", "AppKit", "-framework", "QuartzCore",
            "-o", output.absolutePath)
    }
}

tasks.register<JavaExec>("playerAnimationProbe") {
    group = "verification"
    description = "Measures CPU for static, marquee, smooth waveform, and combined player rendering."
    dependsOn("jvmTestClasses")
    classpath = tasks.named<Test>("jvmTest").get().classpath
    mainClass.set("app.naviamp.ui.NaviampPlayerAnimationProbeKt")
    if (providers.environmentVariable("NAVIAMP_PROBE_COMPOSITOR").orNull == "true") {
        dependsOn(buildAnimationCompositorProbe)
        systemProperty("naviamp.probe.compositor.library", compositorProbeLibrary.get().asFile.absolutePath)
    }
}
