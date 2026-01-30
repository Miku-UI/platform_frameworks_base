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

package com.android.systemui.biometrics.domain.interactor

import android.content.ComponentName
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.Flags
import android.hardware.biometrics.PromptContentViewWithMoreOptionsButton
import android.hardware.biometrics.PromptInfo
import android.hardware.biometrics.PromptVerticalListContentView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.InstanceId
import com.android.internal.widget.LockPatternUtils
import com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_PASSWORD
import com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_PATTERN
import com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_PIN
import com.android.systemui.SysuiTestCase
import com.android.systemui.biometrics.BiometricPromptLogger
import com.android.systemui.biometrics.data.repository.FakeDisplayStateRepository
import com.android.systemui.biometrics.data.repository.FakeFingerprintPropertyRepository
import com.android.systemui.biometrics.data.repository.FakePromptRepository
import com.android.systemui.biometrics.faceSensorPropertiesInternal
import com.android.systemui.biometrics.fingerprintSensorPropertiesInternal
import com.android.systemui.biometrics.shared.model.BiometricModalities
import com.android.systemui.biometrics.shared.model.PromptKind
import com.android.systemui.biometrics.shared.model.toFaceSensorInfo
import com.android.systemui.biometrics.shared.model.toFingerprintSensorInfo
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.display.data.repository.FakeDisplayRepository
import com.android.systemui.display.domain.interactor.DisplayStateInteractor
import com.android.systemui.display.domain.interactor.DisplayStateInteractorImpl
import com.android.systemui.display.shared.model.DisplayRotation
import com.android.systemui.log.SessionTracker
import com.android.systemui.shared.system.SysUiStatsLog
import com.android.systemui.util.concurrency.FakeExecutor
import com.android.systemui.util.mockito.any
import com.android.systemui.util.mockito.whenever
import com.android.systemui.util.time.FakeSystemClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class PromptSelectorInteractorImplTest : SysuiTestCase() {
    companion object {
        private const val TITLE = "hey there"
        private const val SUBTITLE = "ok"
        private const val DESCRIPTION = "football"
        private const val NEGATIVE_TEXT = "escape"

        private const val USER_ID = 8
        private const val REQUEST_ID = 8L
        private const val CHALLENGE = 999L
        private const val OP_PACKAGE_NAME = "biometric.testapp"
        private val componentNameOverriddenForConfirmDeviceCredentialActivity =
            ComponentName("not.com.android.settings", "testapp")
    }

    @JvmField @Rule var mockitoRule = MockitoJUnit.rule()

    @Mock private lateinit var lockPatternUtils: LockPatternUtils
    @Mock private lateinit var biometricManager: BiometricManager
    @Mock private lateinit var sessionTracker: SessionTracker
    @Mock private lateinit var biometricPromptLogger: BiometricPromptLogger
    @Mock private lateinit var instanceId: InstanceId

    private val testScope = TestScope()
    private val fingerprintRepository = FakeFingerprintPropertyRepository()
    private val promptRepository = FakePromptRepository()
    private val fakeExecutor = FakeExecutor(FakeSystemClock())
    private val credentialInteractor = FakeCredentialInteractor()

    private lateinit var displayStateRepository: FakeDisplayStateRepository
    private lateinit var displayRepository: FakeDisplayRepository
    private lateinit var displayStateInteractor: DisplayStateInteractor
    private lateinit var interactor: PromptSelectorInteractor

    @Before
    fun setup() {
        displayStateRepository = FakeDisplayStateRepository()
        displayRepository = FakeDisplayRepository()
        displayStateInteractor =
            DisplayStateInteractorImpl(
                testScope.backgroundScope,
                mContext,
                fakeExecutor,
                displayStateRepository,
                displayRepository,
            )
        interactor =
            PromptSelectorInteractorImpl(
                fingerprintRepository,
                displayStateInteractor,
                credentialInteractor,
                promptRepository,
                lockPatternUtils,
                biometricManager,
                testScope.backgroundScope,
                sessionTracker,
                biometricPromptLogger,
            )

        whenever(sessionTracker.getSessionId(any())).thenReturn(instanceId)
    }

    private fun basicPromptInfo() =
        PromptInfo().apply {
            title = TITLE
            subtitle = SUBTITLE
            description = DESCRIPTION
            negativeButtonText = NEGATIVE_TEXT
            isConfirmationRequested = true
            isDeviceCredentialAllowed = true
            authenticators = Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
        }

    private val modalities =
        BiometricModalities(
            fingerprintSensorInfo =
                fingerprintSensorPropertiesInternal().first().toFingerprintSensorInfo(),
            faceSensorInfo = faceSensorPropertiesInternal().first().toFaceSensorInfo(),
        )

    @Test
    fun useBiometricsAndReset() =
        testScope.runTest { useBiometricsAndReset(allowCredentialFallback = true) }

    @Test
    fun useBiometricsAndResetWithoutFallback() =
        testScope.runTest { useBiometricsAndReset(allowCredentialFallback = false) }

    @Test
    fun useBiometricsAndResetOnConfirmDeviceCredentialActivity() =
        testScope.runTest {
            useBiometricsAndReset(
                allowCredentialFallback = true,
                setComponentNameForConfirmDeviceCredentialActivity = true,
            )
        }

    private fun TestScope.useBiometricsAndReset(
        allowCredentialFallback: Boolean,
        setComponentNameForConfirmDeviceCredentialActivity: Boolean = false,
    ) {
        setUserCredentialType(isPassword = true)

        val confirmationRequired = true
        val info =
            basicPromptInfo().apply {
                isConfirmationRequested = confirmationRequired
                authenticators =
                    if (allowCredentialFallback) {
                        Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
                    } else {
                        Authenticators.BIOMETRIC_STRONG
                    }
                isDeviceCredentialAllowed = allowCredentialFallback
                realCallerForConfirmDeviceCredentialActivity =
                    if (setComponentNameForConfirmDeviceCredentialActivity)
                        componentNameOverriddenForConfirmDeviceCredentialActivity
                    else null
            }

        val currentPrompt by collectLastValue(interactor.prompt)
        val promptKind by collectLastValue(interactor.promptKind)
        val isCredentialAllowed by collectLastValue(interactor.isCredentialAllowed)
        val credentialKind by collectLastValue(interactor.credentialKind)
        val isConfirmationRequired by collectLastValue(interactor.isConfirmationRequired)
        val currentView by collectLastValue(interactor.currentView)

        assertThat(currentPrompt).isNull()

        interactor.setPrompt(
            info,
            USER_ID,
            REQUEST_ID,
            modalities,
            CHALLENGE,
            OP_PACKAGE_NAME,
            onSwitchToCredential = false,
            isLandscape = false,
        )

        assertThat(currentPrompt).isNotNull()
        assertThat(currentPrompt?.title).isEqualTo(TITLE)
        assertThat(currentPrompt?.description).isEqualTo(DESCRIPTION)
        assertThat(currentPrompt?.subtitle).isEqualTo(SUBTITLE)
        assertThat(currentPrompt?.negativeButtonText).isEqualTo(NEGATIVE_TEXT)
        assertThat(currentPrompt?.opPackageName).isEqualTo(OP_PACKAGE_NAME)
        assertThat(currentView).isEqualTo(BiometricPromptView.BIOMETRIC)
        assertThat(promptKind!!.isBiometric()).isTrue()
        assertThat(currentPrompt?.componentNameForConfirmDeviceCredentialActivity)
            .isEqualTo(
                if (setComponentNameForConfirmDeviceCredentialActivity)
                    componentNameOverriddenForConfirmDeviceCredentialActivity
                else null
            )

        if (allowCredentialFallback) {
            assertThat(credentialKind).isSameInstanceAs(PromptKind.Password)
            assertThat(isCredentialAllowed).isTrue()
        } else {
            if (Flags.bpFallbackOptions()) {
                assertThat(credentialKind).isEqualTo(PromptKind.Password)
            } else {
                assertThat(credentialKind).isEqualTo(PromptKind.None)
            }
            assertThat(isCredentialAllowed).isFalse()
        }
        assertThat(isConfirmationRequired).isEqualTo(confirmationRequired)

        interactor.resetPrompt(REQUEST_ID)
        verifyUnset()
    }

    @Test
    fun usePinCredentialAndReset() = testScope.runTest { useCredentialAndReset(PromptKind.Pin) }

    @Test
    fun usePatternCredentialAndReset() =
        testScope.runTest { useCredentialAndReset(PromptKind.Pattern) }

    @Test
    fun usePasswordCredentialAndReset() =
        testScope.runTest { useCredentialAndReset(PromptKind.Password) }

    @Test
    fun promptKind_isBiometric_whenBiometricAllowed() =
        testScope.runTest {
            setUserCredentialType(isPassword = true)

            val promptKind by collectLastValue(interactor.promptKind)
            assertThat(promptKind).isEqualTo(PromptKind.None)

            setPrompt()

            assertThat(promptKind?.isOnePanePortraitBiometric()).isTrue()

            interactor.resetPrompt(REQUEST_ID)
            verifyUnset()
        }

    @Test
    fun promptKind_isBiometricTwoPane_whenBiometricAllowed_landscape() =
        testScope.runTest {
            setUserCredentialType(isPassword = true)
            displayStateRepository.setIsLargeScreen(false)
            displayStateRepository.setCurrentRotation(DisplayRotation.ROTATION_90)

            val promptKind by collectLastValue(interactor.promptKind)
            assertThat(promptKind).isEqualTo(PromptKind.None)

            setPrompt()

            assertThat(promptKind?.isTwoPaneLandscapeBiometric()).isTrue()

            interactor.resetPrompt(REQUEST_ID)
            verifyUnset()
        }

    @Test
    fun promptKind_isBiometricOnePane_whenBiometricAllowed_largeScreenLandscape() =
        testScope.runTest {
            setUserCredentialType(isPassword = true)
            displayStateRepository.setIsLargeScreen(true)
            displayStateRepository.setCurrentRotation(DisplayRotation.ROTATION_90)

            val promptKind by collectLastValue(interactor.promptKind)
            assertThat(promptKind).isEqualTo(PromptKind.None)

            setPrompt()

            assertThat(promptKind?.isOnePaneLargeScreenLandscapeBiometric()).isTrue()

            interactor.resetPrompt(REQUEST_ID)
            verifyUnset()
        }

    @Test
    fun promptKind_isCredential_onSwitchToCredential() =
        testScope.runTest {
            setUserCredentialType(isPassword = true)

            val promptKind by collectLastValue(interactor.promptKind)
            val currentView by collectLastValue(interactor.currentView)
            assertThat(promptKind).isEqualTo(PromptKind.None)

            setPrompt(onSwitchToCredential = true)

            assertThat(promptKind).isEqualTo(PromptKind.Password)
            assertThat(currentView).isEqualTo(BiometricPromptView.CREDENTIAL)

            interactor.resetPrompt(REQUEST_ID)
            verifyUnset()
        }

    @Test
    fun switchToCredential() =
        testScope.runTest {
            setUserCredentialType(isPassword = true)

            val promptKind by collectLastValue(interactor.promptKind)
            val currentView by collectLastValue(interactor.currentView)
            assertThat(promptKind).isEqualTo(PromptKind.None)

            setPrompt(onSwitchToCredential = false)
            interactor.onSwitchToCredential()

            assertThat(promptKind).isEqualTo(PromptKind.Password)
            assertThat(currentView).isEqualTo(BiometricPromptView.CREDENTIAL)
            verify(biometricPromptLogger)
                .logPromptEvent(
                    eq(instanceId),
                    eq(
                        SysUiStatsLog
                            .BIOMETRIC_PROMPT_EVENT__EVENT__EVENT_TYPE_CREDENTIAL_VIEW_SHOWN
                    ),
                )

            interactor.resetPrompt(REQUEST_ID)
            verifyUnset()
        }

    @Test
    fun switchToFallback() =
        testScope.runTest {
            setUserCredentialType(isPassword = true)

            val promptKind by collectLastValue(interactor.promptKind)
            val currentView by collectLastValue(interactor.currentView)
            assertThat(promptKind).isEqualTo(PromptKind.None)

            setPrompt(onSwitchToCredential = false)
            interactor.onSwitchToFallback()

            assertThat(promptKind!!.isBiometric()).isTrue()
            assertThat(currentView).isEqualTo(BiometricPromptView.FALLBACK)
            verify(biometricPromptLogger)
                .logPromptEvent(
                    eq(instanceId),
                    eq(SysUiStatsLog.BIOMETRIC_PROMPT_EVENT__EVENT__EVENT_TYPE_FALLBACK_VIEW_SHOWN),
                )

            interactor.resetPrompt(REQUEST_ID)
            verifyUnset()
        }

    @Test
    fun switchToAuth() =
        testScope.runTest {
            setUserCredentialType(isPassword = true)

            val promptKind by collectLastValue(interactor.promptKind)
            val currentView by collectLastValue(interactor.currentView)
            assertThat(promptKind).isEqualTo(PromptKind.None)

            setPrompt(onSwitchToCredential = true)
            interactor.onSwitchToAuth()

            assertThat(promptKind!!.isBiometric()).isTrue()
            assertThat(currentView).isEqualTo(BiometricPromptView.BIOMETRIC)
            verify(biometricPromptLogger)
                .logPromptEvent(
                    eq(instanceId),
                    eq(SysUiStatsLog.BIOMETRIC_PROMPT_EVENT__EVENT__EVENT_TYPE_BIOMETRIC_VIEW_SHOWN),
                )

            interactor.resetPrompt(REQUEST_ID)
            verifyUnset()
        }

    @Test
    fun promptKind_isCredential_whenBiometricIsNotAllowed() =
        testScope.runTest {
            setUserCredentialType(isPassword = true)
            val info =
                basicPromptInfo().apply {
                    isDeviceCredentialAllowed = true
                    authenticators = Authenticators.DEVICE_CREDENTIAL
                }

            val promptKind by collectLastValue(interactor.promptKind)
            val currentView by collectLastValue(interactor.currentView)
            assertThat(promptKind).isEqualTo(PromptKind.None)

            setPrompt(info)

            assertThat(promptKind).isEqualTo(PromptKind.Password)
            assertThat(currentView).isEqualTo(BiometricPromptView.CREDENTIAL)

            interactor.resetPrompt(REQUEST_ID)
            verifyUnset()
        }

    @Test
    fun promptKind_isCredential_whenBiometricIsNotAllowed_withMoreOptionsButton() =
        testScope.runTest {
            setUserCredentialType(isPassword = true)
            val info =
                basicPromptInfo().apply {
                    isDeviceCredentialAllowed = true
                    authenticators = Authenticators.DEVICE_CREDENTIAL
                    contentView =
                        PromptContentViewWithMoreOptionsButton.Builder()
                            .setMoreOptionsButtonListener(fakeExecutor) { _, _ -> }
                            .build()
                }

            val promptKind by collectLastValue(interactor.promptKind)
            val currentView by collectLastValue(interactor.currentView)
            assertThat(promptKind).isEqualTo(PromptKind.None)

            setPrompt(info)

            assertThat(promptKind).isEqualTo(PromptKind.Password)
            assertThat(currentView).isEqualTo(BiometricPromptView.CREDENTIAL)

            interactor.resetPrompt(REQUEST_ID)
            verifyUnset()
        }

    @Test
    fun promptKind_isBiometric_whenBiometricIsNotAllowed_withVerticalList() =
        testScope.runTest {
            setUserCredentialType(isPassword = true)
            displayStateRepository.setCurrentRotation(DisplayRotation.ROTATION_90)
            val info =
                basicPromptInfo().apply {
                    isDeviceCredentialAllowed = true
                    authenticators = Authenticators.DEVICE_CREDENTIAL
                    contentView = PromptVerticalListContentView.Builder().build()
                }

            val promptKind by collectLastValue(interactor.promptKind)
            val currentView by collectLastValue(interactor.currentView)
            assertThat(promptKind).isEqualTo(PromptKind.None)

            setPrompt(info)

            assertThat(promptKind?.isOnePaneNoSensorLandscapeBiometric()).isTrue()
            assertThat(currentView).isEqualTo(BiometricPromptView.BIOMETRIC)

            interactor.resetPrompt(REQUEST_ID)
            verifyUnset()
        }

    @Test
    fun resetPrompt_onlyResetsViewForCurrentRequestId() =
        testScope.runTest {
            setUserCredentialType(isPassword = true)
            val currentView by collectLastValue(interactor.currentView)
            val requestId by collectLastValue(promptRepository.requestId)

            setPrompt(onSwitchToCredential = true)
            assertThat(currentView).isEqualTo(BiometricPromptView.CREDENTIAL)
            assertThat(requestId).isEqualTo(REQUEST_ID)

            interactor.resetPrompt(0L)

            assertThat(currentView).isEqualTo(BiometricPromptView.CREDENTIAL)
            assertThat(requestId).isEqualTo(REQUEST_ID)

            interactor.resetPrompt(REQUEST_ID)

            assertThat(currentView).isEqualTo(BiometricPromptView.BIOMETRIC)
            verifyUnset()
        }

    private fun setPrompt(
        info: PromptInfo = basicPromptInfo(),
        onSwitchToCredential: Boolean = false,
    ) {
        interactor.setPrompt(
            info,
            USER_ID,
            REQUEST_ID,
            modalities,
            CHALLENGE,
            OP_PACKAGE_NAME,
            onSwitchToCredential = onSwitchToCredential,
            isLandscape =
                displayStateRepository.currentRotation.value == DisplayRotation.ROTATION_90 ||
                    displayStateRepository.currentRotation.value == DisplayRotation.ROTATION_270,
        )
    }

    private fun TestScope.useCredentialAndReset(kind: PromptKind) {
        setUserCredentialType(
            isPin = kind == PromptKind.Pin,
            isPassword = kind == PromptKind.Password,
        )

        val info =
            PromptInfo().apply {
                title = TITLE
                subtitle = SUBTITLE
                description = DESCRIPTION
                negativeButtonText = NEGATIVE_TEXT
                authenticators = Authenticators.DEVICE_CREDENTIAL
                isDeviceCredentialAllowed = true
            }

        val currentPrompt by collectLastValue(interactor.prompt)
        val credentialKind by collectLastValue(interactor.credentialKind)

        assertThat(currentPrompt).isNull()

        interactor.setPrompt(
            info,
            USER_ID,
            REQUEST_ID,
            BiometricModalities(),
            CHALLENGE,
            OP_PACKAGE_NAME,
            onSwitchToCredential = false,
            isLandscape = false,
        )

        if (Flags.bpFallbackOptions()) {
            if (kind == PromptKind.Password) {
                assertThat(credentialKind).isEqualTo(PromptKind.Password)
            } else if (kind == PromptKind.Pin) {
                assertThat(credentialKind).isEqualTo(PromptKind.Pin)
            } else {
                assertThat(credentialKind).isEqualTo(PromptKind.Pattern)
            }
        } else {
            assertThat(credentialKind).isEqualTo(PromptKind.None)
        }

        interactor.resetPrompt(REQUEST_ID)
        verifyUnset()
    }

    private fun TestScope.verifyUnset() {
        val currentPrompt by collectLastValue(interactor.prompt)
        val promptKind by collectLastValue(interactor.promptKind)
        val isCredentialAllowed by collectLastValue(interactor.isCredentialAllowed)
        val credentialKind by collectLastValue(interactor.credentialKind)
        val isConfirmationRequired by collectLastValue(interactor.isConfirmationRequired)

        assertThat(currentPrompt).isNull()
        assertThat(promptKind).isEqualTo(PromptKind.None)
        assertThat(isCredentialAllowed).isFalse()
        assertThat(credentialKind).isEqualTo(PromptKind.None)
        assertThat(isConfirmationRequired).isFalse()
    }

    private fun setUserCredentialType(isPin: Boolean = false, isPassword: Boolean = false) {
        whenever(lockPatternUtils.getCredentialTypeForUser(any()))
            .thenReturn(
                when {
                    isPin -> CREDENTIAL_TYPE_PIN
                    isPassword -> CREDENTIAL_TYPE_PASSWORD
                    else -> CREDENTIAL_TYPE_PATTERN
                }
            )
    }
}
