#!/usr/bin/env kotlinc -cp dist/kotlinc/lib/kotlin-main-kts.jar -script

@file:Repository("https://jcenter.bintray.com")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-html-jvm:0.6.11")

import kotlinx.html.*; import kotlinx.html.stream.*; import kotlinx.html.attributes.*

// Assigning `this` to a variable breaks compiler (v1.6.20, v1.6.21, v1.7.0, v1.7.10):
//
// File being compiled: testData/kotlinx-html.smain.kts
// The root cause java.lang.IllegalStateException was thrown at: org.jetbrains.kotlin.ir.types.IrTypeUtilsKt.isNullable(IrTypeUtils.kt:41): org.jetbrains.kotlin.backend.common.BackendException: Backend Internal error: Exception during IR lowering
//
// It worked fine in v1.6.10.
//
// Note, that if the type is specified explicitly, i.e. `val self: org.jetbrains.kotlin.script.examples.simpleMainKts.SimpleMainKtsScript = this`, then it compiles fine.
//
// To reproduce:
// ./gradlew :jvm:simple-main-kts:simple-main-kts-test:clean :jvm:simple-main-kts:simple-main-kts-test:test --tests org.jetbrains.kotlin.script.examples.simpleMainKts.test.SimpleMainKtsTest.testKotlinxHtml
val self = this

print(createHTML().html {
    body {
        h1 { +"Hello, World!" }
    }
})

