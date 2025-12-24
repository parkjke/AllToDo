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
        maven("https://repository.map.naver.com/archive/maven")
    }
}

rootProject.name = "AllToDo-Android"
include(":app")
