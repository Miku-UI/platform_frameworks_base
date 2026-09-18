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
import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test

class NonSysUISingletonApplicationScopeDetectorTest : SystemUILintDetectorTest() {

    override fun getDetector(): Detector {
        return NonSysUISingletonApplicationScopeDetector()
    }

    override fun getIssues(): List<Issue> {
        return listOf(NonSysUISingletonApplicationScopeDetector.ISSUE)
    }

    @Test
    fun singletonClass_injectsApplicationScope_noViolations() {
        lint()
            .files(
                *DEPENDENCIES,
                TestFiles.kotlin(
                        """
                    package com.android.systemui.test

                    import kotlinx.coroutines.CoroutineScope
                    import com.android.systemui.dagger.qualifiers.Application
                    import com.android.systemui.dagger.SysUISingleton
                    import javax.inject.Inject

                    @SysUISingleton
                    class SingletonClass @Inject constructor(
                        @Application scope: CoroutineScope,
                    )
                    """
                    )
                    .indented(),
            )
            .issues(NonSysUISingletonApplicationScopeDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun nonSingletonClass_injectsApplicationScope_hasViolation() {
        lint()
            .files(
                *DEPENDENCIES,
                TestFiles.kotlin(
                        """
                    package com.android.systemui.test

                    import kotlinx.coroutines.CoroutineScope
                    import com.android.systemui.dagger.qualifiers.Application
                    import javax.inject.Inject

                    class LeakyClass @Inject constructor(
                        @Application scope: CoroutineScope,
                    )
                    """
                    )
                    .indented(),
            )
            .issues(NonSysUISingletonApplicationScopeDetector.ISSUE)
            .run()
            .expectContains(
                "Warning: Classes using @Application or @Background CoroutineScope should be " +
                    "annotated with @SysUISingleton. [NonSysUISingletonApplicationScope]"
            )
    }

    @Test
    fun nonSingletonClass_injectsBackgroundScope_hasViolation() {
        lint()
            .files(
                *DEPENDENCIES,
                TestFiles.kotlin(
                        """
                    package com.android.systemui.test

                    import kotlinx.coroutines.CoroutineScope
                    import com.android.systemui.dagger.qualifiers.Background
                    import javax.inject.Inject

                    class LeakyClass @Inject constructor(
                        @Background scope: CoroutineScope,
                    )
                    """
                    )
                    .indented(),
            )
            .issues(NonSysUISingletonApplicationScopeDetector.ISSUE)
            .run()
            .expectContains(
                "Warning: Classes using @Application or @Background CoroutineScope should be " +
                    "annotated with @SysUISingleton. [NonSysUISingletonApplicationScope]"
            )
    }

    @Test
    fun nonSingletonClass_injectsOtherScope_noViolations() {
        lint()
            .files(
                *DEPENDENCIES,
                TestFiles.kotlin(
                        """
                    package com.android.systemui.test

                    import kotlinx.coroutines.CoroutineScope
                    import javax.inject.Inject
                    import javax.inject.Qualifier

                    @Qualifier
                    annotation class OtherScope

                    class SafeClass @Inject constructor(
                        @OtherScope scope: CoroutineScope,
                    )
                    """
                    )
                    .indented(),
            )
            .issues(NonSysUISingletonApplicationScopeDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun nonSingletonClass_notInjectedConstructor_noViolations() {
        lint()
            .files(
                *DEPENDENCIES,
                TestFiles.kotlin(
                        """
                    package com.android.systemui.test

                    import kotlinx.coroutines.CoroutineScope
                    import com.android.systemui.dagger.qualifiers.Application

                    class RegularClass(
                        @Application scope: CoroutineScope,
                    )
                    """
                    )
                    .indented(),
            )
            .issues(NonSysUISingletonApplicationScopeDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun assistedInjectClass_injectsApplicationScope_hasViolation() {
        lint()
            .files(
                *DEPENDENCIES,
                TestFiles.kotlin(
                        """
                    package com.android.systemui.test

                    import kotlinx.coroutines.CoroutineScope
                    import com.android.systemui.dagger.qualifiers.Application
                    import dagger.assisted.AssistedInject
                    import dagger.assisted.Assisted

                    class LeakyAssistedClass @AssistedInject constructor(
                        @Application scope: CoroutineScope,
                        @Assisted assistedParam: Int
                    )
                    """
                    )
                    .indented(),
            )
            .issues(NonSysUISingletonApplicationScopeDetector.ISSUE)
            .run()
            .expectContains(
                "Warning: Classes using @Application or @Background CoroutineScope should be " +
                    "annotated with @SysUISingleton. [NonSysUISingletonApplicationScope]"
            )
    }

    @Test
    fun assistedInjectClass_assistedParamIsApplicationScope_noViolations() {
        lint()
            .files(
                *DEPENDENCIES,
                TestFiles.kotlin(
                        """
                    package com.android.systemui.test

                    import kotlinx.coroutines.CoroutineScope
                    import com.android.systemui.dagger.qualifiers.Application
                    import dagger.assisted.AssistedInject
                    import dagger.assisted.Assisted

                    // This is technically weird code (passing an app scope as an assisted param)
                    // but the linter should correctly ignore @Assisted params as they aren't injected from the graph.
                    class SafeAssistedClass @AssistedInject constructor(
                        @Assisted @Application scope: CoroutineScope
                    )
                    """
                    )
                    .indented(),
            )
            .issues(NonSysUISingletonApplicationScopeDetector.ISSUE)
            .run()
            .expectClean()
    }

    companion object {
        val backgroundQualifierStub: TestFile =
            TestFiles.kotlin(
                    """
                    package com.android.systemui.dagger.qualifiers

                    @javax.inject.Qualifier @shouldBeDocumented @Retention(AnnotationRetention.RUNTIME) annotation class Background
                """
                )
                .indented()

        val applicationQualifierStub: TestFile =
            TestFiles.kotlin(
                    """
                    package com.android.systemui.dagger.qualifiers

                    @javax.inject.Qualifier @shouldBeDocumented @Retention(AnnotationRetention.RUNTIME) annotation class Application
                """
                )
                .indented()

        val sysuiSingletonStub: TestFile =
            TestFiles.kotlin(
                    """
                    package com.android.systemui.dagger

                    @javax.inject.Scope @shouldBeDocumented @Retention(AnnotationRetention.RUNTIME) annotation class SysUISingleton
                """
                )
                .indented()

        val injectStub: TestFile =
            TestFiles.kotlin(
                    """
                    package javax.inject

                    annotation class Inject
                    annotation class Scope
                    annotation class Qualifier
                """
                )
                .indented()

        val assistedInjectStub: TestFile =
            TestFiles.kotlin(
                    """
                    package dagger.assisted

                    annotation class AssistedInject
                    annotation class Assisted
                """
                )
                .indented()

        private val DEPENDENCIES =
            arrayOf(
                TestFiles.kotlin(
                        """
                    package kotlinx.coroutines

                    interface CoroutineScope
                """
                    )
                    .indented(),
                applicationQualifierStub,
                backgroundQualifierStub,
                sysuiSingletonStub,
                injectStub,
                assistedInjectStub,
            )
    }
}
