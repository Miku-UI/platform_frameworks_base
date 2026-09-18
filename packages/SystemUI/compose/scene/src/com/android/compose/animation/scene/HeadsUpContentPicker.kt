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

package com.android.compose.animation.scene

import com.android.compose.animation.scene.content.state.TransitionState

/**
 * An [ElementContentPicker] that generally picks the content with the lowest z-order, except for
 * specific transitions where we need the content with higher z-order to avoid jump cuts.
 */
class HeadsUpContentPicker(
    private val sceneWithShadeCollapsed: ContentKey,
    private val sceneWithShadeExpanded: ContentKey,
) : ElementContentPicker {
    override fun contentDuringTransition(
        element: ElementKey,
        transition: TransitionState.Transition,
        fromContentZIndex: Long,
        toContentZIndex: Long,
    ): ContentKey {
        val from = transition.fromContent
        val to = transition.toContent

        return when {
            // Pick shade (higher z than lockscreen/AOD) so that HUN gets
            // expanded target bounds at start of transition
            // to prevent jump cut at end of transition
            from == sceneWithShadeCollapsed
                && to == sceneWithShadeExpanded -> to

            else -> if (fromContentZIndex < toContentZIndex) from else to
        }
    }
}
