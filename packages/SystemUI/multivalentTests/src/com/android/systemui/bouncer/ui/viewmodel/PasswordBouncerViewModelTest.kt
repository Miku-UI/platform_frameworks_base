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

package com.android.systemui.bouncer.ui.viewmodel

import android.compat.testing.PlatformCompatChangeRule
import android.content.pm.UserInfo
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.text.ShowSecretsSetting
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.domain.interactor.authenticationInteractor
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.authentication.shared.model.AuthenticationResult
import com.android.systemui.bouncer.domain.interactor.bouncerInteractor
import com.android.systemui.common.ui.data.repository.fakeConfigurationRepository
import com.android.systemui.haptics.msdl.bouncerHapticPlayer
import com.android.systemui.inputmethod.data.model.InputMethodModel
import com.android.systemui.inputmethod.data.repository.fakeInputMethodRepository
import com.android.systemui.inputmethod.domain.interactor.inputMethodInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.collectValues
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.runOnMainThreadAndWaitForIdleSync
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.testKosmos
import com.android.systemui.user.data.model.SelectedUserModel
import com.android.systemui.user.data.model.SelectionStatus
import com.android.systemui.user.data.repository.fakeUserRepository
import com.android.systemui.user.domain.interactor.selectedUserInteractor
import com.android.text.flags.Flags as TextFlags
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class PasswordBouncerViewModelTest : SysuiTestCase() {

    @get:Rule val compatChangeRule = PlatformCompatChangeRule()

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val isInputEnabled = MutableStateFlow(true)
    private val onIntentionalUserInputMock: () -> Unit = mock()

    private val underTest by lazy {
        kosmos.passwordBouncerViewModelFactory.create(
            isInputEnabled = isInputEnabled,
            onIntentionalUserInput = onIntentionalUserInputMock,
            bouncerHapticPlayer = kosmos.bouncerHapticPlayer,
        )
    }

    @Before
    fun setUp() {
        overrideResource(R.string.keyguard_enter_your_password, ENTER_YOUR_PASSWORD)
        overrideResource(R.string.kg_wrong_password, WRONG_PASSWORD)
        underTest.activateIn(kosmos.testScope)
    }

    @Test
    fun onShown() =
        kosmos.runTest {
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            lockDeviceAndOpenPasswordBouncer()

            assertThat(underTest.textFieldState.text.toString()).isEmpty()
            assertThat(currentOverlays).contains(Overlays.Bouncer)
            assertThat(underTest.authenticationMethod).isEqualTo(AuthenticationMethodModel.Password)
            assertThat(underTest.isPasswordRevealed.value).isFalse()
        }

    @Test
    fun onPasswordInputChanged() =
        kosmos.runTest {
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            lockDeviceAndOpenPasswordBouncer()

            verify(onIntentionalUserInputMock, never()).invoke()
            underTest.textFieldState.setTextAndPlaceCursorAtEnd("password")

            runCurrent()

            assertThat(underTest.textFieldState.text.toString()).isEqualTo("password")
            verify(onIntentionalUserInputMock, times(1)).invoke()
            assertThat(currentOverlays).contains(Overlays.Bouncer)
        }

    @Test
    fun onAuthenticateKeyPressed_whenCorrect() =
        kosmos.runTest {
            val authResult by collectLastValue(authenticationInteractor.onAuthenticationResult)
            lockDeviceAndOpenPasswordBouncer()

            underTest.textFieldState.setTextAndPlaceCursorAtEnd("password")
            underTest.onAuthenticateKeyPressed()

            assertThat(authResult).isTrue()
        }

    @Test
    fun onAuthenticateKeyPressed_whenWrong() =
        kosmos.runTest {
            val authResult by collectLastValue(authenticationInteractor.onAuthenticationResult)
            lockDeviceAndOpenPasswordBouncer()

            underTest.textFieldState.setTextAndPlaceCursorAtEnd("wrong")
            underTest.onAuthenticateKeyPressed()

            assertThat(authResult).isFalse()
            assertThat(underTest.textFieldState.text.toString()).isEmpty()
        }

    @Test
    fun onAuthenticateKeyPressed_whenEmpty() =
        kosmos.runTest {
            fakeAuthenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Password)
            showBouncer()

            // No input entered.

            underTest.onAuthenticateKeyPressed()

            assertThat(underTest.textFieldState.text.toString()).isEmpty()
        }

    @Test
    fun onAuthenticateKeyPressed_correctAfterWrong() =
        kosmos.runTest {
            val authResult by collectLastValue(authenticationInteractor.onAuthenticationResult)
            lockDeviceAndOpenPasswordBouncer()

            // Enter the wrong password:
            underTest.textFieldState.setTextAndPlaceCursorAtEnd("wrong")
            underTest.onAuthenticateKeyPressed()
            assertThat(authResult).isFalse()
            assertThat(underTest.textFieldState.text.toString()).isEmpty()

            // Enter the correct password:
            underTest.textFieldState.setTextAndPlaceCursorAtEnd("password")

            underTest.onAuthenticateKeyPressed()

            assertThat(authResult).isTrue()
        }

    @Test
    fun onImeDismissed() =
        kosmos.runTest {
            val events by collectValues(bouncerInteractor.onImeHiddenByUser)
            assertThat(events).isEmpty()

            underTest.onImeDismissed()
            assertThat(events).hasSize(1)
        }

    @Test
    fun isTextFieldFocusRequested_initiallyTrue() =
        kosmos.runTest {
            val isTextFieldFocusRequested by collectLastValue(underTest.isTextFieldFocusRequested)
            assertThat(isTextFieldFocusRequested).isTrue()
        }

    @Test
    fun isTextFieldFocusRequested_focusGained_becomesFalse() =
        kosmos.runTest {
            val isTextFieldFocusRequested by collectLastValue(underTest.isTextFieldFocusRequested)

            underTest.onTextFieldFocusChanged(isFocused = true)

            assertThat(isTextFieldFocusRequested).isFalse()
        }

    @Test
    fun isTextFieldFocusRequested_focusLost_becomesTrue() =
        kosmos.runTest {
            val isTextFieldFocusRequested by collectLastValue(underTest.isTextFieldFocusRequested)
            underTest.onTextFieldFocusChanged(isFocused = true)

            underTest.onTextFieldFocusChanged(isFocused = false)

            assertThat(isTextFieldFocusRequested).isTrue()
        }

    @Test
    fun isTextFieldFocusRequested_focusLostWhileLockedOut_staysFalse() =
        kosmos.runTest {
            val isTextFieldFocusRequested by collectLastValue(underTest.isTextFieldFocusRequested)
            underTest.onTextFieldFocusChanged(isFocused = true)
            setLockout(true)

            underTest.onTextFieldFocusChanged(isFocused = false)

            assertThat(isTextFieldFocusRequested).isFalse()
        }

    @Test
    fun isTextFieldFocusRequested_lockoutCountdownEnds_becomesTrue() =
        kosmos.runTest {
            val isTextFieldFocusRequested by collectLastValue(underTest.isTextFieldFocusRequested)
            underTest.onTextFieldFocusChanged(isFocused = true)
            setLockout(true)
            underTest.onTextFieldFocusChanged(isFocused = false)

            setLockout(false)

            assertThat(isTextFieldFocusRequested).isTrue()
        }

    @Test
    fun isImeSwitcherButtonVisible() =
        kosmos.runTest {
            val selectedUserId by collectLastValue(selectedUserInteractor.selectedUser)
            selectUser(USER_INFOS.first())

            enableInputMethodsForUser(checkNotNull(selectedUserId))

            // Assert initial value, before the UI subscribes.
            assertThat(underTest.isImeSwitcherButtonVisible.value).isFalse()

            // Current implementation only emits if 300ms have passed and a subscriber exists.
            advanceTimeBy(300.milliseconds)

            // Subscription starts; verify a fresh value is fetched.
            val isImeSwitcherButtonVisible by collectLastValue(underTest.isImeSwitcherButtonVisible)
            assertThat(isImeSwitcherButtonVisible).isTrue()

            // Change the user, verify a fresh value is fetched.
            selectUser(USER_INFOS.last())

            assertThat(
                    inputMethodInteractor.hasMultipleEnabledImesOrSubtypes(
                        checkNotNull(selectedUserId)
                    )
                )
                .isFalse()
            assertThat(isImeSwitcherButtonVisible).isFalse()

            // Enable IMEs and add another subscriber; verify a fresh value is fetched.
            enableInputMethodsForUser(checkNotNull(selectedUserId))
            val collector2 by collectLastValue(underTest.isImeSwitcherButtonVisible)
            assertThat(collector2).isTrue()
        }

    @Test
    fun onImeSwitcherButtonClicked() =
        kosmos.runTest {
            val displayId = 7
            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId)
                .isNotEqualTo(displayId)

            underTest.onImeSwitcherButtonClicked(displayId)
            runCurrent()

            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId)
                .isEqualTo(displayId)
        }

    @Test
    fun afterSuccessfulAuthentication_focusIsNotRequested() =
        kosmos.runTest {
            val authResult by collectLastValue(authenticationInteractor.onAuthenticationResult)
            val textInputFocusRequested by collectLastValue(underTest.isTextFieldFocusRequested)
            lockDeviceAndOpenPasswordBouncer()

            // remove focus from text field
            underTest.onTextFieldFocusChanged(false)
            runCurrent()

            // focus should be requested
            assertThat(textInputFocusRequested).isTrue()

            // simulate text field getting focus
            underTest.onTextFieldFocusChanged(true)
            runCurrent()

            // focus should not be requested anymore
            assertThat(textInputFocusRequested).isFalse()

            // authenticate successfully.
            underTest.textFieldState.setTextAndPlaceCursorAtEnd("password")
            underTest.onAuthenticateKeyPressed()
            runCurrent()

            assertThat(authResult).isTrue()

            // remove focus from text field
            underTest.onTextFieldFocusChanged(false)
            runCurrent()
            // focus should not be requested again
            assertThat(textInputFocusRequested).isFalse()
        }

    @Test
    @DisableFlags(Flags.FLAG_MORE_INDICATORS_AND_BUTTONS_ON_PASSWORD_BOUNCER)
    fun moreButtonsAndIndicators_isDisabled_whenFlagNotEnabled() =
        kosmos.runTest {
            overrideResource(R.bool.config_improveLargeScreenInteractionOnLockscreen, true)

            assertThat(underTest.isMoreIndicatorsAndButtonsEnabled).isFalse()
        }

    @Test
    @EnableFlags(Flags.FLAG_MORE_INDICATORS_AND_BUTTONS_ON_PASSWORD_BOUNCER)
    fun moreButtonsAndIndicators_isDisabled_whenDisabledInConfig() =
        kosmos.runTest {
            overrideResource(R.bool.config_improveLargeScreenInteractionOnLockscreen, false)

            assertThat(underTest.isMoreIndicatorsAndButtonsEnabled).isFalse()
        }

    @Test
    @EnableFlags(Flags.FLAG_MORE_INDICATORS_AND_BUTTONS_ON_PASSWORD_BOUNCER)
    fun moreButtonsAndIndicators_isEnabled_whenFlagAndConfigEnabled() =
        kosmos.runTest {
            overrideResource(R.bool.config_improveLargeScreenInteractionOnLockscreen, true)

            assertThat(underTest.isMoreIndicatorsAndButtonsEnabled).isTrue()
        }

    @Test
    @EnableFlags(Flags.FLAG_MORE_INDICATORS_AND_BUTTONS_ON_PASSWORD_BOUNCER)
    fun onRevealPasswordButtonClicked_showsPassword_hidesAfterDelay() =
        kosmos.runTest {
            overrideResource(R.bool.config_improveLargeScreenInteractionOnLockscreen, true)

            val isPasswordRevealed by collectLastValue(underTest.isPasswordRevealed)

            underTest.onRevealPasswordButtonClicked()
            runCurrent()

            assertThat(isPasswordRevealed).isTrue()

            advanceTimeBy(5.seconds - 1.milliseconds)

            assertThat(isPasswordRevealed).isTrue()

            advanceTimeBy(2.milliseconds)

            assertThat(isPasswordRevealed).isFalse()
        }

    @Test
    @EnableFlags(Flags.FLAG_MORE_INDICATORS_AND_BUTTONS_ON_PASSWORD_BOUNCER)
    fun onRevealPasswordButtonClicked_showsPassword_hides5sAfterTextInput() =
        kosmos.runTest {
            overrideResource(R.bool.config_improveLargeScreenInteractionOnLockscreen, true)

            val isPasswordRevealed by collectLastValue(underTest.isPasswordRevealed)

            underTest.onRevealPasswordButtonClicked()

            assertThat(isPasswordRevealed).isTrue()

            advanceTimeBy(2.seconds)

            runOnMainThreadAndWaitForIdleSync {
                underTest.textFieldState.setTextAndPlaceCursorAtEnd("p")
            }

            advanceTimeBy(4.seconds)

            // Still revealed after a total of 6 seconds after clicking the "reveal" button because
            // text has been entered after 2s.
            assertThat(isPasswordRevealed).isTrue()

            advanceTimeBy(1.seconds - 1.milliseconds)

            assertThat(isPasswordRevealed).isTrue()

            advanceTimeBy(2.milliseconds)

            assertThat(isPasswordRevealed).isFalse()
        }

    @Test
    @EnableFlags(Flags.FLAG_MORE_INDICATORS_AND_BUTTONS_ON_PASSWORD_BOUNCER)
    fun onRevealPasswordButtonClicked_doesNotRevealPassword_whenNotEnabled() =
        kosmos.runTest {
            overrideResource(R.bool.config_improveLargeScreenInteractionOnLockscreen, false)

            val isPasswordRevealed by collectLastValue(underTest.isPasswordRevealed)

            underTest.onRevealPasswordButtonClicked()
            runCurrent()

            assertThat(isPasswordRevealed).isFalse()
        }

    @Test
    @EnableFlags(Flags.FLAG_MORE_INDICATORS_AND_BUTTONS_ON_PASSWORD_BOUNCER)
    fun onInactivity_clearPassword_whenRevealable() =
        kosmos.runTest {
            overrideResource(R.bool.config_improveLargeScreenInteractionOnLockscreen, true)

            runOnMainThreadAndWaitForIdleSync {
                underTest.textFieldState.setTextAndPlaceCursorAtEnd("p")
            }

            advanceTimeBy(2.seconds)
            assertThat(underTest.textFieldState.text.toString()).isEqualTo("p")

            runOnMainThreadAndWaitForIdleSync {
                underTest.textFieldState.setTextAndPlaceCursorAtEnd("pa")
            }

            advanceTimeBy(30.seconds - 1.milliseconds)
            assertThat(underTest.textFieldState.text.toString()).isEqualTo("pa")

            advanceTimeBy(2.milliseconds)
            assertThat(underTest.textFieldState.text.toString()).isEmpty()
        }

    @Test
    @EnableFlags(Flags.FLAG_MORE_INDICATORS_AND_BUTTONS_ON_PASSWORD_BOUNCER)
    fun onInactivity_dontClearPassword_whenNotRevealable() =
        kosmos.runTest {
            overrideResource(R.bool.config_improveLargeScreenInteractionOnLockscreen, false)

            runOnMainThreadAndWaitForIdleSync {
                underTest.textFieldState.setTextAndPlaceCursorAtEnd("p")
            }

            advanceTimeBy(31.seconds)
            assertThat(underTest.textFieldState.text.toString()).isEqualTo("p")
        }

    @Test
    fun readyToTryAuthenticate() =
        kosmos.runTest {
            val readyToTryAuthenticate by collectLastValue(underTest.readyToTryAuthenticate)
            lockDeviceAndOpenPasswordBouncer()
            assertThat(readyToTryAuthenticate).isFalse()

            runOnMainThreadAndWaitForIdleSync {
                underTest.textFieldState.setTextAndPlaceCursorAtEnd("p")
            }
            assertThat(readyToTryAuthenticate).isTrue()

            runOnMainThreadAndWaitForIdleSync {
                underTest.textFieldState.setTextAndPlaceCursorAtEnd("")
            }
            assertThat(readyToTryAuthenticate).isFalse()
        }

    private fun Kosmos.showBouncer() {
        val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
        sceneInteractor.showOverlay(Overlays.Bouncer, "reason")
        runCurrent()

        assertThat(currentOverlays).contains(Overlays.Bouncer)
    }

    private fun Kosmos.hideBouncer() {
        val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
        sceneInteractor.hideOverlay(Overlays.Bouncer, "reason")
        underTest.resetTextFieldFocus()
        runCurrent()

        assertThat(currentOverlays).doesNotContain(Overlays.Bouncer)
    }

    private fun Kosmos.lockDeviceAndOpenPasswordBouncer() {
        fakeAuthenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Password)
        showBouncer()
    }

    private suspend fun Kosmos.setLockout(isLockedOut: Boolean, failedAttemptCount: Int = 5) {
        if (isLockedOut) {
            repeat(failedAttemptCount) {
                fakeAuthenticationRepository.reportAuthenticationAttempt(
                    AuthenticationResult.FAILED
                )
            }
            fakeAuthenticationRepository.reportLockoutStarted(30.seconds)
        } else {
            fakeAuthenticationRepository.reportAuthenticationAttempt(AuthenticationResult.SUCCEEDED)
        }
        isInputEnabled.value = !isLockedOut

        runCurrent()
    }

    private fun Kosmos.selectUser(userInfo: UserInfo) {
        fakeUserRepository.selectedUser.value =
            SelectedUserModel(
                userInfo = userInfo,
                selectionStatus = SelectionStatus.SELECTION_COMPLETE,
            )
        advanceTimeBy(PasswordBouncerViewModel.DELAY_TO_FETCH_IMES)
    }

    private suspend fun Kosmos.enableInputMethodsForUser(userId: Int) {
        fakeInputMethodRepository.setEnabledInputMethods(
            userId,
            createInputMethodWithSubtypes(auxiliarySubtypes = 0, nonAuxiliarySubtypes = 0),
            createInputMethodWithSubtypes(auxiliarySubtypes = 0, nonAuxiliarySubtypes = 1),
        )
        assertThat(inputMethodInteractor.hasMultipleEnabledImesOrSubtypes(userId)).isTrue()
    }

    private fun createInputMethodWithSubtypes(
        auxiliarySubtypes: Int,
        nonAuxiliarySubtypes: Int,
    ): InputMethodModel {
        return InputMethodModel(
            userId = UUID.randomUUID().mostSignificantBits.toInt(),
            imeId = UUID.randomUUID().toString(),
            subtypes =
                List(auxiliarySubtypes + nonAuxiliarySubtypes) {
                    InputMethodModel.Subtype(subtypeId = it, isAuxiliary = it < auxiliarySubtypes)
                },
        )
    }

    @Test
    @EnableFlags(Flags.FLAG_MORE_INDICATORS_AND_BUTTONS_ON_PASSWORD_BOUNCER)
    fun isPasswordRevealed_overridesToVisible() =
        kosmos.runTest {
            overrideResource(R.bool.config_improveLargeScreenInteractionOnLockscreen, true)
            kosmos.fakeConfigurationRepository.onConfigurationChange(
                android.content.res.Configuration()
            )
            underTest.onRevealPasswordButtonClicked()
            runCurrent()
            assertThat(underTest.textObfuscationMode).isEqualTo(TextObfuscationMode.Visible)
        }

    @Test
    @EnableFlags(
        Flags.FLAG_MORE_INDICATORS_AND_BUTTONS_ON_PASSWORD_BOUNCER,
        TextFlags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL,
    )
    @EnableCompatChanges(ShowSecretsSetting.SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    fun textObfuscationMode_isHidden_whenRequiredAndNotRevealed() =
        kosmos.runTest {
            overrideResource(R.bool.config_improveLargeScreenInteractionOnLockscreen, true)
            kosmos.fakeConfigurationRepository.onConfigurationChange(
                android.content.res.Configuration()
            )

            fakeAuthenticationRepository.setIsShowPasswordsTouchEnabled(false)
            runCurrent()

            assertThat(underTest.textObfuscationMode).isEqualTo(TextObfuscationMode.Hidden)
        }

    companion object {
        private const val ENTER_YOUR_PASSWORD = "Enter your password"
        private const val WRONG_PASSWORD = "Wrong password"

        private val USER_INFOS =
            listOf(UserInfo(100, "First user", 0), UserInfo(101, "Second user", 0))
    }
}
