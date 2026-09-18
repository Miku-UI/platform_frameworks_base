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
import java.util.EnumSet
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.getContainingUFile

/**
 * A detector for SysUI e2e test classes that ensures any class with tests also includes a
 * `@RunWith` annotation.
 */
class IncludeRunWithAnnotationDetector : Detector(), SourceCodeScanner {
    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UClass::class.java, UAnnotation::class.java, UImportStatement::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            private var isSysUiScenarioTest = false
            private var hasRunWith = false

            override fun visitClass(node: UClass) {
                if (
                    node
                        .getContainingUFile()
                        ?.packageName
                        ?.contains(SYSUI_SCENARIO_TEST_DIRECTORY) == true
                ) {
                    isSysUiScenarioTest = true
                }
            }

            override fun visitImportStatement(node: UImportStatement) {
                if (node.asRenderString() == RUN_WITH_IMPORT) {
                    hasRunWith = true
                }
            }

            override fun visitAnnotation(node: UAnnotation) {
                if (node.qualifiedName == TEST_ANNOTATION && isSysUiScenarioTest && !hasRunWith) {
                    val location = context.getLocation(node)
                    val message = "@Test annotation included without @RunWith annotation"
                    context.report(ISSUE, node, location, message)
                }
            }
        }
    }

    companion object {
        const val SYSUI_SCENARIO_TEST_DIRECTORY = "platform.test.scenario.sysui"
        private const val RUN_WITH_IMPORT = "import org.junit.runner.RunWith"
        private const val TEST_ANNOTATION = "org.junit.Test"

        @JvmField
        val ISSUE: Issue =
            Issue.create(
                id = "IncludeRunWithAnnotation",
                briefDescription = "End-to-end tests should include a `@RunWith` annotation",
                explanation =
                    """
                    Any SysUI end-to-end test should include a `@RunWith` annotation to ensure our
                    custom annotations (like `@NoMetricBefore`) are parsed correctly. Test runners
                    also often include better debugging messages and better error handling. Use
                    `@RunWith(Functional::class)` for functional tests and use
                    `@RunWith(Microbenchmark::class)` for performance tests.
                    """,
                category = Category.TESTING,
                priority = 7,
                severity = Severity.WARNING,
                implementation =
                    Implementation(
                        IncludeRunWithAnnotationDetector::class.java,
                        EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
                    ),
            )
    }
}
