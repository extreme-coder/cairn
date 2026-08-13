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
include(":core:sync")
include(":core:session")
include(":feature:capture")
include(":feature:auth")
include(":feature:collect")
