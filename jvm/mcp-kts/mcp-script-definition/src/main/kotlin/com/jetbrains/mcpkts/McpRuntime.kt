package com.jetbrains.mcpkts

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ContentBlock
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.types.PaginatedRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.double
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Duration.Companion.seconds

data class McpResult(
    val text: String,
    private val structuredContent: JsonObject?,
    val isError: Boolean,
    val content: List<ContentBlock>,
) {
    /** The structured content, or else the text parsed as a JSON object. */
    val json: JsonObject?
        get() = structuredContent ?: runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()

    /** For tools that declare an `outputSchema`; the MCP spec requires them to return structured content. */
    @Suppress("unused") // called from generated bindings
    fun requireStructuredContent(tool: String): JsonObject = structuredContent
        ?: error("Tool '$tool' declares an outputSchema but returned no structuredContent (isError=$isError): ${text.take(240)}")

    companion object {
        fun from(result: CallToolResult): McpResult = McpResult(
            text = result.content.filterIsInstance<TextContent>().joinToString(separator = "\n") { it.text },
            structuredContent = result.structuredContent,
            isError = result.isError == true,
            content = result.content,
        )
    }
}

object McpRuntime {
    private val connectionMutex = Mutex()
    private val connections = linkedMapOf<ServerKey, ConnectedClient>()

    /**
     * Bounded by [PROBE_TIMEOUT]: refinement calls this while the IDE may hold a read lock. A failure
     * evicts the pooled connection and retries once, since a pooled connection can outlive its server
     * (restart, expired session); a timeout does not retry.
     */
    fun listTools(
        serverName: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        envHeaders: Map<String, String> = emptyMap(),
    ): List<Tool> = runBlocking {
        val key = ServerKey(serverName, url, headers.toSortedMap(), envHeaders.toSortedMap())
        try {
            withTimeout(PROBE_TIMEOUT) { listAllTools(connectionFor(key).client) }
        } catch (error: Exception) {
            evict(key)
            if (error is TimeoutCancellationException) throw error
            withTimeout(PROBE_TIMEOUT) { listAllTools(connectionFor(key).client) }
        }
    }

    @Suppress("unused") // called from generated bindings
    fun call(
        serverName: String,
        url: String,
        tool: String,
        args: JsonObject = buildJsonObject { },
        headers: Map<String, String> = emptyMap(),
        envHeaders: Map<String, String> = emptyMap(),
    ): McpResult = runBlocking {
        val key = ServerKey(serverName, url, headers.toSortedMap(), envHeaders.toSortedMap())
        // Not retried: the tool may already have run.
        val result = try {
            connectionFor(key).client.callTool(
                CallToolRequest(
                    CallToolRequestParams(
                        name = tool,
                        arguments = args,
                    ),
                ),
            )
        } catch (error: Exception) {
            evict(key)
            throw error
        }
        McpResult.from(result)
    }

    fun reset() = runBlocking {
        connectionMutex.withLock {
            // Dropping a connection without closing its HTTP client leaks the engine and its SSE session.
            connections.values.forEach { connection -> runCatching { connection.httpClient.close() } }
            connections.clear()
        }
    }

    private suspend fun listAllTools(client: Client): List<Tool> {
        val tools = mutableListOf<Tool>()
        var cursor: String? = null
        do {
            val page = client.listTools(ListToolsRequest(PaginatedRequestParams(cursor = cursor)))
            tools += page.tools
            cursor = page.nextCursor
        } while (cursor != null)
        return tools
    }

    private suspend fun connectionFor(key: ServerKey): ConnectedClient {
        connectionMutex.withLock {
            connections[key]?.let { return it }
        }

        val created = createConnectedClient(key)
        val pooled = connectionMutex.withLock { connections.getOrPut(key) { created } }
        if (pooled !== created) {
            // Another caller connected first; this client would otherwise stay open forever.
            runCatching { created.httpClient.close() }
        }
        return pooled
    }

    private suspend fun evict(key: ServerKey) {
        val stale = connectionMutex.withLock { connections.remove(key) }
        stale?.let { runCatching { it.httpClient.close() } }
    }

    private suspend fun createConnectedClient(key: ServerKey): ConnectedClient {
        val httpClient = HttpClient(CIO) {
            install(SSE)
        }
        try {
            val client = Client(
                clientInfo = Implementation(
                    name = "mcp-kts",
                    version = "0.1.0",
                ),
            )
            client.connect(createTransport(httpClient, key))
            return ConnectedClient(httpClient = httpClient, client = client)
        } catch (error: Throwable) {
            runCatching { httpClient.close() }
            throw error
        }
    }

    private fun createTransport(httpClient: HttpClient, key: ServerKey): Transport {
        val url = key.url
        val requestBuilder: HttpRequestBuilder.() -> Unit = {
            applyCustomHeaders(key.headers)
            // Read per request, so secrets never reach the generated bindings. An unset variable omits the header.
            key.envHeaders.forEach { (name, variable) -> System.getenv(variable)?.let { header(name, it) } }
            applyAuthHeader(key.serverName)
        }

        return when (transportForUrl(url)) {
            McpTransport.SSE -> SseClientTransport(
                client = httpClient,
                urlString = url,
                requestBuilder = requestBuilder,
            )

            McpTransport.STREAMABLE_HTTP -> StreamableHttpClientTransport(
                client = httpClient,
                url = url,
                requestBuilder = requestBuilder,
            )
        }
    }

    private fun transportForUrl(url: String): McpTransport {
        return if (url.substringBefore('?').endsWith("/sse")) {
            McpTransport.SSE
        } else {
            McpTransport.STREAMABLE_HTTP
        }
    }

    private fun HttpRequestBuilder.applyAuthHeader(serverName: String) {
        authToken(serverName)?.let { token ->
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    private fun HttpRequestBuilder.applyCustomHeaders(headers: Map<String, String>) {
        headers.forEach { (name, value) ->
            header(name, value)
        }
    }

    private fun authToken(serverName: String): String? {
        val suffix = serverName
            .uppercase()
            .replace(Regex("[^A-Z0-9]+"), "_")
            .trim('_')
        if (suffix.isEmpty()) {
            return null
        }
        return System.getenv("MCP_KTS_TOKEN_$suffix")
    }

    private data class ServerKey(
        val serverName: String,
        val url: String,
        val headers: Map<String, String>,
        val envHeaders: Map<String, String>,
    )

    private data class ConnectedClient(
        val httpClient: HttpClient,
        val client: Client,
    )

    private enum class McpTransport {
        STREAMABLE_HTTP,
        SSE,
    }

    private val PROBE_TIMEOUT = 10.seconds
}

@Suppress("unused") // called from generated bindings
object McpJsonSupport {
    fun required(json: JsonObject, key: String): JsonElement {
        return json[key] ?: error("Missing required property '$key'.")
    }

    fun asString(element: JsonElement, path: String): String {
        return element.asPrimitive(path).contentOrNull
            ?: error("Expected '$path' to be a string.")
    }

    fun asInt(element: JsonElement, path: String): Int {
        return runCatching { element.asPrimitive(path).int }
            .getOrElse { error("Expected '$path' to be an integer.") }
    }

    fun asDouble(element: JsonElement, path: String): Double {
        return runCatching { element.asPrimitive(path).double }
            .getOrElse { error("Expected '$path' to be a number.") }
    }

    fun asBoolean(element: JsonElement, path: String): Boolean {
        return runCatching { element.asPrimitive(path).boolean }
            .getOrElse { error("Expected '$path' to be a boolean.") }
    }

    fun asObject(element: JsonElement, path: String): JsonObject {
        return element as? JsonObject ?: error("Expected '$path' to be an object.")
    }

    fun asArray(element: JsonElement, path: String): JsonArray {
        return element as? JsonArray ?: error("Expected '$path' to be an array.")
    }

    private fun JsonElement.asPrimitive(path: String): JsonPrimitive {
        return this as? JsonPrimitive ?: error("Expected '$path' to be a primitive value.")
    }
}
