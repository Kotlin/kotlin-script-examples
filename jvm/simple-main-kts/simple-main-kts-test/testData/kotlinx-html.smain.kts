#!/usr/bin/env kotlinc -cp dist/kotlinc/lib/kotlin-main-kts.jar -script

@file:Repository("https://jcenter.bintray.com")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-html-jvm:0.8.0")

import kotlinx.html.*; import kotlinx.html.stream.*; import kotlinx.html.attributes.*

print(createHTML().html {
    body {
        h1 { +"Hello, World!" }
    }
})

