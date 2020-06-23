
plugins {
    kotlin("jvm") version "1.4.0"
}

val kotlinVersion: String by extra("1.4.0")

allprojects {
    repositories {
        jcenter()
    }
}

