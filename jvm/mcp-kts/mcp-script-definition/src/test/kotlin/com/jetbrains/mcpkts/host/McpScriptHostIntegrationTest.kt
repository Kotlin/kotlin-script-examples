package com.jetbrains.mcpkts.host

import com.jetbrains.mcpkts.McpRuntime
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.http.HttpStatusCode
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.script.experimental.api.valueOrThrow

class McpScriptHostIntegrationTest {
    @Test
    fun `loads bindings from McpServer annotation and calls a tool`() {
        val server = startServer()
        val port = server.port

        val scriptFile = File.createTempFile("mcp-kts-host-", ".mcp.kts")
        scriptFile.writeText(
            """
            @file:McpServer("demo", "http://127.0.0.1:$port/mcp")

            println(demo.greet(name = "Ada").text)
            """.trimIndent(),
        )

        val stdout = ByteArrayOutputStream()
        val originalOut = System.out

        try {
            System.setOut(PrintStream(stdout))
            evaluateScript(scriptFile).valueOrThrow()
        } finally {
            System.setOut(originalOut)
            server.close()
            scriptFile.delete()
        }

        assertEquals("Hello, Ada!", stdout.toString().trim())
    }

    @Test
    fun `loads bindings for multiple servers in the same script`() {
        val server = startServer()
        val port = server.port

        val scriptFile = File.createTempFile("mcp-kts-host-multi-server-", ".mcp.kts")
        scriptFile.writeText(
            """
            @file:McpServer("demoAlpha", "http://127.0.0.1:$port/mcp")
            @file:McpServer("demoBeta", "http://127.0.0.1:$port/mcp")

            println("${'$'}{demoAlpha.greet(name = "Ada").text}|${'$'}{demoBeta.greet(name = "Linus").text}")
            """.trimIndent(),
        )

        val stdout = ByteArrayOutputStream()
        val originalOut = System.out

        try {
            System.setOut(PrintStream(stdout))
            evaluateScript(scriptFile).valueOrThrow()
        } finally {
            System.setOut(originalOut)
            server.close()
            scriptFile.delete()
        }

        assertEquals("Hello, Ada!|Hello, Linus!", stdout.toString().trim())
    }

    @Test
    fun `loads bindings from SSE endpoint and calls a tool`() {
        val transports = ConcurrentHashMap<String, SseServerTransport>()
        val server = startServer {
            install(SSE)
            routing {
                sse("/sse") {
                    val transport = SseServerTransport("/sse", this)
                    transports[transport.sessionId] = transport

                    val server = testServer()
                    server.onClose {
                        transports.remove(transport.sessionId)
                    }
                    server.createSession(transport)

                    awaitCancellation()
                }

                post("/sse") {
                    val sessionId = call.request.queryParameters["sessionId"]
                    if (sessionId == null) {
                        call.respondText(
                            "sessionId query parameter is not provided",
                            status = HttpStatusCode.BadRequest,
                        )
                        return@post
                    }

                    val transport = transports[sessionId]
                    if (transport == null) {
                        call.respondText("Session not found", status = HttpStatusCode.NotFound)
                        return@post
                    }

                    transport.handlePostMessage(call)
                }
            }
        }
        val port = server.port

        val scriptFile = File.createTempFile("mcp-kts-host-sse-", ".mcp.kts")
        scriptFile.writeText(
            """
            @file:McpServer("demo", "http://127.0.0.1:$port/sse")

            println(demo.greet(name = "Ada").text)
            """.trimIndent(),
        )

        val stdout = ByteArrayOutputStream()
        val originalOut = System.out

        try {
            System.setOut(PrintStream(stdout))
            evaluateScript(scriptFile).valueOrThrow()
        } finally {
            System.setOut(originalOut)
            server.close()
            scriptFile.delete()
        }

        assertEquals("Hello, Ada!", stdout.toString().trim())
    }

    @Test
    fun `decodes structured tool output into typed bindings`() {
        val server = startServer()
        val port = server.port

        val scriptFile = File.createTempFile("mcp-kts-host-typed-", ".mcp.kts")
        scriptFile.writeText(
            """
            @file:McpServer("demo", "http://127.0.0.1:$port/mcp")

            val profile = demo.describeProfile(userId = "ada", verbose = true)
            println("${'$'}{profile.user.name}|${'$'}{profile.user.nickname ?: "none"}|${'$'}{profile.user.role.wireValue}|${'$'}{profile.labels.joinToString(",") { it.wireValue }}|${'$'}{profile.visits.first().day}")
            """.trimIndent(),
        )

        val stdout = ByteArrayOutputStream()
        val originalOut = System.out

        try {
            System.setOut(PrintStream(stdout))
            evaluateScript(scriptFile).valueOrThrow()
        } finally {
            System.setOut(originalOut)
            server.close()
            scriptFile.delete()
        }

        assertEquals("Ada|none|admin|ops,infra|2026-07-09", stdout.toString().trim())
    }

    @Test
    fun `writes generated bindings next to the script`() {
        val server = startServer()
        val port = server.port
        val scriptDir = Files.createTempDirectory("mcp-kts-script-").toFile()
        val scriptFile = scriptDir.resolve("next-to-demo.mcp.kts")
        scriptFile.writeText(
            """
            @file:McpServer("demo", "http://127.0.0.1:$port/mcp")

            println(demo.greet(name = "Ada").text)
            """.trimIndent(),
        )

        try {
            McpRuntime.reset()
            assertEquals(
                "Hello, Ada!",
                evaluateToStdout(scriptFile),
            )

            val generated = scriptDir.resolve("mcp-kts-generated/next-to-demo/DemoBindings.mcp.kts")
            assertTrue(generated.isFile, "expected generated bindings next to the script")
            assertTrue(generated.readText().contains("fun greet("))
        } finally {
            server.close()
            scriptDir.deleteRecursively()
        }
    }

    @Test
    fun `reuses previously generated bindings when the server is unreachable`() {
        val server = startServer()
        val port = server.port
        val scriptDir = Files.createTempDirectory("mcp-kts-script-").toFile()
        val scriptFile = scriptDir.resolve("offline-demo.mcp.kts")
        scriptFile.writeText(
            """
            @file:McpServer("demo", "http://127.0.0.1:$port/mcp")

            // Resolve the generated binding at compile time without calling the (offline) server.
            val greeter: () -> Unit = { demo.greet(name = "Ada") }
            println("compiled")
            """.trimIndent(),
        )

        try {
            McpRuntime.reset()
            assertEquals(
                "compiled",
                evaluateToStdout(scriptFile),
            )
        } finally {
            server.close()
        }

        // Server is down: the probe fails and the last generated bindings are reused.
        val offline = evaluateScript(scriptFile)
        try {
            assertTrue(
                offline.reports.any { it.message.contains("unreachable") },
                "expected an unreachable-server warning",
            )
            offline.valueOrThrow()
        } finally {
            scriptDir.deleteRecursively()
        }
    }

    @Test
    fun `regenerates bindings when the server schema changes`() {
        var server = startServer()
        val port = server.port
        val scriptDir = Files.createTempDirectory("mcp-kts-script-").toFile()
        val scriptFile = scriptDir.resolve("drift-demo.mcp.kts")
        scriptFile.writeText(
            """
            @file:McpServer("demo", "http://127.0.0.1:$port/mcp")

            println(demo.greet(name = "Ada").text)
            """.trimIndent(),
        )
        val generated = scriptDir.resolve("mcp-kts-generated/drift-demo/DemoBindings.mcp.kts")

        try {
            McpRuntime.reset()
            assertEquals(
                "Hello, Ada!",
                evaluateToStdout(scriptFile),
            )
            assertTrue(!generated.readText().contains("fun ping("))

            server.stop()
            server = startServer(port = port, schemaVersion = 2)

            // The next run re-probes and sees the new "ping" tool.
            val refreshed = evaluateScript(scriptFile)
            refreshed.valueOrThrow()
            assertTrue(generated.readText().contains("fun ping("), "expected regenerated bindings to include the new tool")
            assertTrue(refreshed.reports.any { it.message.contains("schema changed") })
        } finally {
            server.close()
            scriptDir.deleteRecursively()
        }
    }

    @Test
    fun `passes explicit McpServer headers to probe and tool calls`() {
        val received = ConcurrentLinkedQueue<String?>()
        val server = startServer(module = {
            intercept(ApplicationCallPipeline.Plugins) { received += call.request.headers["IJ_MCP_SERVER_PROJECT_PATH"] }
            mcpStreamableHttp { testServer() }
        })
        val port = server.port
        val projectDir = Files.createTempDirectory("mcp-kts-project-").toFile()
        val samplesDir = projectDir.resolve("samples").apply { mkdirs() }
        val scriptFile = samplesDir.resolve("idea-demo.mcp.kts")
        scriptFile.writeText(
            """
            @file:McpServer(
                name = "idea",
                url = "http://127.0.0.1:$port/mcp",
                headers = [
                    "IJ_MCP_SERVER_PROJECT_PATH=${projectDir.absolutePath}"
                ]
            )

            println(idea.greet(name = "Ada").text)
            """.trimIndent(),
        )

        try {
            McpRuntime.reset()
            assertEquals(
                "Hello, Ada!",
                evaluateToStdout(scriptFile),
            )

            // The probe and the call each take several requests; every one must carry the header.
            assertTrue(received.size >= 2 && received.all { it == projectDir.absolutePath }, "headers on the wire: $received")
        } finally {
            server.close()
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun `sends envHeaders from the environment without writing them into the bindings`() {
        val home = assertNotNull(System.getenv("HOME"))
        val received = ConcurrentLinkedQueue<String>()
        val server = startServer(module = {
            intercept(ApplicationCallPipeline.Plugins) { call.request.headers["X-Home"]?.let(received::add) }
            mcpStreamableHttp { testServer() }
        })
        val scriptDir = Files.createTempDirectory("mcp-kts-env-").toFile()
        val scriptFile = scriptDir.resolve("env-demo.mcp.kts")
        scriptFile.writeText(
            """
            @file:McpServer("demo", "http://127.0.0.1:${server.port}/mcp", envHeaders = ["X-Home=HOME"])

            println(demo.greet(name = "Ada").text)
            """.trimIndent(),
        )

        try {
            McpRuntime.reset()
            assertEquals(
                "Hello, Ada!",
                evaluateToStdout(scriptFile = scriptFile),
            )
            assertTrue(received.isNotEmpty() && received.all { it == home }, "X-Home on the wire: $received")
            val bindings = scriptDir.resolve("mcp-kts-generated/env-demo/DemoBindings.mcp.kts").readText()
            assertTrue(home !in bindings, "the value must not be generated")
        } finally {
            server.close()
            scriptDir.deleteRecursively()
        }
    }

    private fun testServer(schemaVersion: Int = 1): Server {
        val server = Server(
            serverInfo = Implementation(
                name = "test-server",
                version = "1.0.0",
            ),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = true),
                ),
            ),
        )

        server.addTool(
            name = "greet",
            description = "Return a greeting",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("name") {
                        put("type", "string")
                    }
                },
                required = listOf("name"),
            ),
        ) { request ->
            val name = request.arguments?.get("name")?.jsonPrimitive?.content ?: "World"
            CallToolResult(
                content = listOf(TextContent("Hello, $name!")),
            )
        }

        if (schemaVersion >= 2) {
            server.addTool(
                name = "ping",
                description = "Second schema version marker",
                inputSchema = ToolSchema(),
            ) {
                CallToolResult(
                    content = listOf(TextContent("pong")),
                )
            }
        }

        server.addTool(
            name = "describe_profile",
            description = "Return a structured profile payload",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("userId") {
                        put("type", "string")
                    }
                    putJsonObject("verbose") {
                        put("type", "boolean")
                    }
                },
                required = listOf("userId"),
            ),
            outputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("user") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("name") {
                                put("type", "string")
                            }
                            putJsonObject("nickname") {
                                put("type", "string")
                            }
                            putJsonObject("role") {
                                put("type", "string")
                                putJsonArray("enum") {
                                    add(JsonPrimitive("admin"))
                                    add(JsonPrimitive("guest"))
                                }
                            }
                        }
                        putJsonArray("required") {
                            add(JsonPrimitive("name"))
                            add(JsonPrimitive("role"))
                        }
                    }
                    putJsonObject("labels") {
                        put("type", "array")
                        putJsonObject("items") {
                            put("type", "string")
                            putJsonArray("enum") {
                                add(JsonPrimitive("ops"))
                                add(JsonPrimitive("infra"))
                            }
                        }
                    }
                    putJsonObject("visits") {
                        put("type", "array")
                        putJsonObject("items") {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("day") {
                                    put("type", "string")
                                }
                                putJsonObject("successful") {
                                    put("type", "boolean")
                                }
                            }
                            putJsonArray("required") {
                                add(JsonPrimitive("day"))
                                add(JsonPrimitive("successful"))
                            }
                        }
                    }
                },
                required = listOf("user", "labels", "visits"),
            ),
        ) { request ->
            val userId = request.arguments?.get("userId")?.jsonPrimitive?.content ?: "unknown"
            val verbose = request.arguments?.get("verbose")?.jsonPrimitive?.content == "true"
            CallToolResult(
                content = listOf(TextContent("Profile for $userId")),
                structuredContent = buildJsonObject {
                    putJsonObject("user") {
                        put("name", "Ada")
                        if (!verbose) {
                            put("nickname", "Ada Lovelace")
                        }
                        put("role", "admin")
                    }
                    putJsonArray("labels") {
                        add(JsonPrimitive("ops"))
                        add(JsonPrimitive("infra"))
                    }
                    putJsonArray("visits") {
                        add(
                            buildJsonObject {
                                put("day", "2026-07-09")
                                put("successful", true)
                            },
                        )
                    }
                },
            )
        }

        return server
    }

    /**
     * Starts a test server and reports the port it actually bound. Picking a free port and binding
     * it in a second step raced with anything else grabbing it in between; asking ktor for the
     * resolved port also waits until the server is listening. Pass [port] only to rebind a known one.
     */
    private fun startServer(
        port: Int = 0,
        schemaVersion: Int = 1,
        module: Application.() -> Unit = { mcpStreamableHttp { testServer(schemaVersion) } },
    ): TestServer {
        val engine = embeddedServer(CIO, host = "127.0.0.1", port = port, module = module).start(wait = false)
        return TestServer(engine, runBlocking { engine.engine.resolvedConnectors().first().port })
    }

    /**
     * Closes the pooled MCP clients before the server they talk to disappears, then lets in-flight
     * exchanges drain. Tearing the engine down outright (`stop(0, 0)`) cancelled live client
     * coroutines and surfaced as an intermittent JobCancellationException.
     */
    private class TestServer(private val engine: EmbeddedServer<*, *>, val port: Int) : AutoCloseable {
        override fun close() {
            McpRuntime.reset()
            stop()
        }

        /** Stops the server but leaves pooled clients alone, as a long-lived IDE process would. */
        fun stop() = engine.stop(gracePeriodMillis = 1_000, timeoutMillis = 5_000)
    }

    private fun evaluateToStdout(scriptFile: File): String {
        val stdout = ByteArrayOutputStream()
        val originalOut = System.out
        return try {
            System.setOut(PrintStream(stdout))
            evaluateScript(scriptFile).valueOrThrow()
            stdout.toString().trim()
        } finally {
            System.setOut(originalOut)
        }
    }
}
