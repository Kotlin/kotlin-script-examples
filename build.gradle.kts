
plugins {
    kotlin("jvm") version "1.6.20"
}

val kotlinVersion: String by extra("1.6.20")
val kotlinCoroutinesVersion: String by extra("1.6.1")

allprojects {
    repositories {
        jcenter()
    }
}

