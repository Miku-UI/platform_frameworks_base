/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.window.ui

import android.util.Log
import com.android.app.tracing.coroutines.launchTraced
import com.android.systemui.Flags
import com.android.systemui.lifecycle.WindowLifecycleState
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.lifecycle.viewModel
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.ui.view.WindowRootView
import com.android.systemui.window.shared.model.BlurEffect
import com.android.systemui.window.ui.viewmodel.WindowRootViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter

/**
 * View binder that wires up window level UI transformations like blur to the [WindowRootView]
 * instance.
 */
object WindowRootViewBinder {
    private const val TAG = "WindowRootViewBinder"

    fun bind(
        view: WindowRootView,
        viewModelFactory: WindowRootViewModel.Factory,
        blurChoreographer: BlurChoreographer,
        mainDispatcher: CoroutineDispatcher,
    ) {
        if (!Flags.bouncerUiRevamp() && !Flags.glanceableHubBlurredBackground()) return
        if (SceneContainerFlag.isEnabled) return

        view.repeatWhenAttached(mainDispatcher) {
            Log.d(TAG, "Binding root view")
            view.viewModel(
                minWindowLifecycleState = WindowLifecycleState.ATTACHED,
                factory = { viewModelFactory.create() },
                traceName = "WindowRootViewBinder#bind",
            ) { viewModel ->
                val onBlurApplied = { blurEffect: BlurEffect ->
                    viewModel.onBlurApplied(blurEffect.radius.toInt(), false)
                }
                blurChoreographer.registerOnBlurAppliedListener(onBlurApplied)
                try {
                    Log.d(TAG, "Launching coroutines that update window root view state")
                    launchTraced("early-wakeup") {
                        viewModel.isPersistentEarlyWakeupRequired.collect { wakeupRequired ->
                            blurChoreographer.setPersistentEarlyWakeup(wakeupRequired)
                        }
                    }

                    launchTraced("WindowBlur") {
                        combine(viewModel.blurRadius, viewModel.blurScale, ::Pair)
                            .filter { it.first >= 0 }
                            .collect { (blurRadius, blurScale) ->
                                blurChoreographer.applyBlur(BlurEffect(blurRadius, blurScale))
                            }
                    }
                    awaitCancellation()
                } finally {
                    blurChoreographer.clearOnBlurAppliedListener()
                    Log.d(TAG, "Wrapped up coroutines that update window root view state")
                }
            }
        }
    }
}
