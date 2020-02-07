
plugins {
    kotlin("jvm")
}

val kotlinVersion: String by rootProject.extra

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-script-util:$kotlinVersion")
    runtimeOnly("com.jcabi:jcabi-aether:0.10.1")
    runtimeOnly("org.sonatype.aether:aether-api:1.13.1")
    runtimeOnly("org.apache.maven:maven-core:3.0.3")
}
