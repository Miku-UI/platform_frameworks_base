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

package com.android.systemui.statusbar.notification.stack.ui.viewmodel

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.ObservableTransitionState.Transition
import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.TransitionKey
import com.android.systemui.Flags
import com.android.systemui.Flags.FLAG_DUAL_SHADE
import com.android.systemui.SysuiTestCase
import com.android.systemui.brightness.domain.interactor.brightnessMirrorShowingInteractor
import com.android.systemui.deviceentry.data.repository.deviceEntryRepository
import com.android.systemui.deviceentry.shared.model.DeviceUnlockStatus
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.scene.data.repository.sceneContainerRepository
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.domain.startable.sceneContainerStartable
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.TransitionKeys.ToAlwaysOnDisplay
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.domain.interactor.enableSplitShade
import com.android.systemui.shade.shadeTestUtil
import com.android.systemui.statusbar.notification.data.repository.UnconfinedFakeHeadsUpRowRepository
import com.android.systemui.statusbar.notification.headsup.PinnedStatus
import com.android.systemui.statusbar.notification.stack.data.repository.headsUpNotificationRepository
import com.android.systemui.statusbar.notification.stack.domain.interactor.notificationStackAppearanceInteractor
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimBounds
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimShape
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.NotificationTransitionThresholds.EXPANSION_FOR_DELAYED_STACK_FADE_IN
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.NotificationTransitionThresholds.EXPANSION_FOR_MAX_SCRIM_ALPHA
import com.android.systemui.statusbar.policy.fakeConfigurationController
import com.android.systemui.testKosmos
import com.android.systemui.util.state.SynchronouslyObservableState
import com.android.systemui.util.state.observableStateOf
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class NotificationScrollViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest by Kosmos.Fixture { notificationScrollViewModel }

    private val fakePinnedHun =
        UnconfinedFakeHeadsUpRowRepository(
            key = "test_hun",
            pinnedStatus = MutableStateFlow(PinnedStatus.PinnedByUser),
        )

    @Before
    fun setUp(): Unit =
        with(kosmos) {
            sceneContainerStartable.start()
            runCurrent()
            underTest.activateIn(testScope)
        }

    @Test
    fun getQsPanelScrim_clears() =
        kosmos.runTest {
            var actual: ShadeScrimShape? = null
            val viewLeft = observableStateOf(10)
            val disposable = underTest.getQsScrimShape(viewLeft).observe { shape -> actual = shape }

            assertThat(actual).isNull()
            notificationStackAppearanceInteractor.setQsPanelShapeInWindow(
                ShadeScrimShape(
                    bounds = ShadeScrimBounds(left = 10f, top = 0f, right = 710f, bottom = 600f),
                    topRadius = 0,
                    bottomRadius = 100,
                )
            )
            assertThat(actual).isNotNull()
            notificationStackAppearanceInteractor.setQsPanelShapeInWindow(null)
            assertThat(actual).isNull()

            disposable.dispose()
        }

    @Test
    fun getQsPanelScrim_includesLeftOffset() =
        kosmos.runTest {
            var actual: ShadeScrimShape? = null
            val viewLeft = observableStateOf(10)
            val disposable = underTest.getQsScrimShape(viewLeft).observe { shape -> actual = shape }

            val shapeInWindow =
                ShadeScrimShape(
                    bounds = ShadeScrimBounds(left = 10f, top = 0f, right = 710f, bottom = 600f),
                    topRadius = 0,
                    bottomRadius = 100,
                )

            val expected =
                ShadeScrimShape(
                    bounds = ShadeScrimBounds(left = 0f, top = 0f, right = 700f, bottom = 600f),
                    topRadius = 0,
                    bottomRadius = 100,
                )

            notificationStackAppearanceInteractor.setQsPanelShapeInWindow(shapeInWindow)

            assertThat(actual).isEqualTo(expected)

            disposable.dispose()
        }

    @Test
    fun getQsPanelScrim_updatesWhenLeftOffsetUpdates() =
        kosmos.runTest {
            var actual: ShadeScrimShape? = null
            val viewLeft = SynchronouslyObservableState(0)
            val disposable = underTest.getQsScrimShape(viewLeft).observe { shape -> actual = shape }

            val shapeInWindow =
                ShadeScrimShape(
                    bounds = ShadeScrimBounds(left = 10f, top = 0f, right = 710f, bottom = 600f),
                    topRadius = 0,
                    bottomRadius = 100,
                )

            notificationStackAppearanceInteractor.setQsPanelShapeInWindow(shapeInWindow)

            assertThat(actual).isEqualTo(shapeInWindow)

            viewLeft.value = 10

            val expected =
                ShadeScrimShape(
                    bounds = ShadeScrimBounds(left = 0f, top = 0f, right = 700f, bottom = 600f),
                    topRadius = 0,
                    bottomRadius = 100,
                )

            assertThat(actual).isEqualTo(expected)

            disposable.dispose()
        }

    @Test
    fun noDualShade_brightnessMirrorShowing_notInteractive() =
        kosmos.runTest {
            enableSingleShade()
            val interactive by collectLastValue(underTest.interactive)

            brightnessMirrorShowingInteractor.setMirrorShowing(false)
            assertThat(interactive).isTrue()

            brightnessMirrorShowingInteractor.setMirrorShowing(true)
            assertThat(interactive).isFalse()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun interactive_whenBlurredAndHunIsPinned_isTrue() =
        kosmos.runTest {
            enableDualShade()
            val interactive by collectLastValue(underTest.interactive)
            brightnessMirrorShowingInteractor.setMirrorShowing(false)

            // GIVEN the background is blurred and a HUN is pinned
            setBlur(true)
            setHunIsPinned(true)

            // THEN the notification stack is interactive (because of the HUN)
            assertThat(interactive).isTrue()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun interactive_whenBlurredAndNoHun_isFalse() =
        kosmos.runTest {
            enableDualShade()
            val interactive by collectLastValue(underTest.interactive)
            brightnessMirrorShowingInteractor.setMirrorShowing(false)

            // GIVEN the background is blurred and no HUN is pinned
            setBlur(true)
            setHunIsPinned(false)

            // THEN the notification stack is NOT interactive
            assertThat(interactive).isFalse()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun interactive_whenNotBlurredAndHunIsPinned_isTrue() =
        kosmos.runTest {
            enableDualShade()
            val interactive by collectLastValue(underTest.interactive)
            brightnessMirrorShowingInteractor.setMirrorShowing(false)

            // GIVEN the background is not blurred but a HUN is still pinned
            setBlur(false)
            setHunIsPinned(true)

            // THEN the notification stack is interactive
            assertThat(interactive).isTrue()
        }

    @Test
    fun allowScrimClipping_toShadeScene_true() =
        kosmos.runTest {
            enableSingleShade()
            val allowScrimClipping by collectLastValue(underTest.allowScrimClipping)

            // GIVEN a transition to Shade scene
            sceneContainerRepository.setTransitionState(
                flowOf(
                    Transition(
                        fromScene = Scenes.Gone,
                        toScene = Scenes.Shade,
                        currentScene = flowOf(Scenes.Gone),
                        progress = flowOf(0.5f),
                        isInitiatedByUserInput = true,
                        isUserInputOngoing = flowOf(true),
                    )
                )
            )
            runCurrent()

            // THEN allowScrimClipping is true
            assertThat(allowScrimClipping).isTrue()
        }

    @Test
    @EnableFlags(Flags.FLAG_FIX_NSSL_BLOCKING_QS, FLAG_DUAL_SHADE)
    fun interactive_whenOnLockscreenAndQsOverlayIsShowing_isFalse() =
        kosmos.runTest {
            val interactive by collectLastValue(underTest.interactive)
            brightnessMirrorShowingInteractor.setMirrorShowing(false)
            setBlur(false)
            setHunIsPinned(false)

            // GIVEN we are on Lockscreen
            sceneInteractor.changeScene(Scenes.Lockscreen, "setup")
            sceneInteractor.setTransitionState(
                flowOf(ObservableTransitionState.Idle(Scenes.Lockscreen))
            )

            // AND QS Overlay is showing
            sceneInteractor.showOverlay(Overlays.QuickSettingsShade, "show")
            // On desktop dual shade, showing QS overlay implies full expansion
            setBlur(true)
            runCurrent()

            // THEN the notification stack is NOT interactive
            assertThat(interactive).isFalse()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun allowScrimClipping_toNotifOverlay_false() =
        kosmos.runTest {
            val allowScrimClipping by collectLastValue(underTest.allowScrimClipping)

            // GIVEN a transition to NotificationsShade overlay
            sceneContainerRepository.setTransitionState(
                flowOf(
                    Transition.ShowOrHideOverlay(
                        overlay = Overlays.NotificationsShade,
                        fromContent = Scenes.Gone,
                        toContent = Overlays.NotificationsShade,
                        currentScene = Scenes.Gone,
                        currentOverlays = flowOf(emptySet()),
                        progress = flowOf(0.5f),
                        isInitiatedByUserInput = true,
                        isUserInputOngoing = flowOf(true),
                        previewProgress = flowOf(0f),
                        isInPreviewStage = flowOf(false),
                    )
                )
            )
            runCurrent()

            // THEN allowScrimClipping is false
            assertThat(allowScrimClipping).isFalse()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun suppressHeightUpdates_idleQuickSettings_EndHeightOnly() {
        kosmos.runTest {
            val suppressHeightUpdates by collectLastValue(underTest.suppressHeightUpdates)

            // GIVEN transition states shows idling on the QuickSettings scene
            sceneContainerRepository.setTransitionState(
                flowOf(ObservableTransitionState.Idle(currentScene = Scenes.QuickSettings))
            )
            runCurrent()

            // THEN suppressHeightUpdates is EndHeightOnly
            assertThat(suppressHeightUpdates)
                .isEqualTo(NotificationScrollViewModel.HeightSuppressionState.EndHeightOnly)
        }
    }

    @Test
    fun suppressHeightUpdates_idleShade_None() {
        kosmos.runTest {
            enableSingleShade()
            val suppressHeightUpdates by collectLastValue(underTest.suppressHeightUpdates)

            // GIVEN transition states shows idling on the Shade scene
            sceneContainerRepository.setTransitionState(
                flowOf(ObservableTransitionState.Idle(currentScene = Scenes.Shade))
            )
            runCurrent()

            // THEN suppressHeightUpdates is None
            assertThat(suppressHeightUpdates)
                .isEqualTo(NotificationScrollViewModel.HeightSuppressionState.None)
        }
    }

    @Test
    fun suppressHeightUpdates_transitionShadeToQuickSettings_EndHeightOnly() {
        kosmos.runTest {
            enableSingleShade()
            val suppressHeightUpdates by collectLastValue(underTest.suppressHeightUpdates)

            // GIVEN a transition from Shade to QuickSettings scene
            sceneContainerRepository.setTransitionState(
                flowOf(
                    Transition(
                        fromScene = Scenes.Shade,
                        toScene = Scenes.QuickSettings,
                        currentScene = flowOf(Scenes.Shade),
                        progress = flowOf(0.5f),
                        isInitiatedByUserInput = true,
                        isUserInputOngoing = flowOf(true),
                    )
                )
            )
            runCurrent()

            // THEN suppressHeightUpdates is EndHeightOnly
            assertThat(suppressHeightUpdates)
                .isEqualTo(NotificationScrollViewModel.HeightSuppressionState.EndHeightOnly)
        }
    }

    @Test
    fun suppressHeightUpdates_transitionQuickSettingsToShade_None() {
        kosmos.runTest {
            enableSingleShade()
            val suppressHeightUpdates by collectLastValue(underTest.suppressHeightUpdates)

            // GIVEN a transition from QuickSettings to Shade scene
            sceneContainerRepository.setTransitionState(
                flowOf(
                    Transition(
                        fromScene = Scenes.QuickSettings,
                        toScene = Scenes.Shade,
                        currentScene = flowOf(Scenes.QuickSettings),
                        progress = flowOf(0.5f),
                        isInitiatedByUserInput = true,
                        isUserInputOngoing = flowOf(true),
                    )
                )
            )
            runCurrent()

            // THEN suppressHeightUpdates is None
            assertThat(suppressHeightUpdates)
                .isEqualTo(NotificationScrollViewModel.HeightSuppressionState.None)
        }
    }

    @Test
    fun suppressHeightUpdates_transitionQuickSettingsToLockscreen_EndHeightOnly() {
        kosmos.runTest {
            enableSingleShade()
            val suppressHeightUpdates by collectLastValue(underTest.suppressHeightUpdates)

            // GIVEN a transition from QuickSettings to Lockscreen scene
            sceneContainerRepository.setTransitionState(
                flowOf(
                    Transition(
                        fromScene = Scenes.QuickSettings,
                        toScene = Scenes.Lockscreen,
                        currentScene = flowOf(Scenes.QuickSettings),
                        progress = flowOf(0.5f),
                        isInitiatedByUserInput = true,
                        isUserInputOngoing = flowOf(true),
                    )
                )
            )
            runCurrent()

            // THEN suppressHeightUpdates is EndHeightOnly
            assertThat(suppressHeightUpdates)
                .isEqualTo(NotificationScrollViewModel.HeightSuppressionState.EndHeightOnly)
        }
    }

    @Test
    fun suppressHeightUpdates_transitionLockScreenToGone_All() {
        kosmos.runTest {
            val suppressHeightUpdates by collectLastValue(underTest.suppressHeightUpdates)

            // GIVEN a transition from Lockscreen to Gone scene
            sceneContainerRepository.setTransitionState(
                flowOf(
                    Transition(
                        fromScene = Scenes.Lockscreen,
                        toScene = Scenes.Gone,
                        currentScene = flowOf(Scenes.Lockscreen),
                        progress = flowOf(0.5f),
                        isInitiatedByUserInput = true,
                        isUserInputOngoing = flowOf(true),
                    )
                )
            )
            runCurrent()

            // THEN suppressHeightUpdates is All
            assertThat(suppressHeightUpdates)
                .isEqualTo(NotificationScrollViewModel.HeightSuppressionState.All)
        }
    }

    @Test
    fun suppressHeightUpdates_transitionShadeToGone_None() {
        kosmos.runTest {
            enableSingleShade()
            val suppressHeightUpdates by collectLastValue(underTest.suppressHeightUpdates)

            // GIVEN a transition from Shade to Gone scene
            sceneContainerRepository.setTransitionState(
                flowOf(
                    Transition(
                        fromScene = Scenes.Shade,
                        toScene = Scenes.Gone,
                        currentScene = flowOf(Scenes.Shade),
                        progress = flowOf(0.5f),
                        isInitiatedByUserInput = true,
                        isUserInputOngoing = flowOf(true),
                    )
                )
            )
            runCurrent()

            // THEN suppressHeightUpdates is None
            assertThat(suppressHeightUpdates)
                .isEqualTo(NotificationScrollViewModel.HeightSuppressionState.None)
        }
    }

    @Test
    fun suppressHeightUpdates_transitionLockScreenToBouncer_All() {
        kosmos.runTest {
            val suppressHeightUpdates by collectLastValue(underTest.suppressHeightUpdates)

            // GIVEN a transition from Lockscreen to show Bouncer
            sceneContainerRepository.setTransitionState(
                flowOf(
                    Transition.ShowOrHideOverlay(
                        overlay = Overlays.Bouncer,
                        fromContent = Scenes.Lockscreen,
                        toContent = Overlays.Bouncer,
                        currentScene = Scenes.Lockscreen,
                        currentOverlays = flowOf(emptySet()),
                        progress = flowOf(0.5f),
                        isInitiatedByUserInput = true,
                        isUserInputOngoing = flowOf(true),
                        previewProgress = flowOf(0f),
                        isInPreviewStage = flowOf(false),
                    )
                )
            )
            runCurrent()

            // THEN suppressHeightUpdates is All
            assertThat(suppressHeightUpdates)
                .isEqualTo(NotificationScrollViewModel.HeightSuppressionState.All)
        }
    }

    @Test
    fun suppressHeightUpdates_transitionBouncerToLockScreen_None() {
        kosmos.runTest {
            val suppressHeightUpdates by collectLastValue(underTest.suppressHeightUpdates)

            // GIVEN a transition from Bouncer to Lockscreen
            sceneContainerRepository.setTransitionState(
                flowOf(
                    Transition.ShowOrHideOverlay(
                        overlay = Overlays.Bouncer,
                        fromContent = Overlays.Bouncer,
                        toContent = Scenes.Lockscreen,
                        currentScene = Scenes.Lockscreen,
                        currentOverlays = flowOf(setOf(Overlays.Bouncer)),
                        progress = flowOf(0.5f),
                        isInitiatedByUserInput = true,
                        isUserInputOngoing = flowOf(true),
                        previewProgress = flowOf(0f),
                        isInPreviewStage = flowOf(false),
                    )
                )
            )
            runCurrent()

            // THEN suppressHeightUpdates is None
            assertThat(suppressHeightUpdates)
                .isEqualTo(NotificationScrollViewModel.HeightSuppressionState.None)
        }
    }

    @Test
    fun shadeExpansion_goneToShade() =
        kosmos.runTest {
            enableSingleShade()
            val expandFraction by collectLastValue(notificationScrollViewModel.expandFraction)
            val isScrollable by collectLastValue(notificationScrollViewModel.isScrollable)

            // Setup: Unlock the device to allow switching to the Gone scene
            unlockDevice()

            driveSceneTransition(
                currentScene = Scenes.Gone,
                targetScene = Scenes.Shade,
                verifyIdleOnCurrentScene = {
                    assertThat(expandFraction).isEqualTo(0f)
                    assertThat(isScrollable).isFalse()
                },
                verifyTransitionStep = { progress ->
                    assertThat(expandFraction).isEqualTo(progress)
                },
                verifyIdleOnTargetScene = {
                    assertThat(expandFraction).isEqualTo(1f)
                    assertThat(isScrollable).isTrue()
                },
            )
        }

    @Test
    fun shadeExpansion_shadeToGone() =
        kosmos.runTest {
            enableSingleShade()
            val expandFraction by collectLastValue(notificationScrollViewModel.expandFraction)
            val isScrollable by collectLastValue(notificationScrollViewModel.isScrollable)

            // Setup: Unlock the device to allow switching to the Gone scene
            unlockDevice()

            driveSceneTransition(
                currentScene = Scenes.Shade,
                targetScene = Scenes.Gone,
                verifyIdleOnCurrentScene = {
                    assertThat(expandFraction).isEqualTo(1f)
                    assertThat(isScrollable).isTrue()
                },
                verifyTransitionStep = { progress ->
                    assertThat(expandFraction).isEqualTo(1 - progress)
                },
                verifyIdleOnTargetScene = {
                    assertThat(expandFraction).isEqualTo(0f)
                    assertThat(isScrollable).isFalse()
                },
            )
        }

    @Test
    fun shadeExpansion_shadeToQs() =
        kosmos.runTest {
            enableSingleShade()
            val expandFraction by collectLastValue(notificationScrollViewModel.expandFraction)
            val isScrollable by collectLastValue(notificationScrollViewModel.isScrollable)

            driveSceneTransition(
                currentScene = Scenes.Shade,
                targetScene = Scenes.QuickSettings,
                verifyIdleOnCurrentScene = {
                    assertThat(expandFraction).isEqualTo(1f)
                    assertThat(isScrollable).isTrue()
                },
                verifyTransitionStep = { _ -> assertThat(expandFraction).isEqualTo(1f) },
                verifyIdleOnTargetScene = {
                    assertThat(expandFraction).isEqualTo(1f)
                    assertThat(isScrollable).isFalse()
                },
            )
        }

    @Test
    fun shadeExpansion_qsToShade() =
        kosmos.runTest {
            enableSingleShade()
            val expandFraction by collectLastValue(notificationScrollViewModel.expandFraction)
            val isScrollable by collectLastValue(notificationScrollViewModel.isScrollable)

            driveSceneTransition(
                currentScene = Scenes.QuickSettings,
                targetScene = Scenes.Shade,
                verifyIdleOnCurrentScene = {
                    assertThat(expandFraction).isEqualTo(1f)
                    assertThat(isScrollable).isFalse()
                },
                verifyTransitionStep = { _ -> assertThat(expandFraction).isEqualTo(1f) },
                verifyIdleOnTargetScene = {
                    assertThat(expandFraction).isEqualTo(1f)
                    assertThat(isScrollable).isTrue()
                },
            )
        }

    @Test
    fun shadeExpansion_goneToQs() =
        kosmos.runTest {
            enableSingleShade()
            val expandFraction by collectLastValue(notificationScrollViewModel.expandFraction)
            val isScrollable by collectLastValue(notificationScrollViewModel.isScrollable)

            // Setup: Unlock the device to allow switching to the Gone scene
            unlockDevice()

            driveSceneTransition(
                currentScene = Scenes.Gone,
                targetScene = Scenes.QuickSettings,
                verifyIdleOnCurrentScene = {
                    assertThat(expandFraction).isEqualTo(0f)
                    assertThat(isScrollable).isFalse()
                },
                verifyTransitionStep = { progress ->
                    assertThat(expandFraction)
                        .isEqualTo(
                            (progress / EXPANSION_FOR_MAX_SCRIM_ALPHA -
                                    EXPANSION_FOR_DELAYED_STACK_FADE_IN)
                                .coerceIn(0f, 1f)
                        )
                },
                verifyIdleOnTargetScene = {
                    assertThat(expandFraction).isEqualTo(1f)
                    assertThat(isScrollable).isFalse()
                },
            )
        }

    @Test
    fun shadeExpansion_goneToLockscreen() =
        kosmos.runTest {
            val expandFraction by collectLastValue(notificationScrollViewModel.expandFraction)

            // Setup: Unlock the device to allow switching to the Gone scene
            unlockDevice()

            driveSceneTransition(
                currentScene = Scenes.Gone,
                targetScene = Scenes.Lockscreen,
                verifyIdleOnCurrentScene = { assertThat(expandFraction).isEqualTo(0f) },
                verifyTransitionStep = { _ -> assertThat(expandFraction).isEqualTo(0f) },
                verifyIdleOnTargetScene = { assertThat(expandFraction).isEqualTo(1f) },
            )
        }

    @Test
    fun shadeExpansion_lockscreenToGone() =
        kosmos.runTest {
            val expandFraction by collectLastValue(notificationScrollViewModel.expandFraction)

            // Setup: Unlock the device to allow switching to the Gone scene
            unlockDevice()

            driveSceneTransition(
                currentScene = Scenes.Lockscreen,
                targetScene = Scenes.Gone,
                verifyIdleOnCurrentScene = { assertThat(expandFraction).isEqualTo(1f) },
                verifyTransitionStep = { _ -> assertThat(expandFraction).isEqualTo(1f) },
                verifyIdleOnTargetScene = { assertThat(expandFraction).isEqualTo(0f) },
            )
        }

    @Test
    fun shadeExpansion_lockscreenToShade() =
        kosmos.runTest {
            enableSingleShade()
            val expandFraction by collectLastValue(notificationScrollViewModel.expandFraction)

            driveSceneTransition(
                currentScene = Scenes.Lockscreen,
                targetScene = Scenes.Shade,
                verifyIdleOnCurrentScene = { assertThat(expandFraction).isEqualTo(1f) },
                verifyTransitionStep = { _ -> assertThat(expandFraction).isEqualTo(1f) },
                verifyIdleOnTargetScene = { assertThat(expandFraction).isEqualTo(1f) },
            )
        }

    @Test
    fun shadeExpansion_shadeToLockscreen() =
        kosmos.runTest {
            enableSingleShade()
            val expandFraction by collectLastValue(notificationScrollViewModel.expandFraction)

            driveSceneTransition(
                currentScene = Scenes.Shade,
                targetScene = Scenes.Lockscreen,
                verifyIdleOnCurrentScene = { assertThat(expandFraction).isEqualTo(1f) },
                verifyTransitionStep = { progress ->
                    assertThat(expandFraction).isWithin(0.01f).of(1f - progress)
                },
                verifyIdleOnTargetScene = { assertThat(expandFraction).isEqualTo(1f) },
            )
        }

    @Test
    fun shadeExpansion_shadeToAod() =
        kosmos.runTest {
            enableSingleShade()
            val expandFraction by collectLastValue(notificationScrollViewModel.expandFraction)

            driveSceneTransition(
                transitionKey = ToAlwaysOnDisplay,
                currentScene = Scenes.Shade,
                targetScene = Scenes.Lockscreen,
                verifyIdleOnCurrentScene = { assertThat(expandFraction).isEqualTo(1f) },
                verifyTransitionStep = { assertThat(expandFraction).isEqualTo(1f) },
                verifyIdleOnTargetScene = { assertThat(expandFraction).isEqualTo(1f) },
            )
        }

    @Test
    fun shadeExpansion_qsToAod() =
        kosmos.runTest {
            enableSingleShade()
            val expandFraction by collectLastValue(notificationScrollViewModel.expandFraction)

            driveSceneTransition(
                transitionKey = ToAlwaysOnDisplay,
                currentScene = Scenes.Shade,
                targetScene = Scenes.Lockscreen,
                verifyIdleOnCurrentScene = { assertThat(expandFraction).isEqualTo(1f) },
                verifyTransitionStep = { assertThat(expandFraction).isEqualTo(1f) },
                verifyIdleOnTargetScene = { assertThat(expandFraction).isEqualTo(1f) },
            )
        }

    @Test
    fun shadeExpansion_occludedToShade() =
        kosmos.runTest {
            enableSingleShade()
            val expandFraction by collectLastValue(notificationScrollViewModel.expandFraction)

            driveSceneTransition(
                currentScene = Scenes.Occluded,
                targetScene = Scenes.Shade,
                verifyIdleOnCurrentScene = { assertThat(expandFraction).isEqualTo(0f) },
                verifyTransitionStep = { progress ->
                    assertThat(expandFraction).isEqualTo(progress)
                },
                verifyIdleOnTargetScene = { assertThat(expandFraction).isEqualTo(1f) },
            )
        }

    @Test
    fun shadeExpansion_shadeToOccluded() =
        kosmos.runTest {
            enableSingleShade()
            val expandFraction by collectLastValue(notificationScrollViewModel.expandFraction)

            driveSceneTransition(
                currentScene = Scenes.Shade,
                targetScene = Scenes.Occluded,
                verifyIdleOnCurrentScene = { assertThat(expandFraction).isEqualTo(1f) },
                verifyTransitionStep = { progress ->
                    assertThat(expandFraction).isWithin(0.01f).of(1f - progress)
                },
                verifyIdleOnTargetScene = { assertThat(expandFraction).isEqualTo(0f) },
            )
        }

    @Test
    fun shadeExpansion_lockscreenToOccluded() =
        kosmos.runTest {
            val expandFraction by collectLastValue(notificationScrollViewModel.expandFraction)

            driveSceneTransition(
                currentScene = Scenes.Lockscreen,
                targetScene = Scenes.Occluded,
                verifyIdleOnCurrentScene = { assertThat(expandFraction).isEqualTo(1f) },
                verifyTransitionStep = { _ -> assertThat(expandFraction).isEqualTo(1f) },
                verifyIdleOnTargetScene = { assertThat(expandFraction).isEqualTo(0f) },
            )
        }

    @Test
    fun shadeExpansion_occludedToLockscreen() =
        kosmos.runTest {
            val expandFraction by collectLastValue(notificationScrollViewModel.expandFraction)

            driveSceneTransition(
                currentScene = Scenes.Occluded,
                targetScene = Scenes.Lockscreen,
                verifyIdleOnCurrentScene = { assertThat(expandFraction).isEqualTo(0f) },
                verifyTransitionStep = { _ -> assertThat(expandFraction).isEqualTo(1f) },
                verifyIdleOnTargetScene = { assertThat(expandFraction).isEqualTo(1f) },
            )
        }

    @Test
    fun shadeExpansion_lockscreenToCommunal() =
        kosmos.runTest {
            val expandFraction by collectLastValue(notificationScrollViewModel.expandFraction)

            driveSceneTransition(
                currentScene = Scenes.Lockscreen,
                targetScene = Scenes.Communal,
                verifyIdleOnCurrentScene = { assertThat(expandFraction).isEqualTo(1f) },
                verifyTransitionStep = { _ -> assertThat(expandFraction).isEqualTo(1f) },
                verifyIdleOnTargetScene = { assertThat(expandFraction).isEqualTo(0f) },
            )
        }

    @Test
    fun shadeExpansion_communalToLockscreen() =
        kosmos.runTest {
            val expandFraction by collectLastValue(notificationScrollViewModel.expandFraction)

            driveSceneTransition(
                currentScene = Scenes.Communal,
                targetScene = Scenes.Lockscreen,
                verifyIdleOnCurrentScene = { assertThat(expandFraction).isEqualTo(0f) },
                verifyTransitionStep = { _ -> assertThat(expandFraction).isEqualTo(1f) },
                verifyIdleOnTargetScene = { assertThat(expandFraction).isEqualTo(1f) },
            )
        }

    @Test
    fun expandFraction_showBouncerOverlay_mustRemainExpanded() =
        kosmos.runTest {
            val expandFraction by collectLastValue(notificationScrollViewModel.expandFraction)

            enableSingleShade()

            // Same as NotificationScrollViewModel SceneKey.showsNotifications
            listOf(Scenes.Shade, Scenes.Lockscreen, Scenes.QuickSettings).forEach {
                sceneWithStackExpanded ->
                driveShowOverlayTransition(
                    currentScene = sceneWithStackExpanded,
                    overlay = Overlays.Bouncer,
                    verifyOverlayHidden = {
                        assertWithMessage("on ${sceneWithStackExpanded.debugName}")
                            .that(expandFraction)
                            .isEqualTo(1f)
                    },
                    verifyTransitionStep = { _ ->
                        assertWithMessage("on ${sceneWithStackExpanded.debugName}")
                            .that(expandFraction)
                            .isEqualTo(1f)
                    },
                    verifyOverlayShown = {
                        assertWithMessage("on ${sceneWithStackExpanded.debugName}")
                            .that(expandFraction)
                            .isEqualTo(1f)
                    },
                )
            }
        }

    @Test
    fun expandFraction_showBouncerOverlay_mustRemainCollapsed() =
        kosmos.runTest {
            val expandFraction by collectLastValue(notificationScrollViewModel.expandFraction)

            listOf(Scenes.Communal, Scenes.Dream, Scenes.Occluded).forEach { sceneWithStackCollapsed
                ->
                driveShowOverlayTransition(
                    currentScene = sceneWithStackCollapsed,
                    overlay = Overlays.Bouncer,
                    verifyOverlayHidden = {
                        assertWithMessage("on ${sceneWithStackCollapsed.debugName}")
                            .that(expandFraction)
                            .isEqualTo(0f)
                    },
                    verifyTransitionStep = { _ ->
                        assertWithMessage("on ${sceneWithStackCollapsed.debugName}")
                            .that(expandFraction)
                            .isEqualTo(0f)
                    },
                    verifyOverlayShown = {
                        assertWithMessage("on ${sceneWithStackCollapsed.debugName}")
                            .that(expandFraction)
                            .isEqualTo(0f)
                    },
                )
            }
        }

    @Test
    fun scrimClippingRadius_singleShade() =
        kosmos.runTest {
            val clippingRadius by collectLastValue(notificationScrollViewModel.scrimClippingRadius)
            overrideResource(R.dimen.overlay_shade_panel_shape_radius, 50)
            overrideResource(R.dimen.notification_scrim_corner_radius, 30)
            fakeConfigurationController.notifyDensityOrFontScaleChanged()

            enableSingleShade()
            assertThat(clippingRadius).isEqualTo(30)
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun scrimClippingRadius_splitShade() =
        kosmos.runTest {
            val clippingRadius by collectLastValue(notificationScrollViewModel.scrimClippingRadius)
            overrideResource(R.dimen.overlay_shade_panel_shape_radius, 50)
            overrideResource(R.dimen.notification_scrim_corner_radius, 30)
            fakeConfigurationController.notifyDensityOrFontScaleChanged()

            enableSplitShade()
            assertThat(clippingRadius).isEqualTo(30)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun scrimClippingRadius_dualShade() =
        kosmos.runTest {
            val clippingRadius by collectLastValue(notificationScrollViewModel.scrimClippingRadius)
            overrideResource(R.dimen.overlay_shade_panel_shape_radius, 50)
            overrideResource(R.dimen.notification_scrim_corner_radius, 30)
            fakeConfigurationController.notifyDensityOrFontScaleChanged()

            enableDualShade()
            assertThat(clippingRadius).isEqualTo(50)
        }

    private fun Kosmos.unlockDevice() {
        deviceEntryRepository.deviceUnlockStatus.value =
            DeviceUnlockStatus(isUnlocked = true, deviceUnlockSource = null)
    }

    private fun Kosmos.driveSceneTransition(
        currentScene: SceneKey,
        targetScene: SceneKey,
        verifyIdleOnCurrentScene: () -> Unit,
        verifyTransitionStep: (progress: Float) -> Unit,
        verifyIdleOnTargetScene: () -> Unit,
        transitionKey: TransitionKey? = null,
    ) {
        // Idle on current Scene
        val transitionState =
            MutableStateFlow<ObservableTransitionState>(
                ObservableTransitionState.Idle(currentScene = currentScene)
            )
        sceneInteractor.snapToScene(currentScene, "Setup currentScene.")
        sceneInteractor.setTransitionState(transitionState)
        verifyIdleOnCurrentScene()

        sceneInteractor.changeScene(targetScene, "Switch to targetScene.")
        val transitionProgress = MutableStateFlow(0f)
        transitionState.value =
            Transition(
                fromScene = currentScene,
                toScene = targetScene,
                currentScene = flowOf(targetScene),
                progress = transitionProgress,
                isInitiatedByUserInput = true,
                isUserInputOngoing = flowOf(false),
                key = transitionKey,
            )
        val steps = 10
        repeat(steps) { repetition ->
            // Transitioning to the target Scene
            val progress = (1f / steps) * (repetition + 1)
            transitionProgress.value = progress
            verifyTransitionStep(progress)
        }

        // Idle on the target Scene
        transitionState.value = ObservableTransitionState.Idle(currentScene = targetScene)
        verifyIdleOnTargetScene()
    }

    private fun Kosmos.driveShowOverlayTransition(
        currentScene: SceneKey,
        overlay: OverlayKey,
        verifyOverlayHidden: () -> Unit,
        verifyTransitionStep: (progress: Float) -> Unit,
        verifyOverlayShown: () -> Unit,
        transitionKey: TransitionKey? = null,
    ) {
        // Idle on current Scene (without overlay)
        val transitionState =
            MutableStateFlow<ObservableTransitionState>(
                ObservableTransitionState.Idle(currentScene = currentScene)
            )
        sceneInteractor.snapToScene(currentScene, "Setup currentScene.")
        sceneInteractor.setTransitionState(transitionState)
        verifyOverlayHidden()

        sceneInteractor.showOverlay(overlay, "show overlay.")
        val transitionProgress = MutableStateFlow(0f)
        transitionState.value =
            Transition.showOverlay(
                overlay = overlay,
                fromScene = currentScene,
                progress = transitionProgress,
                isInitiatedByUserInput = true,
                isUserInputOngoing = flowOf(false),
                currentOverlays = flowOf(emptySet()),
                key = transitionKey,
            )
        val steps = 10
        repeat(steps) { repetition ->
            // Transitioning to show verlay
            val progress = (1f / steps) * (repetition + 1)
            transitionProgress.value = progress
            verifyTransitionStep(progress)
        }

        // Idle on currentScene (with overlay)
        transitionState.value =
            ObservableTransitionState.Idle(
                currentScene = currentScene,
                currentOverlays = setOf(overlay),
            )
        verifyOverlayShown()
    }

    private fun Kosmos.setBlur(isBlurred: Boolean) {
        val expansion = if (isBlurred) 1f else 0f
        shadeTestUtil.setQsExpansion(expansion)
    }

    private fun Kosmos.setHunIsPinned(isPinned: Boolean) {
        headsUpNotificationRepository.setNotifications(
            if (isPinned) listOf(fakePinnedHun) else emptyList()
        )
    }
}
