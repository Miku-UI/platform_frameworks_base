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

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test

class ExposeFlowFromUiLayerDetectorTest : SystemUILintDetectorTest() {

    override fun getDetector(): Detector {
        return ExposeFlowFromUiLayerDetector()
    }

    override fun getIssues(): List<Issue> {
        return listOf(ExposeFlowFromUiLayerDetector.ISSUE)
    }

    @Test
    fun publicFlow_expectViolation() {
        lint()
            .files(
                *androidStubs,
                TestFiles.kotlin(
                        """
                    package com.android.systemui.blah.ui.viewmodel

                    import kotlinx.coroutines.flow.Flow
                    import kotlinx.coroutines.flow.flow

                    class BlahViewModel {
                        val flow: Flow<Boolean> = flow { emit(true) }
                    }
                    """
                    )
                    .indented(),
            )
            .issues(ExposeFlowFromUiLayerDetector.ISSUE)
            .run()
            .expect(
                expectedText =
                    """
                    src/com/android/systemui/blah/ui/viewmodel/BlahViewModel.kt:7: Warning: Public property or method should not be a Flow. [FlowExposedFromViewModel]
                        val flow: Flow<Boolean> = flow { emit(true) }
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                    0 errors, 1 warnings
                """
            )
    }

    @Test
    fun privateFlow_noViolations() {
        lint()
            .files(
                *androidStubs,
                TestFiles.kotlin(
                        """
                    package com.android.systemui.blah.ui.viewmodel

                    import kotlinx.coroutines.flow.Flow
                    import kotlinx.coroutines.flow.flow

                    class BlahViewModel {
                        private val flow: Flow<Boolean> = flow { emit(true) }
                    }
                    """
                    )
                    .indented(),
            )
            .issues(ExposeFlowFromUiLayerDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun publicStateFlow_expectViolation() {
        lint()
            .files(
                *androidStubs,
                TestFiles.kotlin(
                        """
                    package com.android.systemui.blah.ui.viewmodel

                    import kotlinx.coroutines.flow.StateFlow
                    import kotlinx.coroutines.flow.MutableStateFlow

                    class BlahViewModel {
                        val flow: StateFlow<Boolean> = MutableStateFlow(true)
                    }
                    """
                    )
                    .indented(),
            )
            .issues(ExposeFlowFromUiLayerDetector.ISSUE)
            .run()
            .expect(
                expectedText =
                    """
                    src/com/android/systemui/blah/ui/viewmodel/BlahViewModel.kt:7: Warning: Public property or method should not be a Flow. [FlowExposedFromViewModel]
                        val flow: StateFlow<Boolean> = MutableStateFlow(true)
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                    0 errors, 1 warnings
                """
            )
    }

    @Test
    fun privateStateFlow_noViolations() {
        lint()
            .files(
                *androidStubs,
                TestFiles.kotlin(
                        """
                    package com.android.systemui.blah.ui.viewmodel

                    import kotlinx.coroutines.flow.StateFlow
                    import kotlinx.coroutines.flow.MutableStateFlow

                    class BlahViewModel {
                        private val flow: StateFlow<Boolean> = MutableStateFlow(true)
                    }
                    """
                    )
                    .indented(),
            )
            .issues(ExposeFlowFromUiLayerDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun publicMethodThatReturnsFlow_expectViolation() {
        lint()
            .files(
                *androidStubs,
                TestFiles.kotlin(
                        """
                    package com.android.systemui.blah.ui.viewmodel

                    import kotlinx.coroutines.flow.Flow
                    import kotlinx.coroutines.flow.flow

                    class BlahViewModel {
                        fun exposes(): Flow<Int> {
                            return flow { emit(1) }
                        }
                    }
                    """
                    )
                    .indented(),
            )
            .issues(ExposeFlowFromUiLayerDetector.ISSUE)
            // TODO: b/443947014 - Remove allowDuplicates() once the bug is fixed.
            .allowDuplicates()
            .run()
            .expect(
                expectedText =
                    """
                    src/com/android/systemui/blah/ui/viewmodel/BlahViewModel.kt:7: Warning: Public property or method should not be a Flow. [FlowExposedFromViewModel]
                        fun exposes(): Flow<Int> {
                            ~~~~~~~
                    0 errors, 1 warnings
                """
            )
    }

    @Test
    fun privateMethodThatReturnsFlow_noViolations() {
        lint()
            .files(
                *androidStubs,
                TestFiles.kotlin(
                        """
                    package com.android.systemui.blah.ui.viewmodel

                    import kotlinx.coroutines.flow.Flow
                    import kotlinx.coroutines.flow.flow

                    class BlahViewModel {
                        private fun doesNotExpose(): Flow<Int> {
                            return flow { emit(1) }
                        }
                    }
                    """
                    )
                    .indented(),
            )
            .issues(ExposeFlowFromUiLayerDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun publicMethodThatReturnsStateFlow_expectViolation() {
        lint()
            .files(
                *androidStubs,
                TestFiles.kotlin(
                        """
                    package com.android.systemui.blah.ui.viewmodel

                    import kotlinx.coroutines.flow.StateFlow
                    import kotlinx.coroutines.flow.MutableStateFlow

                    class BlahViewModel {
                        fun exposes(): StateFlow<Int> {
                            return MutableStateFlow(1)
                        }
                    }
                    """
                    )
                    .indented(),
            )
            .issues(ExposeFlowFromUiLayerDetector.ISSUE)
            // TODO: b/443947014 - Remove allowDuplicates() once the bug is fixed.
            .allowDuplicates()
            .run()
            .expect(
                expectedText =
                    """
                    src/com/android/systemui/blah/ui/viewmodel/BlahViewModel.kt:7: Warning: Public property or method should not be a Flow. [FlowExposedFromViewModel]
                        fun exposes(): StateFlow<Int> {
                            ~~~~~~~
                    0 errors, 1 warnings
                """
            )
    }

    @Test
    fun privateMethodThatReturnsStateFlow_noViolations() {
        lint()
            .files(
                *androidStubs,
                TestFiles.kotlin(
                        """
                    package com.android.systemui.blah.ui.viewmodel

                    import kotlinx.coroutines.flow.StateFlow
                    import kotlinx.coroutines.flow.MutableStateFlow

                    class BlahViewModel {
                        private fun doesNotExpose(): StateFlow<Int> {
                            return MutableStateFlow(1)
                        }
                    }
                    """
                    )
                    .indented(),
            )
            .issues(ExposeFlowFromUiLayerDetector.ISSUE)
            .run()
            .expectClean()
    }
}
