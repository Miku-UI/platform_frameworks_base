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

import android.view.inputmethod.InputMethodManager
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.snapshotFlow
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.systemui.Flags
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.bouncer.domain.interactor.BouncerInteractor
import com.android.systemui.bouncer.ui.helper.BouncerHapticPlayer
import com.android.systemui.inputmethod.domain.interactor.InputMethodInteractor
import com.android.systemui.res.R
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.user.domain.interactor.SelectedUserInteractor
import com.android.systemui.util.kotlin.onSubscriberAdded
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow

/** Holds UI state and handles user input for the password bouncer UI. */
@OptIn(FlowPreview::class)
class PasswordBouncerViewModel
@AssistedInject
constructor(
    interactor: BouncerInteractor,
    private val inputMethodInteractor: InputMethodInteractor,
    private val selectedUserInteractor: SelectedUserInteractor,
    @Assisted isInputEnabled: StateFlow<Boolean>,
    @Assisted private val onIntentionalUserInput: () -> Unit,
    @Assisted bouncerHapticPlayer: BouncerHapticPlayer,
) :
    AuthMethodBouncerViewModel(
        interactor = interactor,
        isInputEnabled = isInputEnabled,
        traceName = "PasswordBouncerViewModel",
        bouncerHapticPlayer = bouncerHapticPlayer,
    ) {

    val textFieldState = TextFieldState()

    override val authenticationMethod = AuthenticationMethodModel.Password

    override val lockoutMessageId = R.string.kg_too_many_failed_password_attempts_dialog_message

    override val _readyToTryAuthenticate = MutableStateFlow(false)

    val isMoreIndicatorsAndButtonsEnabled: Boolean
        get() =
            (Flags.moreIndicatorsAndButtonsOnPasswordBouncer() ||
                Flags.moreIndicatorsAndButtonsOnPasswordBouncer2()) &&
                interactor.isImproveLargeScreenInteractionEnabled

    /**
     * Only request that the Bouncer overlay displays a sign-in button on the bottom bar if enabled
     * by Flag & aconfig.
     */
    override val showSignInButton = isMoreIndicatorsAndButtonsEnabled

    private val _isImeSwitcherButtonVisible = MutableStateFlow(false)
    /** Informs the UI whether the input method switcher button should be visible. */
    val isImeSwitcherButtonVisible: StateFlow<Boolean> = _isImeSwitcherButtonVisible.asStateFlow()

    /** Whether the text field element currently has focus. */
    private val isTextFieldFocused = MutableStateFlow(false)

    private val _isTextFieldFocusRequested =
        MutableStateFlow(isInputEnabled.value && !isTextFieldFocused.value)
    /** Whether the UI should request focus on the text field element. */
    val isTextFieldFocusRequested = _isTextFieldFocusRequested.asStateFlow()

    private val _isPasswordRevealed = MutableStateFlow(false)
    /** Informs the UI whether the password should be currently revealed in clear text. */
    val isPasswordRevealed: StateFlow<Boolean> = _isPasswordRevealed.asStateFlow()

    /** Informs the UI about the current text obfuscation mode. */
    val textObfuscationMode: TextObfuscationMode by
        combine(_isPasswordRevealed, interactor.isPasswordObfuscationRequired) { revealed, required
                ->
                if (revealed) {
                    TextObfuscationMode.Visible
                } else if (required) {
                    // TODO(b/494143029): Remove this once compose supports physical inputs.
                    TextObfuscationMode.Hidden
                } else {
                    // Note that [TextObfuscationMode.RevealLastTyped] is a misleading name.
                    // On Android it means "briefly reveal last typed character *if and only
                    // if* the System.TEXT_SHOW_PASSWORD setting is enabled, otherwise it
                    // behaves as [TextObfuscationMode.Hidden].
                    TextObfuscationMode.RevealLastTyped
                }
            }
            .hydratedStateOf(initialValue = TextObfuscationMode.RevealLastTyped)

    /** Hides the password if it's been revealed and there was no user interaction. */
    private val HIDE_PASSWORD_DELAY = 5.seconds

    /**
     * Clears the password if it's possible to reveal it and there was no change in input text
     * contents for this time.
     */
    private val CLEAR_PASSWORD_IF_REVEALABLE_DELAY = 30.seconds

    private val _selectedUserId = MutableStateFlow(selectedUserInteractor.getSelectedUserId())
    /** The ID of the currently-selected user. */
    val selectedUserId: StateFlow<Int> = _selectedUserId.asStateFlow()

    private val requests = Channel<Request>(Channel.BUFFERED)

    override suspend fun onActivated(): Nothing {
        coroutineScope {
            launch { super.onActivated() }
            launch {
                requests.receiveAsFlow().collect { request ->
                    when (request) {
                        is OnImeSwitcherButtonClicked -> {
                            inputMethodInteractor.showInputMethodPicker(
                                showAuxiliarySubtypes = false,
                                entryPoint = InputMethodManager.IM_PICKER_ENTRY_POINT_DEFAULT,
                                displayId = request.displayId,
                            )
                        }
                        is OnImeDismissed -> {
                            interactor.onImeHiddenByUser()
                        }
                    }
                }
            }
            launch {
                combine(isInputEnabled, isTextFieldFocused) { hasInput, hasFocus ->
                        hasInput && !hasFocus && !wasSuccessfullyAuthenticated
                    }
                    .collect { _isTextFieldFocusRequested.value = it }
            }
            launch { selectedUserInteractor.selectedUser.collect { _selectedUserId.value = it } }
            launch {
                // Re-fetch the currently-enabled IMEs whenever the selected user changes, and
                // whenever
                // the UI subscribes to the `isImeSwitcherButtonVisible` flow.
                combine(
                        // InputMethodManagerService sometimes takes
                        // some time to update its internal state when the
                        // selected user changes.
                        // As a workaround, delay fetching the IME info.
                        selectedUserInteractor.selectedUser.onEach { delay(DELAY_TO_FETCH_IMES) },
                        _isImeSwitcherButtonVisible.onSubscriberAdded(),
                    ) { selectedUserId, _ ->
                        inputMethodInteractor.hasMultipleEnabledImesOrSubtypes(selectedUserId)
                    }
                    .collect { _isImeSwitcherButtonVisible.value = it }
            }
            launch {
                snapshotFlow { textFieldState.text.toString() }
                    .collect {
                        _readyToTryAuthenticate.value = determineIsReadyToAuthenticate()
                        if (it.isNotEmpty()) {
                            onIntentionalUserInput()
                        }
                    }
            }
            launch {
                // Hide the password 5s after the password has been revealed or the text in the
                // password input field has changed.
                val textChangeEvents = snapshotFlow { textFieldState.text }.map { Unit }
                val revealEvents = _isPasswordRevealed.filter { it == true }.map { Unit }
                val hidePasswordTrigger =
                    merge(textChangeEvents, revealEvents).debounce(HIDE_PASSWORD_DELAY)

                hidePasswordTrigger.collect { _isPasswordRevealed.value = false }
            }

            // It is possible to reveal the password through a button. Make sure to clear it
            // after inactivity.
            launch {
                val textChangeEvents = snapshotFlow { textFieldState.text }.map { Unit }
                textChangeEvents.debounce(CLEAR_PASSWORD_IF_REVEALABLE_DELAY).collect {
                    if (isMoreIndicatorsAndButtonsEnabled) {
                        textFieldState.clearText()
                    }
                }
            }
            awaitCancellation()
        }
    }

    override fun clearInput() {
        textFieldState.clearText()
        _isPasswordRevealed.value = false
    }

    override fun getInput(): List<Any> {
        return textFieldState.text.toList()
    }

    /** Notifies that the user clicked the button to change the input method. */
    fun onImeSwitcherButtonClicked(displayId: Int) {
        requests.trySend(OnImeSwitcherButtonClicked(displayId))
    }

    /** Notifies that the user clicked the button to reveal the password in clear text. */
    fun onRevealPasswordButtonClicked() {
        if (!isMoreIndicatorsAndButtonsEnabled) {
            return
        }

        // TODO(b/427681136): Reset password after 30 seconds if showing the password is visible and
        // the user does not type additional text.
        _isPasswordRevealed.value = true
    }

    /** Notifies that the user clicked the button to hide the password. */
    fun onHidePasswordButtonClicked() {
        _isPasswordRevealed.value = false
    }

    /** Notifies that the user has pressed the key for attempting to authenticate the password. */
    fun onAuthenticateKeyPressed() {
        if (determineIsReadyToAuthenticate()) {
            tryAuthenticate()
        }
    }

    /** Notifies that the user has dismissed the software keyboard (IME). */
    fun onImeDismissed() {
        requests.trySend(OnImeDismissed)
    }

    /** Notifies that the password text field has gained or lost focus. */
    fun onTextFieldFocusChanged(isFocused: Boolean) {
        isTextFieldFocused.value = isFocused
    }

    fun shouldResetFocus(transitionState: TransitionState): Boolean {
        return transitionState.isIdle() &&
            Overlays.Bouncer in transitionState.currentOverlays &&
            transitionState.currentScene == Scenes.Lockscreen
    }

    fun determineIsReadyToAuthenticate(): Boolean {
        return !textFieldState.text.isEmpty()
    }

    fun resetTextFieldFocus() {
        isTextFieldFocused.value = false
    }

    @AssistedFactory
    interface Factory {
        fun create(
            isInputEnabled: StateFlow<Boolean>,
            onIntentionalUserInput: () -> Unit,
            bouncerHapticPlayer: BouncerHapticPlayer,
        ): PasswordBouncerViewModel
    }

    companion object {
        @VisibleForTesting val DELAY_TO_FETCH_IMES = 300.milliseconds
    }

    private sealed interface Request

    private data class OnImeSwitcherButtonClicked(val displayId: Int) : Request

    private data object OnImeDismissed : Request
}
