/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.google.android.lint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClassLiteralExpression

/**
 * Lint Detector that guards against Java enum reflection.
 *
 * This detects usage of `Class.getEnumConstants()` and the base `Enum.valueOf()` method, both of
 * which rely on reflection and can be unsafe when optimizing bytecode.
 *
 * It also checks for generic arguments to `EnumMap` constructors, as R8 can only safely handle the
 * case where an explicit type is provided to the constructor.
 */
@Suppress("UnstableApiUsage")
class EnumReflectionDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("getEnumConstants", "valueOf")

    override fun getApplicableConstructorTypes(): List<String> = listOf("java.util.EnumMap")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        when (method.name) {
            "getEnumConstants" -> {
                if (
                    method.parameterList.parametersCount == 0 &&
                        evaluator.isMemberInClass(method, "java.lang.Class")
                ) {
                    reportGetEnumConstants(context, node)
                }
            }
            "valueOf" -> {
                if (
                    method.parameterList.parametersCount == 2 &&
                        evaluator.isMemberInClass(method, "java.lang.Enum")
                ) {
                    reportEnumValueOf(context, node)
                }
            }
        }
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        if (constructor.containingClass?.qualifiedName != "java.util.EnumMap") return

        // We are only interested in the constructor that takes a Class object type.
        val parameters = constructor.parameterList.parameters
        if (parameters.size != 1) return
        val paramType = parameters[0].type as? PsiClassType ?: return
        if (paramType.rawType().canonicalText != "java.lang.Class") return

        // If the argument is a class literal (e.g., FooEnum.class), it's fine; R8 can handle this.
        val argument = node.valueArguments.getOrNull(0) ?: return
        if (argument is UClassLiteralExpression) return

        // Also validate that the argument is a class type expression. This should generally be the
        // case, as we're checking the *parameter* type above, but technically the AST could produce
        // this intermediate scenario (e.g., lint is running before the compiler), so be defensive.
        val argType = argument.getExpressionType() as? PsiClassType ?: return

        // If we can determine the concrete type, it should be provided as a direct fix.
        val typeParam = argType.parameters.getOrNull(0)
        val enumType =
            (typeParam as? PsiClassType)?.resolve()?.takeIf { it is PsiClass && it.isEnum }
                as? PsiClass

        val fix =
            if (enumType != null) {
                fix()
                    .name("Replace with ${enumType.name}.class")
                    .replace()
                    .range(context.getLocation(argument))
                    .with("${enumType.qualifiedName}.class")
                    .shortenNames()
                    .build()
            } else {
                null
            }

        val message =
            "Reflective usage of `EnumMap` is discouraged; " +
                if (enumType != null) {
                    "use `new EnumMap(${enumType.name}.class)` instead."
                } else {
                    "prefer `new EnumMap(MyEnum.class)` if possible."
                }

        context.report(ISSUE_ENUM_MAP_CONSTRUCTOR, node, context.getLocation(node), message, fix)
    }

    private fun reportGetEnumConstants(context: JavaContext, node: UCallExpression) {
        val receiver = node.receiver as? UClassLiteralExpression
        val enumType = receiver?.type
        val hasFix = enumType != null
        val fix =
            if (hasFix) {
                fix()
                    .name("Replace with ${enumType.presentableText}.values()")
                    .replace()
                    .range(context.getLocation(node.uastParent ?: node))
                    .with("${enumType.canonicalText}.values()")
                    .shortenNames()
                    .reformat(true)
                    .build()
            } else {
                null
            }

        val message =
            "Reflection with `Class.getEnumConstants()` is discouraged; " +
                if (enumType != null) {
                    "use `${enumType.presentableText}.values()` instead."
                } else {
                    "use `YourEnum.values()` if possible."
                }

        context.report(ISSUE_GET_ENUM_CONSTANTS, node, context.getNameLocation(node), message, fix)
    }

    private fun reportEnumValueOf(context: JavaContext, node: UCallExpression) {
        val arguments = node.valueArguments
        val classLiteral = arguments.getOrNull(0) as? UClassLiteralExpression
        val enumType = classLiteral?.type
        val hasFix = enumType != null && arguments.size == 2
        val fix =
            if (hasFix) {
                val stringArgument = arguments[1].asSourceString()
                fix()
                    .name("Replace with ${enumType.presentableText}.valueOf()")
                    .replace()
                    .range(context.getLocation(node))
                    .with("${enumType.canonicalText}.valueOf($stringArgument)")
                    .shortenNames()
                    .reformat(true)
                    .build()
            } else {
                null
            }

        val message =
            "Reflection with `Enum.valueOf()` is discouraged; " +
                if (enumType != null) {
                    "use `${enumType.presentableText}.valueOf()` instead."
                } else {
                    "use `YourEnum.valueOf()` if possible."
                }

        context.report(ISSUE_ENUM_VALUE_OF, node, context.getNameLocation(node), message, fix)
    }

    companion object {
        // Somewhat arbitrary priority, where `10` is the highest.
        private val ISSUE_PRIORITY = 6

        private val GET_ENUM_CONSTANTS_EXPLANATION =
            """
            Reflective enum usage is discouraged as it can be slow and unsafe.
            Prefer using `YourEnum.values()` when possible.
            """
                .trimIndent()

        @JvmField
        val ISSUE_GET_ENUM_CONSTANTS: Issue =
            Issue.create(
                id = "ReflectiveEnumConstants",
                briefDescription = "Reflective usage of Class.getEnumConstants()",
                explanation = GET_ENUM_CONSTANTS_EXPLANATION,
                category = Category.PERFORMANCE,
                priority = ISSUE_PRIORITY,
                severity = Severity.ERROR,
                implementation =
                    Implementation(EnumReflectionDetector::class.java, Scope.JAVA_FILE_SCOPE),
            )

        private val ENUM_VALUE_OF_EXPLANATION =
            """
            Reflective enum usage is discouraged as it can be slow and unsafe.
            Prefer using `YourEnum.valueOf()` when possible.
            """
                .trimIndent()

        @JvmField
        val ISSUE_ENUM_VALUE_OF: Issue =
            Issue.create(
                id = "ReflectiveEnumValueOf",
                briefDescription = "Reflective usage of Enum.valueOf()",
                explanation = ENUM_VALUE_OF_EXPLANATION,
                category = Category.PERFORMANCE,
                priority = ISSUE_PRIORITY,
                severity = Severity.ERROR,
                implementation =
                    Implementation(EnumReflectionDetector::class.java, Scope.JAVA_FILE_SCOPE),
            )

        private val ENUM_MAP_CONSTRUCTOR_EXPLANATION =
            """
            Using `EnumMap` with a generic `Class` arg is generally unsafe when optimizing bytecode.
            Prefer passing a class literal (e.g., `new EnumMap(MyEnum.class)`) whenever possible.
            """
                .trimIndent()

        @JvmField
        val ISSUE_ENUM_MAP_CONSTRUCTOR: Issue =
            Issue.create(
                id = "EnumMapConstructor",
                briefDescription = "EnumMap constructor with non-literal class",
                explanation = ENUM_MAP_CONSTRUCTOR_EXPLANATION,
                category = Category.PERFORMANCE,
                priority = ISSUE_PRIORITY,
                severity = Severity.ERROR,
                implementation =
                    Implementation(EnumReflectionDetector::class.java, Scope.JAVA_FILE_SCOPE),
            )
    }
}
