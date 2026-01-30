/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.scene.ui.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.compose.animation.scene.Back
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.UserAction
import com.android.compose.animation.scene.UserActionResult
import com.android.systemui.lifecycle.Activatable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Defines interface for classes that can describe an "overlay".
 *
 * In the scene framework, there can be multiple overlays in a single scene "container". The
 * container takes care of rendering any current overlays and allowing overlays to be shown, hidden,
 * or replaced based on a user action.
 */
interface Overlay : Activatable, ActionableContent {
    /** Uniquely-identifying key for this overlay. The key must be unique within its container. */
    val key: OverlayKey

    /**
     * The user actions supported by this overlay.
     *
     * @see [ActionableContent.userActions]
     */
    override val userActions: Flow<Map<UserAction, UserActionResult>>
        get() = flowOf(mapOf(Back to UserActionResult.HideOverlay(key)))

    /**
     * Whether this overlay should be invisibly composed even when it's not a current scene and even
     * when it's not participating in any current transition.
     *
     * Please heavily weigh the pros and cons of doing this. On the one hand, composing content
     * before it needs to be visible will "pre-warm" upstream coroutines and flows at close to
     * System UI start time instead of doing all of it just in time (usually in the moment that the
     * content transition begins which can introduce significant jank, especially if that transition
     * is bound to a user-driven drag gesture). On the other hand, content that is always composed
     * will continue to compose its [Content] function which means that all of its state
     * observations and side effects will continue running before it ever shows and after it's no
     * longer showing. Once b/433309418 is closed, there will be good ways to more granularly decide
     * which pieces of work should run ahead of time and which should run just in time.
     */
    val alwaysCompose: Boolean

    @Composable fun ContentScope.Content(modifier: Modifier)
}
