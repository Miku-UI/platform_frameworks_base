/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.window.ui.viewmodel

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.communal.data.repository.communalSceneRepository
import com.android.systemui.communal.shared.model.CommunalScenes
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.kosmos.testScope
import com.android.systemui.testKosmos
import com.android.systemui.window.data.repository.fakeWindowRootViewBlurRepository
import com.android.systemui.window.data.repository.windowRootViewBlurRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(Flags.FLAG_BOUNCER_UI_REVAMP, Flags.FLAG_GLANCEABLE_HUB_BLURRED_BACKGROUND)
class WindowRootViewModelTest : SysuiTestCase() {
    val kosmos = testKosmos()
    val testScope = kosmos.testScope
    private val communalRepository by lazy { kosmos.communalSceneRepository }

    val underTest by lazy { kosmos.windowRootViewModel }

    @Test
    fun bouncerTransitionChangesWindowBlurRadius() =
        testScope.runTest {
            kosmos.fakeWindowRootViewBlurRepository.isBlurSupported.value = true
            val blurRadius by collectLastValue(underTest.blurRadius)
            val isSurfaceOpaque by collectLastValue(underTest.isSurfaceOpaque)
            runCurrent()

            kosmos.fakeBouncerTransitions.first().windowBlurRadius.value = 30.0f
            runCurrent()

            assertThat(blurRadius).isEqualTo(30)
            assertThat(isSurfaceOpaque).isEqualTo(false)
        }

    @Test
    fun blurRadiusDoesNotChangeWhenBlurIsNotSupported() =
        testScope.runTest {
            kosmos.fakeWindowRootViewBlurRepository.isBlurSupported.value = false
            val blurRadius by collectLastValue(underTest.blurRadius)
            runCurrent()

            kosmos.fakeBouncerTransitions.first().windowBlurRadius.value = 30.0f
            runCurrent()

            assertThat(blurRadius).isEqualTo(0f)

            kosmos.fakeGlanceableHubTransitions.first().windowBlurRadius.value = 50.0f
            runCurrent()

            assertThat(blurRadius).isEqualTo(0f)

            kosmos.windowRootViewBlurRepository.blurRequestedByShade.value = 60.0f
            runCurrent()

            assertThat(blurRadius).isEqualTo(0f)
        }

    @Test
    @DisableSceneContainer
    @EnableFlags(Flags.FLAG_GESTURE_BETWEEN_HUB_AND_LOCKSCREEN_MOTION)
    fun blurScale_changes_onZoomOutFromGlanceableHubFlagEnabled() =
        testScope.runTest {
            kosmos.fakeWindowRootViewBlurRepository.isBlurSupported.value = true
            val blurScale by collectLastValue(underTest.blurScale)

            // Communal scene is visible
            val transitionState: MutableStateFlow<ObservableTransitionState> =
                MutableStateFlow(ObservableTransitionState.Idle(CommunalScenes.Communal))
            communalRepository.setTransitionState(transitionState)

            kosmos.fakeGlanceableHubTransitions.first().zoomOut.value = 0.95f
            runCurrent()

            assertThat(blurScale).isEqualTo(0.95f)
        }

    @Test
    @DisableSceneContainer
    @EnableFlags(Flags.FLAG_GESTURE_BETWEEN_HUB_AND_LOCKSCREEN_MOTION)
    fun blurScale_reset_onExitCommunalScene() =
        testScope.runTest {
            kosmos.fakeWindowRootViewBlurRepository.isBlurSupported.value = true
            val blurScale by collectLastValue(underTest.blurScale)

            // Communal scene is visible
            val transitionState: MutableStateFlow<ObservableTransitionState> =
                MutableStateFlow(ObservableTransitionState.Idle(CommunalScenes.Communal))
            communalRepository.setTransitionState(transitionState)

            kosmos.fakeGlanceableHubTransitions.first().zoomOut.value = 0.95f
            runCurrent()

            assertThat(blurScale).isEqualTo(0.95f)

            // Fully exits communal scene
            transitionState.value = ObservableTransitionState.Idle(CommunalScenes.Blank)

            // Scale is reset
            assertThat(blurScale).isEqualTo(1f)
        }

    @Test
    @DisableSceneContainer
    @DisableFlags(Flags.FLAG_GESTURE_BETWEEN_HUB_AND_LOCKSCREEN_MOTION)
    fun blurScale_doesNotChange_onZoomOutFromGlanceableHubFlagDisabled() =
        testScope.runTest {
            kosmos.fakeWindowRootViewBlurRepository.isBlurSupported.value = true
            val blurScale by collectLastValue(underTest.blurScale)

            // Communal scene is visible
            val transitionState: MutableStateFlow<ObservableTransitionState> =
                MutableStateFlow(ObservableTransitionState.Idle(CommunalScenes.Communal))
            communalRepository.setTransitionState(transitionState)

            kosmos.fakeGlanceableHubTransitions.first().zoomOut.value = 0.95f
            runCurrent()

            // Scale is not changed
            assertThat(blurScale).isEqualTo(1f)
        }
}
