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
 * A detector for SysUI e2e test classes to encourage the use of `@NoMetricBefore` /
 * `@NoMetricAfter` in functional tests, as opposed to `@Before` / `@After`.
 *
 * See also: [NoMetricInParameterizedDetector].
 */
class UseNoMetricInFunctionalDetector : Detector(), SourceCodeScanner {
    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UAnnotation::class.java, UImportStatement::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            private var isFunctional = false

            override fun visitImportStatement(node: UImportStatement) {
                // Ideally we could check that there's a `RunWith` annotation that uses `Functional`
                // as its parameter, but that doesn't work in Kotlin files. So, we just check if
                // `Functional` is imported at all.
                if (node.asRenderString() == FUNCTIONAL_IMPORT) {
                    isFunctional = true
                }
            }

            override fun visitAnnotation(node: UAnnotation) {
                if (isFunctional) {
                    if (node.qualifiedName == BEFORE_ANNOTATION) {
                        val location = context.getLocation(node)
                        val message = "Consider using @NoMetricBefore for performance tests"
                        context.report(ISSUE_BEFORE, node, location, message)
                    } else if (node.qualifiedName == AFTER_ANNOTATION) {
                        val location = context.getLocation(node)
                        val message = "Consider using @NoMetricAfter for performance tests"
                        context.report(ISSUE_AFTER, node, location, message)
                    }
                }
            }
        }
    }

    companion object {
        private const val FUNCTIONAL_IMPORT =
            "import android.platform.test.microbenchmark.Functional"
        private const val BEFORE_ANNOTATION = "org.junit.Before"
        private const val AFTER_ANNOTATION = "org.junit.After"

        @JvmField
        val ISSUE_BEFORE: Issue =
            Issue.create(
                id = "UseNoMetricBeforeInFunctional",
                briefDescription = "Consider using @NoMetricBefore for performance tests",
                explanation =
                    """
                    Consider using @NoMetricBefore instead of @Before if this test is also used as a
                    performance test. Otherwise, the content of @Before will appear in the trace and
                    generate performance metrics. See b/454887380 for more information.
                    """,
                category = Category.TESTING,
                priority = 7,
                severity = Severity.WARNING,
                implementation =
                    Implementation(
                        UseNoMetricInFunctionalDetector::class.java,
                        EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
                    ),
            )

        @JvmField
        val ISSUE_AFTER: Issue =
            Issue.create(
                id = "UseNoMetricAfterInFunctional",
                briefDescription = "Consider using @NoMetricAfter in Functional tests",
                explanation =
                    """
                    Consider using @NoMetricAfter instead of @After if this test is also used as a
                    performance test. Otherwise, the content of @After will appear in the trace and
                    generate performance metrics. See b/454887380 for more information.
                    """,
                category = Category.TESTING,
                priority = 7,
                severity = Severity.WARNING,
                implementation =
                    Implementation(
                        UseNoMetricInFunctionalDetector::class.java,
                        EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
                    ),
            )
    }
}
