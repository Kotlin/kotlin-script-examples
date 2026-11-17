#!/usr/bin/env kotlin

/**
 * The MCP server that [hello.mcp.kts] talks to — itself a Kotlin script, so the whole demo runs
 * without a build.
 *
 * Usage:
 *   kotlin samples/demo-server.main.kts [port]     (default 3000)
 *
 * It serves two tools over streamable HTTP: `greet`, which returns text, and `describe_profile`,
 * which returns structured content. Between them they exercise both shapes the generated bindings
 * have to cover.
 */

@file:DependsOn("io.ktor:ktor-server-cio-jvm:3.4.3")
@file:DependsOn("io.ktor:ktor-server-sse-jvm:3.4.3")
@file:DependsOn("io.modelcontextprotocol:kotlin-sdk-server-jvm:0.14.0")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.9.0")

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

fun demoServer(): Server {
    val server = Server(
        serverInfo = Implementation(
            name = "mcp-kts-demo-server",
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

val port = args.firstOrNull()?.toIntOrNull() ?: 3000
println("MCP demo server listening on http://127.0.0.1:$port/mcp")
println("Open samples/hello.mcp.kts in IntelliJ with the plugin installed.")

embeddedServer(CIO, host = "127.0.0.1", port = port) {
    mcpStreamableHttp {
        demoServer()
    }
}.start(wait = true)
