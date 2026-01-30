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
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile

/**
 * Launching coroutines into `CoroutineScope`s that run them on the main thread can cause
 * performance issues when used without care (too many small coroutines can rapidly clog up the
 * single-threaded queue that's also rendering-critical, causing jank). Therefore, this lint is
 * encourages developers to think critically about where they should inject the
 * `@Application`-qualified `CoroutineScope`.
 */
class MainThreadCoroutineScopeDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE: Issue =
            Issue.create(
                id = "WrongCoroutineScope",
                briefDescription = "Misuse of @Application CoroutineScope",
                explanation =
                    """
                    The @Application CoroutineScope is bound to the main thread. Launching
                    coroutines into the main thread hinders performance. Use the @Background one
                    instead.
                """,
                category = Category.PERFORMANCE,
                priority = 8,
                severity = Severity.WARNING,
                implementation =
                    Implementation(
                        MainThreadCoroutineScopeDetector::class.java,
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )

        private const val APPLICATION_ANNOTATION =
            "com.android.systemui.dagger.qualifiers.Application"
        private const val BACKGROUND_ANNOTATION =
            "com.android.systemui.dagger.qualifiers.Background"
        private const val COROUTINE_SCOPE = "kotlinx.coroutines.CoroutineScope"
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UFile::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitFile(node: UFile) {
                if (
                    !node.packageName.contains(".data.") && !node.packageName.contains(".domain.")
                ) {
                    return
                }

                node.classes.forEach { klass ->
                    klass.constructors.forEach { constructor ->
                        constructor.parameterList.parameters.forEach { parameter ->
                            if (
                                parameter.type.getCanonicalText(/* annotated= */ false) ==
                                    COROUTINE_SCOPE
                            ) {
                                parameter.annotations.forEach { annotation ->
                                    if (annotation.hasQualifiedName(APPLICATION_ANNOTATION)) {
                                        context.report(
                                            issue = ISSUE,
                                            scope = annotation,
                                            location = context.getLocation(annotation),
                                            message =
                                                "Do not use @Application-qualified CoroutineScopes in domain and data layers. Use @Background instead.",
                                            quickfixData =
                                                LintFix.create()
                                                    .replace()
                                                    .text(annotation.text)
                                                    .with("@$BACKGROUND_ANNOTATION")
                                                    .shortenNames()
                                                    .imports(BACKGROUND_ANNOTATION)
                                                    .build(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
