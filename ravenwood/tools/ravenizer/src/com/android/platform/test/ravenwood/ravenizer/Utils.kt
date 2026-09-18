/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.platform.test.ravenwood.ravenizer

import android.platform.test.annotations.NoRavenizer
import android.platform.test.annotations.internal.InnerRunner
import android.platform.test.ravenwood.RavenwoodAwareTestRunner
import com.android.hoststubgen.asm.ClassNodes
import com.android.hoststubgen.asm.findAnyAnnotation
import com.android.hoststubgen.asm.isAbstract
import com.android.hoststubgen.asm.isPublic
import com.android.hoststubgen.asm.startsWithAny
import com.android.hoststubgen.asm.toHumanReadableClassName
import com.android.hoststubgen.filters.isRClass
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode

data class TypeHolder(
    val clazz: Class<*>,
) {
    val type = Type.getType(clazz)
    val desc = type.descriptor
    val descAsSet = setOf<String>(desc)
    val internlName = type.internalName
    val humanReadableName = type.internalName.toHumanReadableClassName()
}

val testAnotType = TypeHolder(org.junit.Test::class.java)
val ruleAnotType = TypeHolder(org.junit.Rule::class.java)
val classRuleAnotType = TypeHolder(org.junit.ClassRule::class.java)
val runWithAnotType = TypeHolder(RunWith::class.java)
val innerRunnerAnotType = TypeHolder(InnerRunner::class.java)
val noRavenizerAnotType = TypeHolder(NoRavenizer::class.java)

val testRuleType = TypeHolder(TestRule::class.java)
val ravenwoodTestRunnerType = TypeHolder(RavenwoodAwareTestRunner::class.java)

/**
 * Returns true, if a test looks like it's a test class which needs to be processed.
 */
fun isTestLookingClass(classes: ClassNodes, className: String): Boolean {
    // Similar to  com.android.tradefed.lite.HostUtils.testLoadClass(), except it's more lenient,
    // and accept non-public and/or abstract classes.
    // HostUtils also checks "Suppress" or "SuiteClasses" but this one doesn't.
    // TODO: SuiteClasses may need to be supported.

    val cn = classes.findClass(className) ?: return false

    if (cn.findAnyAnnotation(runWithAnotType.descAsSet) != null) {
        return true
    }
    cn.methods?.forEach { method ->
        if (method.findAnyAnnotation(testAnotType.descAsSet) != null) {
            return true
        }
    }

    if (isJUnit3LookingClass(classes, className)) {
        return true
    }

    // Check the super class.
    if (cn.superName == null) {
        return false
    }
    return isTestLookingClass(classes, cn.superName)
}

fun isJUnit3LookingClass(classes: ClassNodes, className: String): Boolean {
    val cn = classes.findClass(className) ?: return false

    // Check if JUnit3 test class
    return cn.superName != null && !cn.isAbstract() &&
            isJUnit3LookingBaseClass(classes, cn.superName) && hasJUnit3TestMethod(cn)
}

/**
 * Check if a class internal name is a known legacy test base class.
 */
private fun isJUnit3LookingBaseClass(classes: ClassNodes, className: String): Boolean {
    var name = className
    while (true) {
        if (name == "junit/framework/TestCase" ||
            // Unfortunately android.test.* classes are part of the framework
            // and would not be statically included in the test jar. We have
            // to hardcode this pattern as a heuristic for legacy test detection.
            name.matches(Regex("^android/test/[a-zA-Z]*Test(Case|Suite)(2)?$"))) {
            return true
        }

        // Check the super class.
        name = classes.findClass(name)?.superName ?: return false
    }
}

/**
 * Check if a class internal name is a known legacy test base class.
 * Reference: [androidx.test.internal.util.AndroidRunnerBuilderUtil.hasJUnit3TestMethod]
 */
private fun hasJUnit3TestMethod(classNode: ClassNode): Boolean {
    return classNode.methods.any {
        val desc = Type.getMethodType(it.desc)
        it.isPublic() && it.name.startsWith("test") && desc.argumentCount == 0 &&
                desc.returnType == Type.VOID_TYPE
    }
}

fun String.isRavenwoodClass(): Boolean {
    return this.startsWithAny(
        "com/android/hoststubgen/",
        "android/platform/test/ravenwood",
        "com/android/ravenwood/",
        "com/android/platform/test/ravenwood/",
    )
}

/**
 * Classes that should never be modified.
 */
fun String.shouldBypass(): Boolean {
    if (this.isRavenwoodClass() || this.isRClass()) {
        return true
    }
    return this.startsWithAny(
        "java/", // just in case...
        "javax/",
        "junit/",
        "org/junit/",
        "org/mockito/",
        "kotlin/",
        "androidx/",
        "android/support/",
        // TODO -- anything else?
    )
}

/**
 * Inverse of [shouldBypass].
 */
fun String.shouldProcess(): Boolean = !shouldBypass()

/**
 * Files that should be removed when "--strip-mockito" is set.
 */
fun String.isMockitoFile(): Boolean {
    return this.startsWithAny(
        "org/mockito/", // Mockito
        "com/android/dx/", // DexMaker
        "mockito-extensions/", // DexMaker overrides
    ) && !this.startsWithAny(
        "org/mockito/kotlin/", // mockito-kotlin
    )
}

fun includeUnsupportedMockito(classes: ClassNodes): Boolean {
    return classes.findClass("com/android/dx/DexMaker") != null
            || classes.findClass("org/mockito/Matchers") != null
}
