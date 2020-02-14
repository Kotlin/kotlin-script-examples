
# Kotlin Scripting Examples: Script with Dynamic dependencies from Maven

This example demonstrates the [script definition](script/) and appropriate [scripting host ](host/) that implements 
support for dynamic dependencies from Maven repositories usage, so une can write a script:
```kotlin
@file:DependsOn("junit:junit:4.11")
org.junit.Assert.assertTrue(true)
```
meaning the specified dependency to the particular `junit` version will be downloaded (if not in the cache yet)
during the script compilation and used as a dependency for the rest of the script.
