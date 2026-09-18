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

import com.android.internal.systemui.lint.IncludeRunWithAnnotationDetector.Companion.SYSUI_SCENARIO_TEST_DIRECTORY
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import java.util.EnumSet
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.getContainingUFile

/** A detector for SysUI e2e test classes to never import `kotlin.test.Test`. */
class NoKotlinTestInScenarioTestDetector : Detector(), SourceCodeScanner {
    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UImportStatement::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitImportStatement(node: UImportStatement) {
                if (
                    node.asRenderString() == KOTLIN_TEST_IMPORT &&
                        node
                            .getContainingUFile()
                            ?.packageName
                            ?.contains(SYSUI_SCENARIO_TEST_DIRECTORY) == true
                ) {
                    val location = context.getLocation(node)
                    val message = "Do not use kotlin.test.Test"
                    context.report(ISSUE, node, location, message)
                }
            }
        }
    }

    companion object {
        private const val KOTLIN_TEST_IMPORT = "import kotlin.test.Test"

        @JvmField
        val ISSUE: Issue =
            Issue.create(
                id = "KotlinTestAnnotationUsage",
                briefDescription = "End-to-end tests should not use `kotlin.test.Test`",
                explanation =
                    """
                    SysUI end-to-end tests should not use `kotlin.test.Test` and should use
                    `org.junit.Test` instead. See b/470062879.
                    """,
                category = Category.TESTING,
                priority = 8,
                severity = Severity.ERROR,
                implementation =
                    Implementation(
                        NoKotlinTestInScenarioTestDetector::class.java,
                        EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
                    ),
            )
    }
}
