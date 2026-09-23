package com.jetbrains.mcpkts.host

import com.jetbrains.mcpkts.McpScript
import java.io.File
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.host.FileScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate
import kotlin.script.experimental.jvmhost.createJvmEvaluationConfigurationFromTemplate

fun evaluateScript(scriptFile: File): ResultWithDiagnostics<EvaluationResult> =
    BasicJvmScriptingHost().eval(
        FileScriptSource(scriptFile),
        createJvmCompilationConfigurationFromTemplate<McpScript>(),
        createJvmEvaluationConfigurationFromTemplate<McpScript> { constructorArgs(emptyArray<String>()) },
    )
