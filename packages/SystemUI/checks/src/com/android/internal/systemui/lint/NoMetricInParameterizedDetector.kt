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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement

/**
 * A detector for SysUI e2e test classes that use both `@RunWith(Parameterized)` and
 * `@NoMetricBefore` / `@NoMetricAfter`, which isn't allowed.
 *
 * See also: [UseNoMetricInFunctionalDetector].
 */
class NoMetricInParameterizedDetector : Detector(), SourceCodeScanner {
    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UAnnotation::class.java, UImportStatement::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            private var isParameterized = false

            override fun visitImportStatement(node: UImportStatement) {
                // Ideally we could check that there's a `RunWith` annotation that uses
                // `Parameterized` as its parameter, but that doesn't work in Kotlin files. So, we
                // just check if `Parameterized` is imported at all.
                if (node.asRenderString() == PARAMETERIZED_IMPORT) {
                    isParameterized = true
                }
            }

            override fun visitAnnotation(node: UAnnotation) {
                if (isParameterized) {
                    if (node.qualifiedName == NO_METRIC_BEFORE_ANNOTATION) {
                        val location = context.getLocation(node)
                        val message = "@NoMetricBefore can't be used with Parameterized test runner"
                        context.report(ISSUE_BEFORE, node, location, message)
                    } else if (node.qualifiedName == NO_METRIC_AFTER_ANNOTATION) {
                        val location = context.getLocation(node)
                        val message = "@NoMetricAfter can't be used with Parameterized test runner"
                        context.report(ISSUE_AFTER, node, location, message)
                    }
                }
            }
        }
    }

    companion object {
        private const val PARAMETERIZED_IMPORT = "import org.junit.runners.Parameterized"
        private const val NO_METRIC_BEFORE_ANNOTATION =
            "android.platform.test.microbenchmark.Microbenchmark.NoMetricBefore"
        private const val NO_METRIC_AFTER_ANNOTATION =
            "android.platform.test.microbenchmark.Microbenchmark.NoMetricAfter"

        @JvmField
        val ISSUE_BEFORE: Issue =
            Issue.create(
                id = "NoMetricBeforeWithParameterized",
                briefDescription = "Using NoMetricBefore with Parameterized test runner",
                explanation =
                    """
                    @NoMetricBefore cannot be used when also using @RunWith(Parameterized::class). \
                    Use @Before instead. See b/463351048 for more information.
                    """,
                category = Category.TESTING,
                priority = 8,
                severity = Severity.ERROR,
                implementation =
                    Implementation(
                        NoMetricInParameterizedDetector::class.java,
                        EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
                    ),
            )

        @JvmField
        val ISSUE_AFTER: Issue =
            Issue.create(
                id = "NoMetricAfterWithParameterized",
                briefDescription = "Using NoMetricAfter with Parameterized test runner",
                explanation =
                    """
                    @NoMetricAfter cannot be used when also using @RunWith(Parameterized::class). \
                    Use @After instead. See b/463351048 for more information.
                    """,
                category = Category.TESTING,
                priority = 8,
                severity = Severity.ERROR,
                implementation =
                    Implementation(
                        NoMetricInParameterizedDetector::class.java,
                        EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
                    ),
            )
    }
}
