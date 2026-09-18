/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.internal.systemui.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.kotlin.isEnumEntryLightClass

/**
 * A linter to ensure that SysUI view-models do not use CoroutineScope
 *
 * See go/sysui-arch:summer-24 for more.
 */
@Suppress("UnstableApiUsage")
class ViewModelCoroutineScopeBanDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UClass::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {

            override fun visitClass(node: UClass) {
                if (node.isInterface || node.isAnnotationType || node.isEnum) {
                    return
                }

                if (node.hasModifierProperty(PsiModifier.ABSTRACT)) return

                val className = node.name ?: return
                val hasViewModelSuffix =
                    className.endsWith("ViewModel") || className.endsWith("ViewModelImpl")

                if (!hasViewModelSuffix || node.isEnumEntryLightClass) return

                for (method in node.methods) {
                    checkMethod(method, context)
                }
            }

            private fun PsiType?.isCoroutineScopeType(): Boolean {
                return (this as? PsiClassType)?.resolve()?.qualifiedName == COROUTINE_SCOPE_NAME
            }

            private fun checkVariable(variable: UVariable, context: JavaContext) {
                if (variable.type.isCoroutineScopeType()) {
                    context.report(
                        ISSUE,
                        variable as UElement,
                        context.getNameLocation(variable),
                        "Do not use CoroutineScope in view-model classes.",
                    )
                }
            }

            private fun checkMethod(method: UMethod, context: JavaContext) {
                if (method.returnType.isCoroutineScopeType()) {
                    context.report(
                        ISSUE,
                        method as UElement,
                        context.getNameLocation(method),
                        "Do not return CoroutineScope from methods in view-model classes.",
                    )
                    return
                }
                for (param in method.uastParameters) {
                    checkVariable(param, context)
                }
            }
        }
    }

    companion object {
        private const val COROUTINE_SCOPE_NAME = "kotlinx.coroutines.CoroutineScope"

        @JvmField
        val ISSUE: Issue =
            Issue.create(
                id = "ViewModelCoroutineScopeBan",
                briefDescription = "Prevent CoroutineScope usage in SysUI view-models",
                explanation =
                    """
                1. CoroutineScope should not be returned, not be a parameter, and not be a field in view-model classes.
                2. Using scopes not tied to the view-model's lifecycle can lead to work not being cancelled when needed.
                3. Please use an instance of a view-model using either rememberViewModel(...) in Compose or View.viewModel(...) in view-binders.
                """,
                category = Category.CORRECTNESS,
                priority = 8,
                severity = Severity.WARNING,
                implementation =
                    Implementation(
                        ViewModelCoroutineScopeBanDetector::class.java,
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )
    }
}
