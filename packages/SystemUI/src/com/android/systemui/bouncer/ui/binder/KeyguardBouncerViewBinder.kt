/*
 * Copyright (C) 2022 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.bouncer.ui.binder

import android.security.Flags.secureLockDevice
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.window.OnBackAnimationCallback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.keyguard.KeyguardMessageAreaController
import com.android.keyguard.KeyguardSecurityContainerController
import com.android.keyguard.KeyguardSecurityModel
import com.android.keyguard.KeyguardSecurityView
import com.android.keyguard.dagger.KeyguardBouncerComponent
import com.android.systemui.biometrics.plugins.AuthContextPlugins
import com.android.systemui.bouncer.domain.interactor.BouncerMessageInteractor
import com.android.systemui.bouncer.shared.constants.KeyguardBouncerConstants.EXPANSION_VISIBLE
import com.android.systemui.bouncer.ui.BouncerViewDelegate
import com.android.systemui.bouncer.ui.viewmodel.KeyguardBouncerViewModel
import com.android.systemui.keyguard.ui.viewmodel.GlanceableHubToPrimaryBouncerTransitionViewModel
import com.android.systemui.keyguard.ui.viewmodel.PrimaryBouncerToDreamingTransitionViewModel
import com.android.systemui.keyguard.ui.viewmodel.PrimaryBouncerToGoneTransitionViewModel
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.log.BouncerLogger
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.AuthContextPlugin
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.user.domain.interactor.SelectedUserInteractor
import com.android.systemui.util.kotlin.sample
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge

private const val TAG = "KeyguardBouncerViewBinder"

/** Binds the bouncer container to its view model. */
object KeyguardBouncerViewBinder {
    @JvmStatic
    fun bind(
        mainImmediateDispatcher: CoroutineDispatcher,
        view: ViewGroup,
        viewModel: KeyguardBouncerViewModel,
        primaryBouncerToDreamingTransitionViewModel: PrimaryBouncerToDreamingTransitionViewModel,
        primaryBouncerToGoneTransitionViewModel: PrimaryBouncerToGoneTransitionViewModel,
        glanceableHubToPrimaryBouncerTransitionViewModel:
            GlanceableHubToPrimaryBouncerTransitionViewModel,
        componentFactory: KeyguardBouncerComponent.Factory,
        messageAreaControllerFactory: KeyguardMessageAreaController.Factory,
        bouncerMessageInteractor: BouncerMessageInteractor,
        bouncerLogger: BouncerLogger,
        selectedUserInteractor: SelectedUserInteractor,
        plugins: AuthContextPlugins?,
    ) {
        // Builds the KeyguardSecurityContainerController from bouncer view group.
        val securityContainerController: KeyguardSecurityContainerController =
            componentFactory.create(view).securityContainerController
        securityContainerController.init()
        val delegate =
            object : BouncerViewDelegate {
                override fun isFullScreenBouncer(): Boolean {
                    val mode = securityContainerController.currentSecurityMode
                    return mode == KeyguardSecurityModel.SecurityMode.SimPin ||
                        mode == KeyguardSecurityModel.SecurityMode.SimPuk
                }

                override fun getBackCallback(): OnBackAnimationCallback {
                    return securityContainerController.backCallback
                }

                override fun shouldDismissOnMenuPressed(): Boolean {
                    return securityContainerController.shouldEnableMenuKey()
                }

                override fun interceptMediaKey(event: KeyEvent?): Boolean {
                    return securityContainerController.interceptMediaKey(event)
                }

                override fun dispatchBackKeyEventPreIme(): Boolean {
                    return securityContainerController.dispatchBackKeyEventPreIme()
                }

                override fun showNextSecurityScreenOrFinish(): Boolean {
                    return securityContainerController.dismiss(
                        selectedUserInteractor.getSelectedUserId()
                    )
                }

                override fun resume() {
                    securityContainerController.showPrimarySecurityScreen(/* isTurningOff= */ false)
                    securityContainerController.onResume(KeyguardSecurityView.SCREEN_ON)
                }

                override fun setDismissAction(
                    onDismissAction: ActivityStarter.OnDismissAction?,
                    cancelAction: Runnable?,
                ) {
                    securityContainerController.setOnDismissAction(onDismissAction, cancelAction)
                }

                override fun willDismissWithActions(): Boolean {
                    return securityContainerController.hasDismissActions()
                }

                override fun willRunDismissFromKeyguard(): Boolean {
                    return securityContainerController.willRunDismissFromKeyguard()
                }

                override fun showPromptReason(reason: Int) {
                    securityContainerController.showPromptReason(reason)
                }
            }

        view.repeatWhenAttached(mainImmediateDispatcher) {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                launch {
                    viewModel.bouncerExpansionAmount.collect { expansion ->
                        securityContainerController.setExpansion(expansion)
                        if (expansion == EXPANSION_VISIBLE) {
                            securityContainerController.onResume(KeyguardSecurityView.SCREEN_ON)
                        }
                    }
                }

                launch {
                    merge(
                            primaryBouncerToDreamingTransitionViewModel.bouncerAlpha,
                            primaryBouncerToGoneTransitionViewModel.bouncerAlpha,
                        )
                        .collect { alpha -> securityContainerController.setAlpha(alpha) }
                }

                launch {
                    viewModel.keyguardPosition.collect { position ->
                        securityContainerController.updateKeyguardPosition(position)
                    }
                }
            }
        }

        view.repeatWhenAttached {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                try {
                    viewModel.setBouncerViewDelegate(delegate)
                    launch {
                        val isTransitionToGoneFinished =
                            if (secureLockDevice()) {
                                viewModel.isTransitionToGoneFinished
                            } else {
                                flowOf(false)
                            }
                        viewModel.isShowing.sample(isTransitionToGoneFinished, ::Pair)
                            .collect { (isShowing, isTransitionToGoneFinished) ->
                                view.visibility = if (isShowing) View.VISIBLE else View.INVISIBLE
                                if (isShowing) {
                                    // Reset security container because these views are not
                                    // reinflated.
                                    securityContainerController.prepareToShow()
                                    securityContainerController.reinflateViewFlipper {
                                        // Reset Security Container entirely.
                                        securityContainerController.onBouncerVisibilityChanged(
                                            /* isVisible= */ true
                                        )
                                        securityContainerController.showPrimarySecurityScreen(
                                            /* turningOff= */ false
                                        )
                                        securityContainerController.setInitialMessage()
                                        // Delay bouncer appearing animation when opening it from
                                        // the
                                        // glanceable hub in landscape, until after orientation
                                        // changes
                                        // to portrait. This prevents bouncer from showing in
                                        // landscape
                                        // layout, if bouncer rotation is not allowed.
                                        if (
                                            glanceableHubToPrimaryBouncerTransitionViewModel
                                                .willDelayAppearAnimation(
                                                    securityContainerController
                                                        .isLandscapeOrientation
                                                )
                                        ) {
                                            securityContainerController.setupForDelayedAppear()
                                        } else {
                                            securityContainerController.appear()
                                        }
                                        securityContainerController.onResume(
                                            KeyguardSecurityView.SCREEN_ON
                                        )
                                        bouncerLogger.bindingBouncerMessageView()
                                        it.bindMessageView(
                                            bouncerMessageInteractor,
                                            messageAreaControllerFactory,
                                            bouncerLogger,
                                        )
                                    }
                                } else {
                                    securityContainerController.onBouncerVisibilityChanged(
                                        /* isVisible= */ false
                                    )

                                    val isTransitionFromSecureLockDeviceToGone =
                                        secureLockDevice()
                                                && isTransitionToGoneFinished 
                                                && viewModel.lastShownSecurityMode.value ==
                                                KeyguardSecurityModel.SecurityMode
                                                    .SecureLockDeviceBiometricAuth

                                    if (isTransitionFromSecureLockDeviceToGone) {
                                        // Skips below actions because we don't want to retrigger
                                        // the security screen while the biometric bouncer is
                                        // animating out.
                                        Log.d(
                                            TAG,
                                            "Hiding bouncer after completing two-factor " +
                                                "authentication for secure lock device.",
                                        )
                                        securityContainerController.onSecureLockDeviceUnlock()
                                        bouncerMessageInteractor.onSecureLockDeviceUnlock()
                                    } else {
                                        securityContainerController.cancelDismissAction()
                                        securityContainerController.reset()
                                        securityContainerController.onPause()
                                    }
                                }
                                plugins?.apply {
                                    if (isShowing) {
                                        notifyBouncerShowing(view)
                                    } else {
                                        notifyBouncerGone()
                                    }
                                }
                            }
                    }

                    if (secureLockDevice() && !SceneContainerFlag.isEnabled) {
                        launch {
                            combine(
                                    viewModel.requiresPrimaryAuthForSecureLockDevice,
                                    viewModel.requiresStrongBiometricAuthForSecureLockDevice,
                                    viewModel.isReadyToDismissSecureLockDeviceOnUnlock,
                                    ::Triple,
                                )
                                .collect {
                                    (
                                        shouldShowPrimaryAuth,
                                        shouldShowBiometricAuth,
                                        readyToDismissSecureLockDevice) ->
                                    if (
                                        readyToDismissSecureLockDevice
                                    ) { // Secure lock device bio auth -> gone transition
                                        plugins?.notifyBouncerGone()
                                    } else if (shouldShowPrimaryAuth && !shouldShowBiometricAuth) {
                                        // Secure lock device bio auth -> primary auth transition
                                        securityContainerController
                                            .onSecureLockDeviceBiometricAuthInterrupted {
                                                securityContainerController
                                                    .onBouncerVisibilityChanged(
                                                        /* isVisible= */ false
                                                    )
                                                securityContainerController.cancelDismissAction()
                                                securityContainerController.reset()
                                                securityContainerController.onPause()
                                            }
                                        plugins?.notifyBouncerGone()
                                    } else if (!shouldShowPrimaryAuth && shouldShowBiometricAuth) {
                                        // Secure lock device primary auth -> bio auth transition
                                        view.visibility = View.VISIBLE
                                        showSecureLockDeviceBiometricAuth(
                                            securityContainerController,
                                            bouncerLogger,
                                            bouncerMessageInteractor,
                                            messageAreaControllerFactory,
                                        )
                                        plugins?.notifyBouncerShowing(view)
                                    }
                                }
                        }

                        launch {
                            viewModel.showConfirmBiometricAuthButton.collect { showConfirmButton ->
                                if (showConfirmButton) {
                                    bouncerMessageInteractor.onSecureLockDevicePendingConfirmation()
                                }
                            }
                        }

                        launch {
                            combine(viewModel.showTryAgainButton, viewModel.showingError, ::Pair)
                                .collect { (showTryAgainButton, showingError) ->
                                    if (showTryAgainButton) {
                                        bouncerMessageInteractor
                                            .onSecureLockDeviceRetryAuthentication(showingError)
                                    }
                                }
                        }
                    }

                    launch {
                        viewModel.startingToHide.collect {
                            securityContainerController.onStartingToHide()
                        }
                    }

                    launch {
                        viewModel.startDisappearAnimation.collect {
                            securityContainerController.startDisappearAnimation(it)
                        }
                    }

                    launch {
                        viewModel.isInteractable.collect { isInteractable ->
                            securityContainerController.setInteractable(isInteractable)
                        }
                    }

                    launch {
                        viewModel.updateResources.collect {
                            securityContainerController.updateResources()
                            viewModel.notifyUpdateResources()
                        }
                    }

                    launch {
                        viewModel.bouncerShowMessage.collect {
                            securityContainerController.showMessage(
                                it.message,
                                it.colorStateList,
                                /* animated= */ true,
                            )
                            viewModel.onMessageShown()
                        }
                    }

                    launch {
                        viewModel.keyguardAuthenticated.collect {
                            securityContainerController.finish(
                                selectedUserInteractor.getSelectedUserId()
                            )
                            viewModel.notifyKeyguardAuthenticated()
                        }
                    }

                    launch {
                        viewModel
                            .observeOnIsBackButtonEnabled { view.systemUiVisibility }
                            .collect { view.systemUiVisibility = it }
                    }

                    awaitCancellation()
                } finally {
                    viewModel.setBouncerViewDelegate(null)
                    plugins?.notifyBouncerGone()
                }
            }
        }
    }

    private fun showSecureLockDeviceBiometricAuth(
        securityContainerController: KeyguardSecurityContainerController,
        bouncerLogger: BouncerLogger,
        bouncerMessageInteractor: BouncerMessageInteractor,
        messageAreaControllerFactory: KeyguardMessageAreaController.Factory,
    ) {
        // Reset security container because these views are not reinflated.
        securityContainerController.prepareToShow()
        securityContainerController.showSecureLockDeviceView {
            // Reset Security Container entirely.
            securityContainerController.onBouncerVisibilityChanged(/* isVisible= */ true)
            securityContainerController.showPrimarySecurityScreen(/* turningOff= */ false)
            securityContainerController.setInitialMessage()
            securityContainerController.appear()
            securityContainerController.onResume(KeyguardSecurityView.SCREEN_ON)
            bouncerLogger.bindingBouncerMessageView()
            it.bindMessageView(
                bouncerMessageInteractor,
                messageAreaControllerFactory,
                bouncerLogger,
            )
        }
    }
}

private suspend fun AuthContextPlugins.notifyBouncerShowing(view: View) = use { plugin ->
    plugin.onShowingSensitiveSurface(
        AuthContextPlugin.SensitiveSurface.LockscreenBouncer(view = view)
    )
}

private fun AuthContextPlugins.notifyBouncerGone() = useInBackground { plugin ->
    plugin.onHidingSensitiveSurface(AuthContextPlugin.SensitiveSurface.LockscreenBouncer())
}
