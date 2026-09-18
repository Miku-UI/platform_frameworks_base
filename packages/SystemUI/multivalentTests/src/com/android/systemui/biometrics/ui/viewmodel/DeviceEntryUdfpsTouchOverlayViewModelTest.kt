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

package com.android.systemui.biometrics.ui.viewmodel

import android.platform.test.annotations.EnableFlags
import android.security.Flags.FLAG_SECURE_LOCK_DEVICE
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.systemui.SysuiTestCase
import com.android.systemui.biometrics.ui.viewmodel.DeviceEntryUdfpsTouchOverlayViewModel.Companion.ALLOW_TOUCH_SHADE_EXPANSION_MAX_THRESHOLD
import com.android.systemui.bouncer.data.repository.fakeKeyguardBouncerRepository
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.flags.Flags
import com.android.systemui.flags.fakeFeatureFlagsClassic
import com.android.systemui.keyguard.data.repository.fakeBiometricSettingsRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.ui.viewmodel.fakeDeviceEntryIconViewModelTransition
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.scene.data.repository.Transition
import com.android.systemui.scene.data.repository.setSceneTransition
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.securelockdevice.data.repository.fakeSecureLockDeviceRepository
import com.android.systemui.statusbar.phone.SystemUIDialogManager
import com.android.systemui.statusbar.phone.mockSystemUIDialogManager
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

@SmallTest
@RunWith(AndroidJUnit4::class)
class DeviceEntryUdfpsTouchOverlayViewModelTest : SysuiTestCase() {
    private val kosmos =
        testKosmos().useUnconfinedTestDispatcher().apply {
            fakeFeatureFlagsClassic.apply { set(Flags.FULL_SCREEN_USER_SWITCHER, true) }
        }
    private val Kosmos.underTest by Kosmos.Fixture { deviceEntryUdfpsTouchOverlayViewModel }

    @Captor
    private lateinit var sysuiDialogListenerCaptor: ArgumentCaptor<SystemUIDialogManager.Listener>

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
    }

    @Test
    fun dialogShowing_shouldHandleTouchesFalse() =
        kosmos.runTest {
            val shouldHandleTouches by collectLastValue(underTest.shouldHandleTouches)

            deviceEntryIconViewModelVisible(true, kosmos.testScope)
            fakeDeviceEntryIconViewModelTransition.setDeviceEntryParentViewAlpha(1f)

            verify(mockSystemUIDialogManager).registerListener(sysuiDialogListenerCaptor.capture())
            sysuiDialogListenerCaptor.value.shouldHideAffordances(true)

            assertThat(shouldHandleTouches).isFalse()
        }

    @Test
    @DisableSceneContainer
    fun deviceEntryAlphaIsSmall_shouldHandleTouchesFalse() =
        kosmos.runTest {
            val shouldHandleTouches by collectLastValue(underTest.shouldHandleTouches)

            fakeDeviceEntryIconViewModelTransition.setDeviceEntryParentViewAlpha(.3f)

            verify(mockSystemUIDialogManager).registerListener(sysuiDialogListenerCaptor.capture())
            sysuiDialogListenerCaptor.value.shouldHideAffordances(false)

            assertThat(shouldHandleTouches).isFalse()
        }

    @Test
    @DisableSceneContainer
    fun deviceEntryFullyShowing_noDialog_shouldHandleTouchesTrue() =
        kosmos.runTest {
            val shouldHandleTouches by collectLastValue(underTest.shouldHandleTouches)

            deviceEntryIconViewModelVisible(true, kosmos.testScope)
            fakeDeviceEntryIconViewModelTransition.setDeviceEntryParentViewAlpha(1f)

            verify(mockSystemUIDialogManager).registerListener(sysuiDialogListenerCaptor.capture())
            sysuiDialogListenerCaptor.value.shouldHideAffordances(false)

            assertThat(shouldHandleTouches).isTrue()
        }

    @Test
    fun alternateBouncerVisible_deviceEntryAlphaIsSmall_shouldHandleTouchesTrue() =
        kosmos.runTest {
            val shouldHandleTouches by collectLastValue(underTest.shouldHandleTouches)

            deviceEntryIconViewModelVisible(false, kosmos.testScope)
            fakeDeviceEntryIconViewModelTransition.setDeviceEntryParentViewAlpha(0f)

            fakeKeyguardBouncerRepository.setAlternateVisible(true)

            assertThat(shouldHandleTouches).isTrue()
        }

    @Test
    fun transitioningToDozing_shouldHandleTouchesTrue() =
        kosmos.runTest {
            val shouldHandleTouches by collectLastValue(underTest.shouldHandleTouches)

            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.DOZING,
                testScope = testScope,
            )

            assertThat(shouldHandleTouches).isTrue()
        }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @EnableSceneContainer
    @Test
    fun duringSecureLockDeviceBiometricAuth_enableSceneContainer_shouldHandleTouchesTrue() =
        kosmos.runTest {
            val shouldHandleTouches by collectLastValue(underTest.shouldHandleTouches)

            deviceEntryIconViewModelVisible(false, kosmos.testScope)
            fakeDeviceEntryIconViewModelTransition.setDeviceEntryParentViewAlpha(0f)

            fakeSecureLockDeviceRepository.onSecureLockDeviceEnabled()
            fakeSecureLockDeviceRepository.onSuccessfulPrimaryAuth()

            assertThat(shouldHandleTouches).isTrue()
        }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @DisableSceneContainer
    @Test
    fun duringSecureLockDeviceBiometricAuth_disableSceneContainer_shouldHandleTouchesTrue() =
        kosmos.runTest {
            val shouldHandleTouches by collectLastValue(underTest.shouldHandleTouches)

            fakeDeviceEntryIconViewModelTransition.setDeviceEntryParentViewAlpha(0f)

            fakeSecureLockDeviceRepository.onSecureLockDeviceEnabled()
            fakeSecureLockDeviceRepository.onSuccessfulPrimaryAuth()

            assertThat(shouldHandleTouches).isTrue()
        }

    @Test
    @EnableSceneContainer
    fun idleOnLockscreen_shouldHandleTouchesTrue() =
        kosmos.runTest {
            val shouldHandleTouches by collectLastValue(underTest.shouldHandleTouches)
            sceneInteractor.changeScene(
                Scenes.Lockscreen,
                "DeviceEntryUdfpsTouchOverlayViewModelTest",
            )
            sceneInteractor.setTransitionState(
                flowOf(ObservableTransitionState.Idle(Scenes.Lockscreen))
            )
            assertThat(shouldHandleTouches).isTrue()
        }

    @Test
    @EnableSceneContainer
    fun idleOnLockscreen_withBouncerOverlay_shouldHandleTouchesFalse() =
        kosmos.runTest {
            val shouldHandleTouches by collectLastValue(underTest.shouldHandleTouches)
            sceneInteractor.changeScene(
                Scenes.Lockscreen,
                "DeviceEntryUdfpsTouchOverlayViewModelTest",
            )
            sceneInteractor.showOverlay(
                Overlays.Bouncer,
                "DeviceEntryUdfpsTouchOverlayViewModelTest",
            )
            sceneInteractor.setTransitionState(
                flowOf(ObservableTransitionState.Idle(Scenes.Lockscreen, setOf(Overlays.Bouncer)))
            )
            assertThat(shouldHandleTouches).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun idleOnShade_shouldHandleTouchesFalse() =
        kosmos.runTest {
            val shouldHandleTouches by collectLastValue(underTest.shouldHandleTouches)

            sceneInteractor.changeScene(Scenes.Shade, "DeviceEntryUdfpsTouchOverlayViewModelTest")
            sceneInteractor.setTransitionState(flowOf(ObservableTransitionState.Idle(Scenes.Shade)))
            assertThat(shouldHandleTouches).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun transitionToLockscreen_progressHigh_shouldHandleTouchesTrue() =
        kosmos.runTest {
            val shouldHandleTouches by collectLastValue(underTest.shouldHandleTouches)
            kosmos.setSceneTransition(
                Transition(
                    Scenes.QuickSettings,
                    Scenes.Lockscreen,
                    progress = flowOf(1 - ALLOW_TOUCH_SHADE_EXPANSION_MAX_THRESHOLD + .01f),
                )
            )
            assertThat(shouldHandleTouches).isTrue()
        }

    @Test
    @EnableSceneContainer
    fun transitionToLockscreen_progressLow_shouldHandleTouchesFalse() =
        kosmos.runTest {
            val shouldHandleTouches by collectLastValue(underTest.shouldHandleTouches)
            kosmos.setSceneTransition(
                Transition(
                    Scenes.QuickSettings,
                    Scenes.Lockscreen,
                    progress = flowOf(1 - ALLOW_TOUCH_SHADE_EXPANSION_MAX_THRESHOLD - .01f),
                )
            )
            assertThat(shouldHandleTouches).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun transitionFromLockscreen_progressLow_shouldHandleTouchesTrue() =
        kosmos.runTest {
            val shouldHandleTouches by collectLastValue(underTest.shouldHandleTouches)
            kosmos.setSceneTransition(
                Transition(
                    Scenes.Lockscreen,
                    Scenes.Shade,
                    progress = flowOf(ALLOW_TOUCH_SHADE_EXPANSION_MAX_THRESHOLD - .01f),
                )
            )
            assertThat(shouldHandleTouches).isTrue()
        }

    @Test
    @EnableSceneContainer
    fun transitionFromQuickSettingsToShade_shouldHandleTouchesFalse() =
        kosmos.runTest {
            val shouldHandleTouches by collectLastValue(underTest.shouldHandleTouches)
            kosmos.setSceneTransition(
                Transition(
                    Scenes.QuickSettings,
                    Scenes.Shade,
                    progress = flowOf(ALLOW_TOUCH_SHADE_EXPANSION_MAX_THRESHOLD - .01f),
                )
            )
            assertThat(shouldHandleTouches).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun transitionFromLockscreen_progressHigh_shouldHandleTouchesFalse() =
        kosmos.runTest {
            val shouldHandleTouches by collectLastValue(underTest.shouldHandleTouches)
            kosmos.setSceneTransition(
                Transition(
                    Scenes.Lockscreen,
                    Scenes.Shade,
                    progress = flowOf(ALLOW_TOUCH_SHADE_EXPANSION_MAX_THRESHOLD + .01f),
                )
            )
            assertThat(shouldHandleTouches).isFalse()
        }

    suspend fun deviceEntryIconViewModelVisible(visible: Boolean, testScope: TestScope) {
        if (SceneContainerFlag.isEnabled) {
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(true)
            if (visible) {
                kosmos.sceneInteractor.changeScene(
                    Scenes.Lockscreen,
                    "DeviceEntryUdfpsTouchOverlayViewModelTest",
                )
                kosmos.fakeKeyguardTransitionRepository.sendTransitionSteps(
                    from = KeyguardState.UNDEFINED,
                    to = KeyguardState.LOCKSCREEN,
                    testScope = testScope,
                )
            } else {
                kosmos.sceneInteractor.changeScene(
                    Scenes.Lockscreen,
                    "DeviceEntryUdfpsTouchOverlayViewModelTest",
                )
                kosmos.fakeKeyguardTransitionRepository.sendTransitionSteps(
                    from = KeyguardState.LOCKSCREEN,
                    to = KeyguardState.UNDEFINED,
                    testScope = testScope,
                )
                kosmos.sceneInteractor.changeScene(
                    Scenes.Shade,
                    "DeviceEntryUdfpsTouchOverlayViewModelTest",
                )
            }
        }
    }
}
