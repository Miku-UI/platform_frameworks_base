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
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test

class DisplayAwareDetectorTest : SystemUILintDetectorTest() {

    override fun getDetector(): Detector = DisplayAwareDetector()

    override fun getIssues(): List<Issue> =
        listOf(DisplayAwareDetector.ERROR_ISSUE, DisplayAwareDetector.INFO_ISSUE)

    private val displaySubcomponentAnnotations: TestFile =
        kotlin(
                """
            package com.android.systemui.display.dagger

            interface SystemUIDisplaySubcomponent {
                annotation class PerDisplaySingleton
                annotation class DisplayAware
                annotation class DisplayAwareStatusBar
                annotation class DisplayId
            }
            """
            )
            .indented()

    private val coroutineScopeStub: TestFile =
        kotlin(
                """
            package kotlinx.coroutines

            interface CoroutineScope
            """
            )
            .indented()

    private val configurationControllerStub: TestFile =
        kotlin(
                """
            package com.android.systemui.statusbar.policy

            class ConfigurationController
            """
            )
            .indented()

    private val injectAnnotation: TestFile =
        kotlin(
                """
            package javax.inject

            annotation class Inject
            """
            )
            .indented()

    private val assistedInjectAnnotation: TestFile =
        kotlin(
                """
            package dagger.assisted

            annotation class AssistedInject
            """
            )
            .indented()

    private val assistedAnnotation: TestFile =
        kotlin(
                """
            package dagger.assisted

            annotation class Assisted
            """
            )
            .indented()

    private val configStateStub: TestFile =
        kotlin(
                """
            package com.android.systemui.common.ui

            class ConfigurationState
            """
            )
            .indented()

    private val configInteractorStub: TestFile =
        kotlin(
                """
            package com.android.systemui.common.ui.domain.interactor

            class ConfigurationInteractor
            """
            )
            .indented()

    private val applicationStub: TestFile =
        kotlin(
                """
                package com.android.systemui.dagger.qualifiers

                annotation class Application
                """
            )
            .indented()

    private val legacyDisplayIdStub: TestFile =
        kotlin(
                """
                package com.android.systemui.dagger.qualifiers

                annotation class DisplayId
                """
            )
            .indented()

    private val allStubs =
        arrayOf(
            displaySubcomponentAnnotations,
            coroutineScopeStub,
            configurationControllerStub,
            configStateStub,
            configInteractorStub,
            injectAnnotation,
            applicationStub,
            assistedAnnotation,
            assistedInjectAnnotation,
            legacyDisplayIdStub,
            *androidStubs,
        )

    @Test
    fun perDisplaySingleton_contextInjectionWithoutDisplayAware_fails() {
        lint()
            .files(
                kotlin(
                    """
                    package com.android.systemui.foo

                    import android.content.Context
                    import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.PerDisplaySingleton
                    import javax.inject.Inject

                    @PerDisplaySingleton
                    class MyClass @Inject constructor(private val context: Context, )
                    """
                        .trimIndent()
                ),
                *allStubs,
            )
            .issues(DisplayAwareDetector.ERROR_ISSUE, DisplayAwareDetector.INFO_ISSUE)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectErrorCount(1)
            .expectContains(
                "MyClass.kt:8: Error: Per-display classes should inject a qualified version of Context"
            )
    }

    @Test
    fun perDisplaySingleton_displayIdInjectionWithoutDisplayAware_fails() {
        lint()
            .files(
                kotlin(
                    """
                    package com.android.systemui.foo

                    import android.content.Context
                    import com.android.systemui.dagger.qualifiers.DisplayId
                    import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.PerDisplaySingleton
                    import javax.inject.Inject

                    @PerDisplaySingleton
                    class MyClass @Inject constructor(@DisplayId private val displayId: Int)
                    """
                        .trimIndent()
                ),
                *allStubs,
            )
            .issues(DisplayAwareDetector.ERROR_ISSUE, DisplayAwareDetector.INFO_ISSUE)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectErrorCount(1)
            .expectContains(
                "MyClass.kt:9: Error: Per-display classes should inject a qualified version of int"
            )
    }

    @Test
    fun perDisplaySingleton_injectionWithWrongContextQualifier_fails() {
        lint()
            .files(
                kotlin(
                    """
                    package com.android.systemui.foo

                    import android.content.Context
                    import com.android.systemui.dagger.qualifiers.Application
                    import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.PerDisplaySingleton
                    import javax.inject.Inject

                    @PerDisplaySingleton
                    class MyClass @Inject constructor(
                        @Application private val context: Context, 
                    )
                    """
                ),
                *allStubs,
            )
            .issues(DisplayAwareDetector.ERROR_ISSUE, DisplayAwareDetector.INFO_ISSUE)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectErrorCount(1)
            .expectContains(
                "MyClass.kt:11: Error: Per-display classes should inject a qualified version of Context"
            )
    }

    @Test
    fun perDisplaySingleton_injectionWithoutDisplayAware_multipleParameters_failsWithMultipleErrors() {
        lint()
            .files(
                kotlin(
                    """
                    package com.android.systemui.foo

                    import android.content.Context
                    import android.content.res.Resources
                    import android.view.LayoutInflater
                    import android.view.WindowManager
                    import com.android.systemui.dagger.qualifiers.Application
                    import com.android.systemui.common.ui.ConfigurationState
                    import com.android.systemui.common.ui.domain.interactor.ConfigurationInteractor
                    import com.android.systemui.statusbar.policy.ConfigurationController
                    import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.PerDisplaySingleton
                    import javax.inject.Inject
                    import kotlinx.coroutines.CoroutineScope

                    @PerDisplaySingleton
                    class MyClass @Inject constructor(
                        private val context: Context,
                        private val inflater: LayoutInflater,
                        private val windowManager: WindowManager,
                        private val configState: ConfigurationState,
                        private val configController: ConfigurationController,
                        private val configInteractor: ConfigurationInteractor,
                        private val resources: Resources,
                        private val coroutineScope: CoroutineScope,
                        @Application private val context: Context,
                    )
                    """
                        .trimIndent()
                ),
                *allStubs,
            )
            .issues(DisplayAwareDetector.ERROR_ISSUE, DisplayAwareDetector.INFO_ISSUE)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectErrorCount(8)
            .expectContains(
                "MyClass.kt:17: Error: Per-display classes should inject a qualified version of Context"
            )
            .expectContains(
                "MyClass.kt:18: Error: Per-display classes should inject a qualified version of LayoutInflater"
            )
            .expectContains(
                "MyClass.kt:19: Error: Per-display classes should inject a qualified version of WindowManager"
            )
            .expectContains(
                "MyClass.kt:20: Error: Per-display classes should inject a qualified version of ConfigurationState"
            )
            .expectContains(
                "MyClass.kt:21: Error: Per-display classes should inject a qualified version of ConfigurationController"
            )
            .expectContains(
                "MyClass.kt:22: Error: Per-display classes should inject a qualified version of ConfigurationInteractor"
            )
            .expectContains(
                "MyClass.kt:23: Error: Per-display classes should inject a qualified version of Resources"
            )
            .expectContains(
                "MyClass.kt:24: Hint: Per-display classes should inject a qualified version of CoroutineScope"
            )
            .expectContains(
                "MyClass.kt:25: Error: Per-display classes should inject a qualified version of Context"
            )
    }

    @Test
    fun perDisplaySingleton_contextInjectionWithDisplayAware_noErrors() {
        lint()
            .files(
                *allStubs,
                kotlin(
                        """
                    package com.android.systemui.foo

                    import android.content.Context
                    import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware
                    import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.PerDisplaySingleton
                    import javax.inject.Inject

                    @PerDisplaySingleton
                    class MyClass @Inject constructor(@DisplayAware private val context: Context)
                    """
                    )
                    .indented(),
            )
            .issues(DisplayAwareDetector.ERROR_ISSUE, DisplayAwareDetector.INFO_ISSUE)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    @Test
    fun perDisplaySingleton_contextInjectionWithDisplayAwareStatusBar_noErrors() {
        lint()
            .files(
                *allStubs,
                kotlin(
                    """
                    package com.android.systemui.foo

                    import android.content.Context
                    import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAwareStatusBar
                    import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.PerDisplaySingleton
                    import javax.inject.Inject

                    @PerDisplaySingleton
                    class MyClass @Inject constructor(@DisplayAwareStatusBar private val context: Context)

                    """
                        .trimIndent()
                ),
            )
            .issues(DisplayAwareDetector.ERROR_ISSUE, DisplayAwareDetector.INFO_ISSUE)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    @Test
    fun perDisplaySingleton_injectionOfOtherType_noErrors() {
        lint()
            .files(
                *allStubs,
                kotlin(
                        """
                    package com.android.systemui.foo

                    import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.PerDisplaySingleton
                    import javax.inject.Inject

                    class OtherType
                    @PerDisplaySingleton
                    class MyClass @Inject constructor(private val other: OtherType)
                    """
                    )
                    .indented(),
            )
            .issues(DisplayAwareDetector.ERROR_ISSUE, DisplayAwareDetector.INFO_ISSUE)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    @Test
    fun nonPerDisplaySingleton_injectsNonDisplayAwareContext_noErrors() {
        lint()
            .files(
                *allStubs,
                kotlin(
                    """
                    package com.android.systemui.foo

                    import android.content.Context
                    import javax.inject.Inject

                    class MyClass @Inject constructor(private val context: Context)
                    """
                        .trimIndent()
                ),
            )
            .issues(DisplayAwareDetector.ERROR_ISSUE, DisplayAwareDetector.INFO_ISSUE)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    @Test
    fun nonPerDisplaySingleton_atLeastOneDisplayAwareParam_allParamsShouldBeDisplayAware() {
        lint()
            .files(
                *allStubs,
                kotlin(
                    """
                    package com.android.systemui.foo

                    import android.content.Context
                    import android.view.WindowManager
                    import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware
                    import javax.inject.Inject

                    class MyClass @Inject constructor(
                        @DisplayAware private val context: Context, 
                        private val windowManager: WindowManager,
                    )
                    """
                        .trimIndent()
                ),
            )
            .issues(DisplayAwareDetector.ERROR_ISSUE, DisplayAwareDetector.INFO_ISSUE)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectErrorCount(1)
            .expectContains(
                "MyClass.kt:10: Error: Per-display classes should inject a qualified version of WindowManager"
            )
    }

    @Test
    fun assistedInjectClass_noDisplayAwareParam_noParamsNeedToBeDisplayAware() {
        lint()
            .files(
                *allStubs,
                kotlin(
                    """
                    package com.android.systemui.foo

                    import android.content.Context
                    import dagger.assisted.AssistedInject
                    import android.view.WindowManager
                    import javax.inject.Inject

                    class MyClass @AssistedInject constructor(
                        private val context: Context, 
                        private val windowManager: WindowManager,
                    )
                    """
                        .trimIndent()
                ),
            )
            .issues(DisplayAwareDetector.ERROR_ISSUE, DisplayAwareDetector.INFO_ISSUE)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    @Test
    fun assistedInjectClass_atLeastOneDisplayAwareParam_allParamsShouldBeDisplayAware() {
        lint()
            .files(
                *allStubs,
                kotlin(
                    """
                    package com.android.systemui.foo

                    import android.content.Context
                    import dagger.assisted.AssistedInject
                    import android.view.WindowManager
                    import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware
                    import javax.inject.Inject

                    class MyClass @AssistedInject constructor(
                        @DisplayAware private val context: Context, 
                        private val windowManager: WindowManager,
                    )
                    """
                        .trimIndent()
                ),
            )
            .issues(DisplayAwareDetector.ERROR_ISSUE, DisplayAwareDetector.INFO_ISSUE)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectErrorCount(1)
            .expectContains(
                "MyClass.kt:11: Error: Per-display classes should inject a qualified version of WindowManager"
            )
    }

    @Test
    fun assistedInjectClass_assistedParamsDoNotNeedToBeDisplayAware() {
        lint()
            .files(
                *allStubs,
                kotlin(
                    """
                    package com.android.systemui.foo

                    import android.content.Context
                    import dagger.assisted.Assisted
                    import dagger.assisted.AssistedInject
                    import android.view.WindowManager
                    import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware
                    import javax.inject.Inject

                    class MyClass @AssistedInject constructor(
                        @DisplayAware private val context: Context, 
                        @Assisted private val windowManager: WindowManager,
                    )
                    """
                        .trimIndent()
                ),
            )
            .issues(DisplayAwareDetector.ERROR_ISSUE, DisplayAwareDetector.INFO_ISSUE)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }
}
