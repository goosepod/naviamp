pluginManagement {
    includeBuild("build-logic/compose-tvos")
    resolutionStrategy.eachPlugin {
        if (requested.id.id == "org.jetbrains.compose") {
            check(requested.version == "1.12.2")
            useModule("app.naviamp.tvosprobe.compose:compose-gradle-plugin:1.12.2")
        }
    }
    repositories {
        maven("@@UI_URI@@") {
            content { includeGroupByRegex("app[.]naviamp[.]tvosprobe.*") }
        }
        maven("@@RESOURCES_URI@@") {
            content { includeGroupByRegex("app[.]naviamp[.]tvosprobe.*") }
        }
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins { id("dev.sajidali.compose-tvos") }
composeTvos {
    interceptComposeGradlePlugin.set(false)
    manifestUrl.set("")
    strictMode.set(true)
    verbose.set(true)
    repositoryUrl.set("@@UI_URI@@")
    dev.sajidali.compose.tvos.ComposeModules.ALL.forEach { source ->
        additionalGroups.put(source, source.replace("org.jetbrains", "app.naviamp.tvosprobe"))
    }
    dev.sajidali.compose.tvos.ComposeArtifacts.ALL.forEach { source ->
        additionalArtifacts.put(source, source.replace("org.jetbrains", "app.naviamp.tvosprobe"))
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("@@UI_URI@@") {
            content { includeGroupByRegex("app[.]naviamp[.]tvosprobe.*") }
        }
        maven("@@RESOURCES_URI@@") {
            content { includeGroupByRegex("app[.]naviamp[.]tvosprobe.*") }
        }
        google()
        mavenCentral()
    }
}
