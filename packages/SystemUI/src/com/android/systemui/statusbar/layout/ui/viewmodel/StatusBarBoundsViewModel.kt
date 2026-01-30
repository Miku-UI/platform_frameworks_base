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

package com.android.systemui.statusbar.layout.ui.viewmodel

import android.graphics.Rect
import android.view.View
import androidx.compose.runtime.getValue
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.statusbar.policy.Clock
import com.android.systemui.util.boundsOnScreen
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

/**
 * View model for on-screen bounds of elements related to the status bar.
 *
 * Recommended Architecture variant of [StatusBarBoundsProvider].
 */
class StatusBarBoundsViewModel
@AssistedInject
constructor(
    @Assisted private val startSideContainerView: View,
    @Assisted private val clockView: Clock,
    @Background backgroundScope: CoroutineScope,
) : ExclusiveActivatable() {
    private val hydrator = Hydrator(traceName = "StatusBarBoundsViewModel.hydrator")

    private val _startSideContainerBounds: Flow<Rect> =
        conflatedCallbackFlow {
                val layoutListener =
                    View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                        trySend(startSideContainerView.boundsOnScreen)
                    }
                startSideContainerView.addOnLayoutChangeListener(layoutListener)
                awaitClose { startSideContainerView.removeOnLayoutChangeListener(layoutListener) }
            }
            .stateIn(backgroundScope, SharingStarted.WhileSubscribed(), initialValue = Rect())

    /**
     * The on-screen bounds of the start side container of the status bar, which always fills the
     * available start-side space. This is a hydrated value.
     */
    val startSideContainerBounds: Rect by
        hydrator.hydratedStateOf(
            traceName = "StatusBar.startSideContainerBounds",
            initialValue = Rect(),
            source = _startSideContainerBounds,
        )

    private val _dateBounds = MutableStateFlow(Rect())

    /** The on-screen bounds of the status bar date. This is a hydrated value. */
    // TODO(b/390204943): Re-implement this in Compose once the Clock is a Composable.
    val dateBounds: Rect by
        hydrator.hydratedStateOf(
            traceName = "StatusBar.dateBounds",
            initialValue = Rect(),
            source = _dateBounds,
        )

    private val _clockBounds: Flow<Rect> =
        conflatedCallbackFlow {
                val layoutListener =
                    View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                        trySend(clockView.boundsOnScreen)
                    }
                clockView.addOnLayoutChangeListener(layoutListener)
                awaitClose { clockView.removeOnLayoutChangeListener(layoutListener) }
            }
            .stateIn(backgroundScope, SharingStarted.WhileSubscribed(), initialValue = Rect())

    /** The on-screen bounds of the status bar clock. This is a hydrated value. */
    // TODO(b/390204943): Re-implement this in Compose once the Clock is a Composable.
    val clockBounds: Rect by
        hydrator.hydratedStateOf(
            traceName = "StatusBar.clockBounds",
            initialValue = Rect(),
            source = _clockBounds,
        )

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    fun updateDateBounds(bounds: Rect) {
        _dateBounds.value = bounds
    }

    @AssistedFactory
    interface Factory {
        fun create(startSideContainerView: View, clockView: Clock): StatusBarBoundsViewModel
    }
}
