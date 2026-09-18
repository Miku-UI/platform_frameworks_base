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

package com.android.systemui.qs.tiles.dialog

import android.view.LayoutInflater
import android.view.View
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import com.android.compose.modifiers.skipToLookaheadSize
import com.android.systemui.res.R

/**
 * Displays the content for the Internet details dialog.
 *
 * @param viewModel The ViewModel managing the state and view binding for this content.
 * @param onContentReady Callback invoked when the content is initially updated and ready to be
 *   displayed.
 */
@Composable
fun InternetDetailsContent(viewModel: InternetDetailsViewModel, onContentReady: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
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
                                // updates,
                                // forcing Compose to re-run the lookahead measurement pass.
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
            // Inflate with the existing dialog xml layout and bind it with the manager
            val view =
                LayoutInflater.from(context).inflate(R.layout.internet_connectivity_details, null)

            view.addOnLayoutChangeListener(layoutListener)

            val contentListener =
                object : InternetDetailsContentManager.Listener {
                    override fun onContentDataUpdated() {
                        // Mark the content as ready upon the first content update.
                        isContentReady = true
                        onContentReadyCallback()
                        viewModel.internetDetailsContentManager.removeListener(this)
                    }
                }

            viewModel.internetDetailsContentManager.addListener(contentListener)
            viewModel.internetDetailsContentManager.bind(
                contentView = view,
                dialog = null,
                coroutineScope = coroutineScope,
            )

            view
        },
        onRelease = {
            it.removeOnLayoutChangeListener(layoutListener)
            viewModel.internetDetailsContentManager.unBind()
        },
    )
}
