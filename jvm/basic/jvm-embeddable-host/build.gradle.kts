
plugins {
    kotlin("jvm")
}

val kotlinVersion: String by rootProject.extra

dependencies {
    implementation(project(":jvm:basic:jvm-simple-script:script"))
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm:$kotlinVersion")
    compileOnly("org.jetbrains.kotlin:kotlin-scripting-jvm-host:$kotlinVersion")
    testRuntimeOnly("org.jetbrains.kotlin:kotlin-scripting-jvm-host:$kotlinVersion")
    testRuntimeOnly("com.google.guava:guava:28.2-jre")
    testImplementation("junit:junit:4.12")
}

