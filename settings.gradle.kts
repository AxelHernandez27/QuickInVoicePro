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

        // 🔧 Agrega este para resolver la dependencia de google-api-services
        maven {
            url = uri("https://maven.google.com")
        }

        // 🔧 Este también puede ser útil como fallback
        maven {
            url = uri("https://jitpack.io")
        }
    }
}

rootProject.name = "Work Administration"
include(":app")
 