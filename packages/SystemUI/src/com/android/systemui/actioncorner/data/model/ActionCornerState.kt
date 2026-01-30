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

package com.android.systemui.actioncorner.data.model

/**
 * Action corners are regions of a display that trigger specific actions when the cursor moves into
 * that region. [ActionCornerState] represents the current state of action corner.
 */
sealed class ActionCornerState {
    /**
     * Represents the state where a specific display has an active action corner.
     *
     * @property region The region of the active action corner.
     * @property displayId The ID of the display that has this active action corner.
     */
    data class ActiveActionCorner(val region: ActionCornerRegion, val displayId: Int) :
        ActionCornerState()

    /** Represents the state where no display currently has an active action corner. */
    data object InactiveActionCorner : ActionCornerState()
}

/**
 * Indicates the region for the action corner. For [NONE], it means there is no action corner in any
 * display.
 */
enum class ActionCornerRegion {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
}

/**
 * Indicates the action type configured for the action corner. For [NONE], it means there is no
 * action configured for that corner.
 */
enum class ActionType {
    NONE,
    HOME,
    OVERVIEW,
    NOTIFICATIONS,
    QUICK_SETTINGS,
    LOCKSCREEN,
}
