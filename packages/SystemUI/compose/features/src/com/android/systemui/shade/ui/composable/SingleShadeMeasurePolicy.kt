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

package com.android.systemui.shade.ui.composable

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.offset
import androidx.compose.ui.util.fastFirstOrNull
import com.android.systemui.shade.ui.composable.SingleShadeMeasurePolicy.LayoutId

/**
 * Lays out elements from the [LayoutId] in the shade. This policy supports the case when the QS and
 * UMO share the same row and when they should be one below another.
 */
class SingleShadeMeasurePolicy(
    private val onNotificationsTopChanged: (Int) -> Unit,
    private val cutoutInsetsProvider: () -> WindowInsets?,
) : MeasurePolicy {

    enum class LayoutId {
        MediaAndQqs,
        Notifications,
        ShadeHeader,
    }

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val cutoutInsets: WindowInsets? = cutoutInsetsProvider()
        val constraintsWithCutout = applyCutout(constraints, cutoutInsets)
        val insetsLeft = cutoutInsets?.getLeft(this, layoutDirection) ?: 0
        val insetsTop = cutoutInsets?.getTop(this) ?: 0

        val shadeHeaderPlaceable =
            measurables
                .fastFirstOrNull { it.layoutId == LayoutId.ShadeHeader }
                ?.measure(constraintsWithCutout)
        val mediaAndQqsPlaceable =
            measurables
                .fastFirstOrNull { it.layoutId == LayoutId.MediaAndQqs }
                ?.measure(constraintsWithCutout)
        val notificationsPlaceable =
            measurables
                .fastFirstOrNull { it.layoutId == LayoutId.Notifications }
                ?.measure(constraints)

        val notificationsTop =
            calculateNotificationsTop(
                statusBarHeaderPlaceable = shadeHeaderPlaceable,
                mediaAndQqsPlaceable = mediaAndQqsPlaceable,
                insetsTop = insetsTop,
            )
        // Don't send position updates during the lookahead pass, as it can report a value  that is
        // not yet reflected in the UI.
        if (!isLookingAhead) {
            onNotificationsTopChanged(notificationsTop)
        }

        return layout(constraints.maxWidth, constraints.maxHeight) {
            shadeHeaderPlaceable?.placeRelative(x = insetsLeft, y = insetsTop)
            val statusBarHeaderHeight = shadeHeaderPlaceable?.height ?: 0

            mediaAndQqsPlaceable?.placeRelative(
                x = insetsLeft,
                y = insetsTop + statusBarHeaderHeight,
            )

            // Notifications don't need to accommodate for horizontal insets
            notificationsPlaceable?.placeRelative(x = 0, y = notificationsTop)
        }
    }

    private fun calculateNotificationsTop(
        statusBarHeaderPlaceable: Placeable?,
        mediaAndQqsPlaceable: Placeable?,
        insetsTop: Int,
    ): Int {
        val statusBarHeaderHeight = statusBarHeaderPlaceable?.height ?: 0
        val mediaAndQqsHeight = mediaAndQqsPlaceable?.height ?: 0

        return insetsTop + statusBarHeaderHeight + mediaAndQqsHeight
    }

    private fun MeasureScope.applyCutout(
        constraints: Constraints,
        cutoutInsets: WindowInsets?,
    ): Constraints {
        return if (cutoutInsets == null) {
            constraints
        } else {
            val left = cutoutInsets.getLeft(this, layoutDirection)
            val top = cutoutInsets.getTop(this)
            val right = cutoutInsets.getRight(this, layoutDirection)
            val bottom = cutoutInsets.getBottom(this)

            constraints.offset(horizontal = -(left + right), vertical = -(top + bottom))
        }
    }
}
