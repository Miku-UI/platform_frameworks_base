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

package com.android.wm.shell.compatui.components

import android.annotation.SuppressLint
import android.graphics.Point
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.window.TaskConstants
import com.android.wm.shell.R
import com.android.wm.shell.compatui.api.CompatUILayout
import com.android.wm.shell.compatui.api.CompatUILifecyclePredicates
import com.android.wm.shell.compatui.api.CompatUISpec
import com.android.wm.shell.compatui.api.ComponentUiPositionFactory

private val restartButtonPositionFn: ComponentUiPositionFactory = { layout, _, sharedState, _ ->
    val taskBounds = sharedState.taskBoundsFn()
    val taskStableBounds = sharedState.stableBounds ?: Rect()
    val layoutWidth = layout.measuredWidth
    val layoutHeight = layout.measuredHeight
    val positionX =
        if (sharedState.layoutDirectionFn() == View.LAYOUT_DIRECTION_RTL)
            taskStableBounds.left - taskBounds.left
        else taskStableBounds.right - taskBounds.left - layoutWidth
    val positionY = taskStableBounds.bottom - taskBounds.top - layoutHeight
    Point(positionX, positionY)
}

/** CompatUISpec for the Restart Button */
// TODO(b/478792808): Remove suppression
@SuppressWarnings("ProtoLogNonConstantFormat")
@SuppressLint("InflateParams")
val RestartButtonSpec =
    CompatUISpec(
        name = "restartButton",
        lifecycle =
            CompatUILifecyclePredicates(
                creationPredicate = { info, _ ->
                    info.taskInfo.appCompatTaskInfo.isTopActivityInSizeCompat
                },
                removalPredicate = { info, _, _ ->
                    !info.taskInfo.appCompatTaskInfo.isTopActivityInSizeCompat
                },
            ),
        layout =
            CompatUILayout(
                zOrder = TaskConstants.TASK_CHILD_LAYER_COMPAT_UI + 10,
                viewBuilder = { ctx, _, _ ->
                    LayoutInflater.from(ctx).inflate(R.layout.compat_ui_restart_button_layout, null)
                },
                viewBinder = { view, _, _, _ ->
                    view.visibility = View.VISIBLE
                    view.findViewById<View>(R.id.size_compat_restart_button)?.visibility =
                        View.VISIBLE
                },
                positionFactory = restartButtonPositionFn,
            ),
    )
