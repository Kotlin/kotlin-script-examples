
plugins {
    kotlin("jvm") version "1.3.70"
}

val kotlinVersion: String by extra("1.3.70")

allprojects {
    repositories {
        jcenter()
    }
}

