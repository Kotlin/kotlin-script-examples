plugins {
    kotlin("jvm")
}

val mcpSdkVersion = "0.14.0"

dependencies {
    implementation(kotlin("stdlib"))
    api("io.modelcontextprotocol:kotlin-sdk-core:$mcpSdkVersion")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    testImplementation(kotlin("test"))
}
