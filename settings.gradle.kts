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
        maven(url = "https://cdn.osmdroid.org/repository/openkeep/")
    }
}

rootProject.name = "ParentalControl"
include(":core")
include(":app-parent")
include(":app-child")
