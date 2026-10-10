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

rootProject.name = "KKKeyboard"
include(":app")
include(":sharedCore")
project(":sharedCore").projectDir = file("../sharedCore")
include(":sharedUI")
project(":sharedUI").projectDir = file("../sharedUI")
