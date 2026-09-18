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

package com.android.systemui.dreams.ui.model

/**
 * UI model representing the state of an edge-swipe gesture used to switch dreams.
 *
 * This model captures the physical state of the gesture (position, direction) as well as the
 * logical state of the transition (whether it's being committed or released).
 *
 * @property isVisible Whether the edge-swipe affordance or transition UI should be visible.
 * @property isFromLeft Whether the swipe originated from the left edge of the screen. If false, it
 *   originated from the right edge.
 * @property isReleasing Whether the user has lifted their finger and the gesture is currently
 *   animating to a final state (either back to start or completing the switch).
 * @property isCommitted Whether the swipe gesture has passed the threshold to successfully trigger
 *   the dream switch.
 * @property startY The vertical position of the start of the gesture in pixels.
 * @property releaseVelocityX The horizontal velocity of the gesture in pixels per second.
 * @property targetDream The UI model of the dream that will be switched to if the gesture is
 *   successful.
 */
data class EdgeSwipeUiModel(
    val isVisible: Boolean = false,
    val isFromLeft: Boolean = true,
    val isReleasing: Boolean = false,
    val isCommitted: Boolean = false,
    val startY: Float = 0f,
    val releaseVelocityX: Float = 0f,
    val targetDream: DreamItemUiModel? = null,
)
