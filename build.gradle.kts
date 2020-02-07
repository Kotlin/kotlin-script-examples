
plugins {
    kotlin("jvm") version "1.3.70-eap-184"
}

val kotlinVersion: String by extra("1.3.70-eap-184")
    
allprojects {
    repositories {
        maven("https://dl.bintray.com/kotlin/kotlin-eap")
        jcenter() 
    }
}

