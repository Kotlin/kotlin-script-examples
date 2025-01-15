
plugins {
    kotlin("jvm")
}

val kotlinVersion: String by rootProject.extra
val kotlinCoroutinesVersion: String by rootProject.extra

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-scripting-common:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-scripting-dependencies:$kotlinVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinCoroutinesVersion")
    implementation("org.apache.ivy:ivy:2.5.2")
}

sourceSets {
    test {}
}
