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

package com.android.systemui.screencapture.ui

import android.content.Context
import android.view.Display
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.TransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ScreenCaptureScope
import com.android.systemui.screencapture.common.ScreenCaptureUiComponent
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiParameters
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiState
import com.android.systemui.screencapture.ui.viewmodel.ScreenCaptureUiDialogViewModel
import com.android.systemui.screencapture.ui.viewmodel.ScreenCaptureUiViewModel
import com.android.systemui.statusbar.phone.EdgeToEdgeDialogDelegate
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.statusbar.phone.SystemUIDialogFactory
import com.android.systemui.statusbar.phone.create
import javax.inject.Inject

// TODO(b/427481098) Change to TYPE_SCREENSHOT
private const val WINDOW_TYPE = WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL

@ScreenCaptureScope
class ScreenCaptureUiDialogFactory
@Inject
constructor(
    @Application private val appContext: Context,
    private val dialogViewModelFactory: ScreenCaptureUiDialogViewModel.Factory,
    private val viewModelFactory: ScreenCaptureUiViewModel.Factory,
    private val componentBuilders:
        Map<
            @JvmSuppressWildcards
            ScreenCaptureType,
            @JvmSuppressWildcards
            ScreenCaptureUiComponent.Builder,
        >,
    private val dialogFactory: SystemUIDialogFactory,
) {

    fun create(display: Display, type: ScreenCaptureType): SystemUIDialog {
        val dialogViewModel = dialogViewModelFactory.create()
        return dialogFactory
            .create(
                context =
                    appContext.createWindowContext(
                        /* display= */ display,
                        // TODO(b/427481098) Change to TYPE_SCREENSHOT
                        /* type= */ WINDOW_TYPE,
                        /* options= */ null,
                    ),
                theme = R.style.Theme_SystemUI_Dialog_ScreenCapture,
                dialogDelegate =
                    EdgeToEdgeDialogDelegate(touchEvent = dialogViewModel::onTouchEvent),
                dismissOnDeviceLock = true,
                isTransient = !dialogViewModel.isLargeScreen,
            ) { dialog: SystemUIDialog ->
                LaunchedEffect(dialogViewModel) { dialogViewModel.activate() }
                dialog.DialogContent(
                    dialogViewModel = dialogViewModel,
                    type = type,
                    display = display,
                )
            }
            .apply {
                setupWindow(window!!)
                setCancelable(false)
                setCanceledOnTouchOutside(false)
                setDismissOverride {
                    if (dialogViewModel.visibleTransitionState.isIdleAt(false)) {
                        dismissImmediately()
                    } else {
                        dialogViewModel.dismiss()
                    }
                }
            }
    }

    private fun setupWindow(window: Window) {
        window.attributes =
            window.attributes.apply {
                title = "ScreenCaptureUi" // Not the same as Window#setTitle
            }
        with(window) {
            addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
            addPrivateFlags(WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY)
            setType(WINDOW_TYPE)
            setWindowAnimations(-1)
        }
    }

    @Composable
    private fun SystemUIDialog.DialogContent(
        dialogViewModel: ScreenCaptureUiDialogViewModel,
        type: ScreenCaptureType,
        display: Display,
    ) {
        val viewModel =
            rememberViewModel("ScreenCaptureUi#viewModel") { viewModelFactory.create(type) }
        var parametersState: ScreenCaptureUiParameters? by remember { mutableStateOf(null) }
        LaunchedEffect(viewModel.state) {
            (viewModel.state as? ScreenCaptureUiState.Visible)?.parameters?.let {
                parametersState = it
            }
        }
        // Wait until parameters are passed down to Compose
        val parameters = parametersState ?: return

        val transition: Transition<Boolean> =
            rememberTransition(dialogViewModel.visibleTransitionState)
        val builder: ScreenCaptureUiComponent.Builder =
            componentBuilders.getValue(parameters.screenCaptureType)
        val coroutineScope = rememberCoroutineScope()
        val component =
            remember(parameters, coroutineScope) {
                builder
                    .setScope(coroutineScope)
                    .setDisplay(display)
                    .setWindow(window)
                    .setVisibilityTransition(transition)
                    .build()
            }
        Box(modifier = Modifier.focusable()) { component.screenCaptureContent.Content() }

        DisposableEffect(dialogViewModel) {
            // set off the animation once after ScreenCaptureContent#Content has a chance to
            // populate the transition with its animation
            dialogViewModel.show()
            onDispose {}
        }
        SideEffect {
            if (dialogViewModel.isDismissed && transition.isIdle) {
                dismissImmediately()
            }
        }
        DisposableEffect(transition.isIdle) {
            if (transition.isIdle) {
                viewModel.onFinishedChangingVisibility()
            }
            onDispose {}
        }
    }
}

private fun SystemUIDialog.dismissImmediately() {
    setDismissOverride(null)
    dismissWithoutAnimation()
}

private fun <T> TransitionState<T>.isIdleAt(state: T): Boolean =
    currentState == state && targetState == state

private val Transition<*>.isIdle
    get() = (currentState == targetState) && !isRunning
