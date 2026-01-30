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

package com.android.server.wm;

import com.android.internal.annotations.VisibleForTesting;

import java.util.List;

/**
 * The policy that determines the rendering priority of a window. This policy primarily considers
 * user interaction history to assign priority, influencing the value of
 * {@link WindowState#mSystemContentPriority}, which gets sent to SurfaceFlinger to prioritize
 * rendering tasks.
 */
class RenderingPrioritizationPolicy {

    /**
     * A list of weights used to assign content priority to windows based on their
     * position in the interaction history. The first window in the history (most recent)
     * gets the first weight, the second gets the second weight, and so on.
     * Higher values typically indicate higher priority.
     *
     * <p> The last weight must be 0. So that the oldest window in the history get reset the
     * priority to 0. Effectively, weights {4, 2, 0} means giving  priority 4 and 2 to the last two
     * interacted windows.
     */
    @VisibleForTesting
    static final List<Integer> INTERACTION_WEIGHTS = List.of(4, 2, 0);

    /**
     * Updates the content priority of windows based on their recent interaction history.
     *
     * @param interactionHistory A list of {@link WindowState} objects representing
     *                           the windows in order of their interaction, from most recent to
     *                           least recent.
     */
    static void updatePriorityByInteraction(List<WindowState> interactionHistory) {
        if (interactionHistory.isEmpty()) {
            return;
        }
        for (int i = 0; i < Math.min(interactionHistory.size(), INTERACTION_WEIGHTS.size());
                i++) {
            WindowState window = interactionHistory.get(i);
            if (window == null) {
                continue;
            }
            window.mInteractionPriorityScore = INTERACTION_WEIGHTS.get(i);
        }
    }
}
