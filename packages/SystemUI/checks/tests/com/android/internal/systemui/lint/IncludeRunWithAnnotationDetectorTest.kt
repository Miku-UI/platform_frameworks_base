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
import org.junit.Test

class IncludeRunWithAnnotationDetectorTest : SystemUILintDetectorTest() {
    override fun getDetector(): Detector = IncludeRunWithAnnotationDetector()

    override fun getIssues() = listOf(IncludeRunWithAnnotationDetector.ISSUE)

    @Test
    fun hasTestAndRunWith_isSysUiScenario_clean() {
        lint()
            .files(
                kotlin(
                    """
                        package android.platform.test.scenario.sysui.example

                        import org.junit.runner.RunWith
                        import org.junit.Test
                        import android.platform.test.microbenchmark.Functional

                        @RunWith(Functional::class)
                        class TestClass {
                            @Test
                            fun test() {}
                        }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(IncludeRunWithAnnotationDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun hasTestAndRunWith_notSysUiScenario_clean() {
        lint()
            .files(
                kotlin(
                    """
                        package some.other.test.example

                        import org.junit.runner.RunWith
                        import org.junit.Test
                        import android.platform.test.microbenchmark.Functional

                        @RunWith(Functional::class)
                        class TestClass {
                            @Test
                            fun test() {}
                        }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(IncludeRunWithAnnotationDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun hasTestButNoRunWith_isSysUiScenario_error() {
        lint()
            .files(
                kotlin(
                    """
                        package android.platform.test.scenario.sysui.example

                        import org.junit.Test

                        class TestClass {
                            @Test
                            fun test() {}
                        }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(IncludeRunWithAnnotationDetector.ISSUE)
            .run()
            .expect(
                """
src/android/platform/test/scenario/sysui/example/TestClass.kt:6: Warning: @Test annotation included without @RunWith annotation [IncludeRunWithAnnotation]
    @Test
    ~~~~~
0 errors, 1 warnings
                """
            )
    }

    @Test
    fun hasTestButNoRunWith_notSysUiScenario_clean() {
        lint()
            .files(
                kotlin(
                    """
                        package some.other.test.example

                        import org.junit.Test

                        class TestClass {
                            @Test
                            fun test() {}
                        }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(IncludeRunWithAnnotationDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun noRunWithAndNoTest_isSysUiScenario_clean() {
        lint()
            .files(
                kotlin(
                    """
                        package android.platform.test.scenario.sysui.example

                        class TestClass {
                            fun helperMethod() {}
                        }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(IncludeRunWithAnnotationDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun noRunWithAndNoTest_notSysUiScenario_clean() {
        lint()
            .files(
                kotlin(
                    """
                        package android.platform.test.scenario.sysui.example

                        class TestClass {
                            fun helperMethod() {}
                        }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(IncludeRunWithAnnotationDetector.ISSUE)
            .run()
            .expectClean()
    }

    companion object {
        val jUnitTestStub: TestFile =
            kotlin(
                """
                package org.junit

                annotation class Test
                """
                    .trimIndent()
            )

        private val stubs =
            arrayOf(
                jUnitTestStub,
                NoMetricInParameterizedDetectorKotlinTest.runWithStub,
                NoMetricInParameterizedDetectorKotlinTest.functionalStub,
            )
    }
}
