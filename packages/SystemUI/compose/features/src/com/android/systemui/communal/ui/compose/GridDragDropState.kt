/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.communal.ui.compose

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toOffset
import com.android.systemui.Flags.communalWidgetResizing
import com.android.systemui.communal.domain.model.CommunalContentModel
import com.android.systemui.communal.shared.model.CommunalContentSize
import com.android.systemui.communal.ui.compose.extensions.firstItemAtOffset
import com.android.systemui.communal.ui.compose.extensions.plus
import com.android.systemui.communal.ui.viewmodel.BaseCommunalViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

private fun Float.directional(origin: LayoutDirection, current: LayoutDirection): Float =
    if (origin == current) this else -this

@Composable
fun rememberGridDragDropState(
    gridState: LazyGridState,
    contentListState: ContentListState,
    updateDragPositionForRemove: (boundingBox: IntRect) -> Boolean,
): GridDragDropState {
    val coroutineScope = rememberCoroutineScope()
    val autoScrollThreshold = with(LocalDensity.current) { 60.dp.toPx() }

    val state =
        remember(gridState, contentListState, updateDragPositionForRemove) {
            GridDragDropState(
                gridState = gridState,
                contentListState = contentListState,
                coroutineScope = coroutineScope,
                autoScrollThreshold = autoScrollThreshold,
                updateDragPositionForRemove = updateDragPositionForRemove,
            )
        }

    LaunchedEffect(state) { state.processScrollRequests(coroutineScope) }

    return state
}

/**
 * Handles drag and drop cards in the glanceable hub. While dragging to move, other items that are
 * affected will dynamically get positioned and the state is tracked by [ContentListState]. When
 * dragging to remove, affected cards will be moved and [updateDragPositionForRemove] is called to
 * check whether the dragged item can be removed. On dragging ends, call [ContentListState.onRemove]
 * to remove the dragged item if condition met and call [ContentListState.onSaveList] to persist any
 * change in ordering.
 */
class GridDragDropState(
    val gridState: LazyGridState,
    private val contentListState: ContentListState,
    private val coroutineScope: CoroutineScope,
    private val autoScrollThreshold: Float,
    private val updateDragPositionForRemove: (draggingBoundingBox: IntRect) -> Boolean,
) {
    var draggingItemKey by mutableStateOf<String?>(null)
        protected set

    var isDraggingToRemove by mutableStateOf(false)
        protected set

    var draggingItemDraggedDelta by mutableStateOf(Offset.Zero)
    var draggingItemInitialOffset by mutableStateOf(Offset.Zero)

    val draggingItemOffset: Offset
        get() =
            draggingItemLayoutInfo?.let { item ->
                draggingItemInitialOffset + draggingItemDraggedDelta - item.offset.toOffset()
            } ?: Offset.Zero

    val draggingItemLayoutInfo: LazyGridItemInfo?
        get() = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggingItemKey }

    private val scrollChannel = Channel<Float>(Channel.UNLIMITED)

    // Used to keep track of the dragging item during scrolling (because it might be off screen
    // and no longer in the list of visible items).
    private var draggingItemWhileScrolling: LazyGridItemInfo? by mutableStateOf(null)

    private val spacer =
        CommunalContentModel.Spacer(CommunalContentSize.fixedThirdOrResponsiveSize())

    private var previousTargetItemKey: Any? = null

    // Basically, the location of the user's finger on the screen.
    private var currentDragPositionOnScreen by mutableStateOf(Offset.Zero)
    // The offset of the grid from the top of the screen.
    private var contentOffset = Offset.Zero

    // The width of one column in the grid (needed in order to auto-scroll one column at a time).
    private var columnWidth = 0

    suspend fun processScrollRequests(coroutineScope: CoroutineScope) {
        while (true) {
            val amount = scrollChannel.receive()

            if (gridState.isScrollInProgress) {
                // Ignore overscrolling if a scroll is already in progress (but we still want to
                // consume the scroll event so that we don't end up processing a bunch of old
                // events after scrolling has finished).
                continue
            }

            // We perform the rest of the drag action after scrolling has finished (or immediately
            // if there will be no scrolling).
            if (amount != 0f) {
                coroutineScope.launch {
                    gridState.animateScrollBy(
                        amount,
                        tween(delayMillis = 250, durationMillis = 1000),
                    )
                    performDragAction()
                }
            } else {
                performDragAction()
            }
        }
    }

    fun onDragStart(
        offset: Offset,
        screenWidth: Int,
        layoutDirection: LayoutDirection,
        contentOffset: Offset,
    ): Boolean {
        val normalizedOffset =
            Offset(
                if (layoutDirection == LayoutDirection.Ltr) offset.x else screenWidth - offset.x,
                offset.y,
            )

        currentDragPositionOnScreen = normalizedOffset
        this.contentOffset = contentOffset

        gridState.layoutInfo.visibleItemsInfo
            .filter { item -> contentListState.isItemEditable(item.key) }
            // grid item offset is based off grid content container so we need to deduct
            // before content padding from the initial pointer position
            .firstItemAtOffset(normalizedOffset - contentOffset)
            ?.apply {
                draggingItemKey = key as String
                draggingItemWhileScrolling = this
                draggingItemInitialOffset = this.offset.toOffset()
                columnWidth =
                    this.size.width +
                        gridState.layoutInfo.beforeContentPadding +
                        gridState.layoutInfo.afterContentPadding
                // Add a spacer after the last widget if it is larger than the dragging widget.
                // This allows overscrolling, enabling the dragging widget to be placed beyond it.
                val lastWidget = contentListState.list.lastOrNull { it.isWidgetContent() }
                if (
                    lastWidget != null &&
                        draggingItemLayoutInfo != null &&
                        lastWidget.size.span > draggingItemLayoutInfo!!.span
                ) {
                    contentListState.list.add(spacer)
                }
                return true
            }

        return false
    }

    fun onDragInterrupted() {
        draggingItemKey?.let {
            if (isDraggingToRemove) {
                contentListState.onRemove(
                    contentListState.list.indexOfFirst { it.key == draggingItemKey }
                )
                isDraggingToRemove = false
                updateDragPositionForRemove(IntRect.Zero)
            }
            // persist list editing changes on dragging ends
            contentListState.onSaveList()
            draggingItemKey = null
        }
        previousTargetItemKey = null
        draggingItemDraggedDelta = Offset.Zero
        draggingItemInitialOffset = Offset.Zero
        currentDragPositionOnScreen = Offset.Zero
        draggingItemWhileScrolling = null
        // Remove spacer, if one is added at the end, when a drag gesture finishes.
        if (
            contentListState.list.isNotEmpty() &&
                contentListState.list.last() is CommunalContentModel.Spacer
        ) {
            contentListState.list.removeLast()
        }
    }

    fun onDrag(offset: Offset, layoutDirection: LayoutDirection) {
        // Adjust offset to match the layout direction
        val delta = Offset(offset.x.directional(LayoutDirection.Ltr, layoutDirection), offset.y)
        draggingItemDraggedDelta += delta
        currentDragPositionOnScreen += delta

        scrollChannel.trySend(computeAutoscroll(currentDragPositionOnScreen))
    }

    fun performDragAction() {
        val draggingItem = draggingItemLayoutInfo ?: draggingItemWhileScrolling
        if (draggingItem == null) {
            return
        }

        val draggingBoundingBox =
            IntRect(draggingItem.offset + draggingItemOffset.round(), draggingItem.size)
        val curDragPositionInGrid = (currentDragPositionOnScreen - contentOffset)

        val targetItem =
            if (communalWidgetResizing()) {
                val lastVisibleItemIndex = gridState.layoutInfo.visibleItemsInfo.last().index
                gridState.layoutInfo.visibleItemsInfo.findLast(
                    fun(item): Boolean {
                        val itemBoundingBox = IntRect(item.offset, item.size)
                        return draggingItemKey != item.key &&
                            contentListState.isItemEditable(item.key) &&
                            itemBoundingBox.contains(curDragPositionInGrid.round()) &&
                            // If we swap with the last visible item, and that item doesn't fit
                            // in the gap created by moving the current item, then the current item
                            // will get placed after the last visible item. In this case, it gets
                            // placed outside of the viewport. We avoid this here, so the user
                            // has to scroll first before the swap can happen.
                            (item.index != lastVisibleItemIndex || item.span <= draggingItem.span)
                    }
                )
            } else {
                gridState.layoutInfo.visibleItemsInfo
                    .asSequence()
                    .filter { item -> contentListState.isItemEditable(item.key) }
                    .filter { item -> draggingItem.index != item.index }
                    .firstItemAtOffset(curDragPositionInGrid)
            }

        if (
            targetItem != null &&
                (!communalWidgetResizing() || targetItem.key != previousTargetItemKey)
        ) {
            val scrollToIndex =
                if (targetItem.index == gridState.firstVisibleItemIndex) {
                    draggingItem.index
                } else if (draggingItem.index == gridState.firstVisibleItemIndex) {
                    targetItem.index
                } else {
                    null
                }
            if (communalWidgetResizing()) {
                // Keep track of the previous target item, to avoid rapidly oscillating between
                // items if the target item doesn't visually move as a result of the index change.
                // In this case, even after the index changes, we'd still be colliding with the
                // element, so it would be selected as the target item the next time this function
                // runs again, which would trigger us to revert the index change we recently made.
                previousTargetItemKey = targetItem.key
            }
            if (scrollToIndex != null) {
                coroutineScope.launch {
                    // this is needed to neutralize automatic keeping the first item first.
                    gridState.scrollToItem(scrollToIndex, gridState.firstVisibleItemScrollOffset)
                    contentListState.swapItems(draggingItem.index, targetItem.index)
                }
            } else {
                contentListState.swapItems(draggingItem.index, targetItem.index)
            }
            draggingItemWhileScrolling = targetItem
            isDraggingToRemove = false
        } else if (targetItem == null) {
            isDraggingToRemove = checkForRemove(draggingBoundingBox)
            previousTargetItemKey = null
        }
    }

    /** Calculate the amount dragged out of bound on both sides. Returns 0f if not overscrolled. */
    private fun computeAutoscroll(dragOffset: Offset): Float {
        val orientation = gridState.layoutInfo.orientation
        val distanceFromStart =
            if (orientation == Orientation.Horizontal) {
                dragOffset.x
            } else {
                dragOffset.y
            }
        val distanceFromEnd =
            if (orientation == Orientation.Horizontal) {
                gridState.layoutInfo.viewportEndOffset - dragOffset.x
            } else {
                gridState.layoutInfo.viewportEndOffset - dragOffset.y
            }

        return when {
            distanceFromEnd < autoScrollThreshold -> {
                (columnWidth - gridState.layoutInfo.beforeContentPadding).toFloat()
            }
            distanceFromStart < autoScrollThreshold -> {
                -(columnWidth - gridState.layoutInfo.afterContentPadding).toFloat()
            }
            else -> 0f
        }
    }

    /** Calls the callback with the updated drag position and returns whether to remove the item. */
    private fun checkForRemove(draggingItemBoundingBox: IntRect): Boolean {
        return if (draggingItemDraggedDelta.y < 0) {
            updateDragPositionForRemove(draggingItemBoundingBox)
        } else {
            false
        }
    }
}

fun Modifier.dragContainer(
    dragDropState: GridDragDropState,
    layoutDirection: LayoutDirection,
    screenWidth: Int,
    contentOffset: Offset,
    viewModel: BaseCommunalViewModel,
): Modifier {
    return this.then(
        Modifier.pointerInput(dragDropState, contentOffset) {
            detectDragGesturesAfterLongPress(
                onDrag = { change, offset ->
                    change.consume()
                    dragDropState.onDrag(offset, layoutDirection)
                },
                onDragStart = { offset ->
                    if (
                        dragDropState.onDragStart(
                            offset,
                            screenWidth,
                            layoutDirection,
                            contentOffset,
                        )
                    ) {
                        // draggingItemKey is guaranteed to be non-null here because it is set in
                        // onDragStart()
                        viewModel.onReorderWidgetStart(dragDropState.draggingItemKey!!)
                    }
                },
                onDragEnd = {
                    dragDropState.onDragInterrupted()
                    viewModel.onReorderWidgetEnd()
                },
                onDragCancel = {
                    dragDropState.onDragInterrupted()
                    viewModel.onReorderWidgetCancel()
                },
            )
        }
    )
}

/** Wrap LazyGrid item with additional modifier needed for drag and drop. */
@ExperimentalFoundationApi
@Composable
fun LazyGridItemScope.DraggableItem(
    dragDropState: GridDragDropState,
    key: Any,
    enabled: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (isDragging: Boolean) -> Unit,
) {
    if (!enabled) {
        return content(false)
    }

    val dragging = key == dragDropState.draggingItemKey
    val itemAlpha: Float by
        animateFloatAsState(
            targetValue = if (dragDropState.isDraggingToRemove) 0.5f else 1f,
            label = "DraggableItemAlpha",
        )
    val direction = LocalLayoutDirection.current
    val draggingModifier =
        if (dragging) {
            Modifier.graphicsLayer {
                translationX =
                    dragDropState.draggingItemOffset.x.directional(LayoutDirection.Ltr, direction)
                translationY = dragDropState.draggingItemOffset.y
                alpha = itemAlpha
            }
        } else {
            Modifier.animateItem()
        }

    // Animate the highlight alpha manually as alpha modifier (and AnimatedVisibility) clips the
    // widget to bounds, which cuts off the highlight as we are drawing outside the widget bounds.
    val highlightSelected = !communalWidgetResizing() && selected
    val alpha by
        animateFloatAsState(
            targetValue =
                if ((dragging || highlightSelected) && !dragDropState.isDraggingToRemove) {
                    1f
                } else {
                    0f
                },
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "Widget outline alpha",
        )

    Box(modifier) {
        HighlightedItem(Modifier.matchParentSize(), alpha = alpha)
        Box(draggingModifier) { content(dragging) }
    }
}
