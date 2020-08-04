/*
 * Copyright 2000-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.script.examples.jvm.jsr223.mainKts.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import javax.script.ScriptEngineManager

// Adapted from kotlin-main-kts module tests in the main Kotlin repo

class MainKtsJsr223Test {

    @Test
    fun testBasicEval() {
        val engine = ScriptEngineManager().getEngineByExtension("main.kts")!!
        val res1 = engine.eval("val x = 3")
        assertNull(res1)
        val res2 = engine.eval("x + 2")
        assertEquals(5, res2)
    }

    @Test
    fun testWithImport() {
        val engine = ScriptEngineManager().getEngineByExtension("main.kts")!!
        val out = captureOut {
            val res1 = engine.eval("""
                @file:Import("testData/import-common.main.kts")
                @file:Import("testData/import-middle.main.kts")
                sharedVar = sharedVar + 1
                println(sharedVar)
            """.trimIndent())
            assertNull(res1)
        }.lines()
        assertEquals(listOf("Hi from common", "Hi from middle", "5"), out)
    }

    @Test
    fun testKotlinxHtmlExample() {
        val engine = ScriptEngineManager().getEngineByExtension("main.kts")!!
        val scriptFile = File("../../main-kts/scripts/kotlinx-html.main.kts")
        val out = captureOut {
            engine.eval(scriptFile.reader())
        }.lines()
        assertEquals(listOf("<html>", "  <body>", "    <h1>Hello, World!</h1>", "  </body>", "</html>"), out)
    }
}

private fun captureOut(body: () -> Unit): String {
    val outStream = ByteArrayOutputStream()
    val prevOut = System.out
    System.setOut(PrintStream(outStream))
    try {
        body()
    } finally {
        System.out.flush()
        System.setOut(prevOut)
    }
    return outStream.toString().trim()
}
