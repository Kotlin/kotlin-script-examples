package com.jetbrains.mcpkts.intellij

import com.jetbrains.mcpkts.McpScript
import com.jetbrains.mcpkts.locationOf
import java.nio.file.Path
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider

class McpScriptDefinitionsProvider : ScriptDefinitionsProvider {
    override val id: String = "mcp-kts"

    override fun getDefinitionClasses(): Iterable<String> = listOf(McpScript::class.java.name)

    override fun getTemplateClasspath(): Iterable<Path> = listOfNotNull(locationOf(McpScript::class.java)?.toPath())

    override fun useDiscovery(): Boolean = false
}
