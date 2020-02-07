
plugins {
    kotlin("jvm")
}

val kotlinVersion: String by rootProject.extra

dependencies {
    implementation(project(":jvm:basic:jvm-maven-deps:script"))
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host:$kotlinVersion")
    testImplementation("junit:junit:4.12")
}

