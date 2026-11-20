pluginManagement {
    repositories {
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

rootProject.name = "CommandPanel"
include(":app")
// Capability probe for the Thor firmware. Separate debug tool, never part of the app release.
include(":probe")
// TCP Save Clip: a launcher-only app that asks Command Panel to save a replay (a Replay Buffer shortcut for launchers and apps like Back Button Hero).
include(":trigger")
// Fake Panel plugin for scripts/verify-plugin.sh. Debug-only test tool, never part of a release.
include(":fakeplugin")
