pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "nfse4j"

include("nfse4j-core", "nfse4j-danfse", "nfse4j-cli", "nfse4j-mcp")
