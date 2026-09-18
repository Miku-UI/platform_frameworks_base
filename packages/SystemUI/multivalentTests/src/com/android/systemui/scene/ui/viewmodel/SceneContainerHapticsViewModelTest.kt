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

package com.android.systemui.scene.ui.viewmodel

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ContentKey
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.ObservableTransitionState.Transition.ShowOrHideOverlay
import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.haptics.msdl.fakeMSDLPlayer
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.sceneContainerHapticsViewModelFactory
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.disableDualShade
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.shadeInteractor
import com.android.systemui.testKosmos
import com.google.android.msdl.data.model.MSDLToken
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class SceneContainerHapticsViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val msdlPlayer = kosmos.fakeMSDLPlayer
    private val Kosmos.underTest: SceneContainerHapticsViewModel by
        Kosmos.Fixture { sceneContainerHapticsViewModelFactory.create() }

    @Before
    fun setup() {
        with(kosmos) { underTest.activateIn(testScope) }
    }

    @DisableFlags(Flags.FLAG_DUAL_SHADE)
    @Test
    fun onValidSceneTransition_playsMSDLShadePullHaptics() =
        kosmos.runTest {
            disableDualShade()
            // GIVEN a valid scene transition to play haptics
            val validTransition = createTransitionState(from = Scenes.Gone, to = Scenes.Shade)

            // WHEN the transition occurs
            sceneInteractor.setTransitionState(MutableStateFlow(validTransition))
            runCurrent()

            // THEN the expected token plays without interaction properties
            assertThat(msdlPlayer.latestTokenPlayed).isEqualTo(MSDLToken.SWIPE_THRESHOLD_INDICATOR)
            assertThat(msdlPlayer.latestPropertiesPlayed).isNull()
        }

    @DisableFlags(Flags.FLAG_DUAL_SHADE)
    @Test
    fun onInValidSceneTransition_doesNotPlayMSDLShadePullHaptics() =
        kosmos.runTest {
            disableDualShade()
            // GIVEN an invalid scene transition to play haptics
            val invalidTransition = createTransitionState(from = Scenes.Shade, to = Scenes.Gone)

            // WHEN the transition occurs
            sceneInteractor.setTransitionState(MutableStateFlow(invalidTransition))
            runCurrent()

            // THEN the no token plays with no interaction properties
            assertThat(msdlPlayer.latestTokenPlayed).isNull()
            assertThat(msdlPlayer.latestPropertiesPlayed).isNull()
        }

    @EnableFlags(Flags.FLAG_DUAL_SHADE)
    @Test
    fun onValidOverlayTransition_playsMSDLShadePullHaptics() =
        kosmos.runTest {
            enableDualShade()
            // GIVEN a valid scene transition to play haptics
            val validTransition =
                createTransitionState(from = Scenes.Gone, to = Overlays.NotificationsShade)

            // WHEN the transition occurs
            sceneInteractor.setTransitionState(MutableStateFlow(validTransition))
            runCurrent()

            // THEN the expected token plays without interaction properties
            assertThat(msdlPlayer.latestTokenPlayed).isEqualTo(MSDLToken.SWIPE_THRESHOLD_INDICATOR)
            assertThat(msdlPlayer.latestPropertiesPlayed).isNull()
        }

    @EnableFlags(Flags.FLAG_DUAL_SHADE)
    @Test
    fun onInValidOverlayTransition_doesNotPlayMSDLShadePullHaptics() =
        kosmos.runTest {
            enableDualShade()
            // GIVEN an invalid scene transition to play haptics
            val invalidTransition =
                createTransitionState(from = Scenes.QuickSettings, to = Scenes.Gone)

            // WHEN the transition occurs
            sceneInteractor.setTransitionState(MutableStateFlow(invalidTransition))
            runCurrent()

            // THEN the no token plays with no interaction properties
            assertThat(msdlPlayer.latestTokenPlayed).isNull()
            assertThat(msdlPlayer.latestPropertiesPlayed).isNull()
        }

    @DisableFlags(Flags.FLAG_DUAL_SHADE)
    @Test
    fun onRemoteUserInteraction_withValidSceneTransition_playsMSDLShadePullHaptics() =
        kosmos.runTest {
            disableDualShade()
            val isUserInteracting by collectLastValue(shadeInteractor.isUserInteracting)

            // GIVEN a valid scene transition to play haptics that initiated remotely
            val validTransition =
                createTransitionState(from = Scenes.Gone, to = Scenes.Shade, byUser = false)
            sceneInteractor.onRemoteUserInputStarted("remote input")

            // WHEN the transition occurs
            sceneInteractor.setTransitionState(MutableStateFlow(validTransition))
            runCurrent()
            assertThat(isUserInteracting).isTrue()

            // THEN the expected token plays without interaction properties
            assertThat(msdlPlayer.latestTokenPlayed).isEqualTo(MSDLToken.SWIPE_THRESHOLD_INDICATOR)
            assertThat(msdlPlayer.latestPropertiesPlayed).isNull()
        }

    @EnableFlags(Flags.FLAG_DUAL_SHADE)
    @Test
    fun onRemoteUserInteraction_withValidOverlayTransition_playsMSDLShadePullHaptics() =
        kosmos.runTest {
            enableDualShade()
            val isUserInteracting by collectLastValue(shadeInteractor.isUserInteracting)

            // GIVEN a valid scene transition to play haptics that initiated remotely
            val validTransition =
                createTransitionState(
                    from = Scenes.Gone,
                    to = Overlays.NotificationsShade,
                    byUser = false,
                )
            sceneInteractor.onRemoteUserInputStarted("remote input")

            // WHEN the transition occurs
            sceneInteractor.setTransitionState(MutableStateFlow(validTransition))
            runCurrent()
            assertThat(isUserInteracting).isTrue()

            // THEN the expected token plays without interaction properties
            assertThat(msdlPlayer.latestTokenPlayed).isEqualTo(MSDLToken.SWIPE_THRESHOLD_INDICATOR)
            assertThat(msdlPlayer.latestPropertiesPlayed).isNull()
        }

    private fun Kosmos.createTransitionState(
        from: SceneKey,
        to: ContentKey,
        byUser: Boolean = true,
    ) =
        when (to) {
            is SceneKey ->
                ObservableTransitionState.Transition(
                    fromScene = from,
                    toScene = to,
                    currentScene = flowOf(from),
                    progress = MutableStateFlow(0.2f),
                    isInitiatedByUserInput = byUser,
                    isUserInputOngoing = flowOf(true),
                )
            is OverlayKey ->
                ShowOrHideOverlay(
                    overlay = to,
                    fromContent = from,
                    toContent = to,
                    currentScene = from,
                    currentOverlays = sceneInteractor.currentOverlays,
                    progress = MutableStateFlow(0.2f),
                    isInitiatedByUserInput = byUser,
                    isUserInputOngoing = flowOf(true),
                    previewProgress = flowOf(0f),
                    isInPreviewStage = flowOf(false),
                )
        }
}
