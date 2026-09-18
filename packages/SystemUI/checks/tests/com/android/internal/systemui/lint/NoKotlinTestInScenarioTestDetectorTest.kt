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
import com.android.tools.lint.checks.infrastructure.TestMode
import org.junit.Test

class NoKotlinTestInScenarioTestDetectorTest : SystemUILintDetectorTest() {
    override fun getDetector() = NoKotlinTestInScenarioTestDetector()

    override fun getIssues() = listOf(NoKotlinTestInScenarioTestDetector.ISSUE)

    @Test
    fun usesKotlinTestImport_isSysUiScenario_error() {
        lint()
            .files(
                kotlin(
                    """
                        package android.platform.test.scenario.sysui.example

                        import kotlin.test.Test

                        class TestClass {
                            @Test
                            fun test() {}
                        }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(NoKotlinTestInScenarioTestDetector.ISSUE)
            .testModes(TestMode.IMPORT_ALIAS)
            .run()
            .expectContains(
                """
src/android/platform/test/scenario/sysui/example/TestClass.kt:3: Error: Do not use kotlin.test.Test [KotlinTestAnnotationUsage]
import kotlin.test.Test
~~~~~~~~~~~~~~~~~~~~~~~
                """
            )
    }

    @Test
    fun usesKotlinTestImport_notSysUiScenario_clean() {
        lint()
            .files(
                kotlin(
                    """
                        package some.other.test.example

                        import kotlin.test.Test

                        class TestClass {
                            @Test
                            fun test() {}
                        }
                    """
                        .trimIndent()
                ),
                *stubs,
            )
            .issues(NoKotlinTestInScenarioTestDetector.ISSUE)
            .testModes(TestMode.IMPORT_ALIAS)
            .run()
            .expectClean()
    }

    @Test
    fun usesJUnitTestImport_clean() {
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
            .issues(NoKotlinTestInScenarioTestDetector.ISSUE)
            .testModes(TestMode.IMPORT_ALIAS)
            .run()
            .expectClean()
    }

    companion object {
        private val kotlinTestStub: TestFile =
            kotlin(
                """
                package kotlin.test

                annotation class Test
                """
                    .trimIndent()
            )

        private val stubs =
            arrayOf(kotlinTestStub, IncludeRunWithAnnotationDetectorTest.jUnitTestStub)
    }
}
