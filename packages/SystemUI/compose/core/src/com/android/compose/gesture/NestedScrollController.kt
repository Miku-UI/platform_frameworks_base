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

package com.android.compose.gesture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScrollModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.util.fastForEach

/**
 * Update [state] and disallow outer scroll after a child node consumed a non-zero scroll amount
 * before reaching its [bounds], so that the child is overscrolled instead of letting the outer
 * scrollable(s) consume the extra scroll.
 *
 * Example:
 * ```
 * val nestedScrollControlState = remember { NestedScrollControlState() }
 * Column(
 *     Modifier
 *         // Note: Any scrollable/draggable parent should use nestedScrollControlState to
 *         // enable/disable themselves.
 *         .verticalScroll(
 *             rememberScrollState(),
 *             enabled = nestedScrollControlState.isOuterScrollAllowed,
 *         )
 * ) {
 *     Column(
 *         Modifier
 *             .nestedScrollController(nestedScrollControlState)
 *             .verticalScroll(rememberScrollState())
 *     ) { ...}
 * }
 * ```
 */
fun Modifier.nestedScrollController(
    state: NestedScrollControlState,
    bounds: NestedScrollableBound = NestedScrollableBound.Any,
): Modifier {
    return this then NestedScrollControllerElement(listOf(state), bounds)
}

/**
 * Updates [states] and disallow outer scroll after a child node consumed a non-zero scroll amount
 * before reaching its [bounds], so that the child is overscrolled instead of letting the outer
 * scrollable(s) consume the extra scroll.
 *
 * @see nestedScrollController
 */
fun Modifier.nestedScrollController(
    states: List<NestedScrollControlState>,
    bounds: NestedScrollableBound = NestedScrollableBound.Any,
): Modifier {
    return this then NestedScrollControllerElement(states, bounds)
}

/**
 * A state that should be used by outer scrollables to disable themselves so that nested scrollables
 * will overscroll when reaching their bounds.
 *
 * @see nestedScrollController
 */
class NestedScrollControlState {
    private val activeHolders = mutableStateSetOf<NestedScrollControllerNode>()

    val isOuterScrollAllowed: Boolean
        get() = activeHolders.isEmpty()

    internal fun holdOuterScroll(source: NestedScrollControllerNode) {
        activeHolders.add(source)
    }

    internal fun isHolding(source: NestedScrollControllerNode): Boolean {
        return source in activeHolders
    }

    internal fun releaseOuterScroll(source: NestedScrollControllerNode) {
        activeHolders.remove(source)
    }
}

/**
 * Specifies when to disable outer scroll after reaching the bounds of a nested scrollable.
 *
 * @see nestedScrollController
 */
enum class NestedScrollableBound {
    /** Disable after reaching any of the scrollable bounds. */
    Any,

    /** Disable after reaching the top (left) bound when scrolling vertically (horizontally). */
    TopOrLeft,

    /** Disable after reaching the bottom (right) bound when scrolling vertically (horizontally). */
    BottomOrRight;

    companion object {
        /**
         * Disable after reaching the left (right) bound when scrolling horizontally in a LTR (RTL)
         * layout.
         *
         * Note: This is exclusively for horizontal scrolling.
         */
        val Start: NestedScrollableBound
            @Composable
            get() =
                when (LocalLayoutDirection.current) {
                    LayoutDirection.Ltr -> TopOrLeft
                    LayoutDirection.Rtl -> BottomOrRight
                }

        /**
         * Disable after reaching the right (left) bound when scrolling horizontally in a LTR (RTL)
         * layout.
         *
         * Note: This is exclusively for horizontal scrolling.
         */
        val End: NestedScrollableBound
            @Composable
            get() =
                when (LocalLayoutDirection.current) {
                    LayoutDirection.Ltr -> BottomOrRight
                    LayoutDirection.Rtl -> TopOrLeft
                }
    }
}

private data class NestedScrollControllerElement(
    private val states: List<NestedScrollControlState>,
    private val bounds: NestedScrollableBound,
) : ModifierNodeElement<NestedScrollControllerNode>() {

    init {
        require(states.isNotEmpty()) {
            "Require at least 1 NestedScrollControlState, has: ${states.size}"
        }
    }

    override fun create(): NestedScrollControllerNode {
        return NestedScrollControllerNode(states, bounds)
    }

    override fun update(node: NestedScrollControllerNode) {
        node.update(states, bounds)
    }
}

internal class NestedScrollControllerNode(
    private var states: List<NestedScrollControlState>,
    private var bounds: NestedScrollableBound,
) : DelegatingNode(), NestedScrollConnection {
    private var childrenConsumedAnyScroll = false
    private var availableOnPreScroll = Offset.Zero

    init {
        delegate(nestedScrollModifierNode(this, dispatcher = null))
    }

    override fun onDetach() {
        releaseAllOuterScroll()
    }

    fun update(newStates: List<NestedScrollControlState>, bounds: NestedScrollableBound) {
        if (newStates != states) {
            val wasHolding = isHoldingOuterScroll()
            releaseAllOuterScroll()
            states = newStates
            if (wasHolding) holdAllOuterScroll()
        }

        this.bounds = bounds
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        availableOnPreScroll = available
        return Offset.Zero
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        val consumedIncludingPreScroll = availableOnPreScroll - available
        if (
            hasConsumedScrollInBounds(consumedIncludingPreScroll.x) ||
                hasConsumedScrollInBounds(consumedIncludingPreScroll.y)
        ) {
            childrenConsumedAnyScroll = true
        }

        if (!childrenConsumedAnyScroll) {
            releaseAllOuterScroll()
        } else {
            holdAllOuterScroll()
        }

        return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        childrenConsumedAnyScroll = false
        releaseAllOuterScroll()
        return super.onPostFling(consumed, available)
    }

    private fun holdAllOuterScroll() {
        states.fastForEach { it.holdOuterScroll(this) }
    }

    private fun isHoldingOuterScroll(): Boolean {
        return states.first().isHolding(this)
    }

    private fun releaseAllOuterScroll() {
        states.fastForEach { it.releaseOuterScroll(this) }
    }

    private fun hasConsumedScrollInBounds(consumed: Float): Boolean {
        return when {
            consumed < 0f ->
                bounds == NestedScrollableBound.Any || bounds == NestedScrollableBound.BottomOrRight

            consumed > 0f ->
                bounds == NestedScrollableBound.Any || bounds == NestedScrollableBound.TopOrLeft

            else -> false
        }
    }
}
