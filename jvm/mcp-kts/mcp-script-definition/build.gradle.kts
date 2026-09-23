
plugins {
    kotlin("jvm")
}

val ktorVersion = "3.4.3"
val mcpSdkVersion = "0.14.0"

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":mcp-codegen"))
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-sse:$ktorVersion")
    implementation("io.modelcontextprotocol:kotlin-sdk-client:$mcpSdkVersion")
    implementation("io.modelcontextprotocol:kotlin-sdk-core:$mcpSdkVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation(kotlin("scripting-common"))
    implementation(kotlin("scripting-jvm"))
    testImplementation(kotlin("test"))
    testImplementation(kotlin("scripting-jvm-host"))
    testImplementation("io.ktor:ktor-server-cio:$ktorVersion")
    testImplementation("io.ktor:ktor-server-sse:$ktorVersion")
    testImplementation("io.modelcontextprotocol:kotlin-sdk-server:$mcpSdkVersion")
}

val bundledArtifacts = providers.provider {
    configurations.runtimeClasspath.get().resolvedConfiguration.resolvedArtifacts.filter(::shouldBundleInDefinitionJar)
}

tasks.named<Jar>("jar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from({
        bundledArtifacts.get().map { artifact ->
            val file = artifact.file
            if (file.isDirectory) file else zipTree(file)
        }
    })

    exclude(
        "META-INF/*.SF",
        "META-INF/*.DSA",
        "META-INF/*.RSA",
        "META-INF/INDEX.LIST",
        "META-INF/DEPENDENCIES",
        "META-INF/LICENSE*",
        "META-INF/NOTICE*",
    )
}

fun shouldBundleInDefinitionJar(artifact: ResolvedArtifact): Boolean {
    val group = artifact.moduleVersion.id.group
    val name = artifact.name
    if (group == "org.jetbrains.kotlin") {
        return false
    }
    if (group == "org.jetbrains.kotlinx" && name.startsWith("kotlinx-coroutines")) {
        return false
    }
    return true
}
