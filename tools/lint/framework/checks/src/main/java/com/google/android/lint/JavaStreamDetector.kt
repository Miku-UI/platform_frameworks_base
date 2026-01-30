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

import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.uast.UElement
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression

/**
 * Detector for flagging usage of Java Stream APIs.
 *
 * This detector checks for calls to Java Stream APIs and reports a warning if such a call is found,
 * suggesting that direct iteration or utilities should be used instead.
 */
class JavaStreamDetector : Detector(), SourceCodeScanner {
    override fun getApplicableUastTypes(): List<Class<out UElement>>? =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() ?: return
                val returnType = method.returnType ?: return
                // Report only if the method returns a stream.
                if (!returnType.isStream(context)) {
                    return
                }
                // Don't report if the method is already invoked on a stream.
                if (!method.isStatic) {
                    val containingClassType = method.containingClass?.type ?: return
                    if (containingClassType.isStream(context)) {
                        return
                    }
                }
                // Don't report if the method already takes a stream.
                for (parameter in method.parameterList.parameters) {
                    val parameterType = parameter.type
                    if (parameterType.isStream(context)) {
                        return
                    }
                }
                context.report(ISSUE, node, context.getLocation(node), MESSAGE)
            }
        }

    private val PsiMethod.isStatic: Boolean
        get() = hasModifierProperty(PsiModifier.STATIC)

    private val PsiClass.type: PsiType
        get() = PsiTypesUtil.getClassType(this)

    private fun PsiType.isStream(context: JavaContext): Boolean =
        isAssignableTo(context, "java.util.stream.BaseStream")

    private fun PsiType.isAssignableTo(context: JavaContext, qualifiedName: String): Boolean =
        context.evaluator.findClassType(qualifiedName)?.isAssignableFrom(this) == true

    private fun JavaEvaluator.findClassType(qualifiedName: String): PsiType? =
        getClassType(findClass(qualifiedName))

    companion object {
        private val DESCRIPTION = "Flags usage of Java Stream APIs"

        private val EXPLANATION =
            """
                **Problem:**

                Java Stream APIS are significantly slower than direct iteration, and they also tend to allocate many short-lived objects that GC will eventually have to clean up.

                **Solution:**

                Iterate over the data directly, or use utilities like `com.android.internal.util.CollectionUtils`.

                **When to Ignore this Warning:**

                You may choose to ignore this warning if your code isn't part of framework or system server.
            """
                .trimIndent()

        val ISSUE =
            Issue.create(
                id = "JavaStream",
                briefDescription = DESCRIPTION,
                explanation = EXPLANATION,
                category = Category.PERFORMANCE,
                priority = 5,
                severity = Severity.WARNING,
                implementation =
                    Implementation(
                        JavaStreamDetector::class.java,
                        EnumSet.of(Scope.JAVA_FILE),
                    ),
            )

        private val MESSAGE =
            """
                Using Java Stream APIs results in suboptimal performance. Consider iterating over the data directly, or using utilities like `CollectionUtils`.
            """
                .trimIndent()
    }
}
