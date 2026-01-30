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

package com.android.systemui.biometrics.ui.binder

import android.util.Log
import androidx.core.view.isInvisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.biometrics.domain.interactor.UdfpsOverlayInteractor
import com.android.systemui.biometrics.ui.view.UdfpsTouchOverlay
import com.android.systemui.biometrics.ui.viewmodel.UdfpsTouchOverlayViewModel
import com.android.systemui.lifecycle.repeatWhenAttached

object UdfpsTouchOverlayBinder {

    /**
     * Updates visibility for the UdfpsTouchOverlay. This controls whether the view will receive
     * touches or not. This is important for optical-UDFPS to receive the fingerprint-sensor touch
     * events.
     */
    @JvmStatic
    fun bind(
        view: UdfpsTouchOverlay,
        viewModel: UdfpsTouchOverlayViewModel,
        udfpsOverlayInteractor: UdfpsOverlayInteractor? = null,
    ) {
        view.repeatWhenAttached {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                launch {
                        viewModel.shouldHandleTouches.collect { shouldHandleTouches ->
                            if (udfpsOverlayInteractor != null) {
                                Log.d(
                                    "UdfpsTouchOverlayBinder",
                                    "[$view]: update shouldHandleTouches=$shouldHandleTouches",
                                )
                            } else {
                                Log.d(
                                    "UdfpsTouchOverlayBinder",
                                    "[$view]: update isVisible=$shouldHandleTouches",
                                )
                            }
                            view.isInvisible = !shouldHandleTouches
                            udfpsOverlayInteractor?.setHandleTouches(shouldHandleTouches)
                        }
                    }
                    .invokeOnCompletion {
                        if (udfpsOverlayInteractor != null) {
                            Log.d(
                                "UdfpsTouchOverlayBinder",
                                "[$view-detached]: update shouldHandleTouches=false",
                            )
                            udfpsOverlayInteractor?.setHandleTouches(false)
                        }
                    }
            }
        }
    }
}
