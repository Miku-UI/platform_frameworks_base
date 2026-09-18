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

package com.android.systemui.scene.ui.view

import android.os.Handler
import com.android.compose.animation.scene.ContentKey
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.internal.util.LatencyTracker
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import javax.inject.Inject

class SceneTransitionLatencyMonitor
@Inject
constructor(private val latencyTracker: LatencyTracker, @Main private val handler: Handler) {

    /** Notifies that a transition is at its start and latency tracking should start if enabled. */
    fun onTransitionStart(transition: TransitionState.Transition) {
        if (transition.toContent.isShadeContentKey()) {
            latencyTracker.onActionStart(LatencyTracker.ACTION_EXPAND_PANEL)
            // We only track the duration of the first frame.
            handler.postAtFrontOfQueue {
                latencyTracker.onActionEnd(LatencyTracker.ACTION_EXPAND_PANEL)
            }
        }
    }

    private fun ContentKey.isShadeContentKey(): Boolean {
        return this == Scenes.Shade ||
            this == Scenes.QuickSettings ||
            this == Overlays.NotificationsShade ||
            this == Overlays.QuickSettingsShade
    }
}
