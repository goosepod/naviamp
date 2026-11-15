import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.android.build.api.artifact.SingleArtifact
import com.android.manifmerger.ManifestMerger2
import com.android.manifmerger.MergingReport
import com.android.utils.StdLogger

val composeVersion = libs.versions.compose.get()
val visualizerMountProbe = providers.gradleProperty("naviamp.visualizerMountProbe").orNull == "true"
val animationProbe = providers.gradleProperty("naviamp.animationProbe").orNull == "true" || visualizerMountProbe

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kover)
}

// AGP 9.5.1's KMP test merger omits additional Sources.manifests entries. Apply the
// existing fixture through the public artifact pipeline, preserving the generated
// runner, package, SDK and dependency declarations and the normal merger diagnostics.
@CacheableTask
abstract class MergeUiTestHostManifest : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val fixtureManifest: RegularFileProperty

    @get:OutputFile
    abstract val outputManifest: RegularFileProperty

    @TaskAction
    fun merge() {
        val fixture = fixtureManifest.get().asFile
        check("<uses-sdk" !in fixture.readText()) { "Fixture SDK versions must remain in the Gradle DSL." }
        val diagnostics = StdLogger(StdLogger.Level.WARNING)
        val report = ManifestMerger2.newMerger(
            mergedManifest.get().asFile, diagnostics, ManifestMerger2.MergeType.APPLICATION,
        )
            // The input is AGP's already-validated output, including its generated <uses-sdk>.
            // The original source-manifest SDK check stays enabled; the fixture cannot override it.
            .withFeatures(ManifestMerger2.Invoker.Feature.USES_SDK_IN_MANIFEST_LENIENT_HANDLING)
            .addFlavorAndBuildTypeManifests(fixture).merge()
        report.log(diagnostics)
        check(report.result != MergingReport.Result.ERROR) { report.reportString }
        val xml = checkNotNull(report.getMergedDocument(MergingReport.MergedManifestKind.MERGED))
        outputManifest.get().asFile.apply { parentFile.mkdirs(); writeText(xml) }
    }
}

kotlin {
    android {
        namespace = "app.naviamp.ui"
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        withHostTest {}
        androidResources { enable = true }
        withDeviceTestBuilder { sourceSetTreeName = "test" }.configure {
            targetSdk { version = release(34) }
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            applicationId = if (visualizerMountProbe) "app.naviamp.ui.mount544.test" else "app.naviamp.ui.test"
            if (visualizerMountProbe) {
                instrumentationRunnerArguments["class"] = "app.naviamp.ui.AndroidVisualizerMountTest"
            } else if (animationProbe) {
                instrumentationRunnerArguments["class"] = listOf(
                    "app.naviamp.ui.AndroidAnimationProbeTest",
                    "app.naviamp.ui.AndroidRasterPlacementTest",
                    "app.naviamp.ui.AndroidVisualizerMountTest",
                    "app.naviamp.ui.VisualizerCompositionClockTest",
                    "app.naviamp.ui.NaviampMenuAttachmentTest",
                    "app.naviamp.ui.NaviampDialogInitialFocusTest",
                    "app.naviamp.ui.NaviampSmartPlaylistReferencePickerTest",
                    "app.naviamp.ui.NaviampSmartPlaylistSummaryUiTest",
                    "app.naviamp.ui.NaviampArtistPlaylistsUiTest",
                    "app.naviamp.ui.NaviampLibrarySearchLayoutTest",
                    "app.naviamp.ui.NaviampLibrarySearchResultsUiTest",
                    "app.naviamp.ui.NaviampArtistTopTracksUiTest",
                    "app.naviamp.ui.NaviampArtistSectionLayoutsUiTest",
                    "app.naviamp.ui.NaviampLanguageResourcesUiTest",
                ).joinToString(",")
            }
        }
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()
    if (providers.gradleProperty("naviamp.tvos.sceneHost").orNull == "true") {
        tvosArm64()
        tvosSimulatorArm64()
    }

    if (animationProbe) {
        targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
            binaries.framework { baseName = "NaviampAnimationProbe"; isStatic = true }
        }
    }

    sourceSets {
        val renderedUiTests = "src/renderedTest/kotlin"
        jvmTest.get().kotlin.srcDir(renderedUiTests)
        val androidDeviceTest = getByName("androidDeviceTest")
        androidDeviceTest.kotlin.srcDirs(renderedUiTests, "src/androidInstrumentedTest/kotlin")
        if (animationProbe) androidDeviceTest.kotlin.srcDir("src/animationProbeAndroid/kotlin")
        if (animationProbe) {
            commonMain.get().kotlin.srcDir("src/animationProbe/kotlin")
            iosMain.get().kotlin.srcDir("src/animationProbeIos/kotlin")
        }
        androidDeviceTest.dependencies {
            implementation(libs.activity.compose)
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.ext.junit)
            implementation("org.jetbrains.compose.ui:ui-test:$composeVersion")
            // Espresso 3.7 uses the public input service on Android 17.
            implementation("androidx.test.espresso:espresso-core:3.7.0")
            implementation(kotlin("test-junit"))
        }
        commonMain.dependencies {
            implementation(libs.ksoup.entities)
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
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
            implementation("org.jetbrains.compose.ui:ui-test:$composeVersion")
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotlin.test)
        }
    }
}

// Java/Android locale APIs can report Indonesian as either modern `id` or legacy `in`.
// Maintain one catalog and package an identical alias for both runtime representations.
val packagedComposeResources = layout.buildDirectory.dir("generated/languageResources/commonMain")
val prepareLanguageResources by tasks.registering(Sync::class) {
    from("src/commonMain/composeResources")
    from("src/commonMain/composeResources/values-id") { into("values-in") }
    into(packagedComposeResources)
}

compose.resources {
    generateResClass = always
    packageOfResClass = "app.naviamp.ui.generated.resources"
    customDirectory("commonMain", prepareLanguageResources.map { packagedComposeResources.get() })
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
            "-L$javaHome/lib", "-ljawt", "-framework", "AppKit", "-framework", "QuartzCore", "-framework", "CoreFoundation",
            "-o", output.absolutePath)
    }
}

tasks.register<JavaExec>("discoveryArtistWindowProbe") {
    group = "verification"
    description = "Open the shared discovery artist page without a production profile"
    dependsOn("jvmTestClasses")
    classpath = tasks.named<Test>("jvmTest").get().classpath
    mainClass.set("app.naviamp.ui.NaviampDiscoveryWindowProbeKt")
}

tasks.register<JavaExec>("smartPlaylistWindowProbe") {
    group = "verification"
    description = "Checks smart-playlist scrolling in a visible native owned window."
    dependsOn("jvmTestClasses")
    classpath = tasks.named<Test>("jvmTest").get().classpath
    mainClass.set("app.naviamp.ui.NaviampSmartPlaylistWindowProbeKt")
    if (System.getProperty("os.name").startsWith("Windows")) systemProperty("skiko.renderApi", "OPENGL")
}

tasks.register<JavaExec>("playerAnimationProbe") {
    group = "verification"
    description = "Measures CPU for static, marquee, smooth waveform, and combined player rendering."
    dependsOn("jvmTestClasses")
    classpath = tasks.named<Test>("jvmTest").get().classpath
    mainClass.set("app.naviamp.ui.NaviampPlayerAnimationProbeKt")
    // Match the packaged Windows surface backend; native cached animation remains accelerated.
    if (System.getProperty("os.name").startsWith("Windows")) {
        val renderApi = providers.gradleProperty("naviamp.windows.skiko.renderApi")
            .orElse("OPENGL").get().trim()
        if (renderApi.isNotBlank() && !renderApi.equals("DEFAULT", ignoreCase = true)) {
            systemProperty("skiko.renderApi", renderApi)
        }
        val popupRenderApi = providers.gradleProperty("naviamp.windows.popupRenderApi")
            .orElse(providers.provider {
                if (providers.gradleProperty("naviamp.windows.skiko.renderApi").isPresent) "" else "SOFTWARE_FAST"
            }).get().trim()
        if (popupRenderApi.isNotBlank() && !popupRenderApi.equals("DEFAULT", ignoreCase = true)) {
            systemProperty("naviamp.desktop.popupRenderApi", popupRenderApi)
        }
    }
    providers.environmentVariable("NAVIAMP_PROBE_JFR").orNull?.let { recording ->
        jvmArgs("-XX:StartFlightRecording=filename=$recording,settings=profile,dumponexit=true")
    }
    if (providers.environmentVariable("NAVIAMP_PROBE_COMPOSITOR").orNull == "true" ||
        (System.getProperty("os.name").startsWith("Mac") &&
            providers.environmentVariable("NAVIAMP_PROBE_VERIFY").orNull == "true")) {
        dependsOn(buildAnimationCompositorProbe)
        systemProperty("naviamp.probe.compositor.library", compositorProbeLibrary.get().asFile.absolutePath)
    }
}

androidComponents {
    onVariants { variant ->
        variant.deviceTests.values.forEach { deviceTest ->
            val mergeFixture = tasks.register<MergeUiTestHostManifest>("merge${deviceTest.name.replaceFirstChar(Char::uppercase)}HostManifest") {
                fixtureManifest.set(layout.projectDirectory.file(
                    if (animationProbe) "src/animationProbeAndroid/AndroidManifest.xml"
                    else "src/androidInstrumentedTest/AndroidManifest.xml",
                ))
            }
            deviceTest.artifacts.use(mergeFixture)
                .wiredWithFiles(MergeUiTestHostManifest::mergedManifest, MergeUiTestHostManifest::outputManifest)
                .toTransform(SingleArtifact.MERGED_MANIFEST)
        }
    }
}
