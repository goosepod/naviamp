plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.sqldelight) apply false
}

val commonProductionSources = fileTree(layout.projectDirectory) {
    include("core/*/src/commonMain/**/*.kt")
    include("providers/*/src/commonMain/**/*.kt")
}

val hostProductionSources = fileTree(layout.projectDirectory) {
    include("apps/android/src/main/kotlin/**/*.kt")
    include("apps/desktop/src/desktopMain/kotlin/**/*.kt")
}

val existingHostProductDebt = setOf(
    "apps/android/src/main/kotlin/app/naviamp/android/app/AndroidAppState.kt",
    "apps/android/src/main/kotlin/app/naviamp/android/app/AndroidDetailActions.kt",
    "apps/android/src/main/kotlin/app/naviamp/android/app/AndroidHomeMediaActions.kt",
    "apps/android/src/main/kotlin/app/naviamp/android/app/AndroidMainShellActions.kt",
    "apps/android/src/main/kotlin/app/naviamp/android/app/AndroidNowPlayingActions.kt",
    "apps/android/src/main/kotlin/app/naviamp/android/app/AndroidPendingProviderActions.kt",
    "apps/android/src/main/kotlin/app/naviamp/android/media/AndroidMediaActionsController.kt",
    "apps/desktop/src/desktopMain/kotlin/app/naviamp/desktop/app/DesktopAppActions.kt",
    "apps/desktop/src/desktopMain/kotlin/app/naviamp/desktop/app/DesktopAppRouteContent.kt",
    "apps/desktop/src/desktopMain/kotlin/app/naviamp/desktop/app/DesktopNavigationRouteProperty.kt",
    "apps/desktop/src/desktopMain/kotlin/app/naviamp/desktop/app/DesktopPlayerRouteContent.kt",
    "apps/desktop/src/desktopMain/kotlin/app/naviamp/desktop/app/DesktopRestoredAppState.kt",
    "apps/desktop/src/desktopMain/kotlin/app/naviamp/desktop/app/DesktopSharedContentActions.kt",
    "apps/desktop/src/desktopMain/kotlin/app/naviamp/desktop/media/DesktopMediaActionsController.kt",
)

tasks.register("verifyCoreFirstArchitecture") {
    group = "verification"
    description = "Rejects platform APIs in common code and new host-owned product surfaces."
    inputs.files(commonProductionSources, hostProductionSources)
    inputs.property("existingHostProductDebt", existingHostProductDebt.sorted())

    doLast {
        val failures = mutableListOf<String>()
        val forbiddenCommonImport = Regex(
            """^\s*import\s+(?:android\.|androidx\.(?!compose\.)|java\.|javax\.|sun\.|com\.sun\.|platform\.|kotlinx\.cinterop\.)""",
        )
        commonProductionSources.files.sorted().forEach { source ->
            source.readLines().forEachIndexed { index, line ->
                if (forbiddenCommonImport.containsMatchIn(line)) {
                    failures += "${source.relativeTo(projectDir).invariantSeparatorsPath}:${index + 1}: $line"
                }
            }
        }

        val productFileName = Regex(".*(?:Route|Actions|AppState|StateMachine).*\\.kt")
        hostProductionSources.files
            .map { it.relativeTo(projectDir).invariantSeparatorsPath }
            .filter { productFileName.matches(it.substringAfterLast('/')) }
            .filterNot(existingHostProductDebt::contains)
            .sorted()
            .forEach { path -> failures += "$path: new host-owned product surface is not allowlisted" }

        val forbiddenHostDeclaration = Regex(
            """^\s*(?:(?:data|sealed)\s+)?(?:class|interface|object|enum\s+class)\s+(?:SharedRoute|NaviampCoreCommand|NaviampAppShellActions|NaviampCoreState|NaviampCoreApp)\b""",
        )
        hostProductionSources.files.sorted().forEach { source ->
            source.readLines().forEachIndexed { index, line ->
                if (forbiddenHostDeclaration.containsMatchIn(line)) {
                    failures += "${source.relativeTo(projectDir).invariantSeparatorsPath}:${index + 1}: $line"
                }
            }
        }

        if (failures.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Core-first architecture guard failed:")
                    failures.forEach { appendLine("- $it") }
                    append("Move product code into common Core or document a genuine OS/API exception in the guard.")
                },
            )
        }
    }
}
