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

import android.app.admin.DevicePolicyManager
import android.app.admin.DevicePolicyResources
import android.content.Context
import android.graphics.Bitmap
import android.security.Flags.enableWeaverWarmup
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.core.graphics.drawable.toBitmap
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.app.tracing.coroutines.traceCoroutine
import com.android.systemui.Flags
import com.android.systemui.authentication.domain.interactor.AuthenticationInteractor
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.authentication.shared.model.AuthenticationWipeModel
import com.android.systemui.bouncer.domain.interactor.BouncerActionButtonInteractor
import com.android.systemui.bouncer.domain.interactor.BouncerInteractor
import com.android.systemui.bouncer.shared.model.BouncerActionButtonModel
import com.android.systemui.bouncer.shared.model.BouncerInputSide
import com.android.systemui.bouncer.ui.BouncerColors.surfaceColor
import com.android.systemui.bouncer.ui.helper.BouncerHapticPlayer
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.shared.model.Text
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryFaceAuthInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardDismissActionInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardMediaKeyInteractor
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.res.R
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.user.domain.interactor.UserLockedInteractor
import com.android.systemui.user.domain.interactor.UserLogoutInteractor
import com.android.systemui.user.ui.viewmodel.UserSwitcherViewModel
import com.android.systemui.window.domain.interactor.WindowRootViewBlurInteractor
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Models UI state for the content of the bouncer overlay. */
@OptIn(ExperimentalCoroutinesApi::class)
class BouncerOverlayContentViewModel
@AssistedInject
constructor(
    @Application private val applicationContext: Context,
    private val bouncerInteractor: BouncerInteractor,
    private val authenticationInteractor: AuthenticationInteractor,
    private val devicePolicyManager: DevicePolicyManager,
    private val bouncerMessageViewModelFactory: BouncerMessageViewModel.Factory,
    private val userSwitcher: UserSwitcherViewModel,
    private val pinViewModelFactory: PinBouncerViewModel.Factory,
    private val patternViewModelFactory: PatternBouncerViewModel.Factory,
    private val passwordViewModelFactory: PasswordBouncerViewModel.Factory,
    private val bouncerHapticPlayer: BouncerHapticPlayer,
    private val keyguardMediaKeyInteractor: KeyguardMediaKeyInteractor,
    private val bouncerActionButtonInteractor: BouncerActionButtonInteractor,
    private val keyguardDismissActionInteractor: KeyguardDismissActionInteractor,
    private val sceneInteractor: SceneInteractor,
    private val windowRootViewBlurInteractor: WindowRootViewBlurInteractor,
    private val faceAuthInteractor: DeviceEntryFaceAuthInteractor,
    private val userLockedInteractor: UserLockedInteractor,
    private val userLogoutInteractor: UserLogoutInteractor,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
) : HydratedActivatable(enableEnqueuedActivations = enableWeaverWarmup()) {
    private val _selectedUserImage = MutableStateFlow<Bitmap?>(null)
    val selectedUserImage: StateFlow<Bitmap?> = _selectedUserImage.asStateFlow()

    private val _selectedUserName = MutableStateFlow<Text?>(null)
    val selectedUserName: StateFlow<Text?> = _selectedUserName.asStateFlow()

    val message: BouncerMessageViewModel by lazy { bouncerMessageViewModelFactory.create() }

    private val _userSwitcherDropdown =
        MutableStateFlow<List<UserSwitcherDropdownItemViewModel>>(emptyList())
    val userSwitcherDropdown: StateFlow<List<UserSwitcherDropdownItemViewModel>> =
        _userSwitcherDropdown.asStateFlow()

    private val _isUserSwitcherVisible = MutableStateFlow(false)
    val isUserSwitcherVisible: StateFlow<Boolean> = _isUserSwitcherVisible.asStateFlow()

    /**
     * A message for a dialog to show when the user has attempted the wrong credential too many
     * times and now must wait a while before attempting again.
     *
     * If `null`, the lockout dialog should not be shown.
     */
    private val lockoutDialogMessage = MutableStateFlow<String?>(null)

    /**
     * A message for a dialog to show when the user has attempted the wrong credential too many
     * times and their user/profile/device data is at risk of being wiped due to a Device Manager
     * policy.
     *
     * If `null`, the wipe dialog should not be shown.
     */
    private val wipeDialogMessage = MutableStateFlow<String?>(null)

    private val _dialogViewModel = MutableStateFlow<DialogViewModel?>(createDialogViewModel())
    /**
     * Models the dialog to be shown to the user, or `null` if no dialog should be shown.
     *
     * Once the dialog is shown, the UI should call [DialogViewModel.onDismiss] when the user
     * dismisses this dialog.
     */
    val dialogViewModel: StateFlow<DialogViewModel?> = _dialogViewModel.asStateFlow()

    /**
     * The bouncer action button (Return to Call / Emergency Call). If `null`, the button should not
     * be shown.
     */
    val actionButton: BouncerActionButtonModel? by
        bouncerActionButtonInteractor.actionButton.hydratedStateOf(
            initialValue = bouncerActionButtonInteractor.currentActionButton
        )

    private val _isOneHandedModeSupported = MutableStateFlow(false)
    /**
     * Whether the one-handed mode is supported.
     *
     * When presented on its own, without a user switcher (e.g. not on communal devices like
     * tablets, for example), some authentication method UIs don't do well if they're shown in the
     * side-by-side layout; these need to be shown with the standard layout so they can take up as
     * much width as possible.
     */
    val isOneHandedModeSupported: StateFlow<Boolean> = _isOneHandedModeSupported.asStateFlow()

    /**
     * Whether to show a "back" button on bouncer. This is enabled for large screen interaction as
     * these typically don't rely on touch gestures to go back.
     */
    val showBackButton =
        (Flags.backButtonOnBouncerFix() || Flags.backButtonOnBouncerFix2()) &&
            bouncerInteractor.isImproveLargeScreenInteractionEnabled

    /** Whether to show the accessibility button on the bouncer. */
    val showAccessibilityButton =
        Flags.bouncerAccessibilityButtonForDesktop() &&
            bouncerInteractor.isShowAccessibilityButtonOnBouncerEnabled

    val accessibilityActions: List<CustomAccessibilityAction>
        get() = buildList {
            if (faceAuthInteractor.canFaceAuthRun()) {
                add(
                    CustomAccessibilityAction(applicationContext.getString(R.string.retry_face)) {
                        faceAuthInteractor.onAccessibilityAction()
                        true
                    }
                )
            }
        }

    private val _isInputPreferredOnLeftSide = MutableStateFlow(false)
    val isInputPreferredOnLeftSide = _isInputPreferredOnLeftSide.asStateFlow()

    /** How much the bouncer UI should be scaled. */
    val scale: StateFlow<Float> = bouncerInteractor.scale

    /** Bouncer background color */
    val backgroundColor by
        windowRootViewBlurInteractor.isBlurCurrentlySupported
            .map { Color(applicationContext.surfaceColor(it)) }
            .hydratedStateOf(
                "backgroundColor",
                Color(
                    applicationContext.surfaceColor(
                        windowRootViewBlurInteractor.isBlurCurrentlySupported.value
                    )
                ),
            )

    private val _isInputEnabled = MutableStateFlow(authenticationInteractor.lockoutEndTime == null)
    private val isInputEnabled: StateFlow<Boolean> = _isInputEnabled.asStateFlow()

    private val authenticationMethod: AuthenticationMethodModel? by
        authenticationInteractor.authenticationMethod
            .filter { it !is AuthenticationMethodModel.Biometric }
            .distinctUntilChanged()
            .hydratedStateOf(
                initialValue =
                    authenticationInteractor.authenticationMethod.value.takeIf {
                        it !is AuthenticationMethodModel.Biometric
                    }
            )

    /** View-model for the current UI, based on the current authentication method. */
    val authMethodViewModel: AuthMethodBouncerViewModel? by derivedStateOf {
        authenticationMethod?.let { getChildViewModel(it) }
    }

    val showSignInButton: Boolean
        get() = showSignInButton(authMethodViewModel)

    val isSignInButtonEnabled: Boolean by
        snapshotFlow { authMethodViewModel }
            .filterNotNull()
            .flatMapLatest { it.readyToTryAuthenticate }
            .hydratedStateOf(
                initialValue = authMethodViewModel?.readyToTryAuthenticate?.value ?: false
            )

    /**
     * Whether the splitting the UI around the fold seam (where the hinge is on a foldable device)
     * is required.
     */
    val isFoldSplitRequired: Boolean
        get() = isFoldSplitRequired(authMethodViewModel)

    override suspend fun onActivated(): Nothing {
        bouncerInteractor.resetScale()
        coroutineScope {
            launch { message.activate() }
            launch {
                snapshotFlow { authMethodViewModel }
                    .collectLatest { it?.let { traceCoroutine(it.traceName) { it.activate() } } }
            }

            launch {
                authenticationInteractor.upcomingWipe.collect { wipeModel ->
                    wipeDialogMessage.value = wipeModel?.message
                }
            }

            launch {
                userSwitcher.selectedUser
                    .map { it.image.toBitmap() }
                    .flowOn(backgroundDispatcher)
                    .collect { _selectedUserImage.value = it }
            }

            launch {
                userSwitcher.selectedUser.map { it.name }.collect { _selectedUserName.value = it }
            }

            launch {
                combine(userSwitcher.users, userSwitcher.menu) { users, actions ->
                        users.map { user ->
                            UserSwitcherDropdownItemViewModel(
                                icon = Icon.Loaded(user.image, contentDescription = null),
                                text = user.name,
                                onClick = user.onClicked ?: {},
                            )
                        } +
                            actions.map { action ->
                                UserSwitcherDropdownItemViewModel(
                                    icon =
                                        withContext(backgroundDispatcher) {
                                            Icon.Loaded(
                                                applicationContext.resources.getDrawable(
                                                    action.iconResourceId
                                                ),
                                                contentDescription = null,
                                            )
                                        },
                                    text = Text.Resource(action.textResourceId),
                                    onClick = action.onClicked,
                                )
                            }
                    }
                    .collect { _userSwitcherDropdown.value = it }
            }

            launch {
                combine(wipeDialogMessage, lockoutDialogMessage) { _, _ -> createDialogViewModel() }
                    .collect { _dialogViewModel.value = it }
            }

            launch {
                combine(
                        bouncerInteractor.isOneHandedModeSupported,
                        bouncerInteractor.lastRecordedLockscreenTouchPosition,
                        ::Pair,
                    )
                    .collect { (isOneHandedModeSupported, lastRecordedNotificationTouchPosition) ->
                        _isOneHandedModeSupported.value = isOneHandedModeSupported
                        if (
                            isOneHandedModeSupported &&
                                lastRecordedNotificationTouchPosition != null
                        ) {
                            bouncerInteractor.setPreferredBouncerInputSide(
                                if (
                                    lastRecordedNotificationTouchPosition <
                                        applicationContext.resources.displayMetrics.widthPixels / 2
                                ) {
                                    BouncerInputSide.LEFT
                                } else {
                                    BouncerInputSide.RIGHT
                                }
                            )
                        }
                    }
            }

            launch {
                bouncerInteractor.isUserSwitcherVisible.collect {
                    _isUserSwitcherVisible.value = it
                }
            }

            launch {
                bouncerInteractor.preferredBouncerInputSide.collect {
                    _isInputPreferredOnLeftSide.value = it == BouncerInputSide.LEFT
                }
            }

            launch {
                message.isLockoutMessagePresent
                    .map { lockoutMessagePresent -> !lockoutMessagePresent }
                    .collect { _isInputEnabled.value = it }
            }

            awaitCancellation()
        }
    }

    private fun isFoldSplitRequired(authMethod: AuthMethodBouncerViewModel?): Boolean {
        return authMethod !is PasswordBouncerViewModel
    }

    private fun showSignInButton(authMethod: AuthMethodBouncerViewModel?): Boolean {
        return authMethod?.showSignInButton ?: false
    }

    private fun getChildViewModel(
        authenticationMethod: AuthenticationMethodModel
    ): AuthMethodBouncerViewModel? {
        return when (authenticationMethod) {
            is AuthenticationMethodModel.Pin ->
                pinViewModelFactory.create(
                    authenticationMethod = authenticationMethod,
                    onIntentionalUserInput = ::onIntentionalUserInput,
                    isInputEnabled = isInputEnabled,
                    bouncerHapticPlayer = bouncerHapticPlayer,
                )
            is AuthenticationMethodModel.Sim ->
                pinViewModelFactory.create(
                    authenticationMethod = authenticationMethod,
                    onIntentionalUserInput = ::onIntentionalUserInput,
                    isInputEnabled = isInputEnabled,
                    bouncerHapticPlayer = bouncerHapticPlayer,
                )
            is AuthenticationMethodModel.Password ->
                passwordViewModelFactory.create(
                    onIntentionalUserInput = ::onIntentionalUserInput,
                    isInputEnabled = isInputEnabled,
                    bouncerHapticPlayer = bouncerHapticPlayer,
                )
            is AuthenticationMethodModel.Pattern ->
                patternViewModelFactory.create(
                    onIntentionalUserInput = ::onIntentionalUserInput,
                    isInputEnabled = isInputEnabled,
                    bouncerHapticPlayer = bouncerHapticPlayer,
                )
            else -> null
        }
    }

    private fun onIntentionalUserInput() {
        message.showDefaultMessage()
        if (enableWeaverWarmup()) {
            enqueueOnActivatedScope { authenticationInteractor.triggerAuthWarmUp() }
        }
        bouncerInteractor.onIntentionalUserInput()
    }

    /**
     * @return A message warning the user that the user/profile/device will be wiped upon a further
     *   [AuthenticationWipeModel.remainingAttempts] unsuccessful authentication attempts.
     */
    private fun AuthenticationWipeModel.getAlmostAtWipeMessage(): String {
        val message =
            applicationContext.getString(
                wipeTarget.messageIdForAlmostWipe,
                failedAttempts,
                remainingAttempts,
            )
        return if (wipeTarget == AuthenticationWipeModel.WipeTarget.ManagedProfile) {
            devicePolicyManager.resources.getString(
                DevicePolicyResources.Strings.SystemUi
                    .KEYGUARD_DIALOG_FAILED_ATTEMPTS_ALMOST_ERASING_PROFILE,
                { message },
                failedAttempts,
                remainingAttempts,
            ) ?: message
        } else {
            message
        }
    }

    /**
     * @return A message informing the user that their user/profile/device will be wiped promptly.
     */
    private fun AuthenticationWipeModel.getWipeMessage(): String {
        val message = applicationContext.getString(wipeTarget.messageIdForWipe, failedAttempts)
        return if (wipeTarget == AuthenticationWipeModel.WipeTarget.ManagedProfile) {
            devicePolicyManager.resources.getString(
                DevicePolicyResources.Strings.SystemUi
                    .KEYGUARD_DIALOG_FAILED_ATTEMPTS_ERASING_PROFILE,
                { message },
                failedAttempts,
            ) ?: message
        } else {
            message
        }
    }

    private val AuthenticationWipeModel.message: String
        get() = if (remainingAttempts > 0) getAlmostAtWipeMessage() else getWipeMessage()

    private fun createDialogViewModel(): DialogViewModel? {
        val wipeText = wipeDialogMessage.value
        val lockoutText = lockoutDialogMessage.value
        return when {
            // The wipe dialog takes priority over the lockout dialog.
            wipeText != null ->
                DialogViewModel(text = wipeText, onDismiss = { wipeDialogMessage.value = null })
            lockoutText != null ->
                DialogViewModel(
                    text = lockoutText,
                    onDismiss = { lockoutDialogMessage.value = null },
                )
            else -> null // No dialog to show.
        }
    }

    /**
     * Notifies that double tap gesture was detected on the bouncer.
     * [wasEventOnNonInputHalfOfScreen] is true when it happens on the side of the bouncer where the
     * input UI is not present.
     */
    fun onDoubleTap(wasEventOnNonInputHalfOfScreen: Boolean) {
        // Swap of layout columns on double click should be disabled to improve interaction on
        // large-screen form factor, e.g. desktop, kiosk
        val disableDoubleClickSwap =
            (Flags.disableDoubleClickSwapOnBouncer() || Flags.disableDoubleClickSwapOnBouncer2()) &&
                bouncerInteractor.isImproveLargeScreenInteractionEnabled
        if (disableDoubleClickSwap) return
        if (!wasEventOnNonInputHalfOfScreen) return
        if (_isInputPreferredOnLeftSide.value) {
            bouncerInteractor.setPreferredBouncerInputSide(BouncerInputSide.RIGHT)
        } else {
            bouncerInteractor.setPreferredBouncerInputSide(BouncerInputSide.LEFT)
        }
    }

    /**
     * Notifies that onDown was detected on the bouncer. [wasEventOnNonInputHalfOfScreen] is true
     * when it happens on the side of the bouncer where the input UI is not present.
     */
    fun onDown(wasEventOnNonInputHalfOfScreen: Boolean) {
        if (!wasEventOnNonInputHalfOfScreen) return
        bouncerInteractor.onDown()
    }

    fun backgroundTap() {
        if (!bouncerInteractor.isFalseBackgroundTap()) {
            bouncerInteractor.onIntentionalUserInput()
        }
    }

    /*
     * Notifies that the user switcher dropdown was clicked or dismissed.
     */
    fun onUserSwitcherDropdown() {
        bouncerInteractor.onIntentionalUserInput()
    }

    /**
     * Notifies that a key event has occurred.
     *
     * @return `true` when the [KeyEvent] was consumed as user input on bouncer; `false` otherwise.
     */
    fun onKeyEvent(keyEvent: KeyEvent): Boolean {
        if (keyguardMediaKeyInteractor.processMediaKeyEvent(keyEvent.nativeKeyEvent)) return true
        return authMethodViewModel?.onKeyEvent(keyEvent.type, keyEvent.nativeKeyEvent.keyCode)
            ?: false
    }

    fun onActionButtonClicked(actionButtonModel: BouncerActionButtonModel) {
        onIntentionalUserInput()
        when (actionButtonModel) {
            is BouncerActionButtonModel.EmergencyButtonModel -> {
                bouncerHapticPlayer.playEmergencyButtonClickFeedback()
                bouncerActionButtonInteractor.onEmergencyButtonClicked()
            }
            is BouncerActionButtonModel.ReturnToCallButtonModel -> {
                bouncerActionButtonInteractor.onReturnToCallButtonClicked()
            }
        }
    }

    fun onActionButtonLongClicked(actionButtonModel: BouncerActionButtonModel) {
        if (actionButtonModel is BouncerActionButtonModel.EmergencyButtonModel) {
            bouncerActionButtonInteractor.onEmergencyButtonLongClicked()
        }
    }

    /**
     * Notifies that the bouncer UI has been destroyed (e.g. the composable left the composition).
     */
    fun onUiDestroyed() {
        keyguardDismissActionInteractor.clearDismissAction()
    }

    /**
     * Handles back button clicks. Signs out the user or navigates back based on
     * `shouldSignOutOnGoBack()`.
     */
    fun onGoBack() {
        enqueueOnActivatedScope {
            if (shouldSignOutOnGoBack()) {
                userLogoutInteractor.logOutToSystemUser()
            } else {
                navigateBack()
            }
        }
    }

    /**
     * Checks if the user should be signed out when going back.
     *
     * True if the user logged in, but not yet unlocked.
     *
     * @return True to sign out, false otherwise.
     */
    suspend private fun shouldSignOutOnGoBack(): Boolean {
        return userLogoutInteractor.isLogoutToSystemUserEnabled.value &&
            userLockedInteractor.isCurrentUserStorageLocked()
    }

    private fun navigateBack() {
        sceneInteractor.hideOverlay(Overlays.Bouncer, "back button clicked")
    }

    fun onSignIn() {
        authMethodViewModel?.tryAuthenticate()
    }

    fun showAccessibilityDialog() {
        // TODO: b/449824070 - Show dialog once implemented.
    }

    data class DialogViewModel(
        val text: String,

        /** Callback to run after the dialog has been dismissed by the user. */
        val onDismiss: () -> Unit,
    )

    data class UserSwitcherDropdownItemViewModel(
        val icon: Icon,
        val text: Text,
        val onClick: () -> Unit,
    )

    @AssistedFactory
    interface Factory {
        fun create(): BouncerOverlayContentViewModel
    }
}
