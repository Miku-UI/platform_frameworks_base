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

package com.android.systemui.headline.ui.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.unit.dp
import com.android.systemui.headline.ui.viewmodel.HeadlineViewModel
import com.android.systemui.headline.ui.viewmodel.HeadlineViewModel.Companion.GoneScene

/**
 * Draws an alpha scrim around the [HeadlineViewModel.uiBounds] to prevent harsh overlaps.
 *
 * This modifier needs to be applied on the composable displayed under Headline.
 */
public fun Modifier.drawWithHeadlineScrim(headlineViewModel: HeadlineViewModel): Modifier {
    return this.graphicsLayer {
            compositingStrategy =
                if (headlineViewModel.shouldDrawScrim(size.width)) {
                    CompositingStrategy.Offscreen
                } else {
                    CompositingStrategy.Auto
                }
        }
        .then(HeadlineScrimElement(headlineViewModel))
}

private data class HeadlineScrimElement(val headlineViewModel: HeadlineViewModel) :
    ModifierNodeElement<HeadlineScrimNode>() {
    override fun create(): HeadlineScrimNode = HeadlineScrimNode(headlineViewModel)

    override fun update(node: HeadlineScrimNode) {
        node.headlineViewModel = headlineViewModel
    }
}

private class HeadlineScrimNode(var headlineViewModel: HeadlineViewModel) :
    Modifier.Node(), DrawModifierNode {

    override fun ContentDrawScope.draw() {
        drawContent()

        // No need for the scrim
        if (!headlineViewModel.shouldDrawScrim(size.width)) {
            return
        }

        val scrimPaddingPx = ScrimPadding.toPx()
        val scrimFadeWidthPx = ScrimFadedEdgeWidth.toPx()
        // Find the bounds relative to this element and inflate the scrim's width
        val positionInRoot = requireLayoutCoordinates().positionInRoot()
        val scrimBounds =
            headlineViewModel.uiBounds
                .relativeTo(positionInRoot)
                .inflateHorizontally(scrimPaddingPx + scrimFadeWidthPx)

        val scrimStart = scrimBounds.left / size.width
        val scrimEnd = scrimBounds.right / size.width
        val fadeWidth = scrimFadeWidthPx / size.width

        drawRect(
            topLeft = scrimBounds.topLeft,
            size = scrimBounds.size,
            brush =
                Brush.horizontalGradient(
                    0f to Color.Black,
                    scrimStart to Color.Black,
                    scrimStart + fadeWidth to Color.Transparent,
                    scrimEnd - fadeWidth to Color.Transparent,
                    scrimEnd to Color.Black,
                    1f to Color.Black,
                ),
            blendMode = BlendMode.DstIn,
        )
    }
}

/**
 * The scrim should be drawn when all conditions are met:
 * - Headline is displayed;
 * - Headline's isn't on GoneScene;
 * - The composable under Headline is larger than Headline.
 */
private fun HeadlineViewModel.shouldDrawScrim(containerWidth: Float): Boolean {
    return uiBounds != Rect.Zero &&
        !state.isCurrentScene(GoneScene) &&
        containerWidth > uiBounds.width
}

private fun Rect.relativeTo(offset: Offset): Rect {
    return translate(-offset.x, -offset.y)
}

private fun Rect.inflateHorizontally(delta: Float): Rect {
    return Rect(left - delta, top, right + delta, bottom)
}

private val ScrimPadding = 8.dp
private val ScrimFadedEdgeWidth = 10.dp
