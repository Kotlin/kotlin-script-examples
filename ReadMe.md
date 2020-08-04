
# Kotlin scripting examples

This repository contains example projects and individual scripts, as well as links to the external examples 
demonstrating Kotlin Scripting functionality and API usage.

If you know good examples of the Kotlin scripting API usage, not mentioned here, please submit an issue or a pull 
request with the link and short description.  

## Examples in this repository

The complete project could be compiled with Gradle, as well as imported e.g. in IntelliJ IDEA.

The individual example projects are mostly independent and could be copied and reused independently, but build files
should be adopted accordingly, mostly to supply required Kotlin version properties and setup inter-project dependencies
when needed

### Script definitions with scripting hosts

- [Simple script definition](jvm/basic/jvm-simple-script/SimpleScript.md)
- [Script with dynamic dependencies from Maven](jvm/basic/jvm-maven-deps/MavenDeps.md)
- [Scripting Host with Kotlin Compiler Embeddable](jvm/basic/jvm-embeddable-host/EmbeddableCompiler.md)
- [Simplified main-kts-like script implementation](jvm/simple-main-kts/SimpleMainKts.md)
- [`main-kts` scripts examples](jvm/main-kts/MainKts.md)
- [using scripting via JSR 223 interface](jvm/jsr223/jsr223.md)

## External examples

// TODO

## License
The Apache 2 license (given in full in [LICENSE.txt](license/LICENSE.txt) applies to all code in this repository which 
is copyright by JetBrains.
