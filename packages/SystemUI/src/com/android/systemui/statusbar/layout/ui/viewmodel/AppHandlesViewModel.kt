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
import android.graphics.Region
import androidx.compose.runtime.getValue
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.statusbar.layout.StatusBarAppHandleTracking
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import com.android.wm.shell.windowdecor.viewholder.AppHandlePositionCallback
import com.android.wm.shell.windowdecor.viewholder.AppHandles
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.Optional
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** View model for on-screen bounds of app handles overlapping with the status bar. */
class AppHandlesViewModel
@AssistedInject
constructor(
    @Assisted thisDisplayId: Int,
    appHandles: Optional<AppHandles>,
    @Background backgroundScope: CoroutineScope,
    @Main sysuiMainExecutor: Executor,
) : ExclusiveActivatable() {
    private val hydrator = Hydrator(traceName = "AppHandlesViewModel.hydrator")

    private val _appHandleBounds: Flow<List<Rect>> =
        if (StatusBarAppHandleTracking.isEnabled && appHandles.isPresent) {
                conflatedCallbackFlow {
                    val listener = AppHandlePositionCallback { handles ->
                        trySend(
                            handles.values.filter { it.displayId == thisDisplayId }.map { it.rect }
                        )
                    }
                    appHandles.get().addListener(sysuiMainExecutor, listener)
                    awaitClose { appHandles.get().removeListener(listener) }
                }
            } else {
                flowOf(emptyList())
            }
            .stateIn(backgroundScope, SharingStarted.WhileSubscribed(), emptyList())

    /**
     * The on-screen bounds where app handles are showing. Used so that we can ensure clickable
     * status bar content doesn't overlap with them. This is a hydrated value.
     */
    val appHandleBounds: List<Rect> by
        hydrator.hydratedStateOf(
            traceName = "StatusBar.appHandleBounds",
            initialValue = emptyList(),
            source = _appHandleBounds,
        )

    private val _touchableExclusionRegion: Flow<Region> =
        _appHandleBounds.map { appHandles ->
            val exclusionRegion = Region.obtain()
            appHandles.forEach { exclusionRegion.op(it, Region.Op.UNION) }
            exclusionRegion
        }

    /**
     * The on-screen bounds that should be excluded from the status bar's touchable region due to
     * its overlap with app handles.
     */
    val touchableExclusionRegion: Region by
        hydrator.hydratedStateOf(
            traceName = "StatusBar.touchableExclusionRegion",
            initialValue = Region.obtain(),
            source = _touchableExclusionRegion,
        )

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    @AssistedFactory
    interface Factory {
        fun create(displayId: Int): AppHandlesViewModel
    }
}
