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

package com.android.systemui.biometrics.ui.viewmodel

import android.hardware.biometrics.PromptInfo
import android.hardware.face.FaceSensorPropertiesInternal
import android.hardware.fingerprint.FingerprintSensorProperties
import android.hardware.fingerprint.FingerprintSensorProperties.TYPE_POWER_BUTTON
import android.hardware.fingerprint.FingerprintSensorProperties.TYPE_REAR
import android.hardware.fingerprint.FingerprintSensorProperties.TYPE_UDFPS_OPTICAL
import android.hardware.fingerprint.FingerprintSensorProperties.TYPE_UDFPS_ULTRASONIC
import android.hardware.fingerprint.FingerprintSensorPropertiesInternal
import android.platform.test.annotations.EnableFlags
import android.security.Flags.FLAG_SECURE_LOCK_DEVICE
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.biometrics.data.repository.facePropertyRepository
import com.android.systemui.biometrics.data.repository.fingerprintPropertyRepository
import com.android.systemui.biometrics.domain.interactor.PromptSelectorInteractor
import com.android.systemui.biometrics.domain.interactor.promptSelectorInteractor
import com.android.systemui.biometrics.extractAuthenticatorTypes
import com.android.systemui.biometrics.faceSensorPropertiesInternal
import com.android.systemui.biometrics.fingerprintSensorPropertiesInternal
import com.android.systemui.biometrics.shared.model.BiometricModalities
import com.android.systemui.biometrics.shared.model.FaceSensorInfo
import com.android.systemui.biometrics.shared.model.FingerprintSensorInfo
import com.android.systemui.biometrics.shared.model.SensorStrength
import com.android.systemui.biometrics.shared.model.toSensorType
import com.android.systemui.biometrics.ui.viewmodel.BiometricAuthIconViewModel.BiometricAuthModalities
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.keyguard.data.repository.biometricSettingsRepository
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.securelockdevice.ui.viewmodel.SecureLockDeviceBiometricAuthContentViewModel
import com.android.systemui.securelockdevice.ui.viewmodel.secureLockDeviceBiometricAuthContentViewModel
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class BiometricAuthIconViewModelTest() : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val defaultHelpMsg = "default help msg"

    private var promptViewModel: PromptViewModel? = null
    private var secureLockDeviceViewModel: SecureLockDeviceBiometricAuthContentViewModel? = null
    private var faceSensorInfo: FaceSensorInfo? = null
    private var fingerprintSensorInfo: FingerprintSensorInfo? = null
    private lateinit var underTest: BiometricAuthIconViewModel

    private fun enrollFingerprint(
        sensorStrength: SensorStrength = SensorStrength.STRONG,
        @FingerprintSensorProperties.SensorType sensorType: Int,
    ) {
        fingerprintSensorInfo =
            FingerprintSensorInfo(type = sensorType.toSensorType(), strength = sensorStrength)
        if (sensorType == TYPE_POWER_BUTTON) {
            kosmos.fingerprintPropertyRepository.supportsSideFps(sensorStrength)
        } else if (sensorType == TYPE_UDFPS_OPTICAL || sensorType == TYPE_UDFPS_ULTRASONIC) {
            kosmos.fingerprintPropertyRepository.supportsUdfps(sensorStrength)
        } else if (sensorType == TYPE_REAR) {
            kosmos.fingerprintPropertyRepository.supportsRearFps(sensorStrength)
        }
        kosmos.biometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(true)
    }

    private fun enrollFace(isStrongBiometric: Boolean) {
        faceSensorInfo =
            FaceSensorInfo(
                id = 0,
                strength = if (isStrongBiometric) SensorStrength.STRONG else SensorStrength.WEAK,
            )
        kosmos.facePropertyRepository.setSensorInfo(faceSensorInfo)
        kosmos.biometricSettingsRepository.setIsFaceAuthEnrolledAndEnabled(true)
    }

    private fun startBiometricPrompt(hasFpAuth: Boolean, isImplicitFlow: Boolean = false) {
        if (isImplicitFlow) {
            promptViewModel!!.showAuthenticating()
        } else {
            promptViewModel!!.ensureFingerprintHasStarted(isDelayed = false)
            val helpMsg =
                if (hasFpAuth) {
                    defaultHelpMsg
                } else {
                    ""
                }
            promptViewModel!!.showAuthenticating(helpMsg)
        }
    }

    private fun startSecureLockDevicePrompt() {
        secureLockDeviceViewModel!!.showAuthenticating()
    }

    private fun initPromptViewModel() {
        promptViewModel = kosmos.promptViewModel
        underTest = promptViewModel!!.iconViewModel.internal
        underTest.activateIn(testScope)
    }

    private fun initSecureLockDeviceViewModel() {
        secureLockDeviceViewModel = kosmos.secureLockDeviceBiometricAuthContentViewModel
        underTest = secureLockDeviceViewModel!!.iconViewModel
        underTest.activateIn(testScope)
    }

    @Test
    fun activeBiometricAuthType_basedOnModalitiesAndFaceMode_forBiometricPrompt_face() {
        testScope.runTest {
            initPromptViewModel()
            val activeBiometricAuthType by collectLastValue(underTest.activeBiometricAuthType)

            enrollFace(isStrongBiometric = false)
            runCurrent()

            val faceProps = faceSensorPropertiesInternal().first()
            kosmos.promptSelectorInteractor.initializePrompt(null, faceProps)
            runCurrent()
            startBiometricPrompt(hasFpAuth = false)

            assertThat(activeBiometricAuthType).isEqualTo(BiometricAuthModalities.Face)
        }
    }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun activeBiometricAuthType_basedOnModalities_forSecureLockDevice_face() {
        testScope.runTest {
            initSecureLockDeviceViewModel()
            val activeBiometricAuthType by collectLastValue(underTest.activeBiometricAuthType)

            enrollFace(isStrongBiometric = true)
            runCurrent()
            startSecureLockDevicePrompt()

            assertThat(activeBiometricAuthType).isEqualTo(BiometricAuthModalities.Face)
        }
    }

    @Test
    fun activeBiometricAuthType_basedOnModalitiesAndFaceMode_forBiometricPrompt_sfps() {
        testScope.runTest {
            initPromptViewModel()
            val activeBiometricAuthType by collectLastValue(underTest.activeBiometricAuthType)

            enrollFingerprint(sensorType = TYPE_POWER_BUTTON)
            runCurrent()

            val fpProps =
                fingerprintSensorPropertiesInternal(sensorType = TYPE_POWER_BUTTON).first()
            val faceProps = faceSensorPropertiesInternal().first()
            kosmos.promptSelectorInteractor.initializePrompt(fpProps, faceProps)
            runCurrent()
            startBiometricPrompt(hasFpAuth = true)

            assertThat(activeBiometricAuthType).isEqualTo(BiometricAuthModalities.Sfps)
        }
    }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun activeBiometricAuthType_basedOnModalities_forSecureLockDevice_sfps() {
        testScope.runTest {
            initSecureLockDeviceViewModel()
            val activeBiometricAuthType by collectLastValue(underTest.activeBiometricAuthType)

            enrollFingerprint(
                sensorStrength = SensorStrength.STRONG,
                sensorType = TYPE_POWER_BUTTON,
            )
            runCurrent()
            startSecureLockDevicePrompt()

            assertThat(activeBiometricAuthType).isEqualTo(BiometricAuthModalities.Sfps)
        }
    }

    @Test
    fun activeBiometricAuthType_basedOnModalitiesAndFaceMode_forBiometricPrompt_nonSfps() {
        testScope.runTest {
            initPromptViewModel()
            val activeBiometricAuthType by collectLastValue(underTest.activeBiometricAuthType)

            enrollFingerprint(sensorType = TYPE_UDFPS_OPTICAL)
            runCurrent()

            val fpProps =
                fingerprintSensorPropertiesInternal(strong = false, sensorType = TYPE_UDFPS_OPTICAL)
                    .first()
            val faceProps = faceSensorPropertiesInternal().first()
            kosmos.promptSelectorInteractor.initializePrompt(fpProps, faceProps)
            runCurrent()
            startBiometricPrompt(hasFpAuth = true)

            assertThat(activeBiometricAuthType).isEqualTo(BiometricAuthModalities.NonSfps)
        }
    }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun activeBiometricAuthType_basedOnModalities_forSecureLockDevice_nonSfps() {
        testScope.runTest {
            initSecureLockDeviceViewModel()
            val activeBiometricAuthType by collectLastValue(underTest.activeBiometricAuthType)

            enrollFingerprint(
                sensorStrength = SensorStrength.STRONG,
                sensorType = TYPE_UDFPS_OPTICAL,
            )
            runCurrent()
            startSecureLockDevicePrompt()

            assertThat(activeBiometricAuthType).isEqualTo(BiometricAuthModalities.NonSfps)
        }
    }

    @Test
    fun activeBiometricAuthType_basedOnModalitiesAndFaceMode_forBiometricPrompt_sfpsCoexImplicit() {
        testScope.runTest {
            initPromptViewModel()
            val activeBiometricAuthType by collectLastValue(underTest.activeBiometricAuthType)

            enrollFingerprint(sensorType = TYPE_POWER_BUTTON)
            enrollFace(isStrongBiometric = false)
            runCurrent()

            val fpProps =
                fingerprintSensorPropertiesInternal(strong = false, sensorType = TYPE_POWER_BUTTON)
                    .first()
            val faceProps = faceSensorPropertiesInternal(strong = false).first()
            kosmos.promptSelectorInteractor.initializePrompt(fpProps, faceProps)
            runCurrent()
            startBiometricPrompt(hasFpAuth = true, isImplicitFlow = true)

            assertThat(activeBiometricAuthType).isEqualTo(BiometricAuthModalities.Face)
        }
    }

    @Test
    fun activeBiometricAuthType_basedOnModalitiesAndFaceMode_forBiometricPrompt_coexSfpsExplicit() {
        testScope.runTest {
            initPromptViewModel()
            val activeBiometricAuthType by collectLastValue(underTest.activeBiometricAuthType)

            enrollFingerprint(sensorType = TYPE_POWER_BUTTON)
            enrollFace(isStrongBiometric = false)
            runCurrent()

            val fpProps =
                fingerprintSensorPropertiesInternal(strong = false, sensorType = TYPE_POWER_BUTTON)
                    .first()
            val faceProps = faceSensorPropertiesInternal(strong = false).first()
            kosmos.promptSelectorInteractor.initializePrompt(fpProps, faceProps)
            runCurrent()
            startBiometricPrompt(hasFpAuth = true)

            assertThat(activeBiometricAuthType).isEqualTo(BiometricAuthModalities.SfpsCoex)
        }
    }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun activeBiometricAuthType_basedOnModalities_forSecureLockDevice_sfpsCoex() {
        testScope.runTest {
            initSecureLockDeviceViewModel()
            val activeBiometricAuthType by collectLastValue(underTest.activeBiometricAuthType)

            enrollFingerprint(
                sensorStrength = SensorStrength.STRONG,
                sensorType = TYPE_POWER_BUTTON,
            )
            enrollFace(isStrongBiometric = true)
            runCurrent()
            startSecureLockDevicePrompt()

            assertThat(activeBiometricAuthType).isEqualTo(BiometricAuthModalities.SfpsCoex)
        }
    }

    @Test
    fun activeBiometricAuthType_basedOnModalitiesAndFaceMode_forBiometricPrompt_coexNonSfpsImplicit() {
        testScope.runTest {
            initPromptViewModel()
            val activeBiometricAuthType by collectLastValue(underTest.activeBiometricAuthType)

            enrollFingerprint(sensorType = TYPE_UDFPS_OPTICAL)
            enrollFace(isStrongBiometric = false)
            runCurrent()

            val fpProps =
                fingerprintSensorPropertiesInternal(strong = false, sensorType = TYPE_POWER_BUTTON)
                    .first()
            val faceProps = faceSensorPropertiesInternal(strong = false).first()
            kosmos.promptSelectorInteractor.initializePrompt(fpProps, faceProps)
            runCurrent()
            startBiometricPrompt(hasFpAuth = true, isImplicitFlow = true)

            assertThat(activeBiometricAuthType).isEqualTo(BiometricAuthModalities.Face)
        }
    }

    @Test
    fun activeBiometricAuthType_basedOnModalitiesAndFaceMode_forBiometricPrompt_coexNonSfpsExplicit() {
        testScope.runTest {
            initPromptViewModel()
            val activeBiometricAuthType by collectLastValue(underTest.activeBiometricAuthType)

            enrollFingerprint(sensorType = TYPE_UDFPS_OPTICAL)
            enrollFace(isStrongBiometric = false)
            runCurrent()

            val fpProps =
                fingerprintSensorPropertiesInternal(strong = false, sensorType = TYPE_POWER_BUTTON)
                    .first()
            val faceProps = faceSensorPropertiesInternal(strong = false).first()
            kosmos.promptSelectorInteractor.initializePrompt(fpProps, faceProps)
            runCurrent()
            startBiometricPrompt(hasFpAuth = true)

            assertThat(activeBiometricAuthType).isEqualTo(BiometricAuthModalities.NonSfpsCoex)
        }
    }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun activeBiometricAuthType_basedOnModalities_forSecureLockDevice_coexNonSfps() {
        testScope.runTest {
            initSecureLockDeviceViewModel()
            val activeBiometricAuthType by collectLastValue(underTest.activeBiometricAuthType)

            enrollFingerprint(
                sensorStrength = SensorStrength.STRONG,
                sensorType = TYPE_UDFPS_OPTICAL,
            )
            enrollFace(isStrongBiometric = true)
            runCurrent()
            startSecureLockDevicePrompt()

            assertThat(activeBiometricAuthType).isEqualTo(BiometricAuthModalities.NonSfpsCoex)
        }
    }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun activeBiometricAuthNone_forNonStrongBiometrics_forSecureLockDevice() {
        testScope.runTest {
            initSecureLockDeviceViewModel()
            val activeBiometricAuthType by collectLastValue(underTest.activeBiometricAuthType)

            enrollFingerprint(sensorStrength = SensorStrength.WEAK, sensorType = TYPE_UDFPS_OPTICAL)
            enrollFace(isStrongBiometric = false)
            runCurrent()
            startSecureLockDevicePrompt()

            assertThat(activeBiometricAuthType).isEqualTo(BiometricAuthModalities.None)
        }
    }

    /** Initialize the prompt according to the test configuration. */
    private fun PromptSelectorInteractor.initializePrompt(
        fingerprint: FingerprintSensorPropertiesInternal? = null,
        face: FaceSensorPropertiesInternal? = null,
        requireConfirmation: Boolean = false,
    ) {
        val info =
            PromptInfo().apply {
                logoDescription = "logo"
                title = "title"
                subtitle = "subtitle"
                description = "description"
                contentView = null
                authenticators = listOf(face, fingerprint).extractAuthenticatorTypes()
                isDeviceCredentialAllowed = false
                isConfirmationRequested = requireConfirmation
            }

        setPrompt(
            info,
            0,
            0,
            BiometricModalities(fingerprintSensorInfo, faceSensorInfo),
            0L,
            "packageName",
            onSwitchToCredential = false,
            isLandscape = false,
        )
    }
}
