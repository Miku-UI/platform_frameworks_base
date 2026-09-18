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

package com.android.wm.shell.compatui.api

import android.content.Context
import android.graphics.Point
import android.util.Size
import android.view.View
import android.view.WindowManager.LayoutParams
import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.protolog.ShellProtoLogGroup

/**
 * Defines the predicates to invoke for understanding if a component can be created or destroyed.
 */
@SuppressWarnings("ProtoLogNonConstantFormat")
class CompatUILifecyclePredicates(
    // Predicate evaluating to true if the component needs to be created
    val creationPredicate: (CompatUIInfo, CompatUISharedState) -> Boolean,
    // Predicate evaluating to true if the component needs to be destroyed
    val removalPredicate: (CompatUIInfo, CompatUISharedState, CompatUIComponentState?) -> Boolean,
    // Builder for the initial state of the component
    val stateBuilder: (CompatUIInfo, CompatUISharedState) -> CompatUIComponentState? = { _, _ ->
        null
    },
    // Invoked after the removal of a component. It contains clean up code.
    val onRemoval: (CompatUIInfo, CompatUISharedState) -> Unit = { _, _ -> },
)

/** Type for the function responsible to get the position of the ComponentUI */
typealias ComponentUiPositionFactory =
    (View, CompatUIInfo, CompatUISharedState, CompatUIComponentState?) -> Point

/**
 * Type for the function responsible to get the [Size] of the ComponentUI to be used for the
 * [LayoutParams] calculation.
 */
typealias ComponentUiSizeFactory =
    (View, CompatUIInfo, CompatUISharedState, CompatUIComponentState?) -> Size

/** [ComponentUiSizeFactory] that measures the [View] using [MeasureSpec.UNSPECIFIED]. */
val measureSizeFactory: ComponentUiSizeFactory = { view, _, _, _ ->
    view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
    Size(view.measuredWidth, view.measuredHeight)
}

/** [ComponentUiSizeFactory] that measures the [View] using all the task bound. */
val taskBoundsSizeFactory: ComponentUiSizeFactory = { _, _, sharedState, _ ->
    val taskBounds = sharedState.taskBoundsFn()
    Size(taskBounds.width(), taskBounds.height())
}

/** Layout configuration */
data class CompatUILayout(
    val zOrder: Int = 0,
    val layoutParamFlags: Int = FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL,
    val viewBuilder: (Context, CompatUIInfo, CompatUIComponentState?) -> View,
    val viewBinder: (View, CompatUIInfo, CompatUISharedState, CompatUIComponentState?) -> Unit =
        { _, _, _, _ ->
        },
    val positionFactory: ComponentUiPositionFactory? = null,
    val sizeFactory: ComponentUiSizeFactory = measureSizeFactory,
    val viewReleaser: () -> Unit = {},
)

/** Describes each compat ui component to the framework. */
// TODO(b/478792808): Remove suppression
@SuppressWarnings("ProtoLogNonConstantFormat")
class CompatUISpec(
    val log: (String) -> Unit = { str -> ProtoLog.v(ShellProtoLogGroup.WM_SHELL_COMPAT_UI, str) },
    // Unique name for the component. It's used for debug and for generating the
    // unique component identifier in the system.
    val name: String,
    // The lifecycle definition
    val lifecycle: CompatUILifecyclePredicates,
    // The layout definition
    val layout: CompatUILayout,
)
