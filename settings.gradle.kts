pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ProotCowork"
include(":app")
include(":termux-x11", ":shell-loader-stub")
project(":termux-x11").projectDir = file("third_party/termux-x11/app")
project(":shell-loader-stub").projectDir = file("third_party/termux-x11/shell-loader/stub")
