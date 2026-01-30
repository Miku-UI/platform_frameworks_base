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

package com.android.systemui.actioncorner.data.repository

import android.view.WindowManager.LayoutParams.TYPE_BASE_APPLICATION
import android.view.WindowMetrics
import com.android.systemui.actioncorner.data.model.ActionCornerRegion.BOTTOM_LEFT
import com.android.systemui.actioncorner.data.model.ActionCornerRegion.BOTTOM_RIGHT
import com.android.systemui.actioncorner.data.model.ActionCornerRegion.TOP_LEFT
import com.android.systemui.actioncorner.data.model.ActionCornerRegion.TOP_RIGHT
import com.android.systemui.actioncorner.data.model.ActionCornerState
import com.android.systemui.actioncorner.data.model.ActionCornerState.ActiveActionCorner
import com.android.systemui.actioncorner.data.model.ActionCornerState.InactiveActionCorner
import com.android.systemui.cursorposition.data.model.CursorPosition
import com.android.systemui.cursorposition.data.repository.MultiDisplayCursorPositionRepository
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.display.data.repository.DisplayWindowPropertiesRepository
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Repository for action corner states. See [ActionCornerState] for details. */
interface ActionCornerRepository {
    val actionCornerState: StateFlow<ActionCornerState>
}

/**
 * Implementation of [ActionCornerRepository] to detect if any action corner event is triggered. It
 * subscribes to [MultiDisplayCursorPositionRepository] to get the cursor position from all displays
 * and uses the window metrics from [DisplayWindowPropertiesRepository] to determine if the cursor
 * is in any action corner.
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
class ActionCornerRepositoryImpl
@Inject
constructor(
    cursorRepository: MultiDisplayCursorPositionRepository,
    private val displayWindowPropertiesRepository: DisplayWindowPropertiesRepository,
    @Background private val backgroundScope: CoroutineScope,
) : ActionCornerRepository {

    override val actionCornerState: StateFlow<ActionCornerState> =
        cursorRepository.cursorPositions
            .map(::mapToActionCornerState)
            .distinctUntilChanged()
            .debounce { state ->
                if (state is ActiveActionCorner) {
                    DEBOUNCE_DELAY
                } else {
                    0.milliseconds
                }
            }
            .stateIn(backgroundScope, SharingStarted.WhileSubscribed(), InactiveActionCorner)

    private fun mapToActionCornerState(cursorPos: CursorPosition?): ActionCornerState {
        if (cursorPos == null) {
            return InactiveActionCorner
        }

        val windowProperties =
            displayWindowPropertiesRepository.get(cursorPos.displayId, TYPE_BASE_APPLICATION)
                ?: return InactiveActionCorner
        val windowMetrics = windowProperties.windowManager.currentWindowMetrics
        val cornerSizePx = ACTION_CORNER_DP * windowMetrics.density

        // Need to emit INACTIVE_ACTION_CORNER because when users enter and exit the same
        // action corner, we need to emit 2 action corners instead of one. If we don't emit
        // NONE_ACTION_CORNER_MODEL, the 2nd value would be filtered out in distinctUntilChanged()
        return when {
            isTopLeftCorner(cursorPos, cornerSizePx) ->
                ActiveActionCorner(TOP_LEFT, cursorPos.displayId)
            isTopRightCorner(cursorPos, cornerSizePx, windowMetrics) ->
                ActiveActionCorner(TOP_RIGHT, cursorPos.displayId)
            isBottomLeftCorner(cursorPos, cornerSizePx, windowMetrics) ->
                ActiveActionCorner(BOTTOM_LEFT, cursorPos.displayId)
            isBottomRightCorner(cursorPos, cornerSizePx, windowMetrics) ->
                ActiveActionCorner(BOTTOM_RIGHT, cursorPos.displayId)
            else -> InactiveActionCorner
        }
    }

    private fun isTopLeftCorner(cursorPos: CursorPosition, cornerSize: Float): Boolean {
        return cursorPos.x <= cornerSize && cursorPos.y <= cornerSize
    }

    private fun isTopRightCorner(
        cursorPos: CursorPosition,
        cornerSize: Float,
        metrics: WindowMetrics,
    ): Boolean {
        return cursorPos.x >= (metrics.bounds.width() - cornerSize) && cursorPos.y <= cornerSize
    }

    private fun isBottomLeftCorner(
        cursorPos: CursorPosition,
        cornerSize: Float,
        metrics: WindowMetrics,
    ): Boolean {
        return cursorPos.x <= cornerSize && cursorPos.y >= (metrics.bounds.height() - cornerSize)
    }

    private fun isBottomRightCorner(
        cursorPos: CursorPosition,
        cornerSize: Float,
        metrics: WindowMetrics,
    ): Boolean {
        return cursorPos.x >= (metrics.bounds.width() - cornerSize) &&
            cursorPos.y >= (metrics.bounds.height() - cornerSize)
    }

    companion object {
        private const val ACTION_CORNER_DP = 8f
        private val DEBOUNCE_DELAY = 50.milliseconds
    }
}
