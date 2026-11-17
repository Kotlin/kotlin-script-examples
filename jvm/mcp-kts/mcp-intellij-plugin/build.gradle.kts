plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform") version "2.20.1"
}

repositories {
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(project(":mcp-script-definition")) {
        isTransitive = false
    }

    intellijPlatform {
        // The Maven zip instead of the installer (no DMG to mount on macOS); it has no JBR, hence jetbrainsRuntime().
        intellijIdea("2026.3") { useInstaller = false }
        jetbrainsRuntime()
        bundledPlugin("org.jetbrains.kotlin")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "263"
            untilBuild = "271.*"
        }
    }
}
