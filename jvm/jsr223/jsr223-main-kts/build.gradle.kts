
plugins {
    kotlin("jvm")
}

val kotlinVersion: String by rootProject.extra

dependencies {
    runtimeOnly("org.jetbrains.kotlin:kotlin-main-kts:$kotlinVersion")
    runtimeOnly("org.jetbrains.kotlin:kotlin-scripting-jsr223:$kotlinVersion")
    testRuntimeOnly("org.jetbrains.kotlin:kotlin-main-kts:$kotlinVersion")
    testRuntimeOnly("org.jetbrains.kotlin:kotlin-scripting-jsr223:$kotlinVersion")
    testImplementation("junit:junit:4.12")
}
