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

package com.android.wm.shell.bubbles.transitions

import android.os.IBinder
import android.util.Log
import android.view.SurfaceControl
import android.window.TransitionInfo
import com.android.wm.shell.transition.AnimationPlan
import com.android.wm.shell.transition.ITransitionPlanner
import com.android.wm.shell.transition.Transitions

/** Planner for all the transitions that should end up with the task put into the bubble mode */
class BubbleTransitionsPlanner(
    transitions: Transitions,
    private val bubbleTransitions: BubbleTransitions,
) : ITransitionPlanner {

    override fun plan(
        plan: AnimationPlan,
        fullInfo: TransitionInfo,
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
    ) {
        detachTasksLeavingBubbleMode(fullInfo, plan)
        planTransitionAnimations(plan, fullInfo, transition, info, startTransaction)
    }

    private fun detachTasksLeavingBubbleMode(fullInfo: TransitionInfo, plan: AnimationPlan) {
        // TODO: b/483106468
    }

    private fun planTransitionAnimations(
        plan: AnimationPlan,
        fullInfo: TransitionInfo,
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
    ) {
        logTransitionChanges("fullInfo", fullInfo)
        logTransitionChanges("info", info)
        val planner =
            bubbleTransitions.getRunningEnterTransition(transition) as? ITransitionPlanner ?: return
        planner.plan(plan, fullInfo, transition, info, startTransaction)
    }

    private fun logTransitionChanges(transitionName: String = "", info: TransitionInfo) {
        Log.v(TAG, "TransitionInfo $transitionName changes for ${info.debugId}:")
        info.changes.forEachIndexed { index, change -> Log.v(TAG, "  #$index: $change") }
    }

    override fun getDebugName(): String = "BubbleTransitionsPlanner"

    companion object {
        const val TAG = "BubbleTransitionsPlanner"
    }
}
