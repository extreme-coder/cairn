pluginManagement {
    repositories {
        google()
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

rootProject.name = "cairn"

include(":app")
include(":core:model")
include(":core:database")
include(":core:designsystem")
include(":core:network")
include(":feature:capture")
