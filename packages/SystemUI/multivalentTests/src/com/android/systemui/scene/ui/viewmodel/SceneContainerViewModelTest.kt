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
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_OUTSIDE
import android.view.WindowInsetsController
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.DynamicSizeEdgeDetector
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.systemui.Flags.FLAG_BLACK_SCREEN_ON_SCENE_CONTAINER_START_FIX
import com.android.systemui.Flags.FLAG_DUAL_SHADE
import com.android.systemui.SysuiTestCase
import com.android.systemui.accessibility.data.repository.fakeAccessibilityRepository
import com.android.systemui.classifier.fakeFalsingManager
import com.android.systemui.desktop.domain.interactor.enableUsingDesktopStatusBar
import com.android.systemui.deviceentry.domain.interactor.deviceUnlockedInteractor
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.domain.interactor.biometricUnlockInteractor
import com.android.systemui.keyguard.shared.model.BiometricUnlockSource
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.currentValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.power.data.repository.fakePowerRepository
import com.android.systemui.res.R
import com.android.systemui.scene.data.repository.unlockDevice
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.fakeOverlaysByKeys
import com.android.systemui.scene.sceneContainerConfig
import com.android.systemui.scene.sceneContainerViewModelFactory
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.fakeSceneDataSource
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.domain.interactor.enableSplitShade
import com.android.systemui.shade.domain.interactor.shadeMode
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.statusbar.data.repository.fakeRemoteInputRepository
import com.android.systemui.statusbar.phone.BiometricUnlockController
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class SceneContainerViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private lateinit var underTest: SceneContainerViewModel

    private lateinit var activationJob: Job
    private var motionEventHandler: SceneContainerViewModel.MotionEventHandler? = null

    @Before
    fun setUp() {
        underTest =
            kosmos.sceneContainerViewModelFactory.create { motionEventHandler ->
                this@SceneContainerViewModelTest.motionEventHandler = motionEventHandler
            }
        activationJob = Job()
        underTest.activateIn(kosmos.testScope, activationJob)
    }

    @Test
    fun activate_setsMotionEventHandler() =
        kosmos.runTest { assertThat(motionEventHandler).isNotNull() }

    @Test
    fun deactivate_clearsMotionEventHandler() =
        kosmos.runTest {
            activationJob.cancel()

            assertThat(motionEventHandler).isNull()
        }

    @Test
    @EnableFlags(FLAG_BLACK_SCREEN_ON_SCENE_CONTAINER_START_FIX)
    fun isVisible_false_onlyAfterIdleComposed() =
        kosmos.runTest {
            assertThat(underTest.isVisible).isTrue()

            unlockDevice()
            sceneInteractor.changeScene(Scenes.Gone, "Switch to Gone to make isVisible be false.")
            assertThat(underTest.isVisible).isTrue()

            sceneInteractor.onIdleSceneEnteredComposition(Scenes.Gone)
            assertThat(underTest.isVisible).isFalse()

            unlockDevice()
            sceneInteractor.changeScene(
                Scenes.Lockscreen,
                "Switch to Lockscreen to make isVisible be false.",
            )
            assertThat(underTest.isVisible).isTrue()
        }

    @Test
    @DisableFlags(FLAG_BLACK_SCREEN_ON_SCENE_CONTAINER_START_FIX)
    fun isVisible_false_immediately_ifFlagDisabled() =
        kosmos.runTest {
            assertThat(underTest.isVisible).isTrue()

            unlockDevice()
            sceneInteractor.changeScene(Scenes.Gone, "Switch to Gone to make isVisible be false.")
            assertThat(underTest.isVisible).isFalse()

            unlockDevice()
            sceneInteractor.changeScene(
                Scenes.Lockscreen,
                "Switch to Lockscreen to make isVisible be false.",
            )
            assertThat(underTest.isVisible).isTrue()
        }

    @Test
    fun sceneTransition() =
        kosmos.runTest {
            enableSingleShade()
            assertThat(underTest.currentScene).isEqualTo(Scenes.Lockscreen)

            fakeSceneDataSource.changeScene(Scenes.Shade)

            assertThat(underTest.currentScene).isEqualTo(Scenes.Shade)
        }

    @Test
    fun canChangeScene_whenAllowed_switchingFromGone_returnsTrue() =
        kosmos.runTest {
            fakeSceneDataSource.changeScene(toScene = Scenes.Gone)
            assertThat(underTest.currentScene).isEqualTo(Scenes.Gone)

            sceneContainerConfig.sceneKeys
                .filter { it != underTest.currentScene }
                .forEach { toScene ->
                    assertWithMessage("Scene $toScene incorrectly protected when allowed")
                        .that(underTest.canChangeScene(toScene = toScene))
                        .isTrue()
                }
        }

    @Test
    fun canChangeScene_whenAllowed_switchingFromLockscreen_returnsTrue() =
        kosmos.runTest {
            fakeSceneDataSource.changeScene(toScene = Scenes.Lockscreen)
            assertThat(underTest.currentScene).isEqualTo(Scenes.Lockscreen)

            sceneContainerConfig.sceneKeys
                .filter { it != underTest.currentScene && it != Scenes.Gone }
                .forEach { toScene ->
                    assertWithMessage("Scene $toScene incorrectly protected when allowed")
                        .that(underTest.canChangeScene(toScene = toScene))
                        .isTrue()
                }
        }

    @Test
    fun canChangeScene_whenNotAllowed_fromLockscreen_toFalsingProtectedScenes_returnsFalse() =
        kosmos.runTest {
            fakeFalsingManager.setIsFalseTouch(true) // not allowed by falsing
            fakeSceneDataSource.changeScene(toScene = Scenes.Lockscreen)
            assertThat(underTest.currentScene).isEqualTo(Scenes.Lockscreen)

            sceneContainerConfig.sceneKeys
                .filter { it != underTest.currentScene }
                .filter {
                    // These scenes are not currently falsing protected.
                    it != Scenes.Dream && it != Scenes.Occluded
                }
                .forEach { toScene ->
                    // Lockscreen => toScene, initiatedByUserInput=true
                    sceneInteractor.setTransitionState(
                        flowOf(
                            ObservableTransitionState.Transition(
                                fromScene = Scenes.Lockscreen,
                                toScene = toScene,
                                currentScene = flowOf(Scenes.Lockscreen),
                                progress = flowOf(0.5f),
                                isInitiatedByUserInput = true,
                                isUserInputOngoing = flowOf(true),
                            )
                        )
                    )
                    runCurrent()
                    assertThat(sceneInteractor.transitionStateFlow).isNotNull()
                    sendMotionEventUp()

                    assertWithMessage("Protected scene $toScene not properly protected")
                        .that(underTest.canChangeScene(toScene = toScene))
                        .isFalse()
                }
        }

    private fun sendMotionEventUp() {
        val motionEventUp = mock<MotionEvent>()
        whenever(motionEventUp.actionMasked).thenReturn(MotionEvent.ACTION_UP)
        underTest.onMotionEvent(motionEventUp)
    }

    @Test
    fun canChangeScene_whenNotAllowed_fromLockscreen_toFalsingUnprotectedScenes_returnsTrue() =
        kosmos.runTest {
            fakeFalsingManager.setIsFalseTouch(true) // not allowed by falsing
            fakeSceneDataSource.changeScene(toScene = Scenes.Lockscreen)
            assertThat(underTest.currentScene).isEqualTo(Scenes.Lockscreen)

            sceneContainerConfig.sceneKeys
                .filter {
                    // Moving to the Communal scene is not currently falsing protected.
                    it == Scenes.Dream || it == Scenes.Occluded
                }
                .forEach { toScene ->
                    // Lockscreen => toScene, initiatedByUserInput=true
                    sceneInteractor.setTransitionState(
                        flowOf(
                            ObservableTransitionState.Transition(
                                fromScene = Scenes.Lockscreen,
                                toScene = toScene,
                                currentScene = flowOf(Scenes.Lockscreen),
                                progress = flowOf(0.5f),
                                isInitiatedByUserInput = true,
                                isUserInputOngoing = flowOf(true),
                            )
                        )
                    )
                    runCurrent()
                    sendMotionEventUp()

                    assertWithMessage("Unprotected scene $toScene is incorrectly protected")
                        .that(underTest.canChangeScene(toScene = toScene))
                        .isTrue()
                }
        }

    @Test
    fun canChangeScene_whenNotAllowed_fromGone_toAnyOtherScene_returnsTrue() =
        kosmos.runTest {
            fakeFalsingManager.setIsFalseTouch(true) // not allowed by falsing
            fakeSceneDataSource.changeScene(toScene = Scenes.Gone)
            assertThat(underTest.currentScene).isEqualTo(Scenes.Gone)

            sceneContainerConfig.sceneKeys
                .filter { it != underTest.currentScene }
                .forEach { toScene ->
                    // Gone => toScene, initiatedByUserInput=true
                    sceneInteractor.setTransitionState(
                        flowOf(
                            ObservableTransitionState.Transition(
                                fromScene = Scenes.Gone,
                                toScene = toScene,
                                currentScene = flowOf(Scenes.Lockscreen),
                                progress = flowOf(0.5f),
                                isInitiatedByUserInput = true,
                                isUserInputOngoing = flowOf(true),
                            )
                        )
                    )
                    runCurrent()
                    sendMotionEventUp()

                    assertWithMessage("Protected scene $toScene not properly protected")
                        .that(underTest.canChangeScene(toScene = toScene))
                        .isTrue()
                }
        }

    @Test
    fun canChangeScene_toGone_whenLocked_returnsFalse() =
        kosmos.runTest {
            assertThat(currentValue(deviceUnlockedInteractor.deviceUnlockStatus).isUnlocked)
                .isFalse()
            assertThat(underTest.currentScene).isNotEqualTo(Scenes.Gone)

            assertThat(underTest.canChangeScene(toScene = Scenes.Gone)).isFalse()
        }

    @Test
    fun canChangeScene_toGone_whenUnlocked_returnsTrue() =
        kosmos.runTest {
            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            assertThat(currentValue(deviceUnlockedInteractor.deviceUnlockStatus).isUnlocked)
                .isTrue()
            assertThat(underTest.currentScene).isNotEqualTo(Scenes.Gone)

            assertThat(underTest.canChangeScene(toScene = Scenes.Gone)).isTrue()
        }

    @Test
    fun canShowOrReplaceOverlay_whenAllowed_showingWhileOnGone_returnsTrue() =
        kosmos.runTest {
            fakeSceneDataSource.changeScene(toScene = Scenes.Gone)
            assertThat(underTest.currentScene).isEqualTo(Scenes.Gone)

            sceneContainerConfig.overlayKeys.forEach { overlay ->
                assertWithMessage("Overlay $overlay incorrectly protected when allowed")
                    .that(underTest.canShowOrReplaceOverlay(newlyShown = overlay))
                    .isTrue()
            }
        }

    @Test
    fun canShowOrReplaceOverlay_whenAllowed_showingWhileOnLockscreen_returnsTrue() =
        kosmos.runTest {
            fakeSceneDataSource.changeScene(toScene = Scenes.Lockscreen)
            assertThat(underTest.currentScene).isEqualTo(Scenes.Lockscreen)

            sceneContainerConfig.overlayKeys.forEach { overlay ->
                assertWithMessage("Overlay $overlay incorrectly protected when allowed")
                    .that(underTest.canShowOrReplaceOverlay(newlyShown = overlay))
                    .isTrue()
            }
        }

    @Test
    fun canShowOrReplaceOverlay_whenNotAllowed_whileOnLockscreen_returnsFalse() =
        kosmos.runTest {
            fakeFalsingManager.setIsFalseTouch(true) // not allowed by falsing
            fakeSceneDataSource.changeScene(toScene = Scenes.Lockscreen)
            assertThat(underTest.currentScene).isEqualTo(Scenes.Lockscreen)

            sceneContainerConfig.overlayKeys.forEach { overlay ->
                // Lockscreen => showOverlay(overlay), isInitiatedByUserInput=true
                sceneInteractor.setTransitionState(
                    flowOf(
                        ObservableTransitionState.Transition.showOverlay(
                            overlay = overlay,
                            fromScene = Scenes.Lockscreen,
                            currentOverlays = flowOf(emptySet()),
                            progress = flowOf(.5f),
                            isInitiatedByUserInput = true,
                            isUserInputOngoing = flowOf(true),
                        )
                    )
                )
                runCurrent()
                sendMotionEventUp()

                assertWithMessage("Protected overlay $overlay not properly protected")
                    .that(underTest.canShowOrReplaceOverlay(newlyShown = overlay))
                    .isFalse()
            }
        }

    @Test
    fun canShowOrReplaceOverlay_whenNotAllowed_whileOnGone_returnsTrue() =
        kosmos.runTest {
            fakeFalsingManager.setIsFalseTouch(true) // not allowed by falsing
            fakeSceneDataSource.changeScene(toScene = Scenes.Gone)
            assertThat(underTest.currentScene).isEqualTo(Scenes.Gone)

            sceneContainerConfig.overlayKeys.forEach { overlay ->
                // Gone => showOverlay(overlay), isInitiatedByUserInput=true
                sceneInteractor.setTransitionState(
                    flowOf(
                        ObservableTransitionState.Transition.showOverlay(
                            overlay = overlay,
                            fromScene = Scenes.Gone,
                            currentOverlays = flowOf(emptySet()),
                            progress = flowOf(.5f),
                            isInitiatedByUserInput = true,
                            isUserInputOngoing = flowOf(true),
                        )
                    )
                )
                runCurrent()
                sendMotionEventUp()

                assertWithMessage("Protected overlay $overlay not properly protected")
                    .that(underTest.canShowOrReplaceOverlay(newlyShown = overlay))
                    .isTrue()
            }
        }

    @Test
    fun userInput() =
        kosmos.runTest {
            assertThat(fakePowerRepository.userTouchRegistered).isFalse()
            underTest.onMotionEvent(mock())
            assertThat(fakePowerRepository.userTouchRegistered).isTrue()
        }

    @Test
    fun userInputOnEmptySpace_insideEvent() =
        kosmos.runTest {
            assertThat(fakeRemoteInputRepository.areRemoteInputsClosed).isFalse()
            val insideMotionEvent = MotionEvent.obtain(0, 0, ACTION_DOWN, 0f, 0f, 0)
            underTest.onEmptySpaceMotionEvent(insideMotionEvent)
            assertThat(fakeRemoteInputRepository.areRemoteInputsClosed).isFalse()
        }

    @Test
    fun userInputOnEmptySpace_outsideEvent_remoteInputActive() =
        kosmos.runTest {
            fakeRemoteInputRepository.isRemoteInputActive.value = true
            assertThat(fakeRemoteInputRepository.areRemoteInputsClosed).isFalse()
            val outsideMotionEvent = MotionEvent.obtain(0, 0, ACTION_OUTSIDE, 0f, 0f, 0)
            underTest.onEmptySpaceMotionEvent(outsideMotionEvent)
            assertThat(fakeRemoteInputRepository.areRemoteInputsClosed).isTrue()
        }

    @Test
    fun userInputOnEmptySpace_outsideEvent_remoteInputInactive() =
        kosmos.runTest {
            fakeRemoteInputRepository.isRemoteInputActive.value = false
            assertThat(fakeRemoteInputRepository.areRemoteInputsClosed).isFalse()
            val outsideMotionEvent = MotionEvent.obtain(0, 0, ACTION_OUTSIDE, 0f, 0f, 0)
            underTest.onEmptySpaceMotionEvent(outsideMotionEvent)
            assertThat(fakeRemoteInputRepository.areRemoteInputsClosed).isFalse()
        }

    @Test
    fun remoteUserInteraction_keepsContainerVisible() =
        kosmos.runTest {
            unlockDevice()
            sceneInteractor.changeScene(Scenes.Gone, "Switch to Gone to make isVisible be false.")
            sceneInteractor.onIdleSceneEnteredComposition(Scenes.Gone)
            assertThat(underTest.isVisible).isFalse()

            sceneInteractor.onRemoteUserInputStarted("reason")
            assertThat(underTest.isVisible).isTrue()

            underTest.onMotionEvent(mock { on { actionMasked } doReturn MotionEvent.ACTION_UP })

            assertThat(underTest.isVisible).isFalse()
        }

    @Test
    fun getActionableContentKey_noOverlays_returnsCurrentScene() =
        kosmos.runTest {
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            assertThat(underTest.currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).isEmpty()

            val actionableContentKey =
                underTest.getActionableContentKey(
                    currentScene = checkNotNull(underTest.currentScene),
                    currentOverlays = checkNotNull(currentOverlays),
                    overlayByKey = fakeOverlaysByKeys,
                )

            assertThat(actionableContentKey).isEqualTo(Scenes.Lockscreen)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun getActionableContentKey_multipleOverlays_returnsTopOverlay() =
        kosmos.runTest {
            enableDualShade()
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            fakeSceneDataSource.showOverlay(Overlays.QuickSettingsShade)
            fakeSceneDataSource.showOverlay(Overlays.NotificationsShade)
            assertThat(underTest.currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays)
                .containsExactly(Overlays.QuickSettingsShade, Overlays.NotificationsShade)

            val actionableContentKey =
                underTest.getActionableContentKey(
                    currentScene = checkNotNull(underTest.currentScene),
                    currentOverlays = checkNotNull(currentOverlays),
                    overlayByKey = fakeOverlaysByKeys,
                )

            assertThat(actionableContentKey).isEqualTo(Overlays.QuickSettingsShade)
        }

    @Test
    fun edgeDetector_singleShade_usesDynamicEdgeDetector() =
        kosmos.runTest {
            val shadeMode by collectLastValue(shadeMode)
            enableSingleShade()

            assertThat(shadeMode).isEqualTo(ShadeMode.Single)
            assertThat(underTest.swipeSourceDetector)
                .isInstanceOf(DynamicSizeEdgeDetector::class.java)
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun edgeDetector_splitShade_usesDynamicEdgeDetector() =
        kosmos.runTest {
            val shadeMode by collectLastValue(shadeMode)
            enableSplitShade()

            assertThat(shadeMode).isEqualTo(ShadeMode.Split)
            assertThat(underTest.swipeSourceDetector)
                .isInstanceOf(DynamicSizeEdgeDetector::class.java)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun edgeDetector_dualShade_narrowScreen_usesSceneContainerSwipeDetector() =
        kosmos.runTest {
            val shadeMode by collectLastValue(shadeMode)
            enableDualShade(wideLayout = false)

            assertThat(shadeMode).isEqualTo(ShadeMode.Dual)
            assertThat(underTest.swipeSourceDetector)
                .isInstanceOf(SceneContainerSwipeDetector::class.java)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun edgeDetector_dualShade_wideScreen_usesSceneContainerSwipeDetector() =
        kosmos.runTest {
            val shadeMode by collectLastValue(shadeMode)
            enableDualShade(wideLayout = true)

            assertThat(shadeMode).isEqualTo(ShadeMode.Dual)
            assertThat(underTest.swipeSourceDetector)
                .isInstanceOf(SceneContainerSwipeDetector::class.java)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun onEmptySpaceMotionEvent_hidesDualShadeOverlays_onDesktopMode() =
        kosmos.runTest {
            // GIVEN a device in desktop mode with dual shade enabled and an overlay present
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            enableDualShade()
            enableUsingDesktopStatusBar()
            sceneInteractor.showOverlay(Overlays.QuickSettingsShade, "test")
            assertThat(currentOverlays).isNotEmpty()

            // WHEN a touch event occurs outside the shade window and status bar
            underTest.onEmptySpaceMotionEvent(MotionEvent.obtain(0, 0, ACTION_OUTSIDE, 0f, 200f, 0))

            // THEN the overlay is hidden
            assertThat(currentOverlays).isEmpty()
        }

    @Test
    fun lockscreenAod_updateNavigationBarVisibility_hide() =
        kosmos.runTest {
            val windowInsetsController: WindowInsetsController = mock()
            underTest.updateNavigationBarVisibility(
                windowInsetsController = windowInsetsController,
                hasBackAction = false,
                sceneKey = Scenes.Lockscreen,
                aodOrDozing = true,
                hasAnyEnabledBackHandler = false,
            )

            verify(windowInsetsController).hide(eq(WindowInsetsCompat.Type.navigationBars()))
        }

    @Test
    fun lockscreen_updateNavigationBarVisibility_show() =
        kosmos.runTest {
            val windowInsetsController: WindowInsetsController = mock()
            underTest.updateNavigationBarVisibility(
                windowInsetsController = windowInsetsController,
                hasBackAction = false,
                sceneKey = Scenes.Lockscreen,
                aodOrDozing = false,
                hasAnyEnabledBackHandler = false,
            )

            verify(windowInsetsController).show(eq(WindowInsetsCompat.Type.navigationBars()))
        }

    @Test
    fun gone_updateNavigationBarVisibility_show() =
        kosmos.runTest {
            val windowInsetsController: WindowInsetsController = mock()
            underTest.updateNavigationBarVisibility(
                windowInsetsController = windowInsetsController,
                hasBackAction = false,
                sceneKey = Scenes.Gone,
                aodOrDozing = false,
                hasAnyEnabledBackHandler = false,
            )

            verify(windowInsetsController).show(eq(WindowInsetsCompat.Type.navigationBars()))
        }

    @Test
    fun updateNavigationBarVisibilityCalledMultipleTimes_showOnlyCalledOnce() =
        kosmos.runTest {
            val windowInsetsController: WindowInsetsController = mock()
            for (i in 0..5) {
                underTest.updateNavigationBarVisibility(
                    windowInsetsController = windowInsetsController,
                    hasBackAction = true,
                    sceneKey = Scenes.Gone,
                    aodOrDozing = false,
                    hasAnyEnabledBackHandler = false,
                )
            }
            // verify "show" is only called once
            verify(windowInsetsController).show(eq(WindowInsetsCompat.Type.navigationBars()))
        }

    @Test
    fun hasAnyEnabledBackHandler_updateNavigationBarVisibility_show() =
        kosmos.runTest {
            val windowInsetsController: WindowInsetsController = mock()
            underTest.updateNavigationBarVisibility(
                windowInsetsController = windowInsetsController,
                hasBackAction = false,
                sceneKey = Scenes.QuickSettings,
                aodOrDozing = false,
                hasAnyEnabledBackHandler = true,
            )

            verify(windowInsetsController).show(eq(WindowInsetsCompat.Type.navigationBars()))
        }

    @Test
    fun accessibilityTitle_bouncerOverLockscreen() =
        kosmos.runTest {
            kosmos.fakeAccessibilityRepository.isEnabledFiltered.value = true
            sceneInteractor.changeScene(Scenes.Lockscreen, "Switch to Lockscreen for test pre-req")
            fakeSceneDataSource.showOverlay(Overlays.Bouncer)

            assertThat(underTest.accessibilityTitle).isEqualTo(null)
        }

    @Test
    fun accessibilityTitle_lockscreen_accessibilityDisabled() =
        kosmos.runTest {
            kosmos.fakeAccessibilityRepository.isEnabledFiltered.value = false
            sceneInteractor.changeScene(Scenes.Lockscreen, "Switch to Lockscreen for test pre-req")

            assertThat(underTest.accessibilityTitle).isEqualTo(null)
        }

    @Test
    fun accessibilityTitle_lockscreen() =
        kosmos.runTest {
            kosmos.fakeAccessibilityRepository.isEnabledFiltered.value = true
            sceneInteractor.changeScene(Scenes.Lockscreen, "Switch to Lockscreen for test pre-req")

            assertThat(underTest.accessibilityTitle)
                .isEqualTo(R.string.accessibility_desc_lock_screen)
        }

    @Test
    fun accessibilityTitle_singleShade() =
        kosmos.runTest {
            kosmos.fakeAccessibilityRepository.isEnabledFiltered.value = true
            enableSingleShade()

            sceneInteractor.changeScene(Scenes.Shade, "Switch to NotificationShade for test")
            assertThat(underTest.accessibilityTitle)
                .isEqualTo(R.string.accessibility_desc_notification_shade)

            sceneInteractor.changeScene(Scenes.QuickSettings, "Switch to Quicksettings for test")
            assertThat(underTest.accessibilityTitle)
                .isEqualTo(R.string.accessibility_desc_quick_settings)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun accessibilityTitle_dualShade() =
        kosmos.runTest {
            kosmos.fakeAccessibilityRepository.isEnabledFiltered.value = true
            enableDualShade()

            fakeSceneDataSource.showOverlay(Overlays.NotificationsShade)
            assertThat(underTest.accessibilityTitle)
                .isEqualTo(R.string.accessibility_desc_notification_shade)

            fakeSceneDataSource.showOverlay(Overlays.QuickSettingsShade)
            assertThat(underTest.accessibilityTitle)
                .isEqualTo(R.string.accessibility_desc_quick_settings)
        }
}
