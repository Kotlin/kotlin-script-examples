#!/usr/bin/env kotlin

// @file:Repository("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven") // not needed
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-html-jvm:0.7.5")

import kotlinx.html.*; import kotlinx.html.stream.*; import kotlinx.html.attributes.*

val addressee = args.firstOrNull() ?: "World"

print(createHTML().html {
    body {
        h1 { +"Hello, $addressee!" }
    }
})

