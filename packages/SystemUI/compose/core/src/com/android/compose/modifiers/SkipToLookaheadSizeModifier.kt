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

package com.android.compose.modifiers

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * A modifier that forces the layout to layout at its **lookahead size** immediately, effectively
 * skipping the size animation on this node.
 *
 * This **must** be used within a [androidx.compose.ui.layout.LookaheadScope].
 */
// TODO(b/460044592#comment4): This is a simplified version of the modifier originally found in
//  Shared Element Transitions, adapted for general use within any `LookaheadScope`.
//  DIFF: The lookahead size is enforced as a tight constraint on the child node.
fun Modifier.skipToLookaheadSize() = this then SkipToLookaheadSizeElement

private object SkipToLookaheadSizeElement : ModifierNodeElement<SkipToLookaheadSizeNode>() {
    override fun create(): SkipToLookaheadSizeNode = SkipToLookaheadSizeNode()

    override fun update(node: SkipToLookaheadSizeNode) {}

    override fun InspectorInfo.inspectableProperties() {
        name = "skipToLookaheadSize"
    }

    override fun hashCode(): Int = "skipToLookaheadSize".hashCode()

    override fun equals(other: Any?): Boolean = other === this
}

private val InvalidSize = IntSize(Int.MIN_VALUE, Int.MIN_VALUE)
private val IntSize.isValid: Boolean
    get() = this != InvalidSize

private class SkipToLookaheadSizeNode : LayoutModifierNode, Modifier.Node() {
    private var lookaheadConstraints: Constraints? = null
    private var lookaheadSize: IntSize = InvalidSize

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        // Lookahead Pass: Capture the target state. We calculate and store the layout results
        // that the child *should* have once any animations are complete.
        if (isLookingAhead) {
            lookaheadConstraints = constraints
            val placeable = measurable.measure(constraints)
            lookaheadSize = IntSize(placeable.width, placeable.height)
            return layout(placeable.width, placeable.height) { placeable.place(IntOffset.Zero) }
        }

        val lookaheadConstraints = lookaheadConstraints
        if (lookaheadConstraints == null || !lookaheadSize.isValid) {
            error("skipToLookaheadSize() must be used in a LookaheadScope")
        }

        // Main Pass: Override measurement. We ignore the current `constraints` and force the child
        // to measure using the cached `lookaheadConstraints`.
        val placeable = measurable.measure(lookaheadConstraints)

        // Report the lookahead size to the parent, effectively "skipping" the animation measurement
        // for this node.
        return layout(lookaheadSize.width, lookaheadSize.height) { placeable.place(IntOffset.Zero) }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int {
        return if (!isLookingAhead && lookaheadSize.isValid) {
            lookaheadSize.width
        } else {
            measurable.maxIntrinsicWidth(height)
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int {
        return if (!isLookingAhead && lookaheadSize.isValid) {
            lookaheadSize.width
        } else {
            measurable.minIntrinsicWidth(height)
        }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int {
        return if (!isLookingAhead && lookaheadSize.isValid) {
            lookaheadSize.height
        } else {
            measurable.maxIntrinsicHeight(width)
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int {
        return if (!isLookingAhead && lookaheadSize.isValid) {
            lookaheadSize.height
        } else {
            measurable.minIntrinsicHeight(width)
        }
    }
}
