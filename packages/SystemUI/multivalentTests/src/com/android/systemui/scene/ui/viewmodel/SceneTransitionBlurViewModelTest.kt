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

package com.android.systemui.scene.ui.viewmodel

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.deviceentry.data.repository.fakeDeviceEntryRepository
import com.android.systemui.deviceentry.domain.interactor.deviceEntryInteractor
import com.android.systemui.deviceentry.shared.model.DeviceUnlockSource
import com.android.systemui.deviceentry.shared.model.DeviceUnlockStatus
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.domain.interactor.keyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.ui.transitions.blurConfig
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.fakeBlurChoreographer
import com.android.systemui.scene.sceneTransitionBlurViewModel
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.testKosmos
import com.android.systemui.wallpapers.domain.interactor.fakeWallpaperRepository
import com.android.systemui.window.data.repository.fakeWindowRootViewBlurRepository
import com.android.systemui.window.shared.model.BlurEffect
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class SceneTransitionBlurViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val underTest: SceneTransitionBlurViewModel
        get() = kosmos.sceneTransitionBlurViewModel

    private val activationJob: Job = Job()

    @Before
    fun setUp() {
        kosmos.fakeWindowRootViewBlurRepository.isBlurSupported.value = true
        underTest.activateIn(kosmos.testScope, activationJob)
    }

    @Test
    fun idleOnLockscreen_mapsToMinBlur() =
        kosmos.runTest {
            underTest.requestWindowBackgroundBlur(TransitionState.Idle(Scenes.Lockscreen), 1f)

            assertThat(fakeBlurChoreographer.lastAppliedBlurEffect)
                .isEqualTo(BlurEffect(blurConfig.minBlurRadiusPx, 1f))
        }

    @Test
    fun idleOnLockscreen_withAodAndAmbientModeDisabled_mapsToMinBlur() =
        kosmos.runTest {
            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.AOD,
                testScope = testScope,
            )
            runCurrent()
            underTest.requestWindowBackgroundBlur(TransitionState.Idle(Scenes.Lockscreen), 1f)

            assertThat(fakeBlurChoreographer.lastAppliedBlurEffect)
                .isEqualTo(BlurEffect(blurConfig.minBlurRadiusPx, 1f))
        }

    @Test
    fun idleOnLockscreenAod_withAodAndAmbientModeEnabled_mapsToCorrectBlurValue() =
        kosmos.runTest {
            fakeWallpaperRepository.setWallpaperSupportsAmbientMode(true)
            runCurrent()
            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.AOD,
                testScope = testScope,
            )
            runCurrent()
            assertThat(keyguardTransitionInteractor.currentKeyguardState.value)
                .isEqualTo(KeyguardState.AOD)
            underTest.requestWindowBackgroundBlur(TransitionState.Idle(Scenes.Lockscreen), 1f)

            assertThat(fakeBlurChoreographer.lastAppliedBlurEffect)
                .isEqualTo(BlurEffect(blurConfig.maxBlurRadiusPx / 2, 1f))
        }

    @Test
    fun idleOnBouncerOnCommunal_mapsToCorrectBlurValue() =
        kosmos.runTest {
            underTest.requestWindowBackgroundBlur(
                TransitionState.Idle(Scenes.Communal, currentOverlays = setOf(Overlays.Bouncer)),
                1f,
            )

            assertThat(fakeBlurChoreographer.lastAppliedBlurEffect)
                .isEqualTo(BlurEffect(blurConfig.maxBlurRadiusPx, 0.95f))
        }

    @Test
    @DisableFlags(Flags.FLAG_SPATIAL_MODEL_BOUNCER_PUSHBACK)
    fun idleOnBouncerOnLockscreen_mapsToCorrectBlurValue() =
        kosmos.runTest {
            underTest.requestWindowBackgroundBlur(
                TransitionState.Idle(Scenes.Lockscreen, currentOverlays = setOf(Overlays.Bouncer)),
                1f,
            )

            assertThat(fakeBlurChoreographer.lastAppliedBlurEffect)
                .isEqualTo(BlurEffect(blurConfig.maxBlurRadiusPx, 1f))
        }

    @Test
    @EnableFlags(Flags.FLAG_SPATIAL_MODEL_BOUNCER_PUSHBACK)
    fun idleOnBouncerOnLockscreen_enableBouncerSpacialModel_correctBlurValue() =
        kosmos.runTest {
            underTest.requestWindowBackgroundBlur(
                TransitionState.Idle(Scenes.Lockscreen, currentOverlays = setOf(Overlays.Bouncer)),
                1f,
            )

            assertThat(fakeBlurChoreographer.lastAppliedBlurEffect)
                .isEqualTo(BlurEffect(blurConfig.maxBlurRadiusPx, 0.95f))
        }

    @Test
    fun idleOnBouncerOnOccluded_mapsToCorrectBlurValue() =
        kosmos.runTest {
            underTest.requestWindowBackgroundBlur(
                TransitionState.Idle(Scenes.Occluded, currentOverlays = setOf(Overlays.Bouncer)),
                1f,
            )

            assertThat(fakeBlurChoreographer.lastAppliedBlurEffect)
                .isEqualTo(BlurEffect(blurConfig.maxBlurRadiusPx, 1f))
        }

    @Test
    fun idleOnBouncerOnQuickSettings_isIgnored() =
        kosmos.runTest {
            underTest.requestWindowBackgroundBlur(
                TransitionState.Idle(
                    Scenes.QuickSettings,
                    currentOverlays = setOf(Overlays.Bouncer),
                ),
                1f,
            )

            assertThat(fakeBlurChoreographer.lastAppliedBlurEffect).isNull()
        }

    @Test
    fun idleOnBouncerOnShade_isIgnored() =
        kosmos.runTest {
            underTest.requestWindowBackgroundBlur(
                TransitionState.Idle(Scenes.Shade, currentOverlays = setOf(Overlays.Bouncer)),
                1f,
            )

            assertThat(fakeBlurChoreographer.lastAppliedBlurEffect).isNull()
        }

    @Test
    fun idleOnQuickSettingsOnGone_isIgnored() =
        kosmos.runTest {
            underTest.requestWindowBackgroundBlur(
                TransitionState.Idle(
                    Scenes.Gone,
                    currentOverlays = setOf(Overlays.QuickSettingsShade),
                ),
                1f,
            )

            assertThat(fakeBlurChoreographer.lastAppliedBlurEffect).isNull()
        }

    @Test
    fun idleOnNotificationShadeOnGone_isIgnored() =
        kosmos.runTest {
            underTest.requestWindowBackgroundBlur(
                TransitionState.Idle(
                    Scenes.Gone,
                    currentOverlays = setOf(Overlays.NotificationsShade),
                ),
                1f,
            )

            assertThat(fakeBlurChoreographer.lastAppliedBlurEffect).isNull()
        }

    @Test
    fun idleOnCommunal_mapsToMaxBlur() =
        kosmos.runTest {
            underTest.requestWindowBackgroundBlur(TransitionState.Idle(Scenes.Communal), 1f)

            assertThat(fakeBlurChoreographer.lastAppliedBlurEffect)
                .isEqualTo(BlurEffect(blurConfig.maxBlurRadiusPx, 0.95f))
        }

    @Test
    fun idleOnQuickSettingsScene_isIgnored() =
        kosmos.runTest {
            underTest.requestWindowBackgroundBlur(
                TransitionState.Idle(
                    Scenes.QuickSettings,
                    currentOverlays = setOf(Overlays.Bouncer),
                ),
                1f,
            )

            assertThat(fakeBlurChoreographer.lastAppliedBlurEffect).isNull()
        }

    @Test
    fun idleOnNotificationShadeScene_isIgnored() =
        kosmos.runTest {
            underTest.requestWindowBackgroundBlur(TransitionState.Idle(Scenes.Shade), 1f)

            assertThat(fakeBlurChoreographer.lastAppliedBlurEffect).isNull()
        }

    @Test
    fun idleOnDreamScene_mapsToMinBlur() =
        kosmos.runTest {
            underTest.requestWindowBackgroundBlur(TransitionState.Idle(Scenes.Dream), 1f)

            assertThat(fakeBlurChoreographer.lastAppliedBlurEffect)
                .isEqualTo(BlurEffect(blurConfig.minBlurRadiusPx, 1f))
        }

    @Test
    fun idleOnOccludedScene_mapsToMinBlur() =
        kosmos.runTest {
            underTest.requestWindowBackgroundBlur(TransitionState.Idle(Scenes.Occluded), 1f)

            assertThat(fakeBlurChoreographer.lastAppliedBlurEffect)
                .isEqualTo(BlurEffect(blurConfig.minBlurRadiusPx, 1f))
        }

    @Test
    fun idleOnGoneScene_mapsToMinBlur() =
        kosmos.runTest {
            underTest.requestWindowBackgroundBlur(TransitionState.Idle(Scenes.Gone), 1f)

            assertThat(fakeBlurChoreographer.lastAppliedBlurEffect)
                .isEqualTo(BlurEffect(blurConfig.minBlurRadiusPx, 1f))
        }

    @Test
    fun earlyWakeupReset_whenBlurIsNotSupported() =
        kosmos.runTest {
            fakeWindowRootViewBlurRepository.isBlurSupported.value = false

            assertThat(fakeBlurChoreographer.persistentEarlyWakeup).isFalse()
        }

    @Test
    fun earlyWakeupNotSet_whenDeviceIsNotEntered() =
        kosmos.runTest {
            fakeWindowRootViewBlurRepository.isBlurSupported.value = true
            sceneInteractor.snapToScene(Scenes.Lockscreen, loggingReason = "for test")
            runCurrent()
            assertThat(deviceEntryInteractor.isDeviceEntered.value).isFalse()
            assertThat(fakeBlurChoreographer.persistentEarlyWakeup).isFalse()
        }

    @Test
    fun earlyWakeupNotSet_whenDeviceIsEnteredAndShadeIsBeingDragged() =
        kosmos.runTest {
            fakeWindowRootViewBlurRepository.isBlurSupported.value = true
            fakeDeviceEntryRepository.deviceUnlockStatus.value =
                DeviceUnlockStatus(true, deviceUnlockSource = DeviceUnlockSource.Fingerprint)
            sceneInteractor.snapToScene(Scenes.Gone, loggingReason = "for test")
            runCurrent()
            assertThat(deviceEntryInteractor.isDeviceEntered.value).isTrue()
            assertThat(fakeBlurChoreographer.persistentEarlyWakeup).isFalse()

            val shadeExpansionProgress = MutableStateFlow(0.0f)
            val userInputOngoing = MutableStateFlow(true)
            val transitionState =
                MutableStateFlow(
                    ObservableTransitionState.Transition.ChangeScene(
                        fromScene = Scenes.Gone,
                        toScene = Scenes.Shade,
                        progress = shadeExpansionProgress,
                        isInitiatedByUserInput = true,
                        isUserInputOngoing = userInputOngoing,
                        currentOverlays = emptySet(),
                        currentScene = flowOf(Scenes.Gone),
                        previewProgress = flowOf(0f),
                        isInPreviewStage = flowOf(false),
                    )
                )

            sceneInteractor.setTransitionState(transitionState)
            runCurrent()

            assertThat(fakeBlurChoreographer.persistentEarlyWakeup).isFalse()

            shadeExpansionProgress.value = 0.1f
            userInputOngoing.value = false
            runCurrent()

            assertThat(fakeBlurChoreographer.persistentEarlyWakeup).isFalse()

            shadeExpansionProgress.value = 0f
            userInputOngoing.value = true
            runCurrent()

            assertThat(fakeBlurChoreographer.persistentEarlyWakeup).isFalse()
        }

    @Test
    @EnableFlags(Flags.FLAG_SPATIAL_MODEL_BOUNCER_PUSHBACK)
    fun lockscreen_bouncerOpen_correctBlurScale() =
        kosmos.runTest {
            val transitionProgress = 1f
            val transition =
                mock<TransitionState.Transition.ShowOrHideOverlay>() {
                    on { overlay } doReturn Overlays.Bouncer
                    on { fromContent } doReturn Scenes.Lockscreen
                    on { currentScene } doReturn Scenes.Lockscreen
                    on { toContent } doReturn Overlays.Bouncer
                    on { progress } doReturn transitionProgress
                }

            underTest.requestWindowBackgroundBlur(transition, transitionProgress)

            assertThat(fakeBlurChoreographer.lastAppliedBlurEffect)
                .isEqualTo(BlurEffect(blurConfig.maxBlurRadiusPx, 0.95f))
        }

    @Test
    @EnableFlags(Flags.FLAG_SPATIAL_MODEL_BOUNCER_PUSHBACK)
    fun lockscreen_bouncerClosed_startHasCorrectBlurScale() =
        kosmos.runTest {
            val transitionProgress = 0f
            val transition =
                mock<TransitionState.Transition.ShowOrHideOverlay>() {
                    on { overlay } doReturn Overlays.Bouncer
                    on { fromContent } doReturn Overlays.Bouncer
                    on { currentScene } doReturn Scenes.Lockscreen
                    on { toContent } doReturn Scenes.Lockscreen
                    on { progress } doReturn transitionProgress
                }

            underTest.requestWindowBackgroundBlur(transition, transitionProgress)

            assertThat(fakeBlurChoreographer.lastAppliedBlurEffect)
                .isEqualTo(BlurEffect(blurConfig.maxBlurRadiusPx, 0.95f))
        }

    @Test
    @EnableFlags(Flags.FLAG_SPATIAL_MODEL_BOUNCER_PUSHBACK)
    fun lockscreen_bouncerClosed_endHasCorrectBlurScale() =
        kosmos.runTest {
            val transitionProgress = 1f
            val transition =
                mock<TransitionState.Transition.ShowOrHideOverlay>() {
                    on { overlay } doReturn Overlays.Bouncer
                    on { fromContent } doReturn Overlays.Bouncer
                    on { currentScene } doReturn Scenes.Lockscreen
                    on { toContent } doReturn Scenes.Lockscreen
                    on { progress } doReturn transitionProgress
                }

            underTest.requestWindowBackgroundBlur(transition, transitionProgress)

            assertThat(fakeBlurChoreographer.lastAppliedBlurEffect)
                .isEqualTo(BlurEffect(blurConfig.minBlurRadiusPx, 1f))
        }

    @Test
    @EnableFlags(Flags.FLAG_SPATIAL_MODEL_BOUNCER_PUSHBACK)
    fun bouncerClosed_unlocked_startHasCorrectBlurScale() =
        kosmos.runTest {
            val transitionProgress = 0f
            val transition =
                mock<TransitionState.Transition.ShowOrHideOverlay>() {
                    on { overlay } doReturn Overlays.Bouncer
                    on { fromContent } doReturn Overlays.Bouncer
                    on { currentScene } doReturn Scenes.Lockscreen
                    on { toContent } doReturn Scenes.Gone
                    on { progress } doReturn transitionProgress
                }

            underTest.requestWindowBackgroundBlur(transition, transitionProgress)

            assertThat(fakeBlurChoreographer.lastAppliedBlurEffect)
                .isEqualTo(BlurEffect(blurConfig.maxBlurRadiusPx, 0.95f))
        }

    @Test
    @EnableFlags(Flags.FLAG_SPATIAL_MODEL_BOUNCER_PUSHBACK)
    fun bouncerClosed_unlocked_endHasCorrectBlurScale() =
        kosmos.runTest {
            val transitionProgress = 1f
            val transition =
                mock<TransitionState.Transition.ShowOrHideOverlay>() {
                    on { overlay } doReturn Overlays.Bouncer
                    on { fromContent } doReturn Overlays.Bouncer
                    on { currentScene } doReturn Scenes.Lockscreen
                    on { toContent } doReturn Scenes.Gone
                    on { progress } doReturn transitionProgress
                }

            underTest.requestWindowBackgroundBlur(transition, transitionProgress)

            assertThat(fakeBlurChoreographer.lastAppliedBlurEffect)
                .isEqualTo(BlurEffect(blurConfig.minBlurRadiusPx, 1f))
        }
}
