
plugins {
    kotlin("jvm") version "2.1.21"
}

val kotlinVersion: String by extra("2.1.21")
val kotlinCoroutinesVersion: String by extra("1.10.2")

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

