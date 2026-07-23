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
include(":core:app")
include(":core:presentation")
include(":core:storage")
include(":core:ui")
include(":core:testkit")
include(":providers:navidrome")
include(":platforms:desktop")
include(":platforms:android")
include(":apps:desktop")
include(":apps:android")
