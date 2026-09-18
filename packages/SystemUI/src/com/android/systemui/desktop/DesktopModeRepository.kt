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

package com.android.systemui.desktop

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.UiBackground
import com.android.systemui.kairos.awaitClose
import com.android.wm.shell.desktopmode.api.DesktopMode
import com.android.wm.shell.desktopmode.data.DesktopRepository.DeskChangeListener
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@SysUISingleton
class DesktopModeRepository
@Inject
constructor(
    @UiBackground uiBackgroundExecutor: Executor,
    @Background backgroundScope: CoroutineScope,
    private val desktopMode: Optional<DesktopMode>,
) {
    private val displayDesktopState = ConcurrentHashMap<Int, Boolean>()
    private val desktopModeFlow: StateFlow<ConcurrentHashMap<Int, Boolean>> =
        desktopMode
            .map { dm ->
                callbackFlow {
                    val deskChangeListener =
                        object : DeskChangeListener {
                            override fun onDeskAdded(displayId: Int, deskId: Int) {
                                if (maybeUpdateDisplayState(displayId)) {
                                    trySend(displayDesktopState)
                                }
                            }

                            override fun onDeskRemoved(displayId: Int, deskId: Int) {
                                if (maybeUpdateDisplayState(displayId)) {
                                    trySend(displayDesktopState)
                                }
                            }

                            override fun onActiveDeskChanged(
                                displayId: Int,
                                newActiveDeskId: Int,
                                oldActiveDeskId: Int,
                            ) {
                                if (maybeUpdateDisplayState(displayId)) {
                                    trySend(displayDesktopState)
                                }
                            }

                            override fun onCanCreateDesksChanged(canCreateDesks: Boolean) {}
                            override fun onTaskAppearingInDesk(taskId: Int, displayId: Int, deskId: Int) {}
                        }
                    dm.addDeskChangeListener(deskChangeListener, uiBackgroundExecutor)
                    awaitClose { dm.removeDeskChangeListener(deskChangeListener) }
                }.stateIn(
                scope = backgroundScope,
                started = SharingStarted.Eagerly,
                initialValue = displayDesktopState,
            )
            }
            .orElse(MutableStateFlow(ConcurrentHashMap<Int, Boolean>()))

    /**
        Tries to fetch the desktop mode state for the given display id.

        @param displayId The id to use for the check
        @return True or false is the mode can be determined or null if unable to determine the state.
     */
    fun isDisplayInDesktopMode(displayId: Int): Boolean? =
        desktopModeFlow.value.getOrElse(displayId) {
            desktopMode
                .map {
                    val inDesktopMode = it.isDisplayInDesktopMode(displayId)
                    displayDesktopState.put(displayId, inDesktopMode)
                    inDesktopMode
                }
                .orElse(null)
        }

    private fun maybeUpdateDisplayState(displayId: Int): Boolean =
        isDisplayInDesktopMode(displayId) != null
}
