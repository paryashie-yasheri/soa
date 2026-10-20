pluginManagement {
    repositories { gradlePluginPortal(); mavenCentral() }
}
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { mavenCentral() } }
rootProject.name = "soa-lab3"
include("organization-ejb", "organization-web", "orgdirectory-service", "spring-orgdirectory-service")
include("e2e-tests")
