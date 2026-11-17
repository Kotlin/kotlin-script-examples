import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    kotlin("jvm") version "2.4.21" apply false
}

group = "com.jetbrains.mcpkts"
version = "0.1.0"

// An ABI ceiling, not a preference: the definition jar excludes coroutines, so scripts run on whatever
// copy the host has. The oldest is kotlinc's own (1.8.0, also what the IDE's Run action uses); compiled
// against anything newer, `runBlocking` binds to methods that copy lacks and every script fails.
val coroutinesVersion = "1.8.0"

allprojects {
    repositories {
        mavenCentral()
    }

    configurations.configureEach {
        resolutionStrategy.force("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    }
}

subprojects {
    group = rootProject.group
    version = rootProject.version

    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(17)
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // The integration tests drive the whole scripting pipeline in-process; 512m is not enough.
        maxHeapSize = "2g"
    }
}
