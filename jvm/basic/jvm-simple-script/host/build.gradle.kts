
plugins {
    kotlin("jvm")
}

val kotlinVersion: String by rootProject.extra

dependencies {
    implementation(project(":jvm:basic:jvm-simple-script:script"))
    implementation("org.jetbrains.kotlin:kotlin-scripting-common:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-main-kts:$kotlinVersion")
    testImplementation("junit:junit:4.13.1")
}

