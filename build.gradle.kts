
plugins {
    kotlin("jvm") version "1.6.0"
}

val kotlinVersion: String by extra("1.6.20")
val kotlinCoroutinesVersion: String by extra("1.6.0-RC")

allprojects {
    repositories {
        jcenter()
    }
}

