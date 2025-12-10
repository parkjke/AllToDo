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
        maven {
            url = uri("https://devrepo.kakao.com/nexus/repository/kakaomap-releases/")
            isAllowInsecureProtocol = true
        }
        // maven("https://naver.jfrog.io/artifactory/maven/") // REMOVED: Causing corrupt artifacts
    }
}

rootProject.name = "AllToDo-Android"
include(":app")
