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

package com.android.systemui.notifications.ui.composable.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMapIndexed
import androidx.compose.ui.util.fastMapNotNull
import androidx.compose.ui.util.fastMaxOfOrNull
import androidx.compose.ui.util.fastSumBy
import kotlin.math.roundToInt

public interface PrioritizedRowScope {
    /**
     * Marks an element within the [PrioritizedRowScope] as one that can be shrunk down to a reduced
     * width to make space.
     *
     * @param importance The priority for shrinking. Lower numbers are shrunk first.
     * @param minWidth The minimum width this composable can be shrunk to.
     */
    public fun Modifier.shrinkable(importance: Int, minWidth: Dp = 1.dp): Modifier

    /**
     * Marks an element within the [PrioritizedRowScope] as one that can be both shrunk and hidden.
     *
     * @param importance The priority for shrinking and hiding. Lower numbers are removed first.
     * @param reducedWidth The width this composable can be shrunk to, in the first attempt at
     *   making space. If unset (or larger than the required width for this element), this element
     *   will only start shrinking once all the [shrinkable] elements have been shrunk.
     * @param hideWidth The minimum width down to which the layout can try to shrink this composable
     *   before hiding it. Below this width, it will be hidden completely, together with an adjacent
     *   [separator] (if present).
     */
    public fun Modifier.hideable(
        importance: Int,
        reducedWidth: Dp = Dp.Infinity,
        hideWidth: Dp = 1.dp,
    ): Modifier

    /**
     * Tags a composable as a separator, to be hidden along with adjacent [hideable] content if
     * needed.
     */
    public fun Modifier.separator(): Modifier
}

/**
 * A row that lays out its children based on importance. When the content exceeds the available
 * width, it shrinks and then hides lower-importance items to prevent overflow.
 *
 * Children use modifiers from [PrioritizedRowScope] to define their behavior:
 * - [PrioritizedRowScope.shrinkable] allows a child to shrink to a minimum width but never hide.
 * - [PrioritizedRowScope.hideable] allows a child to shrink in stages and then be hidden
 *   completely.
 * - [PrioritizedRowScope.separator] tags a child as a separator to be managed by the layout.
 *
 * Any child without one of these modifiers is treated as a static element that will not be shrunk
 * or hidden.
 *
 * Example usage:
 * ```kotlin
 * PrioritizedRow {
 *     // This item has high importance. It can shrink but will never be hidden.
 *     Text(
 *         text = "High Priority",
 *         modifier = Modifier.shrinkable(importance = 3, minWidth = 80.dp),
 *         maxLines = 1,
 *     )
 *
 *     // A separator to be managed by the layout, that will be hidden when the row below is hidden.
 *     Text(" • ", Modifier.separator())
 *
 *     // This Row is the least important item and will be the first to be hidden.
 *     Row(
 *         modifier = Modifier.hideable(importance = 1),
 *         verticalAlignment = Alignment.CenterVertically,
 *     ) {
 *         Icon(Icons.Default.Info, contentDescription = null)
 *         Text("Lowest Priority", maxLines = 1)
 *     }
 *
 *     // This icon has no priority modifier, so it is static.
 *     Icon(Icons.Default.Star, contentDescription = null)
 * }
 * ```
 *
 * Any elements that should be handled together should be inside a container (like the Row above).
 * If present, separators are expected to alternate with prioritized content. This layout may behave
 * incorrectly if given consecutive separators.
 *
 * @param modifier The [Modifier] to be applied to this layout.
 * @param content The composable children to be laid out, using modifiers from [PrioritizedRowScope]
 *   to define their behavior.
 */
@Composable
public fun PrioritizedRow(
    modifier: Modifier = Modifier,
    content: @Composable PrioritizedRowScope.() -> Unit,
) {
    data class LayoutCandidate(
        val index: Int,
        val measurable: Measurable,
        val isSeparator: Boolean,
        val importance: Int,
        val reducedWidth: Int,
        val hideWidth: Int,
        val canHide: Boolean,
        val preferredWidth: Int,
        var currentWidth: Int,
        var isVisible: Boolean = true,
    ) {
        init {
            check(hideWidth <= reducedWidth) { "hideWidth must be smaller than reducedWidth" }
        }

        fun shrinkable(): Boolean = !isSeparator && (reducedWidth < currentWidth)

        fun hideable(): Boolean = canHide && isVisible && (hideWidth < currentWidth)
    }

    fun List<LayoutCandidate>.previousVisibleChild(index: Int): LayoutCandidate? {
        var prev = index - 1
        while (prev >= 0) {
            if (get(prev).isVisible) {
                return get(prev)
            }
            prev--
        }
        return null
    }

    fun List<LayoutCandidate>.nextVisibleChild(index: Int): LayoutCandidate? {
        var next = index + 1
        while (next < size) {
            if (get(next).isVisible) {
                return get(next)
            }
            next++
        }
        return null
    }

    Layout(content = { PrioritizedRowScopeInstance.content() }, modifier = modifier) {
        measurables,
        constraints ->
        if (measurables.isEmpty()) {
            return@Layout layout(0, 0) {}
        }
        check(constraints.hasBoundedWidth) { "PrioritizedRow width must be constrained" }

        // MEASURE: Calculate initial preferred and min widths for text items.
        val candidates =
            measurables.fastMapIndexed { index, measurable ->
                val data = measurable.parentData as? LayoutData?
                val preferredWidth = measurable.maxIntrinsicWidth(height = Int.MAX_VALUE)

                // If there's no LayoutData, pick the default values such that the candidate will
                // never be hidden or shrunk, and is always at the end of the candidates list
                // when ordered by importance.
                LayoutCandidate(
                    index = index,
                    measurable = measurable,
                    isSeparator = data?.isSeparator ?: false,
                    importance = data?.importance ?: Int.MAX_VALUE,
                    reducedWidth = data?.reducedWidth?.toPx()?.roundToInt() ?: Int.MAX_VALUE,
                    hideWidth = data?.hideWidth?.toPx()?.roundToInt() ?: 0,
                    canHide = data?.canHide ?: false,
                    preferredWidth = preferredWidth,
                    currentWidth = preferredWidth,
                )
            }

        val contentCandidates = candidates.fastFilter { !it.isSeparator }
        val totalWidth = candidates.fastSumBy { it.preferredWidth }
        var overflow = totalWidth - constraints.maxWidth

        if (overflow > 0) {
            val sortedContent = contentCandidates.sortedBy { it.importance }

            // SHRINK: The content doesn't fit, start shrinking elements down to their reduced width
            // based on priority
            var i = 0
            while (i < sortedContent.size) {
                if (!sortedContent[i].shrinkable()) {
                    i++
                    continue
                }

                // Take all candidates with the same importance, and shrink them simultaneously
                val importance = sortedContent[i].importance
                var shrinkableCnt = 1
                var lastShrinkableIdx = i
                for (j in i + 1 until sortedContent.size) {
                    if (sortedContent[j].importance != importance) break
                    if (sortedContent[j].shrinkable()) {
                        lastShrinkableIdx = j
                        shrinkableCnt++
                    }
                }

                var remainingShrinkables = shrinkableCnt
                for (j in i..lastShrinkableIdx) {
                    val shrinkCandidate = sortedContent[j]
                    if (!shrinkCandidate.shrinkable()) continue

                    // Distribute the space needed across all remaining candidates
                    val wantedSpace =
                        (overflow / remainingShrinkables) +
                            minOf(1, overflow % remainingShrinkables)
                    remainingShrinkables--

                    val shrinkableSpace =
                        shrinkCandidate.currentWidth - shrinkCandidate.reducedWidth
                    val shrinkAmount = minOf(wantedSpace, shrinkableSpace, overflow)
                    shrinkCandidate.currentWidth -= shrinkAmount

                    overflow -= shrinkAmount
                }

                if (overflow <= 0) break
                i = lastShrinkableIdx + 1
            }

            // HIDE: Content still doesn't fit, so we need to shrink elements further, and maybe
            // even hide them.
            var somethingWasHidden = false
            if (overflow > 0) {
                i = 0
                while (i < sortedContent.size) {
                    if (!sortedContent[i].hideable()) {
                        i++
                        continue
                    }

                    // Take all hideable candidates with the same importance, and try to shrink them
                    // proportionally before hiding them
                    val importance = sortedContent[i].importance
                    var hideableCnt = 1
                    var lastHideableIdx = i
                    for (j in i + 1 until sortedContent.size) {
                        if (sortedContent[j].importance != importance) break
                        if (sortedContent[j].hideable()) {
                            lastHideableIdx = j
                            hideableCnt++
                        }
                    }

                    var remainingHideables = hideableCnt
                    for (j in i..lastHideableIdx) {
                        val hideCandidate = sortedContent[j]
                        if (!hideCandidate.hideable()) continue

                        // Distribute the space needed across all remaining candidates
                        val wantedSpace =
                            (overflow / remainingHideables) +
                                minOf(1, overflow % remainingHideables)
                        remainingHideables--

                        // One last attempt to shrink this element further
                        val shrinkableSpace = hideCandidate.currentWidth - hideCandidate.hideWidth
                        if (shrinkableSpace >= wantedSpace) {
                            hideCandidate.currentWidth -= wantedSpace
                            overflow -= wantedSpace
                            continue
                        }

                        // Shrinking wouldn't be enough, so let's hide it
                        var spaceToReclaim = hideCandidate.currentWidth
                        hideCandidate.isVisible = false
                        somethingWasHidden = true

                        // Find and hide an adjacent, visible separator
                        val contentIndex = hideCandidate.index // get the position in the layout
                        val prev = candidates.previousVisibleChild(contentIndex)
                        if (prev != null && prev.isSeparator) {
                            prev.isVisible = false
                            spaceToReclaim += prev.currentWidth
                        } else {
                            val next = candidates.nextVisibleChild(contentIndex)
                            if (next != null && next.isSeparator) {
                                next.isVisible = false
                                spaceToReclaim += next.currentWidth
                            }
                        }

                        overflow -= spaceToReclaim
                        if (overflow <= 0) break
                    }

                    if (overflow <= 0) break
                    i = lastHideableIdx + 1
                }
            }

            // REGROW: If hiding items created extra space, give it back to visible shrunk items.
            if (overflow < 0 && somethingWasHidden) {
                var spaceToRegrow = -overflow
                val regrowCandidates =
                    sortedContent.fastFilter { it.isVisible && it.currentWidth < it.preferredWidth }

                for (i in regrowCandidates.indices.reversed()) { // Start with the highest priority
                    val regrowCandidate = regrowCandidates[i]

                    val potentialGrowth =
                        regrowCandidate.preferredWidth - regrowCandidate.currentWidth
                    val amountToGrow = minOf(spaceToRegrow, potentialGrowth)
                    regrowCandidate.currentWidth += amountToGrow

                    spaceToRegrow -= amountToGrow
                    if (spaceToRegrow <= 0) break
                }
            }
        }

        // LAYOUT: Place the visible items and separators according to the measurements
        val placeables =
            candidates.fastMapNotNull { candidate ->
                if (!candidate.isVisible) return@fastMapNotNull null
                candidate.measurable.measure(Constraints.fixedWidth(candidate.currentWidth))
            }
        val height = placeables.fastMaxOfOrNull { it.height } ?: 0

        layout(constraints.constrainWidth(totalWidth), height) {
            var xPosition = 0
            placeables.fastForEach { placeable ->
                placeable.placeRelative(xPosition, y = (height - placeable.height) / 2)
                xPosition += placeable.width
            }
        }
    }
}

private data class LayoutData(
    // Whether this data corresponds to a separator type child
    val isSeparator: Boolean = false,
    // How important this child is in the layout, with lower numbers being shrunk/hidden first for
    // the purpose of making space for the more important ones
    val importance: Int = Int.MAX_VALUE,
    // The first breakpoint width that the layout will try to reduce the child down to, before
    // attempting more aggressive shrinking or hiding.
    val reducedWidth: Dp = Dp.Infinity,
    // The minimum width below which a hideable child will be hidden, rather than being shrunk
    // further. This is unused if canHide = false.
    val hideWidth: Dp = 1.dp,
    // Whether the child can hide or needs to remain visible.
    val canHide: Boolean = false,
)

private class RowDataModifier(val data: LayoutData) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = data
}

private object PrioritizedRowScopeInstance : PrioritizedRowScope {
    override fun Modifier.shrinkable(importance: Int, minWidth: Dp): Modifier {
        return this.then(
            RowDataModifier(LayoutData(importance = importance, reducedWidth = minWidth))
        )
    }

    override fun Modifier.hideable(importance: Int, reducedWidth: Dp, hideWidth: Dp): Modifier {
        return this.then(
            RowDataModifier(
                LayoutData(
                    importance = importance,
                    reducedWidth = reducedWidth,
                    hideWidth = hideWidth,
                    canHide = true,
                )
            )
        )
    }

    override fun Modifier.separator(): Modifier {
        return this.then(
            RowDataModifier(LayoutData(isSeparator = true, importance = Int.MAX_VALUE))
        )
    }
}
