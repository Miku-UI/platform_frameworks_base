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

package com.android.systemui.display.data.repository

import android.view.IWindowManager
import android.window.DesktopExperienceFlags.ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT
import com.android.app.displaylib.DisplaysWithDecorationsRepository
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.statusbar.CommandQueue
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn

@SysUISingleton
class DisplaysWithDecorationsRepositoryImpl
@Inject
constructor(
    private val commandQueue: CommandQueue,
    private val windowManager: IWindowManager,
    @Background bgApplicationScope: CoroutineScope,
    displayRepository: com.android.app.displaylib.DisplayRepository,
) : DisplaysWithDecorationsRepository {

    private val decorationEvents: Flow<Event> = callbackFlow {
        val callback =
            object : CommandQueue.Callbacks {
                override fun onDisplayAddSystemDecorations(displayId: Int) {
                    if (ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT.isTrue()) {
                        trySend(Event.Add(displayId))
                    } else {
                        if (windowManager.shouldShowSystemDecors(displayId)) {
                            trySend(Event.Add(displayId))
                        }
                    }
                }

                override fun onDisplayRemoveSystemDecorations(displayId: Int) {
                    trySend(Event.Remove(displayId))
                }
            }
        commandQueue.addCallback(callback)
        awaitClose { commandQueue.removeCallback(callback) }
    }

    private val initialDisplayIdsWithDecorations: Set<Int> =
        displayRepository.displayIds.value
            .filter { windowManager.shouldShowSystemDecors(it) }
            .toSet()

    /**
     * A [StateFlow] that maintains a set of display IDs that should have system decorations.
     *
     * Updates to the set are triggered by:
     * - Adding displays via [CommandQueue.Callbacks.onDisplayAddSystemDecorations].
     * - Removing displays via [CommandQueue.Callbacks.onDisplayRemoveSystemDecorations].
     * - Removing displays via [displayRemovalEvent] emissions.
     *
     * The set is initialized with displays that qualify for system decorations based on
     * [WindowManager.shouldShowSystemDecors].
     */
    override val displayIdsWithSystemDecorations: StateFlow<Set<Int>> =
        merge(decorationEvents, displayRepository.displayRemovalEvent.map { Event.Remove(it) })
            .scan(initialDisplayIdsWithDecorations) { displayIds: Set<Int>, event: Event ->
                when (event) {
                    is Event.Add -> displayIds + event.displayId
                    is Event.Remove -> displayIds - event.displayId
                }
            }
            .distinctUntilChanged()
            .stateIn(
                scope = bgApplicationScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = initialDisplayIdsWithDecorations,
            )

    private sealed class Event(val displayId: Int) {
        class Add(displayId: Int) : Event(displayId)

        class Remove(displayId: Int) : Event(displayId)
    }
}
