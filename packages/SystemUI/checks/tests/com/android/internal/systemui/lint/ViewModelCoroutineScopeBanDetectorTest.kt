/*
 * Copyright (C) 2026 The Android Open Source Project
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

/** Test [ViewModelCoroutineScopeBanDetector]. */
class ViewModelCoroutineScopeBanDetectorTest : SystemUILintDetectorTest() {

    override fun getDetector(): Detector = ViewModelCoroutineScopeBanDetector()

    override fun getIssues(): List<Issue> = listOf(ViewModelCoroutineScopeBanDetector.ISSUE)

    @Test
    fun viewModel_methodArgumentCoroutineScope_error() {
        lint()
            .files(
                coroutineScopeStub,
                kotlin(
                        """
                import kotlinx.coroutines.CoroutineScope

                class MyViewModel {
                    fun process(scope: CoroutineScope) {}
                }
                """
                    )
                    .indented(),
            )
            .run()
            .expect(
                """
            src/MyViewModel.kt:4: Warning: Do not use CoroutineScope in view-model classes. [ViewModelCoroutineScopeBan]
                fun process(scope: CoroutineScope) {}
                            ~~~~~
            0 errors, 1 warnings
            """
            )
    }

    @Test
    fun viewModel_methodReturnTypeCoroutineScope_error() {
        lint()
            .files(
                coroutineScopeStub,
                kotlin(
                        """
                import kotlinx.coroutines.CoroutineScope

                class MyViewModel {
                    fun getScope(): CoroutineScope? = null
                }
                """
                    )
                    .indented(),
            )
            .allowDuplicates()
            .run()
            .expect(
                """
                src/MyViewModel.kt:4: Warning: Do not return CoroutineScope from methods in view-model classes. [ViewModelCoroutineScopeBan]
                fun getScope(): CoroutineScope? = null
                    ~~~~~~~~
            0 errors, 1 warnings
            """
            )
    }

    @Test
    fun viewModel_methodReceiverTypeCoroutineScope_error() {
        lint()
            .files(
                coroutineScopeStub,
                kotlin(
                        """
                import kotlinx.coroutines.CoroutineScope

                class MyViewModel {
                    fun CoroutineScope.getId(): Int
                }
                """
                    )
                    .indented(),
            )
            .allowDuplicates()
            .run()
            .expect(
                """
                src/MyViewModel.kt:4: Warning: Do not use CoroutineScope in view-model classes. [ViewModelCoroutineScopeBan]
    fun CoroutineScope.getId(): Int
        ~~~~~~~~~~~~~~
0 errors, 1 warnings
            """
            )
    }

    @Test
    fun viewModel_clean() {
        lint()
            .files(
                kotlin(
                        """
                class MyViewModel {
                    fun validMethod(data: String) {}
                }
                """
                    )
                    .indented()
            )
            .run()
            .expectClean()
    }

    @Test
    fun nonViewModel_constructorParameterCoroutineScope_clean() {
        lint()
            .files(
                coroutineScopeStub,
                kotlin(
                        """
                import kotlinx.coroutines.CoroutineScope

                class RandomClass(val scope: CoroutineScope)
                """
                    )
                    .indented(),
            )
            .run()
            .expectClean()
    }

    @Test
    fun viewModel_constructorParameterCoroutineScope_error() {
        lint()
            .files(
                coroutineScopeStub,
                kotlin(
                        """
                import kotlinx.coroutines.CoroutineScope

                class MyViewModel (scope: CoroutineScope) {
                    init {
                        print(scope)
                    }
                }
                """
                    )
                    .indented(),
            )
            .run()
            .expect(
                """
                src/MyViewModel.kt:3: Warning: Do not use CoroutineScope in view-model classes. [ViewModelCoroutineScopeBan]
                class MyViewModel (scope: CoroutineScope) {
                                   ~~~~~
                0 errors, 1 warnings
                                """
                    .trimIndent()
            )
    }

    private val coroutineScopeStub: TestFile =
        kotlin(
                """
        package kotlinx.coroutines
        interface CoroutineScope
        """
            )
            .indented()
}
