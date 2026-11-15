pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
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
if (providers.gradleProperty("naviamp.tvos.feasibility").orNull == "true") {
    include(":platforms:tvos")
}
include(":apps:desktop")
include(":apps:android")
include(":apps:ios")
if (providers.gradleProperty("naviamp.tvos.sceneHost").orNull == "true") {
    check(providers.gradleProperty("naviamp.tvos.feasibility").orNull == "true") {
        "The experimental tvOS scene host requires -Pnaviamp.tvos.feasibility=true and the reviewed Compose port inputs."
    }
    include(":apps:tvos")
}
include(":tools:genre-ontology")
