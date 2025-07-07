#!/usr/bin/env kotlin

/**
 * A command-line utility script that provides directory listing and word counting functionality.
 *
 * Usage:
 * - Without arguments: Lists files in the current directory (equivalent to `ls -l`)
 * - With file argument: Performs word count analysis (similar to `wc` command)
 *      Shows both custom implementation and system `wc` command results
 *
 * Example usage:
 * ```
 * ./kotlin-shell.main.kts           # Lists directory contents
 * ./kotlin-shell.main.kts file.txt  # Counts lines, words, and characters in file.txt
 * ```
 *
 * Dependencies:
 * - kotlin-shell-core:0.2.1
 *
 * @param args - command line arguments. If provided, the first argument should be a file path
 */


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
