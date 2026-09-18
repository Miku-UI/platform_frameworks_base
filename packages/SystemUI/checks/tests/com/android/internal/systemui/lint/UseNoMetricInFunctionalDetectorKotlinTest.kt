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

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test

/** Test [UseNoMetricInFunctionalDetector] with Kotlin files. */
class UseNoMetricInFunctionalDetectorKotlinTest : SystemUILintDetectorTest() {
    override fun getDetector(): Detector = UseNoMetricInFunctionalDetector()

    override fun getIssues(): List<Issue> =
        listOf(
            UseNoMetricInFunctionalDetector.ISSUE_BEFORE,
            UseNoMetricInFunctionalDetector.ISSUE_AFTER,
        )

    @Test
    fun before_withFunctionalRunner_error() {
        lint()
            .files(
                kotlin(
                    """
                        package test.pkg

                        import org.junit.Before
                        import org.junit.runner.RunWith
                        import android.platform.test.microbenchmark.Functional

                        @RunWith(Functional::class)
                        class TestClass {
                            @Before
                            fun setUp() {}
                        }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(UseNoMetricInFunctionalDetector.ISSUE_BEFORE)
            .run()
            .expect(
                """
                src/test/pkg/TestClass.kt:9: Warning: Consider using @NoMetricBefore for performance tests [UseNoMetricBeforeInFunctional]
                    @Before
                    ~~~~~~~
                0 errors, 1 warnings
                                """
                    .trimIndent()
            )
    }

    @Test
    fun noMetricBefore_withFunctionalRunner_clean() {
        lint()
            .files(
                kotlin(
                    """
                        package test.pkg

                        import org.junit.runner.RunWith
                        import android.platform.test.microbenchmark.Functional
                        import android.platform.test.microbenchmark.Microbenchmark.NoMetricBefore

                        @RunWith(Functional::class)
                        class TestClass {
                            @NoMetricBefore
                            fun setUp() {}
                        }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(UseNoMetricInFunctionalDetector.ISSUE_BEFORE)
            .run()
            .expectClean()
    }

    @Test
    fun after_withFunctionalRunner_error() {
        lint()
            .files(
                kotlin(
                    """
                        package test.pkg

                        import org.junit.After
                        import org.junit.runner.RunWith
                        import android.platform.test.microbenchmark.Functional

                        @RunWith(Functional::class)
                        class TestClass {
                            @After
                            fun tearDown() {}
                        }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(UseNoMetricInFunctionalDetector.ISSUE_AFTER)
            .run()
            .expect(
                """
                src/test/pkg/TestClass.kt:9: Warning: Consider using @NoMetricAfter for performance tests [UseNoMetricAfterInFunctional]
                    @After
                    ~~~~~~
                0 errors, 1 warnings
                                """
                    .trimIndent()
            )
    }

    @Test
    fun noMetricAfter_withFunctionalRunner_clean() {
        lint()
            .files(
                kotlin(
                    """
                        package test.pkg

                        import org.junit.runner.RunWith
                        import android.platform.test.microbenchmark.Functional
                        import android.platform.test.microbenchmark.Microbenchmark.NoMetricAfter

                        @RunWith(Functional::class)
                        class TestClass {
                            @NoMetricAfter
                            fun tearDown() {}
                        }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(UseNoMetricInFunctionalDetector.ISSUE_AFTER)
            .run()
            .expectClean()
    }

    @Test
    fun before_withParameterizedRunner_clean() {
        lint()
            .files(
                kotlin(
                    """
                        package test.pkg

                        import org.junit.Before
                        import org.junit.runner.RunWith
                        import org.junit.runners.Parameterized

                        @RunWith(Parameterized::class)
                        class TestClass {
                            @Before
                            fun setUp() {}
                        }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(UseNoMetricInFunctionalDetector.ISSUE_BEFORE)
            .run()
            .expectClean()
    }

    @Test
    fun after_withParameterizedRunner_clean() {
        lint()
            .files(
                kotlin(
                    """
                        package test.pkg

                        import org.junit.After
                        import org.junit.runner.RunWith
                        import org.junit.runners.Parameterized

                        @RunWith(Parameterized::class)
                        class TestClass {
                            @After
                            fun tearDown() {}
                        }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(UseNoMetricInFunctionalDetector.ISSUE_AFTER)
            .run()
            .expectClean()
    }

    companion object {
        private val beforeStub: TestFile =
            kotlin(
                """
        package org.junit

        annotation class Before
        """
            )

        private val afterStub: TestFile =
            kotlin(
                """
        package org.junit

        annotation class After
        """
            )
    }

    private val stubs =
        arrayOf(
            beforeStub,
            afterStub,
            NoMetricInParameterizedDetectorKotlinTest.runWithStub,
            NoMetricInParameterizedDetectorKotlinTest.microbenchmarkStub,
            NoMetricInParameterizedDetectorKotlinTest.functionalStub,
            NoMetricInParameterizedDetectorKotlinTest.parameterizedStub,
        )
}
