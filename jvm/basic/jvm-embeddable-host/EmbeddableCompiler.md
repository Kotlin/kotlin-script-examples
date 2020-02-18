
# Kotlin Scripting Examples: Scripting Host with Kotlin Compiler Embeddable 

This example demonstrates the usage of the same definition as in the  
[Simple script definition example](../jvm-simple-script/SimpleScript.md) but uses the embeddable version of the 
Kotlin compiler. The necessity of using embeddable compiler may arise when the dependencies that are packed into
the `kotlin-compiler` jar, could conflict with the dependencies used by the host.
