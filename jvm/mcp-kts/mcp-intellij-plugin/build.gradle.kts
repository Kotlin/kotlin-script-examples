plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform") version "2.20.1"
}

repositories {
    intellijPlatform {
        defaultRepositories()
        // IntelliJ IDEA 2027.1 nightly. 2026.3 runs scripts with kotlinc 2.4.10, which crashes on imported scripts (KT-87958);
        nightly()
    }
}

dependencies {
    implementation(project(":mcp-script-definition")) {
        isTransitive = false
    }

    intellijPlatform {
        // Nightlies ship only as the Maven zip, which has no JBR: hence jetbrainsRuntime().
        intellijIdea("271-SNAPSHOT") { useInstaller = false }
        jetbrainsRuntime()
        bundledPlugin("org.jetbrains.kotlin")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "271"
            untilBuild = "271.*"
        }
    }
}
