
@file:Import("import-common.smain.kts")
@file:Import("import-middle.smain.kts")

sharedVar = sharedVar + 1

println("sharedVar == $sharedVar")
