/*
 * Copyright 2000-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.script.examples.jvm.simple

import kotlin.script.experimental.annotations.KotlinScript

// The KotlinScript annotation marks a class that can serve as a reference to the script definition for
// `createJvmCompilationConfigurationFromTemplate` call as well as for the discovery mechanism
// The marked class also become the base class for defined script type (unless redefined in the configuration)
@KotlinScript(
    // file name extension by which this script type is recognized by mechanisms built into scripting compiler plugin
    // and IDE support, it is recommendend to use double extension with the last one being "kts", so some non-specific
    // scripting support could be used, e.g. in IDE, if the specific support is not installed.
    fileExtension = "simplescript.kts"
)
// the class is used as the script base class, therefore it should be open or abstract
abstract class SimpleScript
