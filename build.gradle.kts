
plugins {
    kotlin("jvm") version "1.8.21"
}

val kotlinVersion: String by extra("1.8.21")
val kotlinCoroutinesVersion: String by extra("1.7.0-RC")

allprojects {
    repositories {
        mavenCentral()
    }
}

dependencies {
    // adding these dependencies here just to have it locally available after gradle project import
    // can be useful when running performance tests for .main.kts scripts
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.8.0")
    implementation("eu.jrie.jetbrains:kotlin-shell-core:0.2.1")
}

