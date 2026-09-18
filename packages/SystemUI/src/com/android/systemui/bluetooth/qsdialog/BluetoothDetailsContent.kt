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

package com.android.systemui.bluetooth.qsdialog

import android.view.LayoutInflater
import android.view.View
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import com.android.compose.modifiers.skipToLookaheadSize
import com.android.systemui.bluetooth.ui.viewModel.BluetoothDetailsContentViewModel
import com.android.systemui.res.R

/**
 * Displays the content for the Bluetooth details dialog.
 *
 * @param detailsContentViewModel The ViewModel managing the state and view binding for this
 *   content.
 * @param onContentReady Callback invoked when the content is initially updated and ready to be
 *   displayed.
 */
@Composable
fun BluetoothDetailsContent(
    detailsContentViewModel: BluetoothDetailsContentViewModel,
    onContentReady: () -> Unit,
) {
    val onContentReadyCallback by rememberUpdatedState(onContentReady)

    var isContentReady by remember { mutableStateOf(false) }

    // Tracks the AndroidView's height to invalidate the Compose lookahead pass.
    // Updating this state forces Compose to remeasure when the view resizes.
    val lookaheadInvalidator = remember { mutableStateOf(0) }

    val layoutListener = remember {
        View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            lookaheadInvalidator.value = view.height
        }
    }

    AndroidView(
        modifier =
            Modifier.fillMaxWidth()
                .then(
                    if (isContentReady) {
                        Modifier.skipToLookaheadSize().layout { measurable, constraints ->
                            if (isLookingAhead) {
                                // Read `lookaheadInvalidator.value` to establish a state
                                // dependency.
                                // When the underlying AndroidView's height changes, the state
                                // updates, forcing Compose to re-run the lookahead pass.
                                @Suppress("UNUSED_EXPRESSION") lookaheadInvalidator.value
                            }

                            measurable.measure(constraints).run {
                                layout(width, height) { place(IntOffset.Zero) }
                            }
                        }
                    } else {
                        Modifier
                    }
                ),
        factory = { context ->
            // Inflate with the existing dialog xml layout.
            val view =
                LayoutInflater.from(context)
                    .inflate(R.layout.bluetooth_tile_details, /* root= */ null)

            view.addOnLayoutChangeListener(layoutListener)

            val listener =
                object : BluetoothDetailsContentManager.Listener {
                    override fun onContentUpdated() {
                        // Mark the content as ready upon the first update.
                        isContentReady = true
                        onContentReadyCallback()
                        detailsContentViewModel.contentManager.removeListener(this)
                    }
                }

            detailsContentViewModel.bindDetailsView(view, listener)

            view
        },
        onRelease = {
            it.removeOnLayoutChangeListener(layoutListener)
            detailsContentViewModel.unbindDetailsView()
        },
    )
}
