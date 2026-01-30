/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.systemui.flags

import android.platform.test.flag.junit.FlagsParameterization
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.model.Scenes
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

/**
 * Be careful with the {FeatureFlagsReleaseRestarter} in this test. It has a call to System.exit()!
 */
@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class NotOccludedConditionTest(flags: FlagsParameterization) : SysuiTestCase() {
    private lateinit var condition: NotOccludedCondition

    @Mock private lateinit var keyguardTransitionInteractor: KeyguardTransitionInteractor
    @Mock private lateinit var sceneInteractor: SceneInteractor
    private val transitionValue = MutableStateFlow(0f)
    private val currentScene = MutableStateFlow(Scenes.Gone)

    private val testDispatcher: TestDispatcher = StandardTestDispatcher()
    private val testScope: TestScope = TestScope(testDispatcher)

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf().andSceneContainer()
        }
    }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        whenever(keyguardTransitionInteractor.transitionValue(KeyguardState.OCCLUDED))
            .thenReturn(transitionValue)
        whenever(sceneInteractor.currentScene).thenReturn(currentScene)
        condition = NotOccludedCondition({ keyguardTransitionInteractor }, { sceneInteractor })
        testScope.runCurrent()
    }

    @Test
    fun testCondition_occluded() =
        testScope.runTest {
            val canRestart by collectLastValue(condition.canRestartNow)

            currentScene.emit(Scenes.Occluded)
            transitionValue.emit(1f)

            assertThat(canRestart).isFalse()
        }

    @Test
    fun testCondition_notOccluded() =
        testScope.runTest {
            val canRestart by collectLastValue(condition.canRestartNow)

            currentScene.emit(Scenes.Lockscreen)
            transitionValue.emit(0f)

            assertThat(canRestart).isTrue()
        }

    @Test
    fun testCondition_invokesRetry() =
        testScope.runTest {
            val canRestart by collectLastValue(condition.canRestartNow)

            currentScene.emit(Scenes.Occluded)
            transitionValue.emit(1f)

            assertThat(canRestart).isFalse()

            currentScene.emit(Scenes.Gone)
            transitionValue.emit(0f)

            assertThat(canRestart).isTrue()
        }
}
