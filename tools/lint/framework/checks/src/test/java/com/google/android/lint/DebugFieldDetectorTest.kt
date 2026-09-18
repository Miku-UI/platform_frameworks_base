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

package com.google.android.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DebugFieldDetectorTest(private val fieldName: String) : LintDetectorTest() {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<Array<Any>> {
            return listOf(arrayOf("DEBUG"), arrayOf("LOCAL_LOGV"), arrayOf("LOCAL_LOGD"))
        }
    }

    override fun getDetector(): Detector = DebugFieldDetector()

    override fun getIssues(): List<Issue> =
        listOf(DebugFieldDetector.ISSUE_DEBUG_TRUE, DebugFieldDetector.ISSUE_NON_FINAL_DEBUG)

    override fun lint(): TestLintTask = super.lint().allowMissingSdk(true)

    private fun expectedCleanJavaField(fieldName: String) =
        java(
                """
            package test.pkg;
            class TestClass {
                static final boolean $fieldName = false;
            }
        """
            )
            .indented()

    private fun expectedCleanKotlinField(fieldName: String) =
        kotlin(
                """
                package test.pkg
                class TestClass {
                    companion object {
                        const val $fieldName = false
                    }
                }
        """
            )
            .indented()

    @Test
    fun testJavaStaticFinalDebugTrue() {
        lint()
            .files(
                java(
                        """
                package test.pkg;
                class TestClass {
                    static final boolean $fieldName = true;
                }
                """
                    )
                    .indented()
            )
            .run()
            .expectErrorCount(1)
            .checkFix(null, after = expectedCleanJavaField(fieldName))
    }

    @Test
    fun testJavaStaticFinalDebugTrueSuppressLint() {
        lint()
            .files(
                java(
                        """
                package test.pkg;
                class TestClass {
                    @SuppressWarnings("DebugTrue")
                    static final boolean $fieldName = true;
                }
                """
                    )
                    .indented()
            )
            .run()
            .expectClean()
    }

    @Test
    fun testJavaStaticDebugTrue() {
        lint()
            .files(
                java(
                        """
                package test.pkg;
                class TestClass {
                    static boolean $fieldName = true;
                }
                """
                    )
                    .indented()
            )
            .run()
            .expectErrorCount(2)
            .checkFix(null, after = expectedCleanJavaField(fieldName))
    }

    @Test
    fun testJavaStaticFinalDebugFalse() {
        lint()
            .files(
                java(
                        """
                package test.pkg;
                class TestClass {
                    static final boolean $fieldName = false;
                }
                """
                    )
                    .indented()
            )
            .run()
            .expectClean()
    }

    @Test
    fun testJavaStaticDebugFalse() {
        lint()
            .files(
                java(
                        """
                package test.pkg;
                class TestClass {
                    static boolean $fieldName = false;
                }
                """
                    )
                    .indented()
            )
            .run()
            .expectErrorCount(1)
            .checkFix(null, after = expectedCleanJavaField(fieldName))
    }

    @Test
    fun testJavaNonStaticFinalDebugTrue() {
        lint()
            .files(
                java(
                        """
                package test.pkg;
                class TestClass {
                    final boolean $fieldName = true;
                }
                """
                    )
                    .indented()
            )
            .run()
            .expectClean()
    }

    @Test
    fun testKotlinConstDebugTrue() {
        lint()
            .files(
                kotlin(
                        """
                package test.pkg
                class TestClass {
                    companion object {
                        val $fieldName = true
                    }
                }
                """
                    )
                    .indented()
            )
            .run()
            .expectErrorCount(2)
            .checkFix(null, after = expectedCleanKotlinField(fieldName))
    }

    @Test
    fun testKotlinVarDebugFalse() {
        lint()
            .files(
                kotlin(
                        """
                package test.pkg
                class TestClass {
                    companion object {
                        var $fieldName = false
                    }
                }
                """
                    )
                    .indented()
            )
            .run()
            .expectErrorCount(1)
            .checkFix(null, after = expectedCleanKotlinField(fieldName))
    }
}
