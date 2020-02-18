
# Kotlin Scripting Examples: Simplified main-kts

This is a simplified version of the `main-kts` script support, distributed with the Kotlin compiler.

*Simplifications mostly related to the removal of the JSR-223 support, since it uses non-public API. In the future, 
when a public API will become available, the support could be added back. Also the packaging into a singe jar is not
implemented.*

## Description

The purpose of the `simple-main-kts` (as well as the original `main-kts`) is to allow writing simple but extendable 
utility scripts for the usage in the command line, replacing the simple Kotlin programs with `main` function (hence
the `main` in its name).

The script definition implementation demonstrates many aspects of the script definition functionality and 
scripting API usage, in particular:
- refinement of the compilation configuration with the parameters passed to the annotations
- dynamic script dependencies resolved via ivy library
- import scripts with the possibility of sharing instances (diamond-shaped import)
- compilation cache, where cached compiled scripts are saved as executable jars
- discovery file configuration, that could be used with IntelliJ IDEA and command-line compiler

and others.

The original implementation of the `kotlin-main-kts` contains all the dependencies in the single jar, that simplify 
the usage in the command line. This simplified version does not implement it, therefore all dependencies should be 
specified explicitly in the command line, to use it with the `kotlinc`.  

