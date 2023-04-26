
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

