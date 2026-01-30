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

package com.android.systemui.display.domain.interactor

import android.content.Context
import android.content.res.Configuration
import com.android.systemui.common.coroutine.ChannelExt.trySendWithFailureLogging
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware
import com.android.systemui.display.data.repository.DisplayRepository
import com.android.systemui.display.data.repository.DisplayStateRepository
import com.android.systemui.display.shared.model.DisplayRotation
import com.android.systemui.unfold.compat.ScreenSizeFoldProvider
import com.android.systemui.unfold.updates.FoldProvider
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Aggregates display state information. */
interface DisplayStateInteractor {
    /** Whether the default display is currently off. */
    val isDefaultDisplayOff: StateFlow<Boolean>

    /** Whether the device is currently in rear display mode. */
    val isInRearDisplayMode: StateFlow<Boolean>

    /** Whether the device is currently folded. */
    val isFolded: Flow<Boolean>

    /** Current rotation of the display */
    val currentRotation: StateFlow<DisplayRotation>

    /** Display change event indicating a change to the given displayId has occurred. */
    val displayChanges: Flow<Int>

    /**
     * If true, the direction rotation is applied to get to an application's requested orientation
     * is reversed. Normally, the model is that landscape is clockwise from portrait; thus on a
     * portrait device an app requesting landscape will cause a clockwise rotation, and on a
     * landscape device an app requesting portrait will cause a counter-clockwise rotation. Setting
     * true here reverses that logic. See go/natural-orientation for context.
     */
    val isReverseDefaultRotation: Boolean

    /** Called on configuration changes, used to keep the display state in sync */
    fun onConfigurationChanged(newConfig: Configuration)

    /**
     * Provides whether the current display is a large screen (i.e. all edges are >= 600dp). This is
     * agnostic of display rotation.
     */
    val isLargeScreen: StateFlow<Boolean>

    /**
     * Provides whether the display's current horizontal width is large (>= 600dp).
     *
     * Note: Unlike [isLargeScreen], which checks whether either one of the screen's width or height
     * is large, this flow's state is sensitive to the current display's rotation.
     */
    val isWideScreen: StateFlow<Boolean>
}

/** Encapsulates logic for interacting with the display state. */
class DisplayStateInteractorImpl
@Inject
constructor(
    @DisplayAware displayScope: CoroutineScope,
    @DisplayAware context: Context,
    @Main mainExecutor: Executor,
    displayStateRepository: DisplayStateRepository,
    displayRepository: DisplayRepository,
) : DisplayStateInteractor {
    private var screenSizeFoldProvider: ScreenSizeFoldProvider = ScreenSizeFoldProvider(context)

    override val displayChanges = displayRepository.displayChangeEvent

    override val isFolded: Flow<Boolean> =
        conflatedCallbackFlow {
                val sendFoldStateUpdate = { state: Boolean ->
                    trySendWithFailureLogging(
                        state,
                        TAG,
                        "Error sending fold state update to $state",
                    )
                }

                val callback = FoldProvider.FoldCallback(sendFoldStateUpdate)

                sendFoldStateUpdate(false)
                screenSizeFoldProvider.registerCallback(callback, mainExecutor)
                awaitClose { screenSizeFoldProvider.unregisterCallback(callback) }
            }
            .stateIn(displayScope, started = SharingStarted.Eagerly, initialValue = false)

    override val isInRearDisplayMode: StateFlow<Boolean> =
        displayStateRepository.isInRearDisplayMode

    override val currentRotation: StateFlow<DisplayRotation> =
        displayStateRepository.currentRotation

    override val isReverseDefaultRotation: Boolean = displayStateRepository.isReverseDefaultRotation

    override val isDefaultDisplayOff = displayRepository.defaultDisplayOff

    override val isLargeScreen: StateFlow<Boolean> = displayStateRepository.isLargeScreen

    override val isWideScreen: StateFlow<Boolean> = displayStateRepository.isWideScreen

    fun setScreenSizeFoldProvider(foldProvider: ScreenSizeFoldProvider) {
        screenSizeFoldProvider = foldProvider
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        screenSizeFoldProvider.onConfigurationChange(newConfig)
    }

    companion object {
        private const val TAG = "DisplayStateInteractor"
    }
}
