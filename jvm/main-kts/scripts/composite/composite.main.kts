#!/usr/bin/env kotlin

/**
 * A script demonstrating the composition functionality of .main.kts scripts using imports.
 *
 * Key features:
 * - Supports both relative and absolute paths for imports
 * - Multiple imports using repeated annotations
 * - All public top-level declarations (variables, functions, classes) from imported scripts
 *   become available in the importing script
 * - Each imported script is evaluated when imported, not just its declarations are included
 * - Imported scripts are evaluated in the order they appear
 *
 * Note: Import resolution is performed relative to the importing script's location
 * when using relative paths.
 */


@file:Import("kts/simple.kts")
@file:Import("imported.main.kts")

sharedMainKtsVar++
println("simple.kts members: $ktsScriptClassMembers")
