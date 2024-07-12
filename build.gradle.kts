
plugins {
    kotlin("jvm") version "2.0.20-Beta2"
}

val kotlinVersion: String by extra("2.0.20-Beta2")
val kotlinCoroutinesVersion: String by extra("1.9.0-RC")

allprojects {
    repositories {
        mavenCentral()
        google()
        mavenLocal()
    }
}

