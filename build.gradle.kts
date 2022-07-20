
plugins {
    kotlin("jvm") version "1.7.10"
}

val kotlinVersion: String by extra("1.7.10")
val kotlinCoroutinesVersion: String by extra("1.6.4")

allprojects {
    repositories {
        jcenter()
    }
}

