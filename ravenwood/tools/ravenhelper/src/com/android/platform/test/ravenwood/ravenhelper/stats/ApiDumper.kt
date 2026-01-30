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
package com.android.platform.test.ravenwood.ravenhelper.stats

import com.android.hoststubgen.HostStubGenErrors
import com.android.hoststubgen.asm.CLASS_INITIALIZER_NAME
import com.android.hoststubgen.asm.CTOR_NAME
import com.android.hoststubgen.asm.ClassNodes
import com.android.hoststubgen.asm.getClassNameFromFullClassName
import com.android.hoststubgen.asm.getPackageNameFromFullClassName
import com.android.hoststubgen.asm.toHumanReadableClassName
import com.android.hoststubgen.csvEscape
import com.android.hoststubgen.filters.FilterPolicy
import com.android.hoststubgen.filters.FilterPolicyWithReason
import com.android.hoststubgen.filters.OutputFilter
import com.android.hoststubgen.filters.StatsLabel
import com.android.hoststubgen.log
import com.android.hoststubgen.visitors.checkSubstitutionMethodCompatibility
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import java.io.PrintWriter

/**
 * Dump all the API methods in [classes], with inherited methods, with their policies.
 */
class ApiDumper(
    val pw: PrintWriter,
    val classes: ClassNodes,
    val frameworkClasses: ClassNodes?,
    val filter: OutputFilter,
    val errors: HostStubGenErrors,
) {
    private data class MethodKey(
        val name: String,
        val descriptor: String,
    )

    private val shownMethods = mutableSetOf<MethodKey>()

    /**
     * Do the dump.
     */
    fun dump() {
        pw.printf("PackageName,ClassName,Inherited,DeclareClass,MethodName,MethodDesc" +
                ",Supported,Policy,Reason,Boring,StatsLabelValue\n")

        classes.forEach { classNode ->
            shownMethods.clear()
            dump(classNode, classNode)
        }
    }

    private fun dumpMethod(
        classPackage: String,
        className: String,
        isSuperClass: Boolean,
        methodClassName: String,
        methodName: String,
        methodDesc: String,
        computedMethodLabel: StatsLabel,
        methodPolicy: FilterPolicyWithReason,
    ) {
        pw.printf(
            "%s,%s,%d,%s,%s,%s,%d,%s,%s,%d,%d\n",
            csvEscape(classPackage),
            csvEscape(className),
            if (isSuperClass) { 1 } else { 0 },
            csvEscape(methodClassName),
            csvEscape(methodName),
            csvEscape(methodName + methodDesc),
            if (computedMethodLabel.isSupported) { 1 } else { 0 },
            methodPolicy.policy,
            csvEscape(methodPolicy.reason),
            if (computedMethodLabel == StatsLabel.SupportedButBoring) { 1 } else { 0 },
            computedMethodLabel.statValue,
        )
    }

    private fun shownAlready(methodName: String, methodDesc: String): Boolean {
        val methodKey = MethodKey(methodName, methodDesc)

        if (shownMethods.contains(methodKey)) {
            return true
        }
        shownMethods.add(methodKey)
        return false
    }

    private fun getClassLabel(cn: ClassNode, classPolicy: FilterPolicyWithReason): StatsLabel {
        if (!classPolicy.statsLabel.isSupported) {
            return classPolicy.statsLabel
        }
        if (cn.name.endsWith("Proto")
            || cn.name.endsWith("ProtoEnums")
            || cn.name.endsWith("LogTags")
            || cn.name.endsWith("StatsLog")) {
            return StatsLabel.SupportedButBoring
        }

        return classPolicy.statsLabel
    }

    private fun resolveMethodLabel(
        methodName: String,
        methodAccess: Int,
        methodPolicy: FilterPolicyWithReason,
        classLabel: StatsLabel,
    ): StatsLabel {
        // Class label will override the method label
        if (!classLabel.isSupported) {
            return classLabel
        }
        // If method isn't supported, just use it as-is.
        if (!methodPolicy.statsLabel.isSupported) {
            return methodPolicy.statsLabel
        }

        // Use heuristics to override the label.
        if ((methodAccess and Opcodes.ACC_PUBLIC) == 0) {
            // If it's not public, mark it as "boring".
            return StatsLabel.SupportedButBoring
        }

        // If the policy is "not supported" but the statsLabel explicitly says "supported"
        // then that's "throw-but-supported". In that case, let's not mark it as "boring"
        // even if it's abstract, and show it. (if the statsLabel doesn't say "supported",
        // we'd be returning earlier already.)
        if (!methodPolicy.policy.isSupported) {
            return StatsLabel.Supported
        }

        if ((methodAccess and Opcodes.ACC_ABSTRACT) != 0) {
            // An abstract method is normally boring -- but if it's a throw-but-interesting,
            // that's actually not boring. So this check has to be after the other checks.
            return StatsLabel.SupportedButBoring
        }

        // Mark ctors and clinits as boring.
        when (methodName) {
            CTOR_NAME, CLASS_INITIALIZER_NAME -> return StatsLabel.SupportedButBoring
        }

        return methodPolicy.statsLabel
    }

    private fun dump(
        dumpClass: ClassNode,
        methodClass: ClassNode,
        ) {
        val classPolicy = filter.getPolicyForClass(dumpClass.name)
        if (classPolicy.statsLabel == StatsLabel.Ignored) {
            return
        }
        // See below comment on REMOVE'd method -- ideally we want to show it, but for now,
        // we don't.
        if (classPolicy.policy == FilterPolicy.Remove) {
            return
        }
        log.d("Class ${dumpClass.name} -- policy $classPolicy")
        val classLabel = getClassLabel(dumpClass, classPolicy)

        val humanReadableClassName = dumpClass.name.toHumanReadableClassName()
        val pkg = getPackageNameFromFullClassName(humanReadableClassName)
        val cls = getClassNameFromFullClassName(humanReadableClassName)

        val isSuperClass = dumpClass != methodClass

        methodClass.methods?.sortedWith(compareBy({ it.name }, { it.desc }))?.forEach { method ->

            // Don't print ctor's from super classes.
            if (isSuperClass) {
                if (CTOR_NAME == method.name || CLASS_INITIALIZER_NAME == method.name) {
                    return@forEach
                }
            }
            // If we already printed the method from a subclass, don't print it.
            if (shownAlready(method.name, method.desc)) {
                return@forEach
            }

            val methodPolicy = filter.getPolicyForMethod(methodClass.name, method.name, method.desc)

            // Let's skip "Remove" APIs. Ideally we want to print it, just to make the CSV
            // complete, we still need to hide methods substituted (== @RavenwoodReplace) methods
            // and for now we don't have an easy way to detect it.
            if (methodPolicy.policy == FilterPolicy.Remove) {
                return@forEach
            }

            val renameTo = filter.getRenameTo(methodClass.name, method.name, method.desc)

            // The method's "access" bits affect the "method label". But if we're renaming a method,
            // we need to use rename-to's access bits.
            val methodAccess = if (renameTo == null) {
                method.access
            } else {
                checkSubstitutionMethodCompatibility(
                    classes,
                    methodClass.name,
                    renameTo, // method name with the annotation.
                    method.name, // the current method (i.e. `*$ravenwood`).
                    method.desc,
                    errors
                )
            }

            val methodLabel = resolveMethodLabel(
                method.name,
                methodAccess,
                methodPolicy,
                classLabel,
            )

            if (methodLabel != StatsLabel.Ignored) {
                dumpMethod(pkg, cls, isSuperClass, methodClass.name.toHumanReadableClassName(),
                    renameTo ?: method.name, method.desc, methodLabel, methodPolicy)
            }
       }

        // Dump super class methods.
        dumpSuper(dumpClass, methodClass.superName)

        // Dump interface methods (which may have default methods).
        methodClass.interfaces?.sorted()?.forEach { interfaceName ->
            dumpSuper(dumpClass, interfaceName)
        }
    }

    /**
     * Dump a given super class / interface.
     */
    private fun dumpSuper(
        dumpClass: ClassNode,
        methodClassName: String,
    ) {
        classes.findClass(methodClassName)?.let { methodClass ->
            dump(dumpClass, methodClass)
            return
        }
        frameworkClasses?.findClass(methodClassName)?.let { methodClass ->
            dump(dumpClass, methodClass)
            return
        }
        log.w("Super class or interface $methodClassName (used by ${dumpClass.name}) not found.")
    }
}
