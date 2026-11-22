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
rootProject.name = "CybNateAgent"
include(":app")
// Future library modules (split from :app packages once interfaces stabilise):
// include(":core", ":ai-engine", ":agent-runtime", ":tool-execution", ":terminal",
//         ":file-manager", ":project-manager", ":git-integration", ":mcp-client",
//         ":skills-engine", ":memory-engine", ":provider-manager", ":build-system")
