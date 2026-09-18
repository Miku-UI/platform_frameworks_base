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

package com.android.systemui.screencapture.domain.interactor

import android.util.Log
import android.view.Display
import com.android.systemui.cursorposition.data.repository.MultiDisplayCursorPositionRepository
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.display.data.repository.DisplayRepository
import com.android.systemui.display.data.repository.FocusedDisplayRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

private const val TAG = "ScreenCaptureTargetDisplayInteractor"

/** Interactor for screen capture display. */
@SysUISingleton
class ScreenCaptureTargetDisplayInteractor
@Inject
constructor(
    private val cursorPositionRepository: MultiDisplayCursorPositionRepository,
    private val focusedDisplayRepository: FocusedDisplayRepository,
    private val displayRepository: DisplayRepository,
) {
    /** The target display where the screen capture UI should be shown. */
    val targetDisplay: Flow<Display> =
        combine(
                cursorPositionRepository.cursorPositions
                    .map { it?.displayId }
                    .distinctUntilChanged(),
                focusedDisplayRepository.focusedDisplayId,
                displayRepository.displays,
            ) { cursorDisplayId, focusedDisplayId, displays ->
                val id = cursorDisplayId ?: focusedDisplayId
                displays.firstOrNull { it.displayId == id }
                    ?: run {
                        Log.w(TAG, "Couldn't find display for id=$id. Falling back to first.")
                        displays.firstOrNull()
                    }
            }
            .filterNotNull()
            .distinctUntilChanged()
}
