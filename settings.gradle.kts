pluginManagement {
    repositories {
        // Google hosts only the Android and Google plugins. Every other plugin marker resolves from
        // Maven Central first, so the Plugin Portal is a fallback rather than a required hop.
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "naviamp"

include(":core:domain")
include(":core:host-api")
include(":core:app")
include(":core:bootstrap")
include(":core:presentation")
include(":core:storage")
include(":core:ui")
include(":core:testkit")
include(":core:tls-testkit")
include(":providers:navidrome")
include(":providers:jellyfin")
include(":platforms:desktop")
include(":platforms:android")
include(":platforms:ios")
include(":platforms:apple")
include(":platforms:apple-media")
if (providers.gradleProperty("naviamp.tvos.enabled").orNull == "true") {
    include(":platforms:tvos")
}
include(":apps:desktop")
include(":apps:android")
include(":apps:ios")
if (providers.gradleProperty("naviamp.tvos.sceneHost").orNull == "true") {
    check(providers.gradleProperty("naviamp.tvos.enabled").orNull == "true") {
        "The tvOS scene host requires -Pnaviamp.tvos.enabled=true and the reviewed Compose port inputs."
    }
    include(":apps:tvos")
}
include(":tools:genre-ontology")
if (providers.gradleProperty("naviamp.spikes.localFolder").orNull == "true") {
    include(":tools:spikes:local-folder")
}
