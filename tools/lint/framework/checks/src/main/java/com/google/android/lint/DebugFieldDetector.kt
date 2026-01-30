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
import com.android.tools.lint.detector.api.isKotlin
import com.intellij.psi.PsiTypes
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.uast.UField

/**
 * Lint Detector for checking the usage of `DEBUG` flags in Java code.
 *
 * This detects cases where the `DEBUG` flag is set to `true` or is not declared as `final`. Both
 * scenarios are generally undesirable in production code, as they prevent code optimization and can
 * lead to unintended behavior (e.g., log spam).
 *
 * TODO(b/436095548): Consider migrating to global lint after initial application to the framework.
 */
class DebugFieldDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UField::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        DebugUastHandler(context)

    private inner class DebugUastHandler(val context: JavaContext) : UElementHandler() {
        override fun visitField(node: UField) {
            if (!isRelevantDebugField(node)) return

            // We want to alert against both non-final and true values for DEBUG.
            val isKotlin = isKotlin(node)
            val isDebugTrue = node.uastInitializer?.evaluate() == true
            val isDebugNonFinal =
                if (isKotlin) {
                    val ktProperty = node.sourcePsi as? KtProperty
                    ktProperty != null &&
                        !ktProperty.hasModifier(org.jetbrains.kotlin.lexer.KtTokens.CONST_KEYWORD)
                } else {
                    !node.isFinal
                }
            if (!isDebugTrue && !isDebugNonFinal) return

            // Generate any appropriate fixes and aggregate into a single, composite fix.
            val fixes = buildList {
                if (isDebugTrue) {
                    node.uastInitializer?.let {
                        add(fix().replace().range(context.getLocation(it)).with("false").build())
                    }
                }
                if (isDebugNonFinal) {
                    if (isKotlin) {
                        (node.sourcePsi as? KtProperty)?.valOrVarKeyword?.let {
                            add(
                                fix()
                                    .replace()
                                    .range(context.getLocation(it))
                                    .with("const val")
                                    .build()
                            )
                        }
                    } else {
                        node.typeReference?.let {
                            val newText = "final ${it.sourcePsi?.text}"
                            add(
                                fix().replace().range(context.getLocation(it)).with(newText).build()
                            )
                        }
                    }
                }
            }
            val compositeFix =
                if (fixes.isEmpty()) null
                else fix().name("Fix DEBUG field usage").composite(*fixes.toTypedArray())

            // Report the detected issues separately, but with the same composite fix.
            if (isDebugTrue) {
                context.report(
                    ISSUE_DEBUG_TRUE,
                    node,
                    context.getLocation(node),
                    "Avoid enabling `DEBUG`-guarded code in production; set `DEBUG` to `false`.",
                    compositeFix,
                )
            }
            if (isDebugNonFinal) {
                val modifier = if (isKotlin) "`const val`" else "`final`"
                context.report(
                    ISSUE_NON_FINAL_DEBUG,
                    node,
                    context.getLocation(node),
                    "Avoid non-final `DEBUG` usage; mark as $modifier.",
                    compositeFix,
                )
            }
        }

        private fun isRelevantDebugField(node: UField): Boolean {
            return node.isStatic && node.type == PsiTypes.booleanType() && node.name == "DEBUG"
        }
    }

    companion object {
        // Somewhat arbitrary priority, where `10` is the highest.
        private val ISSUE_PRIORITY = 6

        private val ISSUE_SEVERITY = Severity.ERROR

        private val DEBUG_TRUE_EXPLANATION =
            """
            `DEBUG`-guarded statements and code should be disabled in production. \
            Any exceptions should include a detailed comment and bug tracking removal or turndown, \
            with the appropropriate `@SuppressLint("DebugTrue")` usage.
        """
                .trimIndent()

        @JvmField
        val ISSUE_DEBUG_TRUE =
            Issue.create(
                id = "DebugTrue",
                briefDescription = "DEBUG flag is enabled.",
                explanation = DEBUG_TRUE_EXPLANATION,
                category = Category.PERFORMANCE,
                priority = ISSUE_PRIORITY,
                severity = ISSUE_SEVERITY,
                implementation =
                    Implementation(DebugFieldDetector::class.java, Scope.JAVA_FILE_SCOPE),
            )

        private val NON_FINAL_DEBUG_EXPLANATION =
            """
            Non-final `DEBUG` usage prevents code optimization. \
            Any exceptions should include a detailed comment and bug tracking removal or finalization, \
            with the appropropriate `@SuppressLint("DebugNonFinal")` usage.
        """
                .trimIndent()

        @JvmField
        val ISSUE_NON_FINAL_DEBUG =
            Issue.create(
                id = "DebugNonFinal",
                briefDescription = "DEBUG flag is non-final.",
                explanation = NON_FINAL_DEBUG_EXPLANATION,
                category = Category.PERFORMANCE,
                priority = ISSUE_PRIORITY,
                severity = ISSUE_SEVERITY,
                implementation =
                    Implementation(DebugFieldDetector::class.java, Scope.JAVA_FILE_SCOPE),
            )
    }
}
