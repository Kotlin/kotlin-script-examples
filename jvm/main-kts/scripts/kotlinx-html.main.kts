#!/usr/bin/env kotlin

/**
 * A demonstration script showing HTML generation using kotlinx.html DSL.
 * This script creates and prints a simple HTML document with a "Hello, World!" heading.
 *
 * Usage:
 * - Run the script to generate HTML output
 * - No command line arguments needed
 *
 * Dependencies:
 * - kotlinx-html-jvm:0.8.0
 */

@file:DependsOn("org.jetbrains.kotlinx:kotlinx-html-jvm:0.8.0")

import kotlinx.html.*
import kotlinx.html.stream.createHTML

val addressee = args.firstOrNull() ?: "World"

print(createHTML().html {
    body {
        h1 { +"Hello, $addressee!" }
    }
})
