#!/usr/bin/env kotlin

@file:DependsOn("eu.jrie.jetbrains:kotlin-shell-core:0.2.1")
@file:CompilerOptions("-Xopt-in=kotlin.RequiresOptIn")
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@file:Suppress("OPT_IN_ARGUMENT_IS_NOT_MARKER")


import eu.jrie.jetbrains.kotlinshell.shell.shell

shell {
    if (args.isEmpty()) {
        "echo this is 'ls -l' command for current directory: ${env("PWD")}"()
        "ls -l"()
    } else {
        var lines = 0
        var words = 0
        var chars = 0

        var wasSpace = true

        pipeline {
            "cat ${args[0]}".process() pipe
                    streamLambda { strm, _, _ ->
                        while (true) {
                            val byte = strm.read()
                            if (byte < 0) break
                            val ch = byte.toChar()
                            chars++
                            if (ch == '\n') lines++
                            val isSpace = ch == '\n' || ch == '\t' || ch == ' '
                            if (!wasSpace && isSpace) {
                                wasSpace = true
                            } else if (wasSpace && !isSpace) {
                                words++
                                wasSpace = false
                            }
                        }
                    }
        }

        println("My wc:")
        println("       $lines      $words     $chars ${args[0]}")
        println("System wc:")
        "wc ${args[0]}"()
    }
}
