
plugins {
    kotlin("jvm")
}

val kotlinVersion: String by rootProject.extra

dependencies {
    testImplementation(project(":jvm:simple-main-kts:simple-main-kts"))
    testImplementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host:$kotlinVersion")
    testImplementation("junit:junit:4.12")
}

sourceSets {
    main {}
}

