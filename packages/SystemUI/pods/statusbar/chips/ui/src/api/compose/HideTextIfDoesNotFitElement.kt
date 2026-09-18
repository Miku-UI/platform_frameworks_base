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

package com.android.systemui.statusbar.chips.ui.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * A custom layout modifier for text that ensures the text is only visible if it completely fits
 * within the constrained bounds. Imposes a provided [maxTextWidth]. Also, accounts for provided
 * padding values if provided and ensures its text is placed with the provided padding included
 * around it.
 */
fun Modifier.hideTextIfDoesNotFit(
    text: String,
    textStyle: TextStyle,
    textMeasurer: TextMeasurer,
    maxTextWidth: Dp,
    startPadding: Dp = 0.dp,
    endPadding: Dp = 0.dp,
): Modifier {
    return this.then(
        HideTextIfDoesNotFitElement(
            text,
            textStyle,
            textMeasurer,
            maxTextWidth,
            startPadding,
            endPadding,
        )
    )
}

private data class HideTextIfDoesNotFitElement(
    val text: String,
    val textStyle: TextStyle,
    val textMeasurer: TextMeasurer,
    val maxTextWidth: Dp,
    val startPadding: Dp,
    val endPadding: Dp,
) : ModifierNodeElement<HideTextIfDoesNotFitNode>() {
    override fun create(): HideTextIfDoesNotFitNode {
        return HideTextIfDoesNotFitNode(
            text,
            textStyle,
            textMeasurer,
            maxTextWidth,
            startPadding,
            endPadding,
        )
    }

    override fun update(node: HideTextIfDoesNotFitNode) {
        node.text = text
        node.textStyle = textStyle
        node.textMeasurer = textMeasurer
        node.maxTextWidth = maxTextWidth
        node.startPadding = startPadding
        node.endPadding = endPadding
    }
}

private class HideTextIfDoesNotFitNode(
    var text: String,
    var textStyle: TextStyle,
    var textMeasurer: TextMeasurer,
    var maxTextWidth: Dp,
    var startPadding: Dp,
    var endPadding: Dp,
) : Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val horizontalPadding = startPadding + endPadding
        val maxWidth =
            min(maxTextWidth.roundToPx(), (constraints.maxWidth - horizontalPadding.roundToPx()))
                .coerceAtLeast(constraints.minWidth)
        val placeable = measurable.measure(constraints.copy(maxWidth = maxWidth))

        val intrinsicWidth = textMeasurer.measure(text, textStyle, softWrap = false).size.width
        return if (intrinsicWidth <= maxWidth) {
            val height = placeable.height
            val width = placeable.width
            layout(width + horizontalPadding.roundToPx(), height) {
                placeable.placeRelative(x = startPadding.roundToPx(), y = 0)
            }
        } else {
            layout(0, 0) {}
        }
    }
}
