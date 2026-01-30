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

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.UUnaryExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastPrefixOperator
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.isNullLiteral
import org.jetbrains.uast.skipParenthesizedExprDown

/**
 * Lint Detector for encouraging the use of {@link com.android.internal.util.Preconditions}.
 *
 * When a precondition check is done inline, this detector recommends using the corresponding
 * Preconditions utility method instead.
 *
 * In addition, the following best practices are enforced:
 * - Exception message formatting should be deferred to the Preconditions method. This keeps the
 *   client code minimal, outlining the string formatting logic to a single location, and not
 *   formatting the message until after the check has failed.
 * - Use the *WithName variant of the Preconditions methods when applicable. This saves some space
 *   for unnecessary strings.
 *
 * This detector is organized into a main `UElementHandler` that dispatches to specialized handlers
 * for each type of precondition check. Common logic, such as parsing `throw` expressions and
 * handling string formatting, is extracted into helper methods.
 */
// TODO(b/434769547): Add to AndroidFrameworkIssueRegistry
class PreconditionsDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UIfExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        PreconditionsUastHandler(context)

    private inner class PreconditionsUastHandler(val context: JavaContext) : UElementHandler() {
        /**
         * Main entry point for visiting `if` expressions. Dispatches to specific handlers for
         * different precondition checks in a specific order.
         */
        override fun visitIfExpression(node: UIfExpression) {
            // Do not suggest changes within the Preconditions class itself.
            val containingClassName = node.getContainingUClass()?.qualifiedName
            if (containingClassName == PRECONDITIONS_CLASS) {
                return
            }

            // The order of these handlers matters. More specific checks should come first.
            if (handleCheckNotNull(node)) return
            if (handleCheckArgument(node)) return
            handleCheckState(node)
        }

        /**
         * Handles `if (foo == null) { throw ... }` patterns and suggests
         * `Preconditions.checkNotNull()`. It can handle different exception types
         * (`NullPointerException`, `IllegalArgumentException`, `IllegalStateException`) and various
         * message formats, including string concatenation and `String.format`.
         */
        private fun handleCheckNotNull(node: UIfExpression): Boolean {
            val condition =
                node.condition.skipParenthesizedExprDown() as? UBinaryExpression ?: return false
            if (
                condition.operator != UastBinaryOperator.IDENTITY_EQUALS &&
                    condition.operator != UastBinaryOperator.EQUALS
            ) {
                return false
            }

            // Extract the variable being checked for null.
            val variable =
                when {
                    condition.rightOperand.skipParenthesizedExprDown().isNullLiteral() ->
                        condition.leftOperand.skipParenthesizedExprDown()
                    condition.leftOperand.skipParenthesizedExprDown().isNullLiteral() ->
                        condition.rightOperand.skipParenthesizedExprDown()
                    else -> return false
                }
            val variableName = (variable as? UReferenceExpression)?.sourcePsi?.text ?: return false

            val throwExpression = findThrowExpression(node) ?: return false

            val thrownException =
                throwExpression.thrownExpression?.skipParenthesizedExprDown() as? UCallExpression
                    ?: return false
            val exceptionType = thrownException.resolve()?.containingClass?.qualifiedName

            // For null checks, we allow a few different exception types to be thrown,
            // but still recommend `checkNotNull` for consistency.
            val allowedExceptions =
                setOf(NULL_POINTER_EXCEPTION, ILLEGAL_ARGUMENT_EXCEPTION, ILLEGAL_STATE_EXCEPTION)
            if (exceptionType !in allowedExceptions) {
                return false
            }
            val constructorArgs = thrownException.valueArguments
            val replacement: String
            // Suggest `checkNotNullWithName` only for NullPointerExceptions with a
            // specific message format for conciseness.
            if (constructorArgs.isNotEmpty()) {
                val messageExpression = constructorArgs[0]
                val message = messageExpression.evaluateString()

                if (
                    exceptionType == NULL_POINTER_EXCEPTION &&
                        messageStartsWithVariableName(message, variableName)
                ) {
                    replacement =
                        "$PRECONDITIONS_CLASS.checkNotNullWithName($variableName, \"$variableName\");"
                } else {
                    val (format, args) = getFormatAndArgs(messageExpression)
                    val argsPart = if (args.isNotEmpty()) ", $args" else ""
                    // Handle formatted messages.
                    replacement =
                        "$PRECONDITIONS_CLASS.checkNotNull($variableName, $format$argsPart);"
                }
            } else {
                replacement = "$PRECONDITIONS_CLASS.checkNotNull($variableName);"
            }
            val fix =
                fix()
                    .name("Replace with Preconditions.checkNotNull()")
                    .replace()
                    .range(context.getLocation(node))
                    .with(replacement)
                    .shortenNames()
                    .reformat(true)
                    .build()

            context.report(
                ISSUE_CHECK_NOT_NULL,
                node,
                context.getLocation(node),
                "Use Preconditions.checkNotNull() instead of manual null check",
                fix,
            )
            return true
        }

        /**
         * Handles `if (!condition) { throw IllegalArgumentException() }` patterns and suggests
         * `Preconditions.checkArgument()`.
         */
        private fun handleCheckArgument(node: UIfExpression): Boolean {
            return handleBooleanPrecondition(
                node,
                exceptionFqn = ILLEGAL_ARGUMENT_EXCEPTION,
                preconditionMethod = "checkArgument",
                issue = ISSUE_CHECK_ARGUMENT,
            )
        }

        /**
         * Handles `if (!condition) { throw IllegalStateException() }` patterns and suggests
         * `Preconditions.checkState()`.
         */
        private fun handleCheckState(node: UIfExpression): Boolean {
            return handleBooleanPrecondition(
                node,
                exceptionFqn = ILLEGAL_STATE_EXCEPTION,
                preconditionMethod = "checkState",
                issue = ISSUE_CHECK_STATE,
            )
        }

        /** A generic handler for `if (!condition) { throw ... }` patterns. */
        private fun handleBooleanPrecondition(
            node: UIfExpression,
            exceptionFqn: String,
            preconditionMethod: String,
            issue: Issue,
        ): Boolean {
            val condition =
                node.condition.skipParenthesizedExprDown() as? UUnaryExpression ?: return false
            if (condition.operator != UastPrefixOperator.LOGICAL_NOT) {
                return false
            }

            val expression = condition.operand.skipParenthesizedExprDown()
            val expressionText = expression.sourcePsi?.text ?: return false

            val throwExpression = findThrowExpression(node) ?: return false

            val thrownException =
                throwExpression.thrownExpression?.skipParenthesizedExprDown() as? UCallExpression
                    ?: return false
            val exceptionType = thrownException.resolve()?.containingClass?.qualifiedName
            if (exceptionType != exceptionFqn) {
                return false
            }

            val constructorArgs = thrownException.valueArguments
            val replacement =
                if (constructorArgs.isNotEmpty()) {
                    val messageExpression = constructorArgs[0]
                    val (format, args) = getFormatAndArgs(messageExpression)
                    val argsPart = if (args.isNotEmpty()) ", $args" else ""
                    "$PRECONDITIONS_CLASS.$preconditionMethod($expressionText, $format$argsPart);"
                } else {
                    "$PRECONDITIONS_CLASS.$preconditionMethod($expressionText);"
                }
            val fix =
                fix()
                    .name("Replace with Preconditions.$preconditionMethod()")
                    .replace()
                    .range(context.getLocation(node))
                    .with(replacement)
                    .shortenNames()
                    .reformat(true)
                    .build()

            context.report(
                issue,
                node,
                context.getLocation(node),
                "Use Preconditions.$preconditionMethod() instead of manual check",
                fix,
            )
            return true
        }

        /**
         * Extracts the single `throw` expression from the `then` branch of an `if` statement.
         * Handles both block and single-line `then` branches.
         */
        private fun findThrowExpression(node: UIfExpression): UThrowExpression? {
            val thenBranch = node.thenExpression?.skipParenthesizedExprDown() ?: return null
            return if (thenBranch is UBlockExpression) {
                if (thenBranch.expressions.size != 1) return null
                thenBranch.expressions[0].skipParenthesizedExprDown() as? UThrowExpression
            } else {
                thenBranch as? UThrowExpression
            }
        }

        /**
         * Checks if the given message is not null and starts with the specified variable name. For
         * a variable named "foo" matches messages like "foo must not be null", "foo cannot be
         * empty", etc.
         */
        private fun messageStartsWithVariableName(message: String?, variableName: String): Boolean =
            message?.startsWith("$variableName ") ?: false

        /**
         * Converts a message expression to a format string and a list of arguments.
         *
         * The original expression can be a string concatenation, a `String.format` call, or a
         * simple string literal.
         */
        private fun getFormatAndArgs(expression: UExpression): Pair<String, String> {
            val callExpression =
                when (val expr = expression.skipParenthesizedExprDown()) {
                    is UCallExpression -> expr
                    is UQualifiedReferenceExpression -> expr.selector as? UCallExpression
                    else -> null
                }

            // Case 1: A String.format(...) call.
            // Peel out the format string and arguments from the call.
            if (
                callExpression?.methodName == "format" &&
                    callExpression.resolve()?.containingClass?.qualifiedName == "java.lang.String"
            ) {
                val args = callExpression.valueArguments
                if (args.isNotEmpty()) {
                    val formatString = args[0].evaluateString()
                    if (formatString != null) {
                        val formatArgs =
                            args.drop(1).joinToString(", ") { it.sourcePsi?.text ?: "" }
                        return Pair("\"$formatString\"", formatArgs)
                    }
                }
            }

            // Case 2: A string concatenation with the `+` operator.
            // Transform to a format string and format args.
            if (
                expression is UPolyadicExpression && expression.operator == UastBinaryOperator.PLUS
            ) {
                val formatString = StringBuilder()
                val args = mutableListOf<String>()
                for (operand in expression.operands) {
                    val value = operand.evaluateString()
                    if (value != null) {
                        formatString.append(value)
                    } else {
                        formatString.append("%s")
                        args.add(operand.sourcePsi?.text ?: "")
                    }
                }
                return Pair("\"$formatString\"", args.joinToString(", "))
            }

            // Case 3: A simple literal string or other expression.
            // Return the expression source as a format string with no arguments.
            return Pair(expression.sourcePsi?.text ?: "", "")
        }
    }

    companion object {
        private const val PRECONDITIONS_CLASS = "com.android.internal.util.Preconditions"
        private const val NULL_POINTER_EXCEPTION = "java.lang.NullPointerException"
        private const val ILLEGAL_ARGUMENT_EXCEPTION = "java.lang.IllegalArgumentException"
        private const val ILLEGAL_STATE_EXCEPTION = "java.lang.IllegalStateException"

        @JvmField
        val ISSUE_CHECK_NOT_NULL =
            Issue.create(
                id = "PreconditionsCheckNotNull",
                briefDescription = "Use Preconditions.checkNotNull()",
                explanation =
                    """
                    Instead of manually checking for null and throwing a NullPointerException,
                    use the `$PRECONDITIONS_CLASS.checkNotNull()` method.
                    It is more concise and less error-prone.
                    """,
                category = Category.CORRECTNESS,
                priority = 6,
                severity = Severity.WARNING,
                implementation =
                    Implementation(PreconditionsDetector::class.java, Scope.JAVA_FILE_SCOPE),
            )

        @JvmField
        val ISSUE_CHECK_ARGUMENT =
            Issue.create(
                id = "PreconditionsCheckArgument",
                briefDescription = "Use Preconditions.checkArgument()",
                explanation =
                    """
                    Instead of manually checking an argument and throwing an IllegalArgumentException,
                    use the `$PRECONDITIONS_CLASS.checkArgument()` method.
                    It is more concise and less error-prone.
                    """,
                category = Category.CORRECTNESS,
                priority = 6,
                // TODO(b/434769547): Consider upgrading to ERROR.
                severity = Severity.WARNING,
                implementation =
                    Implementation(PreconditionsDetector::class.java, Scope.JAVA_FILE_SCOPE),
            )

        @JvmField
        val ISSUE_CHECK_STATE =
            Issue.create(
                id = "PreconditionsCheckState",
                briefDescription = "Use Preconditions.checkState()",
                explanation =
                    """
                    Instead of manually checking state and throwing an IllegalStateException,
                    use the `$PRECONDITIONS_CLASS.checkState()` method.
                    It is more concise and less error-prone.
                    """,
                category = Category.CORRECTNESS,
                priority = 6,
                severity = Severity.WARNING,
                implementation =
                    Implementation(PreconditionsDetector::class.java, Scope.JAVA_FILE_SCOPE),
            )

        val ISSUES = listOf(ISSUE_CHECK_NOT_NULL, ISSUE_CHECK_ARGUMENT, ISSUE_CHECK_STATE)
    }
}
