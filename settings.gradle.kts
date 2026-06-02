pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.5.2"
}

stonecutter {
    kotlinController = true
    centralScript = "build.gradle.kts"

    create(rootProject) {
        // 실호환 경계별 대표 버전
        //   1.20.1 → 1.20~1.20.1   (renderBackground/mouseScrolled 구 API — //? 분기)
        //   1.20.4 → 1.20.2~1.20.4
        //   1.20.6 → 1.20.5~1.20.6
        //   1.21.1 → 1.21+
        versions("1.20.1", "1.20.4", "1.20.6", "1.21.1")
        vcsVersion = "1.21.1"
    }
}

rootProject.name = "musix"
