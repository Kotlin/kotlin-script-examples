
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

