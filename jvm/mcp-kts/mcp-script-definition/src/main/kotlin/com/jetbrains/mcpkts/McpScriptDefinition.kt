package com.jetbrains.mcpkts

import com.jetbrains.mcpkts.codegen.McpCodeGenerator
import io.modelcontextprotocol.kotlin.sdk.types.ContentBlock
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.net.JarURLConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ResultWithDiagnostics.Success
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptCompilationConfigurationKeys
import kotlin.script.experimental.api.ScriptCollectedData
import kotlin.script.experimental.api.ScriptConfigurationRefinementContext
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfigurationRefinementContext
import kotlin.script.experimental.api.ScriptAcceptedLocation
import kotlin.script.experimental.api.acceptedLocations
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.collectedAnnotations
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.api.isStandalone
import kotlin.script.experimental.api.makeFailureResult
import kotlin.script.experimental.api.providedProperties
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.api.refineConfigurationBeforeEvaluate
import kotlin.script.experimental.api.valueOrThrow
import kotlin.script.experimental.api.importScripts
import kotlin.script.experimental.host.FileScriptSource
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.util.PropertiesCollection
import kotlin.script.experimental.util.PropertiesCollection.Key

@Target(AnnotationTarget.FILE)
@Repeatable
annotation class McpServer(
    val name: String,
    val url: String,
    val headers: Array<String> = [],
    val envHeaders: Array<String> = [],
)

@KotlinScript(
    fileExtension = "mcp.kts",
    compilationConfiguration = McpScriptCompilationConfiguration::class,
    evaluationConfiguration = McpScriptEvaluationConfiguration::class,
)
abstract class McpScript(val args: Array<String> = emptyArray())

object McpScriptCompilationConfiguration : ScriptCompilationConfiguration({
    defaultImports(McpServer::class)
    jvm {
        updateClasspath(scriptCompilationClasspath())
    }
    refineConfiguration {
        onAnnotations(listOf(McpServer::class), ::refineMcp)
    }
    isStandalone(true)
    ide {
        acceptedLocations(ScriptAcceptedLocation.Everywhere)
    }
})

private object McpScriptCompilationConfigurationKeys : ScriptCompilationConfigurationKeys

private val ScriptCompilationConfigurationKeys.mcpProvidedBindings: Key<Map<String, String>> by PropertiesCollection.key()

object McpScriptEvaluationConfiguration : ScriptEvaluationConfiguration({
    refineConfigurationBeforeEvaluate(::refineMcpEvaluation)
})

/**
 * Pinned rather than inherited: the host's classpath differs per host (59 jars under Gradle, one in the IDE).
 * Coroutines are not in the definition jar, and kotlinc 2.4.20+ loads kotlin-reflect where it cannot see
 * the stdlib unless it is listed here.
 */
private fun scriptCompilationClasspath(): List<File> = listOfNotNull(
    locationOf(McpScript::class.java),
    locationOf(ContentBlock::class.java),
    locationOf(JsonObject::class.java),
    locationOf(Unit::class.java),
    locationOf(CoroutineScope::class.java),
    runCatching { Class.forName("kotlin.reflect.full.KClasses") }.getOrNull()?.let(::locationOf),
).distinct().filter { it.exists() }

/** The jar a class was loaded from; via its resource, as IntelliJ's `UrlClassLoader` leaves `codeSource` null. */
fun locationOf(klass: Class<*>): File? {
    val resource = klass.getResource("/" + klass.name.replace('.', '/') + ".class")
    if (resource != null) {
        val connection = runCatching { resource.openConnection() }.getOrNull()
        if (connection is JarURLConnection) {
            runCatching { File(connection.jarFileURL.toURI()) }.getOrNull()?.let { return it }
        }
    }
    return klass.protectionDomain?.codeSource?.location
        ?.let { runCatching { File(it.toURI()) }.getOrNull() }
}

private fun refineMcp(
    context: ScriptConfigurationRefinementContext,
): ResultWithDiagnostics<ScriptCompilationConfiguration> {
    val servers = try {
        parseMcpServers(context)
    } catch (error: IllegalArgumentException) {
        return makeFailureResult(error.message ?: "Invalid McpServer annotation.")
    }
    if (servers.isEmpty()) {
        return context.compilationConfiguration.asSuccess()
    }

    val bindingsOwners = mutableMapOf<String, String>()
    for (server in servers) {
        if (!server.name.matches(KOTLIN_IDENTIFIER)) {
            return makeFailureResult(
                "MCP server name '${server.name}' is not a valid Kotlin identifier for generated bindings.",
            )
        }
        // "demo" and "Demo" would share one class, and the second would overwrite the first.
        val previous = bindingsOwners.put(McpCodeGenerator.bindingsClassName(server.name), server.name)
        if (previous != null) {
            return makeFailureResult(
                "MCP servers '$previous' and '${server.name}' both generate " +
                    "${McpCodeGenerator.bindingsClassName(server.name)}; rename one of them.",
            )
        }
    }

    val options = McpRefinementOptions.fromSystem()
    val scriptLocation = context.script.locationId?.let(::File)?.absoluteFile
    val generatedFiles = mutableListOf<File>()
    val providedBindings = linkedMapOf<String, String>()   // JVM binary names, for reflection at eval time
    val providedTypeNames = linkedMapOf<String, String>()  // Kotlin type names, for the compiler
    val diagnostics = mutableListOf<ScriptDiagnostic>()

    for (server in servers) {
        val resolution = resolveBindings(scriptLocation, server, options)
        val sourceFile = resolution.sourceFile ?: return makeFailureResult(resolution.message.orEmpty())
        resolution.message?.let { warning ->
            diagnostics += ScriptDiagnostic(
                ScriptDiagnostic.unspecifiedInfo,
                warning,
                ScriptDiagnostic.Severity.WARNING,
                scriptLocation?.path,
                null,
                null,
            )
        }

        if (options.printBindings) {
            println(sourceFile.readText())
        }
        publishGeneratedFile(sourceFile)
        generatedFiles += sourceFile
        // Nested in the imported script's class: IdeaBindings.mcp.kts -> IdeaBindings_mcp$IdeaBindings.
        val scriptClass = sourceFile.name.removeSuffix(".kts").replace('.', '_')
        providedBindings[server.name] = scriptClass + "$" + McpCodeGenerator.bindingsClassName(server.name)
        providedTypeNames[server.name] = McpCodeGenerator.bindingsClassName(server.name)
    }

    val refined = ScriptCompilationConfiguration(context.compilationConfiguration) {
        // The IDE ignores the text and resolves imports through the VFS: see publishGeneratedFile.
        importScripts.append(
            generatedFiles.map { file -> FileScriptSource(file, file.readText()) },
        )
        providedProperties.append(
            providedTypeNames.mapValues { (_, typeName) -> KotlinType(typeName) },
        )
        McpScriptCompilationConfigurationKeys.mcpProvidedBindings(providedBindings)
    }

    return Success(refined, diagnostics)
}

private fun refineMcpEvaluation(
    context: ScriptEvaluationConfigurationRefinementContext,
): ResultWithDiagnostics<ScriptEvaluationConfiguration> {
    val providedBindings = context.compiledScript.compilationConfiguration[McpScriptCompilationConfigurationKeys.mcpProvidedBindings]
        .orEmpty()
    if (providedBindings.isEmpty()) {
        return context.evaluationConfiguration.asSuccess()
    }

    val scriptClass = try {
        runBlocking {
            context.compiledScript.getClass(context.evaluationConfiguration).valueOrThrow()
        }
    } catch (error: Exception) {
        return makeFailureResult(
            "Failed to resolve compiled MCP script class: ${error.message ?: error::class.simpleName}",
        )
    }

    val classLoader = scriptClass.java.classLoader
    val instances = try {
        linkedMapOf<String, Any>().apply {
            providedBindings.forEach { (propertyName, jvmClassName) ->
                put(propertyName, instantiateGeneratedBinding(classLoader, jvmClassName))
            }
        }
    } catch (error: Exception) {
        return makeFailureResult(
            "Failed to instantiate MCP bindings: ${error.message ?: error::class.simpleName}",
        )
    }

    val refined = ScriptEvaluationConfiguration(context.evaluationConfiguration) {
        providedProperties.append(instances)
    }
    return refined.asSuccess()
}

private fun instantiateGeneratedBinding(
    classLoader: ClassLoader,
    jvmClassName: String,
): Any {
    val bindingClass = Class.forName(jvmClassName, true, classLoader)
    return bindingClass.getDeclaredConstructor().newInstance()
}

/**
 * Refreshes the IDE's VFS, which does not see files written with plain IO; a no-op on other hosts.
 * Reflective and here, not in the plugin: the definition has its own class loader and runs without IntelliJ.
 * The platform refuses a synchronous refresh under a read lock, hence a plain thread
 * (`executeOnPooledThread` needs JetBrains' patched coroutines, which the Run action lacks).
 */
private fun publishGeneratedFile(generatedFile: File) {
    val refresh = Runnable {
        try {
            val localFileSystem = Class.forName("com.intellij.openapi.vfs.LocalFileSystem")
            val fileSystem = localFileSystem.getMethod("getInstance").invoke(null)
            localFileSystem.getMethod("refreshAndFindFileByIoFile", File::class.java)
                .invoke(fileSystem, generatedFile)
        } catch (error: Throwable) {
            rethrowControlFlow(error)
            // Not in an IDE: the file is on disk anyway.
        }
    }

    val underReadLock = try {
        val application = Class.forName("com.intellij.openapi.application.ApplicationManager")
            .getMethod("getApplication").invoke(null) ?: return
        Class.forName("com.intellij.openapi.application.Application")
            .getMethod("isReadAccessAllowed").invoke(application) as Boolean
    } catch (error: Throwable) {
        rethrowControlFlow(error)
        return
    }

    if (underReadLock) {
        Thread(refresh, "mcp-kts-vfs-refresh").apply { isDaemon = true }.start()
    } else {
        refresh.run()
    }
}

private fun rethrowControlFlow(error: Throwable) {
    if (error::class.java.name.substringAfterLast('.') in CONTROL_FLOW_EXCEPTIONS) {
        throw error
    }
}

/** Re-probes [server] and rewrites its bindings; when it is unreachable, reuses the last ones. */
private fun resolveBindings(
    scriptLocation: File?,
    server: McpServerSpec,
    options: McpRefinementOptions,
): BindingResolution {
    val mirrorFile = generatedBindingsFile(scriptLocation, server, options)
    val previousSource = mirrorFile.takeIf { it.isFile }?.readText()

    val tools = try {
        McpRuntime.listTools(server.name, server.url, server.headers, server.envHeaders)
    } catch (error: Exception) {
        val unreachable = "MCP server '${server.name}' is unreachable at ${server.url}: ${error.message ?: error::class.simpleName}."
        return if (previousSource != null) {
            BindingResolution(mirrorFile, "$unreachable Using the previously generated bindings.")
        } else {
            BindingResolution(null, "$unreachable No previously generated bindings are available; open the script while the server is running to generate them.")
        }
    }

    val bindings = McpCodeGenerator.generate(
        serverName = server.name,
        serverUrl = server.url,
        serverHeaders = server.headers,
        serverEnvHeaders = server.envHeaders,
        tools = tools,
    )
    if (bindings == previousSource) {
        return BindingResolution(mirrorFile)
    }

    try {
        mirrorFile.parentFile?.mkdirs()
        mirrorFile.writeText(bindings)
    } catch (error: Exception) {
        return BindingResolution(null, "Failed to write bindings for '${server.name}': ${error.message ?: error::class.simpleName}")
    }
    return BindingResolution(
        mirrorFile,
        previousSource?.let { "MCP server '${server.name}' schema changed at ${server.url}; regenerated bindings." },
    )
}

private val KOTLIN_IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")

private fun parseMcpServers(
    context: ScriptConfigurationRefinementContext,
): List<McpServerSpec> {
    return context.collectedData
        ?.get(ScriptCollectedData.collectedAnnotations)
        .orEmpty()
        .mapNotNull { sourceAnnotation ->
            (sourceAnnotation.annotation as? McpServer)?.let { annotation ->
                McpServerSpec(
                    name = annotation.name,
                    url = annotation.url,
                    headers = parseHeaders(annotation.name, annotation.headers),
                    envHeaders = parseHeaders(annotation.name, annotation.envHeaders),
                )
            }
        }
}

private fun parseHeaders(serverName: String, headers: Array<String>): Map<String, String> {
    return headers.associate { header ->
        val separatorIndex = header.indexOf('=')
        require(separatorIndex > 0) {
            "MCP server '$serverName' has invalid header '$header'. Use the form \"Name=Value\"."
        }
        val name = header.substring(0, separatorIndex).trim()
        val value = header.substring(separatorIndex + 1).trim()
        require(name.isNotEmpty()) {
            "MCP server '$serverName' has invalid header '$header'. Header name cannot be blank."
        }
        name to value
    }
}

private data class McpServerSpec(
    val name: String,
    val url: String,
    val headers: Map<String, String>,
    val envHeaders: Map<String, String>,
)

/** A null [sourceFile] is a failure explained by [message]; otherwise [message] is a warning. */
private data class BindingResolution(
    val sourceFile: File?,
    val message: String? = null,
)

private data class McpRefinementOptions(
    val printBindings: Boolean,
    val cacheDir: File,
) {
    companion object {
        fun fromSystem(): McpRefinementOptions {
            val cacheDir = System.getProperty(MCP_KTS_CACHE_DIR_PROPERTY)
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?: System.getenv(MCP_KTS_CACHE_DIR_ENV)
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::File)
                ?: File(System.getProperty("user.home")).resolve(".cache/kotlin-mcp-kts")
            return McpRefinementOptions(
                printBindings = System.getProperty(MCP_KTS_PRINT_BINDINGS_PROPERTY).toBoolean(),
                cacheDir = cacheDir.absoluteFile,
            )
        }
    }
}

/** Next to the script under `mcp-kts-generated/<script-name>/`; in the cache dir for a script with no file. */
private fun generatedBindingsFile(
    scriptLocation: File?,
    server: McpServerSpec,
    options: McpRefinementOptions,
): File {
    val fileName = McpCodeGenerator.bindingsFileName(server.name)
    if (scriptLocation != null) {
        val baseName = scriptLocation.name.removeSuffix(".mcp.kts")
        return scriptLocation.resolveSibling(GENERATED_DIR_NAME)
            .resolve(baseName)
            .resolve(fileName)
            .absoluteFile
    }
    return options.cacheDir.resolve(GENERATED_DIR_NAME).resolve(fileName).absoluteFile
}

private const val MCP_KTS_CACHE_DIR_ENV = "MCP_KTS_CACHE_DIR"
private const val MCP_KTS_CACHE_DIR_PROPERTY = "mcp.kts.cache.dir"
private const val MCP_KTS_PRINT_BINDINGS_PROPERTY = "mcp.kts.printBindings"
private const val GENERATED_DIR_NAME = "mcp-kts-generated"
private val CONTROL_FLOW_EXCEPTIONS = setOf("ProcessCanceledException", "CancellationException")
