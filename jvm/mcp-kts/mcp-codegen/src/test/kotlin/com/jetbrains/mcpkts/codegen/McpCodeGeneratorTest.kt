package com.jetbrains.mcpkts.codegen

import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpCodeGeneratorTest {
    @Test
    fun `generates typed parameters and raw result fallback`() {
        val bindings = generate(
            Tool(
                name = "greet",
                inputSchema = objectSchema(required = listOf("name")) {
                    property("name", "string")
                },
            ),
        )

        assertContains(bindings, "fun greet(name: String): McpResult")
        assertContains(bindings, "put(\"name\", JsonPrimitive(name))")
    }

    @Test
    fun `generates nested typed output models`() {
        val bindings = generate(
            Tool(
                name = "describe_profile",
                inputSchema = objectSchema(required = listOf("user_id")) {
                    property("user_id", "string")
                    property("verbose", "boolean")
                },
                outputSchema = objectSchema(required = listOf("user", "labels")) {
                    putJsonObject("user") {
                        put("type", "object")
                        putJsonObject("properties") {
                            property("name", "string")
                            property("nickname", "string")
                            enumProperty("role", "admin", "guest")
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
                },
            ),
        )

        assertContains(bindings, "fun describeProfile(userId: String, verbose: Boolean? = null): DescribeProfileResult")
        assertContains(bindings, "data class DescribeProfileResult(")
        assertContains(bindings, "val nickname: String? = null")
        assertContains(bindings, "enum class DescribeProfileResultUserRole")
        assertContains(bindings, "val labels: List<DescribeProfileResultLabelsItem>")
        assertContains(bindings, "decodeDescribeProfileResult(")
        assertFalse(bindings.contains("fun encodeDescribeProfileResult"), "output types are only decoded")
    }

    @Test
    fun `generates bindings for the search_issues example schema`() {
        val bindings = generate(
            Tool(
                name = "search_issues",
                description = "Search issues in a project",
                inputSchema = objectSchema(required = listOf("project")) {
                    property("project", "string")
                    property("query", "string")
                    property("limit", "integer")
                    property("resolved", "boolean")
                },
                outputSchema = objectSchema {
                    property("id", "string")
                    property("summary", "string")
                    enumProperty("priority", "Critical", "Major", "Normal", "Minor")
                },
            ),
        )

        assertContains(
            bindings,
            "fun searchIssues(project: String, query: String? = null, limit: Int? = null, resolved: Boolean? = null): SearchIssuesResult",
        )
        assertContains(bindings, "put(\"project\", JsonPrimitive(project))")
        assertContains(bindings, "resolved?.let { put(\"resolved\", JsonPrimitive(it)) }")

        assertContains(bindings, "data class SearchIssuesResult(")
        assertContains(bindings, "val id: String? = null")
        assertContains(bindings, "val priority: SearchIssuesResultPriority? = null")

        assertContains(bindings, "enum class SearchIssuesResultPriority(val wireValue: String)")
        assertContains(bindings, "CRITICAL(\"Critical\")")
        assertContains(bindings, "MINOR(\"Minor\")")

        assertBalancedDelimiters(bindings)
    }

    @Test
    fun `disambiguates enum entries that collapse to the same identifier`() {
        val bindings = generate(
            Tool(
                name = "set_mode",
                inputSchema = objectSchema(required = listOf("mode")) {
                    enumProperty("mode", "read", "READ", "write")
                },
            ),
        )

        assertContains(bindings, "READ(\"read\")")
        assertContains(bindings, "READ2(\"READ\")")
        assertContains(bindings, "WRITE(\"write\")")
        assertFalse(
            Regex("""\bREAD\("READ"\)""").containsMatchIn(bindings),
            "expected the second wire value to be renamed, not emitted as a duplicate READ entry",
        )
    }

    @Test
    fun `disambiguates functions when distinct tool names collapse to the same identifier`() {
        val bindings = generate(
            Tool(name = "get_user", inputSchema = ToolSchema()),
            Tool(name = "get-user", inputSchema = ToolSchema()),
        )

        assertContains(bindings, "fun getUser(")
        assertContains(bindings, "fun getUser2(")
    }

    @Test
    fun `resolves nullable union types to their concrete non-null type`() {
        // Servers commonly express an optional field as ["integer", "null"] / ["boolean", "null"].
        val bindings = generate(
            Tool(
                name = "run_command",
                inputSchema = ToolSchema(),
                outputSchema = objectSchema {
                    putJsonObject("exitCode") {
                        putJsonArray("type") {
                            add(JsonPrimitive("integer"))
                            add(JsonPrimitive("null"))
                        }
                    }
                    putJsonObject("timedOut") {
                        putJsonArray("type") {
                            add(JsonPrimitive("boolean"))
                            add(JsonPrimitive("null"))
                        }
                    }
                },
            ),
        )

        assertContains(bindings, "val exitCode: Int? = null")
        assertContains(bindings, "val timedOut: Boolean? = null")
        assertContains(bindings, "exitCode = json[\"exitCode\"]?.let { McpJsonSupport.asInt(it, path + \".exitCode\") }")
        assertFalse(bindings.contains("val exitCode: JsonElement?"))
    }

    @Test
    fun `omits the redundant let for untyped optional properties`() {
        // A property with no recognizable type stays a raw JsonElement; the decoder must not
        // emit a no-op `?.let { it }` for it.
        val bindings = generate(
            Tool(
                name = "inspect",
                inputSchema = ToolSchema(),
                outputSchema = objectSchema {
                    putJsonObject("payload") {
                        // no "type", no "properties" -> untyped JsonElement
                    }
                },
            ),
        )

        assertContains(bindings, "val payload: JsonElement? = null")
        assertContains(bindings, "payload = json[\"payload\"]")
        assertFalse(
            bindings.contains("?.let { it }"),
            "generator should not emit a no-op ?.let { it }",
        )
    }

    @Test
    fun `tools without input properties accept a raw json object argument`() {
        val bindings = generate(
            Tool(name = "status", inputSchema = ToolSchema()),
        )

        assertContains(bindings, "fun status(args: JsonObject = buildJsonObject { }): McpResult")
        assertContains(bindings, "args = args,")
    }

    @Test
    fun `generates typed list parameters`() {
        val bindings = generate(
            Tool(
                name = "bulk_close",
                inputSchema = objectSchema(required = listOf("ids")) {
                    putJsonObject("ids") {
                        put("type", "array")
                        putJsonObject("items") {
                            put("type", "string")
                        }
                    }
                },
            ),
        )

        assertContains(bindings, "fun bulkClose(ids: List<String>): McpResult")
        assertContains(bindings, "put(\"ids\", buildJsonArray { ids.forEach { item -> add(JsonPrimitive(item)) } })")
    }

    @Test
    fun `leaves an omitted optional object argument out of the request`() {
        val bindings = generate(
            Tool(
                name = "search",
                inputSchema = objectSchema {
                    property("filter", "object")
                },
            ),
        )

        assertContains(bindings, "fun search(filter: JsonObject? = null): McpResult")
        assertContains(bindings, "filter?.let { put(\"filter\", it) }")
    }

    @Test
    fun `keeps the wire type of non-string enums`() {
        val bindings = generate(
            Tool(
                name = "set_level",
                inputSchema = objectSchema(required = listOf("level")) {
                    putJsonObject("level") {
                        put("type", "integer")
                        putJsonArray("enum") {
                            add(JsonPrimitive(1))
                            add(JsonPrimitive(2))
                        }
                    }
                },
            ),
        )

        assertContains(bindings, "fun setLevel(level: Int): McpResult")
        assertContains(bindings, "put(\"level\", JsonPrimitive(level))")
    }

    @Test
    fun `escapes control characters in string literals`() {
        val bindings = generate(
            Tool(
                name = "pick",
                inputSchema = objectSchema(required = listOf("mode")) {
                    enumProperty("mode", "line\nbreak", "tab\there")
                },
            ),
        )

        assertContains(bindings, "(\"line\\nbreak\")")
        assertContains(bindings, "(\"tab\\there\")")
        assertTrue(bindings.lines().none { it.endsWith("\"line") }, "a raw newline split a string literal")
    }

    @Test
    fun `escapes kotlin keywords used as parameter names`() {
        val bindings = generate(
            Tool(
                name = "reserve",
                inputSchema = objectSchema(required = listOf("class", "typeof")) {
                    property("class", "string")
                    property("typeof", "string")
                },
            ),
        )

        assertContains(bindings, "fun reserve(`class`: String, `typeof`: String): McpResult")
        assertContains(bindings, "put(\"class\", JsonPrimitive(`class`))")
    }

    @Test
    fun `hoists server, url, headers and tool names into constants`() {
        val bindings = McpCodeGenerator.generate(
            serverName = "demo",
            serverUrl = "http://127.0.0.1:3000/mcp",
            serverHeaders = mapOf("X-Trace" to "1", "Authorization" to "Bearer token"),
            tools = listOf(
                Tool(name = "ping", inputSchema = ToolSchema()),
                Tool(name = "xdebug_get_frame_values", inputSchema = ToolSchema()),
            ),
        )

        assertContains(bindings, "const val SERVER_NAME = \"demo\"")
        assertContains(bindings, "const val SERVER_URL = \"http://127.0.0.1:3000/mcp\"")
        assertContains(bindings, "const val HEADER_AUTHORIZATION = \"Authorization\"")
        assertContains(bindings, "const val HEADER_AUTHORIZATION_VALUE = \"Bearer token\"")
        assertContains(bindings, "const val HEADER_X_TRACE = \"X-Trace\"")
        assertContains(bindings, "HEADER_AUTHORIZATION to HEADER_AUTHORIZATION_VALUE,")
        assertContains(bindings, "const val TOOL_PING = \"ping\"")
        assertContains(
            bindings,
            "const val TOOL_XDEBUG_GET_FRAME_VALUES = \"xdebug_get_frame_values\"",
        )

        assertContains(bindings, "serverName = SERVER_NAME,")
        assertContains(bindings, "url = SERVER_URL,")
        assertContains(bindings, "headers = HEADERS,")
        assertContains(bindings, "tool = TOOL_XDEBUG_GET_FRAME_VALUES,")
        assertFalse(
            bindings.contains("tool = \"xdebug_get_frame_values\""),
            "the wire string should only appear in the constant",
        )
    }

    @Test
    fun `keeps tool constants unique when wire names collapse`() {
        val bindings = generate(
            Tool(name = "get_user", inputSchema = ToolSchema()),
            Tool(name = "get-user", inputSchema = ToolSchema()),
        )

        assertContains(bindings, "const val TOOL_GET_USER = \"get_user\"")
        assertContains(bindings, "const val TOOL_GET_USER2 = \"get-user\"")
    }

    @Test
    fun `emits an empty header map when the server has no headers`() {
        val bindings = generate(Tool(name = "ping", inputSchema = ToolSchema()))

        assertContains(bindings, "val HEADERS: Map<String, String> = emptyMap()")
        assertContains(bindings, "headers = HEADERS,")
    }

    @Test
    fun `suppresses inspections in the generated file`() {
        val bindings = generate(Tool(name = "ping", inputSchema = ToolSchema()))
        val header = bindings.lineSequence().first()

        assertContains(header, "@file:Suppress(")
        listOf("unused", "SpellCheckingInspection", "KDocUnresolvedReference", "FunctionName")
            .forEach { inspection -> assertContains(header, "\"$inspection\"") }
    }

    @Test
    fun `documents tools and parameters from the schema`() {
        val bindings = McpCodeGenerator.generate(
            serverName = "demo",
            serverUrl = "http://127.0.0.1:3000/mcp",
            tools = listOf(
                Tool(
                    name = "search_text",
                    description = "Searches for a text substring.",
                    inputSchema = ToolSchema(
                        properties = buildJsonObject {
                            putJsonObject("q") {
                                put("type", "string")
                                put("description", "Text to search for")
                            }
                        },
                        required = listOf("q"),
                    ),
                ),
            ),
        )

        assertContains(bindings, "* Searches for a text substring.")
        assertContains(bindings, "* @param q Text to search for")
    }

    @Test
    fun `defangs comment markers in descriptions`() {
        // A glob such as "src/**" contains an opening comment marker, and Kotlin block comments nest,
        // so leaving it intact swallows the rest of the generated file.
        val bindings = McpCodeGenerator.generate(
            serverName = "demo",
            serverUrl = "http://127.0.0.1:3000/mcp",
            tools = listOf(
                Tool(
                    name = "find",
                    description = """Globs like "src/**" and terminators like */ must not break the file.""",
                    inputSchema = ToolSchema(
                        properties = buildJsonObject {
                            putJsonObject("glob") {
                                put("type", "string")
                                put("description", "e.g. **/*.kt or /* weird */")
                            }
                        },
                        required = listOf("glob"),
                    ),
                ),
            ),
        )

        val docText = bindings.lines()
            .map { it.trim() }
            .filter { it.startsWith("*") && it != "*/" }
            .joinToString("\n")
        assertFalse(docText.contains("/*"), "an opening comment marker survived: $docText")
        assertFalse(docText.contains("*/"), "a closing comment marker survived: $docText")
        assertEquals(
            Regex("/\\*").findAll(bindings).count(),
            Regex("\\*/").findAll(bindings).count(),
            "unbalanced comment markers",
        )
        assertBalancedDelimiters(bindings)
    }

    @Test
    fun `derives binding names from the server name`() {
        assertEquals("DemoBindings", McpCodeGenerator.bindingsClassName("demo"))
        assertEquals("DemoBindings.mcp.kts", McpCodeGenerator.bindingsFileName("demo"))
        assertEquals("MyServerBindings", McpCodeGenerator.bindingsClassName("my-server"))

        val bindings = generate(Tool(name = "ping", inputSchema = ToolSchema()))
        assertContains(bindings, "class DemoBindings {")
    }

    @Test
    fun `emits an empty binding class when there are no tools`() {
        val bindings = generate()
        assertContains(bindings, "class DemoBindings {")
        assertBalancedDelimiters(bindings)
    }

    private fun generate(vararg tools: Tool): String =
        McpCodeGenerator.generate(
            serverName = "demo",
            serverUrl = "http://127.0.0.1:3000/mcp",
            tools = tools.toList(),
        )

    private fun objectSchema(
        required: List<String> = emptyList(),
        properties: JsonObjectBuilder.() -> Unit,
    ): ToolSchema = ToolSchema(
        properties = buildJsonObject(properties),
        required = required,
    )

    private fun JsonObjectBuilder.property(name: String, type: String) {
        putJsonObject(name) {
            put("type", type)
        }
    }

    private fun JsonObjectBuilder.enumProperty(name: String, vararg values: String) {
        putJsonObject(name) {
            put("type", "string")
            putJsonArray("enum") {
                values.forEach { add(JsonPrimitive(it)) }
            }
        }
    }

    /**
     * Naive structural sanity check: for the controlled schemas in these tests no string
     * literal contains braces or parentheses, so a raw count is enough to catch a generator
     * that emits unbalanced delimiters (which would never compile).
     */
    private fun assertBalancedDelimiters(source: String) {
        assertEquals(
            source.count { it == '{' },
            source.count { it == '}' },
            "unbalanced curly braces in generated source",
        )
        assertEquals(
            source.count { it == '(' },
            source.count { it == ')' },
            "unbalanced parentheses in generated source",
        )
    }
}
