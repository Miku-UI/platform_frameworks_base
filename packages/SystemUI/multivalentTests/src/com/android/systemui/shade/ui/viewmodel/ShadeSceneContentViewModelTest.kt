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

package com.android.systemui.shade.ui.viewmodel

import android.app.StatusBarManager.DISABLE2_QUICK_SETTINGS
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.systemui.Flags.FLAG_DUAL_SHADE
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.Pin
import com.android.systemui.common.ui.data.repository.fakeConfigurationRepository
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.ui.transitions.blurConfig
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useStandardTestDispatcher
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.remedia.data.repository.mediaPipelineRepository
import com.android.systemui.qs.panels.domain.interactor.tileSquishinessInteractor
import com.android.systemui.res.R
import com.android.systemui.scene.SceneHelper.setDeviceEntered
import com.android.systemui.scene.data.repository.Transition
import com.android.systemui.scene.data.repository.setSceneTransition
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.domain.interactor.enableSplitShade
import com.android.systemui.shade.domain.interactor.shadeInteractor
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.statusbar.disableflags.data.repository.fakeDisableFlagsRepository
import com.android.systemui.testKosmos
import com.android.systemui.unfold.fakeUnfoldTransitionProgressProvider
import com.android.systemui.window.data.repository.fakeWindowRootViewBlurRepository
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
@EnableSceneContainer
class ShadeSceneContentViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.underTest: ShadeSceneContentViewModel by
        Kosmos.Fixture { shadeSceneContentViewModel }

    @Before
    fun setUp() {
        with(kosmos) {
            underTest.activateIn(testScope)
            testScope.backgroundScope.launch { underTest.detectShadeModeChanges() }
        }
    }

    @Test
    fun isEmptySpaceClickable_deviceUnlocked_false() =
        kosmos.runTest {
            enableSingleShade()
            fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            setDeviceEntered(true)

            assertThat(underTest.isEmptySpaceClickable(sceneInteractor.transitionState)).isFalse()
        }

    @Test
    fun isEmptySpaceClickable_deviceLockedSecurely_true() =
        kosmos.runTest {
            enableSingleShade()
            fakeAuthenticationRepository.setAuthenticationMethod(Pin)

            assertThat(underTest.isEmptySpaceClickable(sceneInteractor.transitionState)).isTrue()
        }

    @Test
    fun onEmptySpaceClicked_deviceLockedSecurely_switchesToLockscreen() =
        kosmos.runTest {
            enableSingleShade()
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            fakeAuthenticationRepository.setAuthenticationMethod(Pin)

            underTest.onEmptySpaceClicked(sceneInteractor.transitionState)

            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
        }

    @Test
    fun isEmptySpaceClickable_deviceLocked_transitioningToQs_false() =
        kosmos.runTest {
            enableSingleShade()
            fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            setDeviceEntered(false)
            runCurrent()
            assertThat(underTest.isEmptySpaceClickable(sceneInteractor.transitionState)).isTrue()

            setSceneTransition(Transition(Scenes.Shade, Scenes.QuickSettings))
            assertThat(underTest.isEmptySpaceClickable(sceneInteractor.transitionState)).isFalse()
        }

    @Test
    fun addAndRemoveMedia_mediaVisibilityIsUpdated() =
        kosmos.runTest {
            enableSingleShade()
            val userMedia = MediaData(active = true)

            assertThat(underTest.showMedia).isFalse()

            mediaPipelineRepository.addCurrentUserMediaEntry(userMedia)

            assertThat(underTest.showMedia).isTrue()

            mediaPipelineRepository.removeCurrentUserMediaEntry(userMedia.instanceId)

            assertThat(underTest.showMedia).isFalse()
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun shadeMode_dualShadeFlagOff() =
        kosmos.runTest {
            enableSplitShade()
            assertThat(underTest.shadeMode).isEqualTo(ShadeMode.Split)

            enableSingleShade()
            assertThat(underTest.shadeMode).isEqualTo(ShadeMode.Single)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun shadeMode_dualShadeFlagOn() =
        kosmos.runTest {
            enableDualShade()
            assertThat(underTest.shadeMode).isEqualTo(ShadeMode.Dual)

            enableSingleShade()
            assertThat(underTest.shadeMode).isEqualTo(ShadeMode.Single)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun shadeModeChange_dualOnLockscreen_switchToOverlay() =
        kosmos.runTest {
            setDeviceEntered(false)
            val scene by collectLastValue(sceneInteractor.currentScene)
            val overlays by collectLastValue(sceneInteractor.currentOverlays)

            enableSingleShade()

            shadeInteractor.expandNotificationsShade("test")
            assertThat(scene).isEqualTo(Scenes.Shade)
            assertThat(overlays).isEmpty()

            enableDualShade()

            assertThat(scene).isEqualTo(Scenes.Lockscreen)
            assertThat(overlays).containsExactly(Overlays.NotificationsShade)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun shadeModeChange_dualOnGone_switchToOverlay() =
        kosmos.runTest {
            setDeviceEntered(true)
            val scene by collectLastValue(sceneInteractor.currentScene)
            val overlays by collectLastValue(sceneInteractor.currentOverlays)

            enableSingleShade()

            shadeInteractor.expandNotificationsShade("test")
            assertThat(scene).isEqualTo(Scenes.Shade)
            assertThat(overlays).isEmpty()

            enableDualShade()

            assertThat(scene).isEqualTo(Scenes.Gone)
            assertThat(overlays).containsExactly(Overlays.NotificationsShade)
        }

    @Test
    fun unfoldTransitionProgress() =
        testKosmos().useStandardTestDispatcher().runTest {
            // Set up and activate a new `underTest` which uses the StandardTestDispatcher.
            val underTest = shadeSceneContentViewModel
            underTest.activateIn(testScope)

            enableSingleShade()
            runCurrent()
            val maxTranslation = prepareConfiguration()

            val unfoldProvider = fakeUnfoldTransitionProgressProvider
            unfoldProvider.onTransitionStarted()
            assertThat(underTest.unfoldTranslationXForStartSide).isEqualTo(0f)

            repeat(10) { repetition ->
                val transitionProgress = 0.1f * (repetition + 1)
                unfoldProvider.onTransitionProgress(transitionProgress)
                runCurrent()

                assertThat(underTest.unfoldTranslationXForStartSide)
                    .isEqualTo((1 - transitionProgress) * maxTranslation)
            }

            unfoldProvider.onTransitionFinishing()
            runCurrent()
            assertThat(underTest.unfoldTranslationXForStartSide).isEqualTo(0f)

            unfoldProvider.onTransitionFinished()
            runCurrent()
            assertThat(underTest.unfoldTranslationXForStartSide).isEqualTo(0f)
        }

    @Test
    fun disable2QuickSettings_isQsEnabledIsFalse() =
        kosmos.runTest {
            assertThat(underTest.isQsEnabled).isTrue()

            fakeDisableFlagsRepository.disableFlags.update {
                it.copy(disable2 = DISABLE2_QUICK_SETTINGS)
            }

            assertThat(underTest.isQsEnabled).isFalse()
        }

    @Test
    fun squishiness() =
        kosmos.runTest {
            val squishiness by collectLastValue(tileSquishinessInteractor.squishiness)

            underTest.setTileSquishiness(0f)
            assertThat(squishiness).isWithin(0.0001f).of(0.1f)

            underTest.setTileSquishiness(1f)
            assertThat(squishiness).isEqualTo(1f)
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun isBlurred_whenBouncerOverlayShowingOverShadeAndBlurSupported_isTrue() =
        kosmos.runTest {
            assertThat(
                    underTest.calculateBlur(
                        transitionState =
                            TransitionState.Idle(
                                currentScene = Scenes.Lockscreen,
                                currentOverlays = setOf(Overlays.Bouncer),
                            )
                    )
                )
                .isEqualTo(0f)

            kosmos.fakeWindowRootViewBlurRepository.isBlurSupported.value = true
            kosmos.runCurrent()
            assertThat(
                    underTest.calculateBlur(
                        transitionState =
                            TransitionState.Idle(
                                currentScene = Scenes.Lockscreen,
                                currentOverlays = setOf(Overlays.Bouncer),
                            )
                    )
                )
                .isEqualTo(0f)

            assertThat(
                    underTest.calculateBlur(
                        transitionState =
                            TransitionState.Idle(
                                currentScene = Scenes.Shade,
                                currentOverlays = setOf(Overlays.Bouncer),
                            )
                    )
                )
                .isEqualTo(kosmos.blurConfig.maxBlurRadiusPx)

            assertThat(
                    underTest.calculateBlur(
                        transitionState = TransitionState.Idle(currentScene = Scenes.Shade)
                    )
                )
                .isEqualTo(0)
        }

    private fun Kosmos.prepareConfiguration(): Int {
        val configuration = context.resources.configuration
        configuration.setLayoutDirection(Locale.US)
        fakeConfigurationRepository.onConfigurationChange(configuration)
        val maxTranslation = 10
        fakeConfigurationRepository.setDimensionPixelSize(
            R.dimen.notification_side_paddings_single,
            maxTranslation,
        )
        return maxTranslation
    }
}
