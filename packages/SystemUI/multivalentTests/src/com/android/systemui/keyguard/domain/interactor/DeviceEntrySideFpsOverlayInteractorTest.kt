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

package com.android.systemui.keyguard.domain.interactor

import android.platform.test.annotations.EnableFlags
import android.security.Flags.FLAG_SECURE_LOCK_DEVICE
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.keyguard.keyguardUpdateMonitor
import com.android.systemui.SysuiTestCase
import com.android.systemui.bouncer.data.repository.keyguardBouncerRepository
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.coroutines.collectValues
import com.android.systemui.deviceentry.data.repository.fakeDeviceEntryRepository
import com.android.systemui.deviceentry.shared.model.DeviceUnlockSource
import com.android.systemui.deviceentry.shared.model.DeviceUnlockStatus
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.data.repository.deviceEntryFingerprintAuthRepository
import com.android.systemui.kosmos.testScope
import com.android.systemui.res.R
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.securelockdevice.data.repository.fakeSecureLockDeviceRepository
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.whenever
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

@SmallTest
@RunWith(AndroidJUnit4::class)
class DeviceEntrySideFpsOverlayInteractorTest : SysuiTestCase() {
    @JvmField @Rule var mockitoRule: MockitoRule = MockitoJUnit.rule()

    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope

    private val bouncerRepository = kosmos.keyguardBouncerRepository
    private val deviceEntryFingerprintAuthRepository = kosmos.deviceEntryFingerprintAuthRepository
    private val keyguardUpdateMonitor = kosmos.keyguardUpdateMonitor

    private lateinit var underTest: DeviceEntrySideFpsOverlayInteractor

    @Before
    fun setup() {
        underTest = kosmos.deviceEntrySideFpsOverlayInteractor
    }

    @Test
    fun updatesShowIndicatorForDeviceEntry_onPrimaryBouncerShowing() =
        testScope.runTest {
            val showIndicatorForDeviceEntry by
                collectLastValue(underTest.showIndicatorForDeviceEntry)
            runCurrent()

            updatePrimaryBouncer(
                isShowing = true,
                isAnimatingAway = false,
                fpsDetectionRunning = true,
                isUnlockingWithFpAllowed = true,
            )
            assertThat(showIndicatorForDeviceEntry).isTrue()
        }

    @Test
    fun updatesShowIndicatorForDeviceEntry_onPrimaryBouncerHidden() =
        testScope.runTest {
            val showIndicatorForDeviceEntry by
                collectLastValue(underTest.showIndicatorForDeviceEntry)
            runCurrent()

            updatePrimaryBouncer(
                isShowing = false,
                isAnimatingAway = false,
                fpsDetectionRunning = true,
                isUnlockingWithFpAllowed = true,
            )
            assertThat(showIndicatorForDeviceEntry).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun updatesShowIndicatorForDeviceEntry_onBouncerSceneActive() =
        testScope.runTest {
            val showIndicatorForDeviceEntry by
                collectLastValue(underTest.showIndicatorForDeviceEntry)
            runCurrent()

            updateBouncer(
                isActive = true,
                fpsDetectionRunning = true,
                isUnlockingWithFpAllowed = true,
            )
            assertThat(showIndicatorForDeviceEntry).isTrue()
        }

    @Test
    @EnableSceneContainer
    fun updatesShowIndicatorForDeviceEntry_onBouncerSceneInactive() =
        testScope.runTest {
            val showIndicatorForDeviceEntry by
                collectLastValue(underTest.showIndicatorForDeviceEntry)
            runCurrent()

            updateBouncer(
                isActive = false,
                fpsDetectionRunning = true,
                isUnlockingWithFpAllowed = true,
            )
            assertThat(showIndicatorForDeviceEntry).isFalse()
        }

    @Test
    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    fun updatesShowIndicatorForDeviceEntry_onEnteringAndExitingSecureLockDeviceBiometricAuth() =
        testScope.runTest {
            val showIndicatorForDeviceEntry by
                collectLastValue(underTest.showIndicatorForDeviceEntry)

            // Secure lock device credential auth step
            kosmos.fakeSecureLockDeviceRepository.onSecureLockDeviceEnabled()
            whenever(keyguardUpdateMonitor.isFingerprintDetectionRunning).thenReturn(false)
            whenever(keyguardUpdateMonitor.isUnlockingWithFingerprintAllowed).thenReturn(false)
            runCurrent()
            assertThat(showIndicatorForDeviceEntry).isFalse()

            // Secure lock device biometric auth step
            kosmos.fakeSecureLockDeviceRepository.onSuccessfulPrimaryAuth()
            whenever(keyguardUpdateMonitor.isFingerprintDetectionRunning).thenReturn(true)
            whenever(keyguardUpdateMonitor.isUnlockingWithFingerprintAllowed).thenReturn(true)
            runCurrent()
            assertThat(showIndicatorForDeviceEntry).isTrue()

            // Mock biometric unlock / secure lock device disabled, should hide SFPS indicator
            kosmos.fakeSecureLockDeviceRepository.onSecureLockDeviceDisabled()
            kosmos.fakeDeviceEntryRepository.deviceUnlockStatus.value =
                DeviceUnlockStatus(true, DeviceUnlockSource.Fingerprint)
            whenever(keyguardUpdateMonitor.isFingerprintDetectionRunning).thenReturn(false)
            whenever(keyguardUpdateMonitor.isUnlockingWithFingerprintAllowed).thenReturn(false)
            runCurrent()
            assertThat(showIndicatorForDeviceEntry).isFalse()
        }

    @Test
    fun updatesShowIndicatorForDeviceEntry_fromPrimaryBouncer_whenFpsDetectionNotRunning() {
        testScope.runTest {
            val showIndicatorForDeviceEntry by
                collectLastValue(underTest.showIndicatorForDeviceEntry)
            runCurrent()

            updatePrimaryBouncer(
                isShowing = true,
                isAnimatingAway = false,
                fpsDetectionRunning = false,
                isUnlockingWithFpAllowed = true,
            )
            assertThat(showIndicatorForDeviceEntry).isFalse()
        }
    }

    @Test
    fun updatesShowIndicatorForDeviceEntry_fromPrimaryBouncer_onUnlockingWithFpDisallowed() {
        testScope.runTest {
            val showIndicatorForDeviceEntry by
                collectLastValue(underTest.showIndicatorForDeviceEntry)
            runCurrent()

            updatePrimaryBouncer(
                isShowing = true,
                isAnimatingAway = false,
                fpsDetectionRunning = true,
                isUnlockingWithFpAllowed = false,
            )
            assertThat(showIndicatorForDeviceEntry).isFalse()
        }
    }

    @Test
    fun updatesShowIndicatorForDeviceEntry_fromBouncerScene_whenFpsDetectionNotRunning() {
        testScope.runTest {
            val showIndicatorForDeviceEntry by
                collectLastValue(underTest.showIndicatorForDeviceEntry)
            runCurrent()

            updateBouncer(
                isActive = true,
                fpsDetectionRunning = false,
                isUnlockingWithFpAllowed = true,
            )
            assertThat(showIndicatorForDeviceEntry).isFalse()
        }
    }

    @Test
    fun updatesShowIndicatorForDeviceEntry_fromBouncerScene_onUnlockingWithFpDisallowed() {
        testScope.runTest {
            val showIndicatorForDeviceEntry by
                collectLastValue(underTest.showIndicatorForDeviceEntry)
            runCurrent()

            updateBouncer(
                isActive = true,
                fpsDetectionRunning = true,
                isUnlockingWithFpAllowed = false,
            )
            assertThat(showIndicatorForDeviceEntry).isFalse()
        }
    }

    @Test
    fun updatesShowIndicatorForDeviceEntry_onPrimaryBouncerAnimatingAway() {
        testScope.runTest {
            val showIndicatorForDeviceEntry by
                collectLastValue(underTest.showIndicatorForDeviceEntry)
            runCurrent()

            updatePrimaryBouncer(
                isShowing = true,
                isAnimatingAway = true,
                fpsDetectionRunning = true,
                isUnlockingWithFpAllowed = true,
            )
            assertThat(showIndicatorForDeviceEntry).isFalse()
        }
    }

    @Test
    fun updatesShowIndicatorForDeviceEntry_onAlternateBouncerRequest() =
        testScope.runTest {
            val showIndicatorForDeviceEntry by
                collectLastValue(underTest.showIndicatorForDeviceEntry)
            runCurrent()

            bouncerRepository.setAlternateVisible(true)
            assertThat(showIndicatorForDeviceEntry).isTrue()

            bouncerRepository.setAlternateVisible(false)
            assertThat(showIndicatorForDeviceEntry).isFalse()
        }

    @Test
    fun ignoresDuplicateRequestsToShowIndicatorForDeviceEntry() =
        testScope.runTest {
            val showIndicatorForDeviceEntry by collectValues(underTest.showIndicatorForDeviceEntry)
            runCurrent()

            // Request to show indicator for primary bouncer showing
            updatePrimaryBouncer(
                isShowing = true,
                isAnimatingAway = false,
                fpsDetectionRunning = true,
                isUnlockingWithFpAllowed = true,
            )

            // Another request to show indicator for deviceEntryFingerprintAuthRepository update
            deviceEntryFingerprintAuthRepository.setShouldUpdateIndicatorVisibility(true)

            // Request to show indicator for alternate bouncer showing
            bouncerRepository.setAlternateVisible(true)

            // Ensure only one show request is sent
            assertThat(showIndicatorForDeviceEntry).containsExactly(false, true)
        }

    private fun TestScope.updatePrimaryBouncer(
        isShowing: Boolean,
        isAnimatingAway: Boolean,
        fpsDetectionRunning: Boolean,
        isUnlockingWithFpAllowed: Boolean,
    ) {
        bouncerRepository.setPrimaryShow(isShowing)
        bouncerRepository.setPrimaryStartingToHide(false)
        val primaryStartDisappearAnimation = if (isAnimatingAway) Runnable {} else null
        bouncerRepository.setPrimaryStartDisappearAnimation(primaryStartDisappearAnimation)

        updateBouncer(isShowing && !isAnimatingAway, fpsDetectionRunning, isUnlockingWithFpAllowed)
    }

    private fun TestScope.updateBouncer(
        isActive: Boolean,
        fpsDetectionRunning: Boolean,
        isUnlockingWithFpAllowed: Boolean,
    ) {
        if (isActive) {
            kosmos.sceneInteractor.showOverlay(Overlays.Bouncer, "reason")
        } else {
            kosmos.sceneInteractor.hideOverlay(Overlays.Bouncer, "reason")
        }

        whenever(keyguardUpdateMonitor.isFingerprintDetectionRunning)
            .thenReturn(fpsDetectionRunning)
        whenever(keyguardUpdateMonitor.isUnlockingWithFingerprintAllowed)
            .thenReturn(isUnlockingWithFpAllowed)
        mContext.orCreateTestableResources.addOverride(
            R.bool.config_show_sidefps_hint_on_bouncer,
            true,
        )

        runCurrent()
    }
}
