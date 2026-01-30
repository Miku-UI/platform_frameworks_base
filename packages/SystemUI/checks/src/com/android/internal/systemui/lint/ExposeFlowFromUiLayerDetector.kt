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
import com.intellij.psi.util.PsiUtil
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UField
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UastVisibility
import org.jetbrains.uast.toUElementOfType

class ExposeFlowFromUiLayerDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val FLOW_QUALIFIED_NAME = "kotlinx.coroutines.flow.Flow"

        @JvmField
        val ISSUE =
            Issue.create(
                id = "FlowExposedFromViewModel",
                briefDescription = "View-models shouldn't expose Flows",
                explanation =
                    """
                As per our best practices, UI layer objects like view-models should not expose
                flows. Instead, they should expose snapshot/compose state-backed properties.

                This is primarily a performance optimization.

                Consider migrating to snapshot state. You can use HydratedActivatable to convert
                flows to state in a view-model.
            """,
                category = Category.PERFORMANCE,
                priority = 8,
                severity = Severity.WARNING,
                implementation =
                    Implementation(ExposeFlowFromUiLayerDetector::class.java, Scope.JAVA_FILE_SCOPE),
            )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UFile::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitFile(node: UFile) {
                if (!node.packageName.contains(".ui.")) return

                node.classes.forEach { klass ->
                    klass.methods
                        .filter { method -> method.visibility == UastVisibility.PUBLIC }
                        .forEach { method ->
                            if (
                                context.evaluator.inheritsFrom(
                                    PsiUtil.resolveClassInType(method.returnType),
                                    FLOW_QUALIFIED_NAME,
                                )
                            ) {
                                val propertyOrMethod =
                                    (method.sourcePsi as? KtProperty)?.toUElementOfType<UField>()
                                        ?: method as UElement
                                context.report(
                                    issue = ISSUE,
                                    scope = propertyOrMethod,
                                    location = context.getLocation(propertyOrMethod),
                                    message = "Public property or method should not be a Flow.",
                                )
                            }
                        }
                }
            }
        }
    }
}
