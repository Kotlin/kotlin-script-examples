@file:CompilerOptions("-jvm-target", "1.8", "-Xabi-stability=unstable")

interface Test {
    fun print()
    fun printSuper() = println("Hi from super")
}

class TestImpl : Test {
    override fun print() = println("Hi from sub")
}

inline fun printRandom() = println("Hi from random")

TestImpl().run {
    print()
    printSuper()
    printRandom()
}

val x = length // drop(4)
