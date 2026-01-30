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

class DebugFieldDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = DebugFieldDetector()

    override fun getIssues(): List<Issue> =
        listOf(DebugFieldDetector.ISSUE_DEBUG_TRUE, DebugFieldDetector.ISSUE_NON_FINAL_DEBUG)

    override fun lint(): TestLintTask = super.lint().allowMissingSdk(true)

    val expectedCleanJavaField =
        java(
                """
            package test.pkg;
            class TestClass {
                static final boolean DEBUG = false;
            }
        """
            )
            .indented()

    val expectedCleanKotlinField =
        kotlin(
                """
                package test.pkg
                class TestClass {
                    companion object {
                        const val DEBUG = false
                    }
                }
        """
            )
            .indented()

    fun testJavaStaticFinalDebugTrue() {
        lint()
            .files(
                java(
                        """
                package test.pkg;
                class TestClass {
                    static final boolean DEBUG = true;
                }
                """
                    )
                    .indented()
            )
            .run()
            .expectErrorCount(1)
            .checkFix(null, after = expectedCleanJavaField)
    }

    fun testJavaStaticFinalDebugTrueSuppressLint() {
        lint()
            .files(
                java(
                        """
                package test.pkg;
                class TestClass {
                    @SuppressWarnings("DebugTrue")
                    static final boolean DEBUG = true;
                }
                """
                    )
                    .indented()
            )
            .run()
            .expectClean()
    }

    fun testJavaStaticDebugTrue() {
        lint()
            .files(
                java(
                        """
                package test.pkg;
                class TestClass {
                    static boolean DEBUG = true;
                }
                """
                    )
                    .indented()
            )
            .run()
            .expectErrorCount(2)
            .checkFix(null, after = expectedCleanJavaField)
    }

    fun testJavaStaticFinalDebugFalse() {
        lint()
            .files(
                java(
                        """
                package test.pkg;
                class TestClass {
                    static final boolean DEBUG = false;
                }
                """
                    )
                    .indented()
            )
            .run()
            .expectClean()
    }

    fun testJavaStaticDebugFalse() {
        lint()
            .files(
                java(
                        """
                package test.pkg;
                class TestClass {
                    static boolean DEBUG = false;
                }
                """
                    )
                    .indented()
            )
            .run()
            .expectErrorCount(1)
            .checkFix(null, after = expectedCleanJavaField)
    }

    fun testJavaNonStaticFinalDebugTrue() {
        lint()
            .files(
                java(
                        """
                package test.pkg;
                class TestClass {
                    final boolean DEBUG = true;
                }
                """
                    )
                    .indented()
            )
            .run()
            .expectClean()
    }

    fun testKotlinConstDebugTrue() {
        lint()
            .files(
                kotlin(
                        """
                package test.pkg
                class TestClass {
                    companion object {
                        val DEBUG = true
                    }
                }
                """
                    )
                    .indented()
            )
            .run()
            .expectErrorCount(2)
            .checkFix(null, after = expectedCleanKotlinField)
    }

    fun testKotlinVarDebugFalse() {
        lint()
            .files(
                kotlin(
                        """
                package test.pkg
                class TestClass {
                    companion object {
                        var DEBUG = false
                    }
                }
                """
                    )
                    .indented()
            )
            .run()
            .expectErrorCount(1)
            .checkFix(null, after = expectedCleanKotlinField)
    }
}
