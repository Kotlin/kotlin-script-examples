
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { url = uri("https://dl.bintray.com/kotlin/kotlin-eap") }
    }
}

include("jvm:basic:jvm-simple-script:script")
include("jvm:basic:jvm-simple-script:host")

include("jvm:basic:jvm-maven-deps:script")
include("jvm:basic:jvm-maven-deps:host")
