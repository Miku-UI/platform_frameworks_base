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

package com.android.systemui.common.domain.interactor

import android.util.Log
import com.android.app.displaylib.PerDisplayRepository
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.display.data.repository.DisplayRepository
import com.android.systemui.model.StateChange
import com.android.systemui.model.SysUiState
import javax.inject.Inject

/** Handles [SysUiState] changes between displays. */
@SysUISingleton
class SysUIStateDisplaysInteractor
@Inject
constructor(
    private val sysUIStateRepository: PerDisplayRepository<SysUiState>,
    private val displayRepository: DisplayRepository,
) {

    /**
     * Sets the flags based on [stateChanges] on the given [targetDisplayId], while making sure that
     * those flags are cleared (i.e., set to false) on all other displays.
     *
     * In other words, this function makes sure that any change introduced by [stateChanges] in the
     * [SysUiState] of [targetDisplayId] is not present on any other display's [SysUiState] (i.e.
     * all of them will be set to false).
     */
    fun setFlagsExclusivelyToDisplay(targetDisplayId: Int, stateChanges: StateChange) {
        if (SysUiState.DEBUG) {
            Log.d(TAG, "Setting flags $stateChanges only for display $targetDisplayId")
        }
        setFlagsExclusivelyToDisplays(setOf(targetDisplayId), stateChanges)
    }

    /**
     * Sets the flags based on [stateChanges] on the given [displays] while making sure that those
     * flags are cleared (i.e., set to false) on all other displays.
     *
     * In other words, this function makes sure that any change introduced by [stateChanges] in the
     * [SysUiState] is only present on displays represented by [displays] and is not present on any
     * other display's [SysUiState].
     */
    fun setFlagsExclusivelyToDisplays(displays: Set<Int>, stateChanges: StateChange) {
        if (SysUiState.DEBUG) {
            Log.d(TAG, "Setting flags $stateChanges exclusively to displays $displays")
        }
        displayRepository.displays.value
            .mapNotNull { sysUIStateRepository[it.displayId] }
            .apply {
                // Let's first modify all states, without committing changes ...
                forEach { displaySysUIState ->
                    if (displaySysUIState.displayId in displays) {
                        stateChanges.applyTo(displaySysUIState)
                    } else {
                        stateChanges.clearFrom(displaySysUIState)
                    }
                }
                // ... And commit changes at the end
                forEach { sysuiState -> sysuiState.commitUpdate() }
            }
    }

    /**
     * Applies a [StateChange] to the [SysUiState] for the given [targetDisplayId].
     *
     * Unlike [setFlagsExclusivelyToDisplay], this method does not update the state of other
     * displays. In other words, it only changes the state of [targetDisplayId], leaving all other
     * states left untouched.
     */
    fun setFlags(targetDisplayId: Int, stateChanges: StateChange) {
        sysUIStateRepository[targetDisplayId]?.apply {
            stateChanges.applyTo(this)
            commitUpdate()
        }
    }

    private companion object {
        const val TAG = "SysUIStateInteractor"
    }
}
