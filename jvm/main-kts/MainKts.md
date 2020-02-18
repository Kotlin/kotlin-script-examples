
# Kotlin Scripting Examples: `kotlin-main-kts` usages

Scripts demonstrating the usage of the `kotlin-main-kts` script definition jar, distributed with the Kotlin compiler
and the IntelliJ plugin.

*See also [simplified implementation in this repository](../simple-main-kts/SimpleMainKts.md)*

## Description
 
The purpose of the `main-kts` is to allow writing simple but extendable  utility scripts for the usage in the command 
line, replacing the simple Kotlin programs with `main` function (hence the `main` in its name).

### Usage

For example a script (note that the file should have an extension corresponding to the script definition, in this case 
`.smain.kts`):

```kotlin
println("Hello, ${args[0]}")
```

could be executed with the command

```
kotlinc -cp <path/to/kotlin-main-kts.jar> script.main.kts
```

and starting from Kotlin version 1.3.70 it could even be used without explicit `kotlin-main-kts.jar` in the classpath,
provided that the compiler could find the required jars. In addition starting from 1.3.70 `kotlin` runner supports 
scripts the same way as `kotlinc -script` combination:

```
kotlin script.main.kts
```

or even as simple as 

```
./script.main.kts
```

provided that the shebang line is added to the script and works in the given OS shell. *(See examples below.)*

### Caching

The compiled scripts are cashed to the directory defined by an environment variable `KOTLIN_MAIN_KTS_COMPILED_SCRIPTS_CACHE_DIR` 
*(`$TEMP/main.kts.compiled.cache` by default)*, and if the script is not changed, the compiled one is executed from the cache.

### IntelliJ support

Starting from the Kotlin IntelliJ plugin version 1.3.70, the `.main.kts` scripts are supported automatically in the 
IntelliJ IDEA, provided that they are placed outside of the regular source folders. E.g. if this project is imported into 
the IntelliJ, the demo scripts in the [`scripts`](scripts) folders should be properly highlighted and support navigation,
including navigation into imported libraries. 

## Demo Scripts

- [`kotlinx-html.main.kts`](scripts/kotlinx-html.main.kts) demonstrates usage of the 
[`kotlinx-html` library](https://github.com/Kotlin/kotlinx.html) by JetBrains, to generate HTML output 
- [`kotlin-shell.main.kts`](scripts/kotlin-shell.main.kts) demonstrates usage of the 
[`kotlin-shell` library](https://github.com/jakubriegel/kotlin-shell) by Jakub Riegel, to execute OS shell commands
with easy interaction with Kotlin code
