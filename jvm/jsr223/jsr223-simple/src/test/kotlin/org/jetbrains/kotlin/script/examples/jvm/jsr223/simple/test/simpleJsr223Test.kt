/*
 * Copyright 2000-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.script.examples.jvm.jsr223.simple.test

import org.junit.Assert.*
import org.junit.Test
import javax.script.Compilable
import javax.script.Invocable
import javax.script.ScriptEngineManager
import javax.script.ScriptException

// Adapted from kotlin-scripting-jsr223 module tests in the main Kotlin repo

class SimpleJsr223Test {

    @Test
    fun testBasicEval() {
        val engine = ScriptEngineManager().getEngineByExtension("kts")!!
        val res1 = engine.eval("val x = 3")
        assertNull(res1)
        val res2 = engine.eval("x + 2")
        assertEquals(5, res2)
    }

    @Test
    fun testEvalWithBindings() {
        val engine = ScriptEngineManager().getEngineByExtension("kts")!!

        engine.put("z", 33)

        engine.eval("val x = 10 + z")

        val result = engine.eval("x + 20")
        assertEquals(63, result)

        val result2 = engine.eval("11 + boundValue", engine.createBindings().apply {
            put("boundValue", 100)
        })
        assertEquals(111, result2)
    }

    @Test
    fun testEvalWithErrorsAndExceptions() {
        val engine = ScriptEngineManager().getEngineByExtension("kts")!!

        try {
            engine.eval("java.lang.fish")
            fail("Script error expected")
        } catch (e: ScriptException) {}

        val res1 = engine.eval("val x = 3")
        assertNull(res1)

        try {
            engine.eval("throw Exception(\"!!\")")
            fail("Expecting exception to propagate")
        } catch (e: ScriptException) {
            assertEquals("!!", e.cause?.message)
        }

        try {
            engine.eval("y")
            fail("Script error expected")
        } catch (e: ScriptException) {}

        val res3 = engine.eval("x + 2")
        assertEquals(5, res3)
    }

    @Test
    fun testInvocable() {
        val engine = ScriptEngineManager().getEngineByExtension("kts")!!
        val res1 = engine.eval("""
            fun fn(x: Int) = x + 2
            val obj = object {
                fun fn1(x: Int) = x + 3
            }
            obj""".trimIndent())
        assertNotNull(res1)

        val invocator = engine as? Invocable
        assertNotNull(invocator)

        try {
            invocator!!.invokeFunction("fn1", 3)
            fail("NoSuchMethodException expected")
        } catch (e: NoSuchMethodException) {}

        val res2 = invocator!!.invokeFunction("fn", 3)
        assertEquals(5, res2)

        val res3 = invocator.invokeMethod(res1, "fn1", 3)
        assertEquals(6, res3)
    }

    @Test
    fun testCompilable() {
        val engine = ScriptEngineManager().getEngineByExtension("kts") as Compilable
        val comp1 = engine.compile("val x = 3")
        val comp2 = engine.compile("x + 2")
        val res1 = comp1.eval()
        assertNull(res1)
        val res2 = comp2.eval()
        assertEquals(5, res2)
    }

    @Test
    fun testResolveSymbolsFromContext() {
        val scriptEngine = ScriptEngineManager().getEngineByExtension("kts")!!
        val result = scriptEngine.eval("${this::class.java.name}.shouldBeVisibleFromRepl * 6")
        assertEquals(42, result)
    }

    companion object {
        @Suppress("unused") // accessed from the tests below
        @JvmStatic
        val shouldBeVisibleFromRepl = 7
    }
}