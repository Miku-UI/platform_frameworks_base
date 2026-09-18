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
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod

/** Detects usages of @Application and @Background CoroutineScopes in non-singleton classes. */
class NonSysUISingletonApplicationScopeDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (!node.isConstructor) {
                    return
                }
                val parent = node.uastParent as? UClass ?: return
                if (parent.isInterface || parent.isEnum || parent.isAnnotationType) {
                    return
                }
                if (parent.hasAnnotation(SYSUI_SINGLETON)) {
                    return
                }
                visitConstructor(node)
            }

            private fun visitConstructor(constructor: PsiMethod) {
                val isInject = constructor.hasAnnotation(INJECT)
                val isAssistedInject = constructor.hasAnnotation(ASSISTED_INJECT)

                if (!isInject && !isAssistedInject) {
                    return
                }
                constructor.parameterList.parameters.forEach { parameter -> visitParam(parameter) }
            }

            private fun visitParam(parameter: PsiParameter) {
                if (parameter.hasAnnotation(ASSISTED)) {
                    return
                }
                if (parameter.type.getCanonicalText(false) != COROUTINE_SCOPE) {
                    return
                }
                val hasAppScope = parameter.hasAnnotation(APPLICATION_QUALIFIER)
                val hasBgScope = parameter.hasAnnotation(BACKGROUND_QUALIFIER)

                if (hasAppScope || hasBgScope) {
                    context.report(
                        issue = ISSUE,
                        scope = parameter,
                        location = context.getLocation(parameter),
                        message =
                            "Classes using @Application or @Background CoroutineScope should " +
                                "be annotated with @SysUISingleton.",
                    )
                }
            }
        }
    }

    companion object {
        private const val SYSUI_SINGLETON = "com.android.systemui.dagger.SysUISingleton"
        private const val APPLICATION_QUALIFIER =
            "com.android.systemui.dagger.qualifiers.Application"
        private const val BACKGROUND_QUALIFIER = "com.android.systemui.dagger.qualifiers.Background"
        private const val COROUTINE_SCOPE = "kotlinx.coroutines.CoroutineScope"
        private const val INJECT = "javax.inject.Inject"
        private const val ASSISTED_INJECT = "dagger.assisted.AssistedInject"
        private const val ASSISTED = "dagger.assisted.Assisted"

        @JvmField
        val ISSUE: Issue =
            Issue.create(
                id = "NonSysUISingletonApplicationScope",
                briefDescription =
                    "Application/Background CoroutineScope used in non-singleton class",
                explanation =
                    """
                    Classes that use `@Application` or `@Background` qualified `CoroutineScope`s \
                    should be annotated with `@SysUISingleton`. These scopes live as long as the \
                    application, so classes using them should also be singletons to prevent \
                    memory leaks. If this class is intended to have a shorter lifecycle, consider \
                    using a different scope or refactoring.
                    
                    Examples:
                    - If your class is per-display, inject `@DisplayAware CoroutineScope`.  
                    - If your class is a ViewModel, don't use CoroutineScope at all. Use the \ 
                      hydrator pattern instead. 
                    - If your class is used in a Service, create a CoroutineScope that is \ 
                      cancelled when the service is destroyed. 
                    """,
                category = Category.PERFORMANCE,
                priority = 7,
                severity = Severity.WARNING,
                implementation =
                    Implementation(
                        NonSysUISingletonApplicationScopeDetector::class.java,
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )
    }
}
