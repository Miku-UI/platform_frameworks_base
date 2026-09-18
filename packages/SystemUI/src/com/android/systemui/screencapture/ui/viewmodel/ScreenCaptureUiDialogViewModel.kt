/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.screencapture.ui.viewmodel

import android.view.MotionEvent
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.TransitionState
import androidx.compose.runtime.getValue
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.screencapture.domain.interactor.ScreenCaptureOverlayStateInteractor
import com.android.systemui.screencapture.domain.interactor.ScreenCaptureUiInteractor
import com.android.systemui.statusbar.phone.SystemUIDialog
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class ScreenCaptureUiDialogViewModel
@AssistedInject
constructor(
    interactor: ScreenCaptureUiInteractor,
    overlayInteractor: ScreenCaptureOverlayStateInteractor,
) : HydratedActivatable() {

    val isLargeScreen: Boolean by
        interactor.isLargeScreen.hydratedStateOf("ScreenCaptureUiDialogViewModel#isLargeScreen")
    private val cancelOnTouchOutside: Boolean by
        interactor.isLargeScreen
            .flatMapLatest { isLargeScreen ->
                if (isLargeScreen) {
                    flowOf(false)
                } else {
                    // overlay will dismiss the toolbar when necessary
                    overlayInteractor.isVisible.map { !it }
                }
            }
            .hydratedStateOf("ScreenCaptureUiDialogViewModel#cancelOnTouchOutside", false)
    var isDismissed: Boolean = false
        private set

    private val _visibleTransitionState = MutableTransitionState(false)
    val visibleTransitionState: TransitionState<Boolean> = _visibleTransitionState

    fun onTouchEvent(dialog: SystemUIDialog, motionEvent: MotionEvent): Boolean {
        if (motionEvent.action != MotionEvent.ACTION_OUTSIDE) return false
        if (!cancelOnTouchOutside) return false
        dialog.dismiss()
        return true
    }

    fun dismiss() {
        _visibleTransitionState.targetState = false
        isDismissed = true
    }

    fun show() {
        isDismissed = false
        _visibleTransitionState.targetState = true
    }

    fun setupDismiss(dialog: SystemUIDialog) {
        dialog.setDismissOverride {}
    }

    @AssistedFactory
    interface Factory {
        fun create(): ScreenCaptureUiDialogViewModel
    }
}
