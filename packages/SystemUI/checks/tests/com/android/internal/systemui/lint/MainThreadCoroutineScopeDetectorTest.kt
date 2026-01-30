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

class MainThreadCoroutineScopeDetectorTest : SystemUILintDetectorTest() {

    override fun getDetector(): Detector {
        return MainThreadCoroutineScopeDetector()
    }

    override fun getIssues(): List<Issue> {
        return listOf(MainThreadCoroutineScopeDetector.ISSUE)
    }

    @Test
    fun uiLayerClass_injectsApplicationScope_noViolations() {
        lint()
            .files(
                *DEPENDENCIES,
                TestFiles.kotlin(
                        """
                    package com.android.systemui.blah.ui.viewmodel

                    import kotlinx.coroutines.CoroutineScope
                    import com.android.systemui.dagger.qualifiers.Application

                    class BlahViewModel(
                        @Application scope: CoroutineScope,
                    )
                    """
                    )
                    .indented(),
            )
            .issues(MainThreadCoroutineScopeDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun uiLayerClass_injectsBackgroundScope_noViolations() {
        lint()
            .files(
                *DEPENDENCIES,
                TestFiles.kotlin(
                        """
                    package com.android.systemui.blah.ui.viewmodel

                    import kotlinx.coroutines.CoroutineScope
                    import com.android.systemui.dagger.qualifiers.Application

                    class BlahViewModel(
                        @Background scope: CoroutineScope,
                    )
                    """
                    )
                    .indented(),
            )
            .issues(MainThreadCoroutineScopeDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun domainLayerClass_injectsBackgroundScope_noViolations() {
        lint()
            .files(
                *DEPENDENCIES,
                TestFiles.kotlin(
                        """
                    package com.android.systemui.blah.domain.intereactor

                    import kotlinx.coroutines.CoroutineScope
                    import com.android.systemui.dagger.qualifiers.Background

                    class BlahInteractor(
                        @Background scope: CoroutineScope,
                    )
                    """
                    )
                    .indented(),
            )
            .issues(MainThreadCoroutineScopeDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun dataLayerClass_injectsBackgroundScope_noViolations() {
        lint()
            .files(
                *DEPENDENCIES,
                TestFiles.kotlin(
                        """
                    package com.android.systemui.blah.data.repository

                    import kotlinx.coroutines.CoroutineScope
                    import com.android.systemui.dagger.qualifiers.Background

                    class BlahRepository(
                        @Background scope: CoroutineScope,
                    )
                    """
                    )
                    .indented(),
            )
            .issues(MainThreadCoroutineScopeDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun domainLayerClass_injectsApplicationScope_hasViolation() {
        lint()
            .files(
                *DEPENDENCIES,
                TestFiles.kotlin(
                        """
                    package com.android.systemui.blah.domain.intereactor

                    import kotlinx.coroutines.CoroutineScope
                    import com.android.systemui.dagger.qualifiers.Application

                    class BlahInteractor(
                        @Application scope: CoroutineScope,
                    )
                    """
                    )
                    .indented(),
            )
            .issues(MainThreadCoroutineScopeDetector.ISSUE)
            // TODO: b/443947014 - Remove allowDuplicates() once the bug is fixed.
            .allowDuplicates()
            .run()
            .expect(
                expectedText =
                    """
                    src/com/android/systemui/blah/domain/intereactor/BlahInteractor.kt:7: Warning: Do not use @Application-qualified CoroutineScopes in domain and data layers. Use @Background instead. [WrongCoroutineScope]
                        @Application scope: CoroutineScope,
                        ~~~~~~~~~~~~
                    0 errors, 1 warnings
                """
            )
    }

    @Test
    fun dataLayerClass_injectsApplicationScope_hasViolation() {
        lint()
            .files(
                *DEPENDENCIES,
                TestFiles.kotlin(
                        """
                    package com.android.systemui.blah.data.repository

                    import kotlinx.coroutines.CoroutineScope
                    import com.android.systemui.dagger.qualifiers.Application

                    class BlahRepository(
                        @Application scope: CoroutineScope,
                    )
                    """
                    )
                    .indented(),
            )
            .issues(MainThreadCoroutineScopeDetector.ISSUE)
            // TODO: b/443947014 - Remove allowDuplicates() once the bug is fixed.
            .allowDuplicates()
            .run()
            .expect(
                expectedText =
                    """
                    src/com/android/systemui/blah/data/repository/BlahRepository.kt:7: Warning: Do not use @Application-qualified CoroutineScopes in domain and data layers. Use @Background instead. [WrongCoroutineScope]
                        @Application scope: CoroutineScope,
                        ~~~~~~~~~~~~
                    0 errors, 1 warnings
                """
            )
    }

    companion object {
        private val DEPENDENCIES =
            arrayOf(
                TestFiles.kotlin(
                        """
                    package kotlinx.coroutines

                    class CoroutineScope
                """
                    )
                    .indented(),
                TestFiles.kotlin(
                        """
                    package com.android.systemui.dagger.qualifiers

                    @Qualifier @MustBeDocumented @Retention(AnnotationRetention.RUNTIME) annotation class Application
                """
                    )
                    .indented(),
                TestFiles.kotlin(
                        """
                    package com.android.systemui.dagger.qualifiers

                    @Qualifier @MustBeDocumented @Retention(AnnotationRetention.RUNTIME) annotation class Background
                """
                    )
                    .indented(),
            )
    }
}
