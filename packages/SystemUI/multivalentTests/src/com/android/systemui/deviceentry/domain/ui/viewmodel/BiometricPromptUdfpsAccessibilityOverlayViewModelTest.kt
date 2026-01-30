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

package com.android.systemui.deviceentry.domain.ui.viewmodel

import android.graphics.Point
import android.hardware.biometrics.PromptInfo
import android.hardware.fingerprint.FingerprintSensorProperties
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.accessibility.data.repository.fakeAccessibilityRepository
import com.android.systemui.accessibility.domain.interactor.accessibilityInteractor
import com.android.systemui.biometrics.data.repository.fingerprintPropertyRepository
import com.android.systemui.biometrics.data.repository.promptRepository
import com.android.systemui.biometrics.domain.interactor.promptSelectorInteractor
import com.android.systemui.biometrics.fingerprintSensorPropertiesInternal
import com.android.systemui.biometrics.shared.model.BiometricModalities
import com.android.systemui.biometrics.shared.model.BiometricModality
import com.android.systemui.biometrics.shared.model.PromptKind
import com.android.systemui.biometrics.shared.model.toFingerprintSensorInfo
import com.android.systemui.biometrics.udfpsUtils
import com.android.systemui.biometrics.ui.viewmodel.BiometricPromptUdfpsAccessibilityOverlayViewModel
import com.android.systemui.biometrics.ui.viewmodel.biometricPromptUdfpsAccessibilityOverlayViewModel
import com.android.systemui.biometrics.ui.viewmodel.promptViewModel
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.keyguard.data.repository.deviceEntryFingerprintAuthRepository
import com.android.systemui.keyguard.data.repository.fakeBiometricSettingsRepository
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class BiometricPromptUdfpsAccessibilityOverlayViewModelTest() : SysuiTestCase() {
    @JvmField @Rule val mockito = MockitoJUnit.rule()

    private val kosmos = testKosmos()

    @Mock private lateinit var motionEvent: MotionEvent
    private val testScope = kosmos.testScope
    private val biometricSettingsRepository = kosmos.fakeBiometricSettingsRepository
    private val accessibilityRepository = kosmos.fakeAccessibilityRepository
    private val fingerprintPropertyRepository = kosmos.fingerprintPropertyRepository

    private lateinit var underTest: BiometricPromptUdfpsAccessibilityOverlayViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(kosmos.testDispatcher)
        // A11y enabled
        accessibilityRepository.isTouchExplorationEnabled.value = true

        // Listening for UDFPS
        biometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(true)

        underTest = kosmos.biometricPromptUdfpsAccessibilityOverlayViewModel
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun visible() =
        testScope.runTest {
            val isTouchExplorationEnabled by
                collectLastValue(kosmos.accessibilityInteractor.isTouchExplorationEnabled)
            val modalities by collectLastValue(kosmos.promptViewModel.modalities)
            val authState by collectLastValue(kosmos.promptViewModel.isAuthenticated)
            val visible by collectLastValue(underTest.visible)

            setupPrompt(FingerprintSensorProperties.TYPE_UDFPS_OPTICAL)
            runCurrent()
            assertThat(isTouchExplorationEnabled).isTrue()
            assertThat(modalities?.hasUdfps).isTrue()
            assertThat(authState?.isAuthenticated).isFalse()
            assertThat(visible).isTrue()
        }

    @Test
    fun touchExplorationNotEnabled_overlayNotVisible() =
        testScope.runTest {
            val visible by collectLastValue(underTest.visible)
            setupPrompt(FingerprintSensorProperties.TYPE_UDFPS_OPTICAL)
            accessibilityRepository.isTouchExplorationEnabled.value = false
            assertThat(visible).isFalse()
        }

    @Test
    fun nonUdfpsModality_overlayNotVisible() =
        testScope.runTest {
            val visible by collectLastValue(underTest.visible)
            setupPrompt(FingerprintSensorProperties.TYPE_POWER_BUTTON)
            accessibilityRepository.isTouchExplorationEnabled.value = false
            assertThat(visible).isFalse()
        }

    @Test
    fun afterAuthenticated_overlayNotVisible() =
        testScope.runTest {
            val visible by collectLastValue(underTest.visible)
            setupPrompt(FingerprintSensorProperties.TYPE_UDFPS_OPTICAL)
            kosmos.promptViewModel.showAuthenticated(
                modality = BiometricModality.Fingerprint,
                dismissAfterDelay = 1000L,
            )
            runCurrent()
            assertThat(visible).isFalse()
        }

    @Test
    fun udfpsDirectionalFeedbackReturnsNull_ifNotListeningForUdfps() =
        testScope.runTest {
            val isListeningForUdfps by collectLastValue(underTest.isListeningForUdfps)
            setupPrompt(FingerprintSensorProperties.TYPE_UDFPS_OPTICAL, false)
            setupMotionEvent(MotionEvent.ACTION_HOVER_ENTER)
            runCurrent()
            assertThat(isListeningForUdfps).isEqualTo(false)
            assertThat(underTest.getUdfpsDirectionalFeedbackOnHoverEnterOrMove(motionEvent))
                .isEqualTo(null)
        }

    @Test
    fun udfpsDirectionalFeedback_onTouchOutsideSensorArea() =
        testScope.runTest {
            val promptKind by collectLastValue(kosmos.promptRepository.promptKind)
            val prompt by collectLastValue(kosmos.promptSelectorInteractor.prompt)
            val modalities by collectLastValue(kosmos.promptViewModel.modalities)
            val isListeningForUdfps by collectLastValue(underTest.isListeningForUdfps)
            setupPrompt(FingerprintSensorProperties.TYPE_UDFPS_OPTICAL)
            setupMotionEvent(MotionEvent.ACTION_HOVER_ENTER)
            runCurrent()
            assertThat(promptKind?.isBiometric()).isEqualTo(true)
            assertThat((promptKind as PromptKind.Biometric).activeModalities.hasUdfps)
                .isEqualTo(true)
            assertThat(prompt?.modalities?.hasUdfps).isEqualTo(true)
            assertThat(modalities?.hasUdfps).isEqualTo(true)
            assertThat(isListeningForUdfps).isEqualTo(true)
            assertThat(underTest.getUdfpsDirectionalFeedbackOnHoverEnterOrMove(motionEvent))
                .isEqualTo("Move left")

            setupMotionEvent(MotionEvent.ACTION_HOVER_MOVE)
            runCurrent()
            assertThat(underTest.getUdfpsDirectionalFeedbackOnHoverEnterOrMove(motionEvent))
                .isEqualTo("Move left")
        }

    private fun setupUdfpsUtils() {
        whenever(kosmos.udfpsUtils.getTouchInNativeCoordinates(any(), any(), any(), anyBoolean()))
            .thenReturn(Point(0, 0))
        whenever(kosmos.udfpsUtils.isWithinSensorArea(any(), any(), any(), anyBoolean()))
            .thenReturn(false)
        whenever(
                kosmos.udfpsUtils.onTouchOutsideOfSensorArea(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    anyBoolean(),
                )
            )
            .thenReturn("Move left")
    }

    private fun setupPrompt(sensorType: Int?, isSensorListening: Boolean = true) {
        var activeModalities = BiometricModalities()

        if (
            sensorType == FingerprintSensorProperties.TYPE_UDFPS_OPTICAL ||
                sensorType == FingerprintSensorProperties.TYPE_UDFPS_ULTRASONIC
        ) {
            fingerprintPropertyRepository.supportsUdfps()
            if (isSensorListening) {
                setupUdfpsUtils()
            }
        } else if (sensorType == FingerprintSensorProperties.TYPE_POWER_BUTTON) {
            fingerprintPropertyRepository.supportsSideFps()
        }
        kosmos.deviceEntryFingerprintAuthRepository.setIsRunning(isSensorListening)

        if (sensorType != null && isSensorListening) {
            activeModalities =
                BiometricModalities(
                    fingerprintSensorPropertiesInternal(sensorType = sensorType)
                        .first()
                        .toFingerprintSensorInfo()
                )
        }

        kosmos.promptRepository.setPrompt(
            PromptInfo(),
            0,
            activeModalities,
            0,
            0L,
            PromptKind.Biometric(activeModalities = activeModalities),
            false,
            "test",
        )
    }

    private fun setupMotionEvent(eventType: Int) {
        whenever(motionEvent.action).thenReturn(eventType)
    }
}
