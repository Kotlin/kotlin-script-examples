#!/usr/bin/env kotlin

@file:Repository("https://dl.bintray.com/jakubriegel/kotlin-shell")
@file:DependsOn("eu.jrie.jetbrains:kotlin-shell-core:0.2.1")
@file:DependsOn("org.slf4j:slf4j-simple:1.7.28")
@file:CompilerOptions("-Xopt-in=kotlin.RequiresOptIn")
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

import eu.jrie.jetbrains.kotlinshell.shell.*

shell {
    if (args.isEmpty()) {
        "ls -l"()
    } else {
        var lines = 0
        var words = 0
        var chars = 0

        var wasSpace = false

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
        println("$lines $words $chars")
        println("System wc:")
        "wc ${args[0]}"()
    }
}
