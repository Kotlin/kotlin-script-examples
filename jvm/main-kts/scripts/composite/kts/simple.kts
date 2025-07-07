import kotlin.reflect.full.declaredMembers

// Kotlin scripts (.kts files) automatically include these dependencies:
// 1. kotlin-script-runtime.jar - provides script-specific functionality
val scriptArgs = args

// 2. kotlin-stdlib.jar - provides the Kotlin standard library
println("Hello from simple.kts! Args=$scriptArgs")

// 3. kotlin-reflect.jar - provides reflection capabilities
val ktsScriptClassMembers = this::class.declaredMembers
