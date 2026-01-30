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

import android.hardware.biometrics.IIdentityCheckStateListener
import android.hardware.biometrics.PromptInfo
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.biometrics.biometricManager
import com.android.systemui.biometrics.domain.interactor.promptSelectorInteractor
import com.android.systemui.biometrics.shared.model.BiometricModalities
import com.android.systemui.biometrics.shared.model.WatchRangingState
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.kosmos.testScope
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit

@SmallTest
class PromptFallbackViewModelTest : SysuiTestCase() {

    @JvmField @Rule var mockitoRule = MockitoJUnit.rule()

    @Captor
    private lateinit var identityCheckStateListenerCaptor:
        ArgumentCaptor<IIdentityCheckStateListener>

    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val interactor = kosmos.promptSelectorInteractor
    private val biometricManager = kosmos.biometricManager

    private lateinit var viewModel: PromptFallbackViewModel

    @Before
    fun setUp() {
        viewModel = PromptFallbackViewModel(interactor)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun icCredentialButtonSubtitleAndFooter() =
        testScope.runTest {
            val isEnabled by collectLastValue(viewModel.icCredentialButtonEnabled)
            val subtitle by collectLastValue(viewModel.icCredentialSubtitle)
            val showFooter by collectLastValue(viewModel.icShowFooter)

            runCurrent()

            verify(biometricManager)
                .registerIdentityCheckStateListener(identityCheckStateListenerCaptor.capture())
            val listener = identityCheckStateListenerCaptor.value

            // WATCH_RANGING_IDLE - Button disabled, footer, no subtitle
            listener.onWatchRangingStateChanged(WatchRangingState.WATCH_RANGING_IDLE.ordinal)
            assertThat(isEnabled).isFalse()
            assertThat(subtitle).isNull()
            assertThat(showFooter).isTrue()

            // WATCH_RANGING_STARTED - Button disabled, no footer, ranging subtitle
            listener.onWatchRangingStateChanged(WatchRangingState.WATCH_RANGING_STARTED.ordinal)
            assertThat(isEnabled).isFalse()
            assertThat(subtitle).isEqualTo(R.string.biometric_dialog_identity_check_watch_ranging)
            assertThat(showFooter).isFalse()

            // WATCH_RANGING_SUCCESSFUL - Button enabled, no footer, no subtitle
            listener.onWatchRangingStateChanged(WatchRangingState.WATCH_RANGING_SUCCESSFUL.ordinal)
            assertThat(isEnabled).isTrue()
            assertThat(subtitle).isNull()
            assertThat(showFooter).isFalse()

            // WATCH_RANGING_STOPPED - Button disabled, footer, disabled subtitle
            listener.onWatchRangingStateChanged(WatchRangingState.WATCH_RANGING_STOPPED.ordinal)
            assertThat(isEnabled).isFalse()
            assertThat(subtitle).isEqualTo(R.string.biometric_dialog_unavailable)
            assertThat(showFooter).isTrue()
        }

    @Test
    fun showCredentialAndManageIdentityCheckButtons() =
        testScope.runTest {
            val showCredential by collectLastValue(viewModel.showCredential)
            val showManageIdentityCheck by collectLastValue(viewModel.showManageIdentityCheck)

            // When credential is allowed and identity check is inactive, show credential button
            setPrompt(
                PromptInfo().apply {
                    isDeviceCredentialAllowed = true
                    isIdentityCheckActive = false
                }
            )
            assertThat(showCredential).isTrue()
            assertThat(showManageIdentityCheck).isFalse()

            // When credential is allowed and identity check is active, show manage button
            setPrompt(
                PromptInfo().apply {
                    isDeviceCredentialAllowed = true
                    isIdentityCheckActive = true
                }
            )
            assertThat(showCredential).isFalse()
            assertThat(showManageIdentityCheck).isTrue()

            // When credential is not allowed, show neither button
            setPrompt(
                PromptInfo().apply {
                    isDeviceCredentialAllowed = false
                    isIdentityCheckActive = false
                }
            )
            assertThat(showCredential).isFalse()
            assertThat(showManageIdentityCheck).isFalse()

            // When credential is allowed and identity check is not active, show neither
            setPrompt(
                PromptInfo().apply {
                    isDeviceCredentialAllowed = false
                    isIdentityCheckActive = true
                }
            )
            assertThat(showCredential).isFalse()
            assertThat(showManageIdentityCheck).isFalse()
        }

    private fun setPrompt(promptInfo: PromptInfo) {
        interactor.setPrompt(
            promptInfo,
            0,
            0,
            BiometricModalities(),
            0L,
            "",
            onSwitchToCredential = false,
            isLandscape = false,
        )
    }
}
