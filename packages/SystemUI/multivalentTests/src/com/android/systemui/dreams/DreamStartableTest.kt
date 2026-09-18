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

package com.android.systemui.dreams

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.service.dream.dreamManager
import android.service.dreams.Flags.FLAG_DRIVE_DREAM_STATE_FROM_OCCLUSION
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.deviceentry.data.repository.fakeDeviceEntryRepository
import com.android.systemui.deviceentry.shared.model.DeviceUnlockStatus
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.KeyguardViewMediator.KEYGUARD_LOCK_AFTER_DELAY_DEFAULT
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFingerprintAuthRepository
import com.android.systemui.keyguard.data.repository.keyguardRepository
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.domain.interactor.biometricUnlockInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardOcclusionInteractor
import com.android.systemui.keyguard.shared.model.BiometricUnlockSource
import com.android.systemui.keyguard.shared.model.SuccessFingerprintAuthenticationStatus
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.scene.data.model.asIterable
import com.android.systemui.scene.domain.interactor.sceneBackInteractor
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.statusbar.phone.BiometricUnlockController
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class DreamStartableTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    @Before
    fun setUp() {
        kosmos.dreamStartable.start()
    }

    /**
     * This test verifies that the dream is stopped when the device is unlocked and the UI
     * transitions to the "Gone" scene.
     */
    @EnableSceneContainer
    @Test
    fun testStopDreamWhenGoingToGone() =
        kosmos.runTest {
            // Start dreaming.
            keyguardRepository.setDreaming(true)
            advanceTimeBy(DREAMING_DELAY_MS)
            sceneInteractor.changeScene(Scenes.Dream, loggingReason = "dream started")

            verify(dreamManager, never()).stopDream()

            // Show the bouncer.
            sceneInteractor.showOverlay(Overlays.Bouncer, loggingReason = "show bouncer")

            verify(dreamManager, never()).stopDream()

            // Authenticate and unlock the device.
            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            fakeDeviceEntryFingerprintAuthRepository.setAuthenticationStatus(
                SuccessFingerprintAuthenticationStatus(0, true)
            )
            sceneInteractor.changeScene(Scenes.Gone, loggingReason = "device unlocked")

            // Verify that the dream is stopped when leaving the dream scene.
            verify(dreamManager).stopDream()
        }

    /**
     * This test ensures that when a dream starts from the bouncer, the scene correctly transitions
     * to the dream scene and the bouncer is hidden.
     */
    @EnableSceneContainer
    @DisableFlags(FLAG_DRIVE_DREAM_STATE_FROM_OCCLUSION)
    @Test
    fun switchesToDreamAndHidesBouncer_whenDreamingStartsFromBouncer() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            sceneInteractor.changeScene(Scenes.Lockscreen, "starting scene")
            sceneInteractor.showOverlay(Overlays.Bouncer, "show bouncer")
            runCurrent()

            // Start dreaming.
            keyguardRepository.setDreaming(true)
            advanceTimeBy(DREAMING_DELAY_MS)

            // Verify that the current scene is the dream and the bouncer overlay is not shown.
            assertThat(currentScene).isEqualTo(Scenes.Dream)
            assertThat(currentOverlays).doesNotContain(Overlays.Bouncer)
        }

    /** This test checks that a dream can start correctly from the notification shade. */
    @EnableSceneContainer
    @DisableFlags(FLAG_DRIVE_DREAM_STATE_FROM_OCCLUSION)
    @Test
    fun switchesToDream_whenDreamingStartsFromShade() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            sceneInteractor.changeScene(Scenes.Shade, "starting scene")
            runCurrent()

            // Start dreaming.
            keyguardRepository.setDreaming(true)
            advanceTimeBy(DREAMING_DELAY_MS)

            assertThat(currentScene).isEqualTo(Scenes.Dream)
        }

    /**
     * This test verifies that when a dream stops and the device is locked, the UI correctly returns
     * to the lockscreen.
     */
    @EnableSceneContainer
    @DisableFlags(FLAG_DRIVE_DREAM_STATE_FROM_OCCLUSION)
    @Test
    fun switchesToLockscreen_whenDreamStopsAndDeviceIsLocked() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)

            // Start on the Shade scene with the device locked.
            sceneInteractor.changeScene(Scenes.Shade, "starting on shade")
            fakeDeviceEntryRepository.deviceUnlockStatus.value =
                DeviceUnlockStatus(isUnlocked = false, deviceUnlockSource = null)

            // Set the dream state and advance time before starting the component to ensure the
            // initial state is correct.
            keyguardRepository.setDreaming(true)
            advanceTimeBy(DREAMING_DELAY_MS)
            runCurrent()

            // The scene should now be Dream because isDreamingNotDozing was true on start.
            assertThat(currentScene).isEqualTo(Scenes.Dream)

            // Stop dreaming.
            keyguardRepository.setDreaming(false)
            advanceTimeBy(DREAMING_DELAY_MS)
            runCurrent()

            // Verify that the UI has returned to the lockscreen.
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
        }

    /**
     * This test ensures that if the device is occluded while dreaming, the dream continues to show
     * and is not replaced by the occluded scene.
     */
    @EnableSceneContainer
    @DisableFlags(FLAG_DRIVE_DREAM_STATE_FROM_OCCLUSION)
    @Test
    fun staysOnDream_whenOccludedWhileDreaming() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)

            // Start on the lockscreen.
            sceneInteractor.changeScene(Scenes.Lockscreen, "starting on lockscreen")
            runCurrent()

            // Start dreaming.
            keyguardRepository.setDreaming(true)
            advanceTimeBy(DREAMING_DELAY_MS)
            runCurrent()
            assertThat(currentScene).isEqualTo(Scenes.Dream)

            // Occlude the device.
            keyguardOcclusionInteractor.setOccludedFromWm(true)
            runCurrent()

            // Verify that the dream scene is still showing.
            assertThat(currentScene).isEqualTo(Scenes.Dream)

            // Stop dreaming.
            keyguardRepository.setDreaming(false)
            advanceTimeBy(DREAMING_DELAY_MS)
            runCurrent()

            // Verify that the scene switches to occluded if the device is still occluded.
            assertThat(currentScene).isEqualTo(Scenes.Occluded)
        }

    /**
     * This test checks that the lockscreen is shown when the device is dozing, even if the device
     * is also dreaming.
     */
    @EnableSceneContainer
    @DisableFlags(FLAG_DRIVE_DREAM_STATE_FROM_OCCLUSION)
    @Test
    fun staysOnLockscreen_whenDozing() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)

            // Start on the lockscreen.
            sceneInteractor.changeScene(Scenes.Lockscreen, "starting on lockscreen")
            runCurrent()

            // When dozing starts, the scene should stay on the lockscreen, even if dreaming is
            // active.
            keyguardRepository.setIsDozing(true)
            keyguardRepository.setDreaming(true)
            runCurrent()

            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
        }

    @EnableSceneContainer
    @DisableFlags(FLAG_DRIVE_DREAM_STATE_FROM_OCCLUSION)
    @Test
    fun switchFromShadeToDream_whenDreamStarted() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)

            // GIVEN we are on the shade
            sceneInteractor.changeScene(Scenes.Shade, "reason")
            runCurrent()
            assertThat(currentScene).isEqualTo(Scenes.Shade)

            // WHEN dreaming is started
            keyguardRepository.setDreaming(true)
            advanceTimeBy(DREAMING_DELAY_MS)
            runCurrent()

            // THEN we should be on the dream scene
            assertThat(currentScene).isEqualTo(Scenes.Dream)
        }

    @EnableSceneContainer
    @DisableFlags(FLAG_DRIVE_DREAM_STATE_FROM_OCCLUSION)
    @Test
    fun switchFromQuickSettingsToDream_whenDreamStarted() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)

            // GIVEN we are on quick settings
            enableSingleShade() // Scenes.QuickSettings is only available in SingleShade
            sceneInteractor.changeScene(Scenes.QuickSettings, "reason")
            runCurrent()
            assertThat(currentScene).isEqualTo(Scenes.QuickSettings)

            // WHEN dreaming is started
            keyguardRepository.setDreaming(true)
            advanceTimeBy(DREAMING_DELAY_MS)
            runCurrent()

            // THEN we should be on the dream scene
            assertThat(currentScene).isEqualTo(Scenes.Dream)
        }

    @EnableSceneContainer
    @DisableFlags(FLAG_DRIVE_DREAM_STATE_FROM_OCCLUSION)
    @Test
    fun snapsToHomeWhenBouncerShowing() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            // GIVEN the device is dreaming and locked and bouncer is showing over the dream
            sceneInteractor.changeScene(Scenes.Lockscreen, "starting on lockscreen")
            fakeDeviceEntryRepository.deviceUnlockStatus.value =
                DeviceUnlockStatus(isUnlocked = false, deviceUnlockSource = null)
            keyguardRepository.setDreaming(true)
            advanceTimeBy(DREAMING_DELAY_MS)
            sceneInteractor.showOverlay(Overlays.Bouncer, "show bouncer over dream")

            // The bouncer should be showing over the dream
            assertThat(currentScene).isEqualTo(Scenes.Dream)
            assertThat(currentOverlays).containsExactly(Overlays.Bouncer)

            // Stop dreaming.
            keyguardRepository.setDreaming(false)
            advanceTimeBy(DREAMING_DELAY_MS)

            // Verify that the UI has returned to the lockscreen and bouncer is still showing
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).containsExactly(Overlays.Bouncer)
        }

    @EnableSceneContainer
    @DisableFlags(FLAG_DRIVE_DREAM_STATE_FROM_OCCLUSION)
    @Test
    fun switchFromOccludedToDream_whenDreamStarted() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)

            // GIVEN we are on occluded
            sceneInteractor.changeScene(Scenes.Occluded, "starting on occluded")
            runCurrent()
            assertThat(currentScene).isEqualTo(Scenes.Occluded)

            // WHEN dreaming is started
            keyguardRepository.setDreaming(true)
            advanceTimeBy(DREAMING_DELAY_MS)
            runCurrent()

            // THEN we should be on the dream scene
            assertThat(currentScene).isEqualTo(Scenes.Dream)
        }

    @EnableSceneContainer
    @DisableFlags(FLAG_DRIVE_DREAM_STATE_FROM_OCCLUSION)
    @Test
    fun switchFromCommunalToDream_whenDreamStarted() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)

            // GIVEN we are on communal
            sceneInteractor.changeScene(Scenes.Communal, "reason")
            runCurrent()
            assertThat(currentScene).isEqualTo(Scenes.Communal)

            // WHEN dreaming is started
            keyguardRepository.setDreaming(true)
            advanceTimeBy(DREAMING_DELAY_MS)
            runCurrent()

            // THEN we should be on the dream scene
            assertThat(currentScene).isEqualTo(Scenes.Dream)
        }

    @EnableSceneContainer
    @DisableFlags(FLAG_DRIVE_DREAM_STATE_FROM_OCCLUSION)
    @Test
    fun switchFromDreamToLockscreen_whenDreamStops() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)

            // GIVEN device is locked and dreaming
            sceneInteractor.changeScene(Scenes.Lockscreen, "reason")
            fakeDeviceEntryRepository.deviceUnlockStatus.value =
                DeviceUnlockStatus(isUnlocked = false, deviceUnlockSource = null)
            keyguardRepository.setDreaming(true)
            advanceTimeBy(DREAMING_DELAY_MS)
            runCurrent()
            assertThat(currentScene).isEqualTo(Scenes.Dream)

            // WHEN dreaming is stopped
            keyguardRepository.setDreaming(false)
            advanceTimeBy(DREAMING_DELAY_MS)
            runCurrent()

            // THEN scene changes to lockscreen
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
        }

    @EnableSceneContainer
    @Test
    fun addLockscreenToBackStackAfterDreamStartWhenUnsecured() =
        kosmos.runTest {
            val backStack by collectLastValue(sceneBackInteractor.backStack)

            // GIVEN an unsecured authentication method
            fakeAuthenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.None)
            runCurrent()

            // WHEN dreaming is started
            keyguardRepository.setDreaming(true)
            advanceTimeBy(DREAMING_DELAY_MS)
            runCurrent()

            // THEN the back stack should be empty initially
            assertThat(backStack?.asIterable()?.toList()).isEmpty()

            // WHEN the delay passes
            advanceTimeBy(KEYGUARD_LOCK_AFTER_DELAY_DEFAULT.toLong())
            runCurrent()

            // THEN the back stack should contain the Lockscreen scene
            assertThat(backStack?.asIterable()?.toList()).containsExactly(Scenes.Lockscreen)
        }

    @EnableSceneContainer
    @Test
    @EnableFlags(FLAG_DRIVE_DREAM_STATE_FROM_OCCLUSION)
    fun doesNotSwitchToDream_whenDreamingStarts_ifFlagEnabled() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            sceneInteractor.changeScene(Scenes.Lockscreen, "starting scene")
            runCurrent()

            // Start dreaming.
            keyguardRepository.setDreaming(true)
            advanceTimeBy(DREAMING_DELAY_MS)
            runCurrent()

            // Scene should NOT change to Dream because DreamStartable shouldn't be handling it
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
        }

    private companion object {
        // A delay to move past the initial dreaming delay.
        const val DREAMING_DELAY_MS = KeyguardInteractor.IS_DREAMING_NOT_DOZING_DELAY_MS + 100L
    }
}
