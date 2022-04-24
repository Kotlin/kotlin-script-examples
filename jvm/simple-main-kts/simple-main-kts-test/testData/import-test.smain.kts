
@file:Import("import-common.smain.kts")
@file:Import("import-middle.smain.kts")

sharedVar = sharedVar + 1

println("sharedVar == $sharedVar")

fun someFun() {
  // Using `sharedVar` inside a function breaks compiler (v1.6.20, v1.6.21):
  // File being compiled: testData/import-test.smain.kts
  // The root cause java.lang.RuntimeException was thrown at:
  // org.jetbrains.kotlin.backend.jvm.codegen.FunctionCodegen.generate(FunctionCodegen.kt:50):
  // org.jetbrains.kotlin.backend.common.BackendException: Backend Internal error: Exception during
  // IR lowering
  //
  // It worked fine in v1.6.10.
  //
  // To reproduce:
  // ./gradlew :jvm:simple-main-kts:simple-main-kts-test:clean :jvm:simple-main-kts:simple-main-kts-test:test
  println(sharedVar)
}
