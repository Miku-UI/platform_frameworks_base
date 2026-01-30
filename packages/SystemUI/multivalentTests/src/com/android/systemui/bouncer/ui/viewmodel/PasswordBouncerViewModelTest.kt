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

import android.content.pm.UserInfo
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.domain.interactor.authenticationInteractor
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.bouncer.domain.interactor.bouncerInteractor
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
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.testKosmos
import com.android.systemui.user.data.model.SelectedUserModel
import com.android.systemui.user.data.model.SelectionStatus
import com.android.systemui.user.data.repository.fakeUserRepository
import com.android.systemui.user.domain.interactor.selectedUserInteractor
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class PasswordBouncerViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val isInputEnabled = MutableStateFlow(true)
    private val onIntentionalUserInputMock: () -> Unit = mock()

    private val underTest by lazy {
        kosmos.passwordBouncerViewModelFactory.create(
            isInputEnabled = isInputEnabled,
            onIntentionalUserInput = onIntentionalUserInputMock,
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
        }

    @Test
    fun onHidden_resetsPasswordInputAndMessage() =
        kosmos.runTest {
            lockDeviceAndOpenPasswordBouncer()

            underTest.textFieldState.setTextAndPlaceCursorAtEnd("password")
            assertThat(underTest.textFieldState.text.toString()).isNotEmpty()

            underTest.onHidden()
            assertThat(underTest.textFieldState.text.toString()).isEmpty()
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
    fun onShown_againAfterSceneChange_resetsPassword() =
        kosmos.runTest {
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            lockDeviceAndOpenPasswordBouncer()

            // The user types a password.
            underTest.textFieldState.setTextAndPlaceCursorAtEnd("password")
            assertThat(underTest.textFieldState.text.toString()).isEqualTo("password")

            // The user doesn't confirm the password, but navigates back to the lockscreen instead.
            hideBouncer()

            // The user navigates to the bouncer again.
            showBouncer()

            // Ensure the previously-entered password is not shown.
            assertThat(underTest.textFieldState.text.toString()).isEmpty()
            assertThat(currentOverlays).contains(Overlays.Bouncer)
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

    private fun Kosmos.showBouncer() {
        val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
        sceneInteractor.showOverlay(Overlays.Bouncer, "reason")
        runCurrent()

        assertThat(currentOverlays).contains(Overlays.Bouncer)
    }

    private fun Kosmos.hideBouncer() {
        val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
        sceneInteractor.hideOverlay(Overlays.Bouncer, "reason")
        underTest.onHidden()
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
                fakeAuthenticationRepository.reportAuthenticationAttempt(false)
            }
            fakeAuthenticationRepository.reportLockoutStarted(
                30.seconds.inWholeMilliseconds.toInt()
            )
        } else {
            fakeAuthenticationRepository.reportAuthenticationAttempt(true)
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

    companion object {
        private const val ENTER_YOUR_PASSWORD = "Enter your password"
        private const val WRONG_PASSWORD = "Wrong password"

        private val USER_INFOS =
            listOf(UserInfo(100, "First user", 0), UserInfo(101, "Second user", 0))
    }
}
