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

package com.android.systemui.statusbar.systemstatusicons.data.repository

import com.android.internal.statusbar.StatusBarIcon
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.statusbar.CommandQueue
import com.android.systemui.statusbar.systemstatusicons.shared.model.ExternalIconModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn

/**
 * A repository storing status bar icons received from outside of the SystemUI process (i.e., they
 * are sent to us over the [IStatusBarService#setIcon] binder call.)
 */
@SysUISingleton
class ExternalSystemStatusIconRepository
@Inject
constructor(commandQueue: CommandQueue, @Background backgroundScope: CoroutineScope) {
    /**
     * A list of icons received from outside of the SystemUI process that should be visible.
     *
     * Sorted in order of recency, where the earliest-received icon is *first*.
     */
    val icons: StateFlow<List<ExternalIconModel>> =
        // Don't use `conflatedCallbackFlow` because then we may drop updates that come in rapidly.
        callbackFlow {
                val callback =
                    object : CommandQueue.Callbacks {
                        override fun setIcon(slot: String?, icon: StatusBarIcon?) {
                            // TODO(b/475251350): Log callback values in LogBuffer.
                            if (slot == null) {
                                return
                            }

                            if (icon != null) {
                                trySend(IconEvent.AddIcon(slot, icon))
                            } else {
                                trySend(IconEvent.RemoveIcon(slot))
                            }
                        }

                        override fun removeIcon(slot: String?) {
                            if (slot == null) {
                                return
                            }
                            trySend(IconEvent.RemoveIcon(slot))
                        }
                    }
                commandQueue.addCallback(callback)
                awaitClose { commandQueue.removeCallback(callback) }
            }
            .scan(initial = emptyList()) { currentIcons: List<ExternalIconModel>, iconChangeEvent ->
                when (iconChangeEvent) {
                    is IconEvent.AddIcon -> {
                        val newIcon =
                            ExternalIconModel(iconChangeEvent.slotName, iconChangeEvent.icon)
                        val indexOfIcon =
                            currentIcons.indexOfFirst { it.slotName == newIcon.slotName }
                        if (indexOfIcon != -1) {
                            // Update the icon if we already have a slot for it
                            currentIcons.toMutableList().apply { this[indexOfIcon] = newIcon }
                        } else {
                            // Add new icon at the end so that the icons received earlier are first.
                            currentIcons + newIcon
                        }
                    }
                    is IconEvent.RemoveIcon -> {
                        currentIcons.filterNot { it.slotName == iconChangeEvent.slotName }
                    }
                }
            }
            .stateIn(backgroundScope, SharingStarted.Eagerly, emptyList())

    private sealed interface IconEvent {
        data class AddIcon(val slotName: String, val icon: StatusBarIcon) : IconEvent

        data class RemoveIcon(val slotName: String) : IconEvent
    }
}
