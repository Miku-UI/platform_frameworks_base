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

package com.android.protolog

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import java.util.EnumSet
import kotlin.math.min
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElementOfType

class ProtoLogFormatDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() ?: return

                var protoLogCall = getDirectProtoLogCall(context, node, method)
                if (protoLogCall == null) {
                    protoLogCall = getIndirectProtoLogCall(context, node, method)
                }

                if (protoLogCall != null) {
                    validateProtoLogCall(context, protoLogCall)
                }
            }
        }

    private data class ProtoLogCall(
        val call: UCallExpression,
        val formatStringArg: UExpression,
        val formatString: String?,
        val args: List<UExpression>
    )

    private fun getDirectProtoLogCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ): ProtoLogCall? {
        if (!isProtoLogCall(context, node, method)) {
            return null
        }

        if (node.valueArguments.size < 2) {
            return null
        }

        val formatStringArg = node.valueArguments[1]
        val formatArgs = node.valueArguments.subList(2, node.valueArguments.size)
        val formatString = formatStringArg.evaluate() as? String

        return ProtoLogCall(node, formatStringArg, formatString, formatArgs)
    }

    private fun getIndirectProtoLogCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ): ProtoLogCall? {
        val internalCall = findInnerProtoLogCall(context, method) ?: return null

        val internalArgs = internalCall.valueArguments
        if (internalArgs.size < 2) {
            return null
        }

        val internalFormatArg = internalArgs[1]
        // If the format string is not a parameter of the wrapper method (or depends on it),
        // we don't need to validate the indirect ProtoLog call site.
        if (!checkDependsOnParameter(internalFormatArg, method)) {
            return null
        }

        val mapping = context.evaluator.computeArgumentMapping(node, method)

        val formatString =
            evaluateSubstituted(internalFormatArg, node, method, mapping) as? String

        val effectiveArgs = mutableListOf<UExpression>()

        for (i in 2 until internalArgs.size) {
            val arg = internalArgs[i]
            // Check if the argument passed to the internal ProtoLog call is a parameter
            // of the wrapper method.
            val paramIndex = getParameterIndex(arg, method)
            if (paramIndex != null) {
                val param: PsiParameter = method.parameterList.parameters[paramIndex]
                val mappedArgs = mapping.filterValues { it == param }.keys

                if (param.isVarArgs) {
                    // If the wrapper parameter is a varargs, we need to find all the arguments
                    // passed to the wrapper that map to this parameter.
                    for (callArg in node.valueArguments) {
                        if (mapping[callArg] == param) {
                            effectiveArgs.add(callArg)
                        }
                    }
                } else {
                    // If it's a regular parameter, we take the argument passed to the wrapper.
                    val mappedArg = mappedArgs.firstOrNull()
                    if (mappedArg != null) {
                        effectiveArgs.add(mappedArg)
                    } else {
                        // If the argument is not mapped, it might be using a default value.
                        // We use the internal argument for type checking.
                        effectiveArgs.add(arg)
                    }
                }
            } else {
                // If the argument is not a parameter of the wrapper method we use the argument as is.
                effectiveArgs.add(arg)
            }
        }

        return ProtoLogCall(node, internalFormatArg, formatString, effectiveArgs)
    }

    private fun validateProtoLogCall(
        context: JavaContext,
        protoLogCall: ProtoLogCall
    ) {
        if (protoLogCall.args.size > 32) {
            context.report(
                ISSUE_TOO_MANY_ARGS,
                protoLogCall.call,
                context.getLocation(protoLogCall.call),
                "ProtoLog method has too many arguments ${protoLogCall.args.size} (limit is 32)",
            )
        }

        if (protoLogCall.formatString == null) {
            val errorLocation = if (protoLogCall.formatStringArg.sourcePsi != null) {
                 context.getLocation(protoLogCall.formatStringArg)
            } else {
                 context.getLocation(protoLogCall.call)
            }

            context.report(
                 ISSUE_NON_CONSTANT_FORMAT,
                 if (protoLogCall.call.valueArguments.contains(protoLogCall.formatStringArg)) protoLogCall.formatStringArg else protoLogCall.call,
                 context.getLocation(if (protoLogCall.call.valueArguments.contains(protoLogCall.formatStringArg)) protoLogCall.formatStringArg else protoLogCall.call),
                 "ProtoLog format string should be a compile-time constant."
            )
            return
        }

        validateFormatString(context, protoLogCall.call, protoLogCall.formatString, protoLogCall.formatStringArg, protoLogCall.args)
    }

    private fun isProtoLogCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ): Boolean {
        if (node.methodName !in listOf("v", "d", "i", "w", "e", "wtf")) {
            return false
        }
        return context.evaluator.isMemberInSubClassOf(
            method,
            "com.android.internal.protolog.ProtoLog",
            false,
        )
    }

    private fun validateFormatString(
        context: JavaContext,
        call: UCallExpression,
        formatString: String,
        formatStringDecl: UExpression,
        args: List<UExpression>
    ) {
        val specifiers = parseFormatString(formatString)
        val isValidFormatString = validateSpecifiers(specifiers, context, formatStringDecl)

        if (isValidFormatString && args.isNotEmpty()) {
            val nonContextContent = formatString
                .replace("%(${SUPPORTED_SPECIFIERS.joinToString("|")})".toRegex(), "")
            if (nonContextContent.isBlank()) {
                context.report(
                    ISSUE_NO_TEXT_CONTEXT,
                    formatStringDecl,
                    context.getLocation(formatStringDecl),
                    "ProtoLog format string should contain some context, not just specifiers."
                )
            }
        }

        val validSpecifiers = specifiers.filter {
            it.char in SUPPORTED_SPECIFIERS
        }

        if (validSpecifiers.size != args.size && isValidFormatString) {
            context.report(
                ISSUE_ARG_COUNT,
                call,
                context.getLocation(call),
                "Incorrect argument count: format string expects ${specifiers.size} " +
                        "arguments but ${args.size} were provided.",
            )
        }

        val checkCount = min(specifiers.size, args.size)
        val reportedConstants = mutableSetOf<UExpression>()

        for (j in 0 until checkCount) {
            val specifier = specifiers[j]
            val arg = args[j]

            // Only report issues for arguments that are actually passed in this call.
            // Internal arguments of a wrapper are checked at the wrapper definition.
            if (!call.valueArguments.contains(arg)) {
                continue
            }

            checkArgumentType(context, specifier, arg)

            if (arg.evaluate() != null && arg !in reportedConstants) {
                // We only want to report if the argument is a literal constant,
                // not if it is a reference to a constant (e.g. static final field or val).
                // References to constants are generally fine and often used for readability.
                var expression = arg
                while (expression is org.jetbrains.uast.UParenthesizedExpression) {
                    expression = expression.expression
                }

                if (expression !is org.jetbrains.uast.UReferenceExpression &&
                    expression !is org.jetbrains.uast.UQualifiedReferenceExpression) {
                    reportedConstants.add(arg)
                    context.report(
                        ISSUE_CONSTANT_ARGUMENT,
                        arg,
                        context.getLocation(arg),
                        "ProtoLog format string argument should not be a constant."
                    )
                }
            }
        }
    }

    private fun checkArgumentType(context: JavaContext, specifier: Specifier, arg: UExpression) {
        val type = arg.getExpressionType()
        if (type == null || type.canonicalText == "<ErrorType>") {
            return
        }

        // Ignore spread operators (*args) which appear as arrays but expand at runtime.
        val text = arg.sourcePsi?.text ?: ""
        val parentText = arg.sourcePsi?.parent?.text ?: ""
        if (text.startsWith("*") || parentText.startsWith("*")) {
            return
        }

        val specifierChar = specifier.char ?: return

        if (specifierChar !in SUPPORTED_SPECIFIERS) {
            // We are already validating specifier above,
            // and invalid specifiers don't have a defined type to check below
            return
        }

        val typeOk =
            when {
                isBoolean(type) -> specifierChar == 'b'
                isInteger(type) -> specifierChar == 'd' || specifierChar == 'x' || specifierChar == 'o'
                isFloat(type) -> specifierChar == 'f' || specifierChar == 'e' || specifierChar == 'g'
                else -> specifierChar == 's'
            }

        if (!typeOk) {
            val expectedType =
                when (specifierChar) {
                    'b' -> "boolean"
                    'd', 'x', 'o' -> "integer, long, short, or byte"
                    'f', 'e', 'g' -> "float or double"
                    else -> "String"
                }

            context.report(
                ISSUE_ARG_TYPE,
                arg,
                context.getLocation(arg),
                "Incorrect argument type for format specifier '%$specifierChar': expected " +
                        "$expectedType but got ${type.presentableText}",
            )
        }
    }

    private fun evaluateSubstituted(
        expression: UExpression,
        call: UCallExpression,
        method: PsiMethod,
        mapping: Map<UExpression, PsiParameter>
    ): Any? {
        var expr = expression
        while (expr is org.jetbrains.uast.UParenthesizedExpression) {
            expr = expr.expression
        }

        // Minimal evaluator supporting string concat and interpolation
        if (expr is org.jetbrains.uast.UPolyadicExpression) {
            val sb = StringBuilder()
            for (operand in expr.operands) {
                val valObj = evaluateSubstituted(operand, call, method, mapping) ?: return null
                sb.append(valObj.toString())
            }
            return sb.toString()
        }

        // Check if expression is a reference to a parameter
        val paramIndex = getParameterIndex(expr, method)
        if (paramIndex != null) {
            val param = method.parameterList.parameters[paramIndex]
            val mappedArg = mapping.entries.firstOrNull { it.value == param }?.key
            return mappedArg?.evaluate()
        }

        return expr.evaluate()
    }

    private fun getParameterIndex(expression: UExpression, method: PsiMethod): Int? {
        var expr = expression
        while (expr is org.jetbrains.uast.UParenthesizedExpression) {
            expr = expr.expression
        }

        var resolved = (expr as? org.jetbrains.uast.UReferenceExpression)?.resolve()

        if (resolved == null && expr.sourcePsi?.text?.startsWith("*") == true) {
            // Handle spread operator which might be wrapped in USpreadExpression
            // We scan children for the reference
            var foundRef: com.intellij.psi.PsiElement? = null
            expr.accept(object : org.jetbrains.uast.visitor.AbstractUastVisitor() {
                override fun visitElement(node: UElement): Boolean {
                    if (foundRef != null) return true
                    if (node is org.jetbrains.uast.UReferenceExpression) {
                        foundRef = node.resolve()
                        return true
                    }
                    return false
                }
            })
            resolved = foundRef
        }

        if (resolved is PsiParameter) {
            val index = method.parameterList.parameters.indexOf(resolved)
            if (index == -1) {
                return null
            }
            return index
        }
        return null
    }

    private fun checkDependsOnParameter(expression: UExpression, method: PsiMethod): Boolean {
        var depends = false
        expression.accept(object : org.jetbrains.uast.visitor.AbstractUastVisitor() {
            override fun visitElement(node: UElement): Boolean {
                if (depends) return true
                if (node is UExpression) {
                    val paramIndex = getParameterIndex(node, method)
                    if (paramIndex != null) {
                        depends = true
                        return true
                    }
                }
                return false
            }
        })
        return depends
    }

    private fun findInnerProtoLogCall(
        context: JavaContext,
        method: PsiMethod
    ): UCallExpression? {
        val uMethod = method.toUElementOfType<UMethod>() ?: return null
        var foundCall: UCallExpression? = null

        uMethod.accept(object : org.jetbrains.uast.visitor.AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (foundCall != null) return true

                val resolved = node.resolve()
                if (resolved != null && isProtoLogCall(context, node, resolved)) {
                    foundCall = node
                    return true
                }
                return false
            }
        })
        return foundCall
    }



    private fun validateSpecifiers(
        specs: List<Specifier>,
        context: JavaContext,
        formatStringArg: UExpression
    ): Boolean {
        var isValidFormatString = true
        specs.forEach { spec ->
            if (spec.char !in SUPPORTED_SPECIFIERS) {
                context.report(
                    ISSUE_INVALID_FORMAT_SPECIFIER,
                    formatStringArg,
                    context.getLocation(formatStringArg),
                    "Unsupported format specifier '%${spec.char ?: ""}'. " +
                            "Supported: " +
                            "[${SUPPORTED_SPECIFIERS.joinToString(", %", "%")}]. " +
                            "Use %% to escape."
                )
                isValidFormatString = false
            }
        }
        return isValidFormatString
    }

    private data class Specifier(val char: Char?)

    private fun parseFormatString(formatString: String): List<Specifier> {
        val specifiers = mutableListOf<Specifier>()
        var i = 0
        while (i < formatString.length) {
            if (formatString[i] == '%') {
                if (i + 1 < formatString.length) {
                    val nextChar = formatString[i + 1]
                    if (nextChar == '%') {
                        i += 2
                    } else {
                        specifiers.add(Specifier(nextChar))
                        i += 2
                    }
                } else {
                    specifiers.add(Specifier(null))
                    i++
                }
            } else {
                i++
            }
        }
        return specifiers
    }



    private fun isGenericObject(type: PsiType): Boolean {
        return type.equalsToText("java.lang.Object") || type.equalsToText("kotlin.Any")
    }

    private fun isString(type: PsiType): Boolean {
        return type.equalsToText("java.lang.String") || type.equalsToText("kotlin.String")
    }

    private fun isBoolean(type: PsiType): Boolean =
        type.equalsToText("boolean") || type.equalsToText("java.lang.Boolean") || type.equalsToText(
            "kotlin.Boolean"
        )

    private fun isInteger(type: PsiType): Boolean =
        type.equalsToText("int") ||
                type.equalsToText("long") ||
                type.equalsToText("short") ||
                type.equalsToText("byte") ||
                type.equalsToText("java.lang.Integer") ||
                type.equalsToText("java.lang.Long") ||
                type.equalsToText("java.lang.Short") ||
                type.equalsToText("java.lang.Byte") ||
                type.equalsToText("kotlin.Int") ||
                type.equalsToText("kotlin.Long") ||
                type.equalsToText("kotlin.Short") ||
                type.equalsToText("kotlin.Byte")

    private fun isFloat(type: PsiType): Boolean =
        type.equalsToText("float") ||
                type.equalsToText("double") ||
                type.equalsToText("java.lang.Float") ||
                type.equalsToText("java.lang.Double") ||
                type.equalsToText("kotlin.Float") ||
                type.equalsToText("kotlin.Double")

    companion object {
        // Matches a format specifier starting with %, followed by any non-whitespace characters.
        private val SUPPORTED_SPECIFIERS = arrayOf('b', 'd', 'o', 'x', 'f', 'e', 'g', 's')

        val ISSUE_INVALID_FORMAT_SPECIFIER: Issue =
            Issue.create(
                id = "ProtoLogInvalidFormatSpecifier",
                briefDescription = "ProtoLog format string contains an invalid specifier",
                explanation =
                    """
                    ProtoLog format strings only support a subset of format specifiers.
                    Supported specifiers are ${SUPPORTED_SPECIFIERS.joinToString(", %", "%")}
                    """,
                category = Category.CORRECTNESS,
                priority = 6,
                severity = Severity.ERROR,
                implementation =
                    Implementation(
                        ProtoLogFormatDetector::class.java,
                        EnumSet.of(Scope.JAVA_FILE),
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )

        val ISSUE_NON_CONSTANT_FORMAT: Issue =
            Issue.create(
                id = "ProtoLogNonConstantFormat",
                briefDescription = "ProtoLog format string must be a compile-time constant",
                explanation =
                    """
                    The format string argument to ProtoLog methods must be a string literal or a
                    compile-time constant. Using dynamically generated strings prevents
                    build-time optimizations and can lead to unexpected behavior.
                    """,
                category = Category.CORRECTNESS,
                priority = 6,
                severity = Severity.ERROR,
                implementation =
                    Implementation(
                        ProtoLogFormatDetector::class.java,
                        EnumSet.of(Scope.JAVA_FILE),
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )

        val ISSUE_ARG_COUNT: Issue =
            Issue.create(
                id = "ProtoLogArgCount",
                briefDescription = "Incorrect number of arguments for ProtoLog format string",
                explanation =
                    """
                    The number of arguments passed to a ProtoLog method must match the number
                    of format specifiers in the format string.
                    """,
                category = Category.CORRECTNESS,
                priority = 6,
                severity = Severity.ERROR,
                implementation =
                    Implementation(
                        ProtoLogFormatDetector::class.java,
                        EnumSet.of(Scope.JAVA_FILE),
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )

        val ISSUE_ARG_TYPE: Issue =
            Issue.create(
                id = "ProtoLogArgType",
                briefDescription = "Incorrect argument type for ProtoLog format specifier",
                explanation =
                    """
                    The type of arguments passed to ProtoLog methods must match the type
                    required by the format specifiers. For example, %d requires an integer
                    and %b requires a boolean.
                    """,
                category = Category.CORRECTNESS,
                priority = 6,
                severity = Severity.ERROR,
                implementation =
                    Implementation(
                        ProtoLogFormatDetector::class.java,
                        EnumSet.of(Scope.JAVA_FILE),
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )

        val ISSUE_CONSTANT_ARGUMENT: Issue =
            Issue.create(
                id = "ProtoLogConstantArgument",
                briefDescription = "ProtoLog format string argument should not be a constant",
                explanation =
                    """
                    ProtoLog format string arguments should not be constants.
                    If the argument is constant, it should be directly included in the format string.
                    """,
                category = Category.PERFORMANCE,
                priority = 6,
                severity = Severity.ERROR,
                implementation =
                    Implementation(
                        ProtoLogFormatDetector::class.java,
                        EnumSet.of(Scope.JAVA_FILE),
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )

        val ISSUE_NO_TEXT_CONTEXT: Issue =
            Issue.create(
                id = "ProtoLogNoContext",
                briefDescription = "ProtoLog format string should have some context",
                explanation =
                    """
                    The format string should contain some text context, not just format specifiers.
                    Logging only the arguments without any explanation is discouraged.
                    """,
                category = Category.CORRECTNESS,
                priority = 4,
                severity = Severity.WARNING,
                implementation =
                    Implementation(
                        ProtoLogFormatDetector::class.java,
                        EnumSet.of(Scope.JAVA_FILE),
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )

        val ISSUE_TOO_MANY_ARGS: Issue =
            Issue.create(
                id = "ProtoLogTooManyArgs",
                briefDescription = "ProtoLog method has too many arguments",
                explanation =
                    """
                    ProtoLog methods support at most 32 arguments.
                    """,
                category = Category.CORRECTNESS,
                priority = 6,
                severity = Severity.ERROR,
                implementation =
                    Implementation(
                        ProtoLogFormatDetector::class.java,
                        EnumSet.of(Scope.JAVA_FILE),
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )
    }
}
