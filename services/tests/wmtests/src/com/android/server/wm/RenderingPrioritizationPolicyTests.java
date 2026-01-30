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

import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION;

import static org.junit.Assert.assertEquals;

import android.platform.test.annotations.Presubmit;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@Presubmit
@RunWith(WindowTestRunner.class)
public class RenderingPrioritizationPolicyTests extends WindowTestsBase {

    @Test
    public void testUpdatePriorityByInteraction_normalCase() {
        if (RenderingPrioritizationPolicy.INTERACTION_WEIGHTS.isEmpty()) {
            return;
        }

        List<WindowState> interactionHistory = new ArrayList<>();
        for (int i = 0; i < RenderingPrioritizationPolicy.INTERACTION_WEIGHTS.size(); i++) {
            interactionHistory.add(newWindowBuilder("window " + i, TYPE_APPLICATION).build());
        }

        RenderingPrioritizationPolicy.updatePriorityByInteraction(interactionHistory);

        for (int i = 0; i < RenderingPrioritizationPolicy.INTERACTION_WEIGHTS.size(); i++) {
            assertEquals(RenderingPrioritizationPolicy.INTERACTION_WEIGHTS.get(i).intValue(),
                    interactionHistory.get(i).mInteractionPriorityScore);
        }
    }

    @Test
    public void testUpdatePriorityByInteraction_historyLessThanWeights() {
        if (RenderingPrioritizationPolicy.INTERACTION_WEIGHTS.size() < 2) {
            return;
        }
        WindowState window1 = newWindowBuilder("window 1", TYPE_APPLICATION).build();

        List<WindowState> interactionHistory = List.of(window1);
        RenderingPrioritizationPolicy.updatePriorityByInteraction(interactionHistory);

        assertEquals(RenderingPrioritizationPolicy.INTERACTION_WEIGHTS.get(0).intValue(),
                window1.mInteractionPriorityScore);
    }
}
