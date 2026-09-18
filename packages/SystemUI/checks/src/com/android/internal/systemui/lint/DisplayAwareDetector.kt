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
import com.intellij.psi.PsiParameter
import org.jetbrains.uast.UClass

/**
 * A lint check to ensure that classes associated with a specific display inject `@DisplayAware`
 * dependencies.
 *
 * Per-display classes are identified by the `@PerDisplaySingleton` annotation or by injecting at
 * least one `@DisplayAware` dependency.
 *
 * Using non-display-aware variants of certain types (like `Context` or `Resources`) in these
 * classes can lead to resource mismatches, visual inconsistencies, or memory leaks.
 */
class DisplayAwareDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitClass(node: UClass) {
                if (!node.isPerDisplay) {
                    return
                }
                for (parameter in node.injectedConstructorParameters) {
                    if (parameter.shouldReport()) {
                        context.report(
                            issue = getIssue(parameter),
                            scope = parameter.declarationScope,
                            location = context.getNameLocation(parameter),
                            message =
                                """
                                    Per-display classes should inject a qualified version of ${parameter.type.presentableText}. 
                                    
                                    Annotate the parameter with @DisplayAware or @DisplayAwareStatusBar. 
                                    
                                    Reach out to chrisgollner@ or nicomazz@ for any questions.
                                    """
                                    .trimIndent(),
                        )
                    }
                }
            }

            private fun getIssue(parameter: PsiParameter) =
                if (DISPLAY_AWARE_REQUIRED_TYPES_INFO.contains(parameter.type.canonicalText)) {
                    INFO_ISSUE
                } else {
                    ERROR_ISSUE
                }
        }
    }

    private val UClass.isPerDisplay
        get() = hasAnnotation(PER_DISPLAY_SINGLETON_ANNOTATION) || injectsDisplayAwareDependencies

    private val UClass.injectsDisplayAwareDependencies: Boolean
        get() {
            for (parameter in injectedConstructorParameters) {
                if (DISPLAY_AWARE_ANNOTATIONS.any { parameter.hasAnnotation(it) }) {
                    return true
                }
            }
            return false
        }

    private val UClass.injectedConstructorParameters: List<PsiParameter>
        get() =
            constructors
                .filter { constructor ->
                    INJECT_ANNOTATIONS.any { injectAnnotation ->
                        constructor.hasAnnotation(injectAnnotation)
                    }
                }
                .flatMap { it.parameterList.parameters.toList() }

    private fun PsiParameter.shouldReport(): Boolean {
        if (hasAnnotation(LEGACY_DISPLAY_ID_ANNOTATION)) {
            return true
        }
        if (hasAnnotation(ASSISTED_ANNOTATION)) {
            return false
        }
        if (!DISPLAY_AWARE_REQUIRED_TYPES.contains(type.canonicalText)) {
            return false
        }
        return DISPLAY_AWARE_ANNOTATIONS.all { !this.hasAnnotation(it) }
    }

    companion object {
        private const val PER_DISPLAY_SINGLETON_ANNOTATION =
            "com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.PerDisplaySingleton"
        private const val DISPLAY_AWARE_ANNOTATION =
            "com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware"
        private const val DISPLAY_AWARE_STATUS_BAR_ANNOTATION =
            "com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAwareStatusBar"

        private const val LEGACY_DISPLAY_ID_ANNOTATION =
            "com.android.systemui.dagger.qualifiers.DisplayId"

        private const val INJECT_ANNOTATION = "javax.inject.Inject"
        private const val ASSISTED_INJECT_ANNOTATION = "dagger.assisted.AssistedInject"
        private const val ASSISTED_ANNOTATION = "dagger.assisted.Assisted"

        private val INJECT_ANNOTATIONS = setOf(INJECT_ANNOTATION, ASSISTED_INJECT_ANNOTATION)

        private val DISPLAY_AWARE_ANNOTATIONS =
            setOf(DISPLAY_AWARE_ANNOTATION, DISPLAY_AWARE_STATUS_BAR_ANNOTATION)

        private val DISPLAY_AWARE_REQUIRED_TYPES_ERROR =
            setOf(
                "android.content.Context",
                "android.view.WindowManager",
                "android.view.LayoutInflater",
                "android.content.res.Resources",
                "com.android.systemui.common.ui.ConfigurationState",
                "com.android.systemui.statusbar.policy.ConfigurationController",
                "com.android.systemui.common.ui.domain.interactor.ConfigurationInteractor",
            )
        private val DISPLAY_AWARE_REQUIRED_TYPES_INFO = setOf("kotlinx.coroutines.CoroutineScope")

        private val DISPLAY_AWARE_REQUIRED_TYPES =
            DISPLAY_AWARE_REQUIRED_TYPES_ERROR + DISPLAY_AWARE_REQUIRED_TYPES_INFO

        private const val BRIEF_DESCRIPTION =
            "Injecting non-@DisplayAware dependency in per display class"

        private val EXPLANATION =
            """
            Classes that are associated with a specific display must use `@DisplayAware` variants of
            certain framework and SystemUI types like `Context`, `Resources`, `WindowManager`, 
            `CoroutineScope`, etc.

            A class is considered per-display if it is either annotated with `@PerDisplaySingleton` 
            or if it injects at least one `@DisplayAware` dependency.

            Injecting a non-display-aware version of these types can lead to subtle bugs, resource
            mismatches, visual inconsistencies across different displays, or even memory leaks.
            """
                .trimIndent()

        @JvmField
        val ERROR_ISSUE: Issue =
            Issue.create(
                id = "PerDisplayClassInjectedWithNonDisplayAwareError",
                briefDescription = BRIEF_DESCRIPTION,
                explanation = EXPLANATION.trimIndent(),
                category = Category.CORRECTNESS,
                priority = 8,
                severity = Severity.ERROR,
                implementation =
                    Implementation(DisplayAwareDetector::class.java, Scope.JAVA_FILE_SCOPE),
            )

        @JvmField
        val INFO_ISSUE: Issue =
            Issue.create(
                id = "PerDisplayClassInjectedWithNonDisplayAwareInfo",
                briefDescription = BRIEF_DESCRIPTION,
                explanation = EXPLANATION.trimIndent(),
                category = Category.CORRECTNESS,
                priority = 5,
                severity = Severity.INFORMATIONAL,
                implementation =
                    Implementation(DisplayAwareDetector::class.java, Scope.JAVA_FILE_SCOPE),
            )
    }
}
