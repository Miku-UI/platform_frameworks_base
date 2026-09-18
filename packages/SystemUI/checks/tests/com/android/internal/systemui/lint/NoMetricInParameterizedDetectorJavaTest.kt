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

/** Test [NoMetricInParameterizedDetector] with Java files. */
class NoMetricInParameterizedDetectorJavaTest : SystemUILintDetectorTest() {
    override fun getDetector(): Detector = NoMetricInParameterizedDetector()

    override fun getIssues(): List<Issue> =
        listOf(
            NoMetricInParameterizedDetector.ISSUE_BEFORE,
            NoMetricInParameterizedDetector.ISSUE_AFTER,
        )

    @Test
    fun noMetricBefore_withFunctionalRunner_clean() {
        lint()
            .files(
                java(
                    """
                        package test.pkg;

                        import org.junit.runner.RunWith;
                        import android.platform.test.microbenchmark.Functional;
                        import android.platform.test.microbenchmark.Microbenchmark.NoMetricBefore;

                        @RunWith(Functional.class)
                        class TestClass {
                            @NoMetricBefore
                            void setUp() {}
                        }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(NoMetricInParameterizedDetector.ISSUE_BEFORE)
            .run()
            .expectClean()
    }

    @Test
    fun noMetricAfter_withFunctionalRunner_clean() {
        lint()
            .files(
                java(
                    """
                        package test.pkg;

                        import org.junit.runner.RunWith;
                        import android.platform.test.microbenchmark.Functional;
                        import android.platform.test.microbenchmark.Microbenchmark.NoMetricAfter;

                        @RunWith(Functional.class)
                        class TestClass {
                            @NoMetricAfter
                            void tearDown() {}
                        }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(NoMetricInParameterizedDetector.ISSUE_AFTER)
            .run()
            .expectClean()
    }

    @Test
    fun noMetricBefore_withMicrobenchmarkRunner_clean() {
        lint()
            .files(
                java(
                    """
                        package test.pkg;

                        import org.junit.runner.RunWith;
                        import android.platform.test.microbenchmark.Microbenchmark;
                        import android.platform.test.microbenchmark.Microbenchmark.NoMetricBefore;

                        @RunWith(Microbenchmark.class)
                        class TestClass {
                            @NoMetricBefore
                            void setUp() {}
                        }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(NoMetricInParameterizedDetector.ISSUE_BEFORE)
            .run()
            .expectClean()
    }

    @Test
    fun noMetricAfter_withMicrobenchmarkRunner_clean() {
        lint()
            .files(
                java(
                    """
                        package test.pkg;

                        import org.junit.runner.RunWith;
                        import android.platform.test.microbenchmark.Microbenchmark;
                        import android.platform.test.microbenchmark.Microbenchmark.NoMetricAfter;

                        @RunWith(Microbenchmark.class)
                        class TestClass {
                            @NoMetricAfter
                            void tearDown() {}
                        }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(NoMetricInParameterizedDetector.ISSUE_AFTER)
            .run()
            .expectClean()
    }

    @Test
    fun noMetricBefore_withParameterizedRunner_error() {
        lint()
            .files(
                java(
                    """
                        package test.pkg;

                        import org.junit.runner.RunWith;
                        import org.junit.runners.Parameterized;
                        import android.platform.test.microbenchmark.Microbenchmark.NoMetricBefore;

                        @RunWith(Parameterized.class)
                        class TestClass {
                            @NoMetricBefore
                            void setUp() {}
                        }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(NoMetricInParameterizedDetector.ISSUE_BEFORE)
            .run()
            .expect(
                """
src/test/pkg/TestClass.java:9: Error: @NoMetricBefore can't be used with Parameterized test runner [NoMetricBeforeWithParameterized]
    @NoMetricBefore
    ~~~~~~~~~~~~~~~
1 errors, 0 warnings
                """
            )
    }

    @Test
    fun noMetricAfter_withParameterizedRunner_error() {
        lint()
            .files(
                java(
                    """
                        package test.pkg;

                        import org.junit.runner.RunWith;
                        import org.junit.runners.Parameterized;
                        import android.platform.test.microbenchmark.Microbenchmark.NoMetricAfter;

                        @RunWith(Parameterized.class)
                        class TestClass {
                            @NoMetricAfter
                            void tearDown() {}
                        }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(NoMetricInParameterizedDetector.ISSUE_AFTER)
            .run()
            .expect(
                """
src/test/pkg/TestClass.java:9: Error: @NoMetricAfter can't be used with Parameterized test runner [NoMetricAfterWithParameterized]
    @NoMetricAfter
    ~~~~~~~~~~~~~~
1 errors, 0 warnings
                """
            )
    }

    companion object {
        val runWithStub: TestFile =
            java(
                """
        package org.junit.runner;

        public @interface RunWith {
            Class<T> value();
        }
        """
            )

        val microbenchmarkStub: TestFile =
            java(
                """
        package android.platform.test.microbenchmark;

        public class Microbenchmark {
            public @interface NoMetricBefore {}
            public @interface NoMetricAfter {}
        }
        """
            )

        val functionalStub: TestFile =
            java(
                """
            package android.platform.test.microbenchmark;
            public class Functional {}
"""
            )

        val parameterizedStub: TestFile =
            java(
                """
            package org.junit.runners;
            public class Parameterized {}
"""
            )
    }

    private val stubs = arrayOf(runWithStub, microbenchmarkStub, functionalStub, parameterizedStub)
}
