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

package com.android.systemui.screencapture.record.largescreen.ui.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import kotlin.math.max
import kotlin.math.min
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class RegionBoxStateTest : SysuiTestCase() {
    private lateinit var state: RegionBoxState
    private var touchOffsetPx = TOUCH_TARGET_SIZE_PX / 4f

    @Before
    fun setUp() {
        // Initialize the state before each test
        state = RegionBoxState(MIN_SIZE_PX, Density(DENSITY))
        state.screenWidth = SCREEN_WIDTH
        state.screenHeight = SCREEN_HEIGHT
    }

    @Test
    fun init_rectIsNull() {
        assertThat(state.rect).isNull()
    }

    @Test
    fun startDrag_withNoRect_setsDrawingMode() {
        val pointerPosition = Offset(100f, 150f)
        state.startDrag(PointerType.Mouse, pointerPosition)

        assertThat(state.dragMode).isEqualTo(DragMode.DRAWING)
        assertThat(state.rect).isNull()
        assertThat(state.newBoxStartOffset).isEqualTo(pointerPosition)
    }

    @Test
    fun startDrag_outsideRect_setsDrawingMode() {
        state.rect = Rect(100f, 100f, 200f, 200f)

        val pointerPosition = Offset(500f, 500f)

        // Start drag far outside the existing rect and its touch zones
        state.startDrag(PointerType.Mouse, pointerPosition)

        assertThat(state.dragMode).isEqualTo(DragMode.DRAWING)
        assertThat(state.resizeZone).isNull()
        assertThat(state.newBoxStartOffset).isEqualTo(pointerPosition)
    }

    @Test
    fun startDrag_insideRect_setsMovingMode() {
        val currentRect = Rect(100f, 100f, 300f, 300f)
        // Start drag inside the existing rect, away from edges
        state.rect = currentRect
        state.startDrag(PointerType.Mouse, currentRect.center)

        assertThat(state.dragMode).isEqualTo(DragMode.MOVING)
        assertThat(state.resizeZone).isNull()
    }

    // Helper function for StartDrag tests related to dragging from corner or edge
    private fun handleAndAssertStartDragResizes(
        getDragPoint: (Rect) -> Offset,
        expectedZone: ResizeZone,
    ) {
        val currentRect = Rect(100f, 100f, 300f, 300f)
        state.rect = currentRect

        // Start drag on the specified point (corner or edge)
        val dragStartPoint = getDragPoint(currentRect)
        state.startDrag(PointerType.Mouse, dragStartPoint)

        assertThat(state.dragMode).isEqualTo(DragMode.RESIZING)
        assertThat(state.resizeZone).isEqualTo(expectedZone)
    }

    @Test
    fun startDrag_onTopLeftCorner_setsResizingModeToTopLeft() {
        handleAndAssertStartDragResizes(
            getDragPoint = { rect -> rect.topLeft + Offset(touchOffsetPx, touchOffsetPx) },
            expectedZone = ResizeZone.Corner.TopLeft,
        )
    }

    @Test
    fun startDrag_onTopRightCorner_setsResizingModeToTopRight() {
        handleAndAssertStartDragResizes(
            getDragPoint = { rect -> rect.topRight + Offset(-touchOffsetPx, touchOffsetPx) },
            expectedZone = ResizeZone.Corner.TopRight,
        )
    }

    @Test
    fun startDrag_onBottomLeftCorner_setsResizingModeToBottomLeft() {
        handleAndAssertStartDragResizes(
            getDragPoint = { rect -> rect.bottomLeft + Offset(touchOffsetPx, -touchOffsetPx) },
            expectedZone = ResizeZone.Corner.BottomLeft,
        )
    }

    @Test
    fun startDrag_onBottomRightCorner_setsResizingModeToBottomRight() {
        handleAndAssertStartDragResizes(
            getDragPoint = { rect -> rect.bottomRight + Offset(-touchOffsetPx, -touchOffsetPx) },
            expectedZone = ResizeZone.Corner.BottomRight,
        )
    }

    @Test
    fun startDrag_onTopEdge_setsResizingModeToTop() {
        handleAndAssertStartDragResizes(
            getDragPoint = { rect -> Offset(rect.center.x, rect.top + touchOffsetPx) },
            expectedZone = ResizeZone.Edge.Top,
        )
    }

    @Test
    fun startDrag_onBottomEdge_setsResizingModeToBottom() {
        handleAndAssertStartDragResizes(
            getDragPoint = { rect -> Offset(rect.center.x, rect.bottom - touchOffsetPx) },
            expectedZone = ResizeZone.Edge.Bottom,
        )
    }

    @Test
    fun startDrag_onLeftEdge_setsResizingModeToLeft() {
        handleAndAssertStartDragResizes(
            getDragPoint = { rect -> Offset(rect.left + touchOffsetPx, rect.center.y) },
            expectedZone = ResizeZone.Edge.Left,
        )
    }

    @Test
    fun startDrag_onRightEdge_setsResizingModeToRight() {
        handleAndAssertStartDragResizes(
            getDragPoint = { rect -> Offset(rect.right - touchOffsetPx, rect.center.y) },
            expectedZone = ResizeZone.Edge.Right,
        )
    }

    @Test
    fun startDrag_withMousePointerType_hasSmallerTargetSize() {
        val currentRect = Rect(100f, 100f, 300f, 300f)
        state.rect = currentRect

        val pointerPosition =
            currentRect.topLeft + Offset(TOUCH_TARGET_SIZE_PX / 2f, TOUCH_TARGET_SIZE_PX / 2f)

        // Demonstrate that touch type for the position is treated as resizing.
        state.startDrag(PointerType.Touch, pointerPosition)

        assertThat(state.dragMode).isEqualTo(DragMode.RESIZING)
        assertThat(state.resizeZone).isEqualTo(ResizeZone.Corner.TopLeft)

        // Demonstrate that touch type for same position is not treated as resizing.
        state.startDrag(PointerType.Mouse, pointerPosition)

        assertThat(state.dragMode).isEqualTo(DragMode.MOVING)
        assertThat(state.resizeZone).isNull()
    }

    @Test
    fun drag_inDrawingMode_createsCorrectRect() {
        state.startDrag(PointerType.Mouse, Offset(100f, 100f))
        val endOffset = Offset(200f, 250f)
        assertThat(state.dragMode).isEqualTo(DragMode.DRAWING)
        state.drag(endOffset, Offset.Zero)

        val expectedRect =
            Rect(
                left = min(state.newBoxStartOffset.x, endOffset.x),
                top = min(state.newBoxStartOffset.y, endOffset.y),
                right = max(state.newBoxStartOffset.x, endOffset.x),
                bottom = max(state.newBoxStartOffset.y, endOffset.y),
            )
        assertThat(state.rect).isEqualTo(expectedRect)
    }

    @Test
    fun drag_inDrawingMode_constrainsToScreenBounds() {
        state.startDrag(PointerType.Mouse, Offset(50f, 50f))

        // Drag outside screen boundaries
        val endOffset = Offset(SCREEN_WIDTH + 100f, SCREEN_HEIGHT + 100f)
        assertThat(state.dragMode).isEqualTo(DragMode.DRAWING)
        state.drag(endOffset, Offset.Zero)

        assertThat(state.rect).isEqualTo(Rect(50f, 50f, SCREEN_WIDTH, SCREEN_HEIGHT))
    }

    @Test
    fun drag_inMovingMode_movesRect() {
        val initialRect = Rect(100f, 100f, 200f, 200f)

        state.rect = initialRect
        val dragStartPoint = initialRect.center

        state.startDrag(PointerType.Mouse, dragStartPoint)
        assertThat(state.dragMode).isEqualTo(DragMode.MOVING)

        val dragAmount = Offset(50f, 70f)
        val currentDragPosition = dragStartPoint + dragAmount
        state.drag(currentDragPosition, dragAmount)

        val expectedRect = initialRect.translate(dragAmount.x, dragAmount.y)
        assertThat(state.rect).isEqualTo(expectedRect)
    }

    // Helper function for testing drag MOVING with boundary constraints
    private fun handleAndAssertDragMovingConstrained(
        initialRect: Rect,
        dragAmount: Offset,
        expectedClampedRect: Rect,
    ) {
        state.rect = initialRect

        val dragStartPoint = initialRect.center
        state.startDrag(PointerType.Mouse, dragStartPoint)
        assertThat(state.dragMode).isEqualTo(DragMode.MOVING)

        val currentDragPosition = dragStartPoint + dragAmount
        state.drag(currentDragPosition, dragAmount)

        assertThat(state.rect).isEqualTo(expectedClampedRect)
    }

    @Test
    fun drag_inMovingMode_constrainsToTopScreenEdge() {
        handleAndAssertDragMovingConstrained(
            initialRect = Rect(INITIAL_LEFT, 10f, INITIAL_RIGHT, 10f + RECT_HEIGHT),
            dragAmount = Offset(0f, -50f),
            expectedClampedRect = Rect(INITIAL_LEFT, 0f, INITIAL_RIGHT, RECT_HEIGHT),
        )
    }

    @Test
    fun drag_inMovingMode_constrainsToLeftScreenEdge() {
        handleAndAssertDragMovingConstrained(
            initialRect = Rect(10f, INITIAL_TOP, 10f + RECT_WIDTH, INITIAL_BOTTOM),
            dragAmount = Offset(-50f, 0f),
            expectedClampedRect = Rect(0f, INITIAL_TOP, RECT_WIDTH, INITIAL_BOTTOM),
        )
    }

    @Test
    fun drag_inMovingMode_constrainsToBottomScreenEdge() {
        handleAndAssertDragMovingConstrained(
            initialRect =
                Rect(
                    INITIAL_LEFT,
                    SCREEN_HEIGHT - RECT_HEIGHT - 10f,
                    INITIAL_RIGHT,
                    SCREEN_HEIGHT - 10f,
                ),
            dragAmount = Offset(0f, 50f),
            expectedClampedRect =
                Rect(INITIAL_LEFT, SCREEN_HEIGHT - RECT_HEIGHT, INITIAL_RIGHT, SCREEN_HEIGHT),
        )
    }

    @Test
    fun drag_inMovingMode_constrainsToRightScreenEdge() {
        handleAndAssertDragMovingConstrained(
            initialRect =
                Rect(
                    SCREEN_WIDTH - RECT_WIDTH - 10f,
                    INITIAL_TOP,
                    SCREEN_WIDTH - 10f,
                    INITIAL_BOTTOM,
                ),
            dragAmount = Offset(50f, 0f),
            expectedClampedRect =
                Rect(SCREEN_WIDTH - RECT_WIDTH, INITIAL_TOP, SCREEN_WIDTH, INITIAL_BOTTOM),
        )
    }

    @Test
    fun drag_inMovingMode_constrainsToTopLeftScreenCorner() {
        handleAndAssertDragMovingConstrained(
            initialRect = Rect(10f, 10f, 10f + RECT_WIDTH, 10f + RECT_HEIGHT),
            dragAmount = Offset(-50f, -50f),
            expectedClampedRect =
                Rect(left = 0f, top = 0f, right = RECT_WIDTH, bottom = RECT_HEIGHT),
        )
    }

    @Test
    fun drag_inMovingMode_constrainsToTopRightScreenCorner() {
        handleAndAssertDragMovingConstrained(
            initialRect =
                Rect(SCREEN_WIDTH - RECT_WIDTH - 10f, 10f, SCREEN_WIDTH - 10f, 10f + RECT_HEIGHT),
            dragAmount = Offset(50f, -50f),
            expectedClampedRect =
                Rect(
                    left = SCREEN_WIDTH - RECT_WIDTH,
                    top = 0f,
                    right = SCREEN_WIDTH,
                    bottom = RECT_HEIGHT,
                ),
        )
    }

    @Test
    fun drag_inMovingMode_constrainsToBottomLeftScreenCorner() {
        handleAndAssertDragMovingConstrained(
            initialRect =
                Rect(10f, SCREEN_HEIGHT - RECT_HEIGHT - 10f, 10f + RECT_WIDTH, SCREEN_HEIGHT - 10f),
            dragAmount = Offset(-50f, 50f),
            expectedClampedRect =
                Rect(
                    left = 0f,
                    top = SCREEN_HEIGHT - RECT_HEIGHT,
                    right = RECT_WIDTH,
                    bottom = SCREEN_HEIGHT,
                ),
        )
    }

    @Test
    fun drag_inMovingMode_constrainsToBottomRightScreenCorner() {
        handleAndAssertDragMovingConstrained(
            initialRect =
                Rect(
                    SCREEN_WIDTH - RECT_WIDTH - 10f,
                    SCREEN_HEIGHT - RECT_HEIGHT - 10f,
                    SCREEN_WIDTH - 10f,
                    SCREEN_HEIGHT - 10f,
                ),
            dragAmount = Offset(50f, 50f),
            expectedClampedRect =
                Rect(
                    left = SCREEN_WIDTH - RECT_WIDTH,
                    top = SCREEN_HEIGHT - RECT_HEIGHT,
                    right = SCREEN_WIDTH,
                    bottom = SCREEN_HEIGHT,
                ),
        )
    }

    @Test
    fun drag_inMovingMode_withZeroAmount_noChange() {
        val localInitialRect = Rect(100f, 100f, 200f, 200f)
        handleAndAssertDragMovingConstrained(
            initialRect = localInitialRect,
            dragAmount = Offset.Zero,
            expectedClampedRect = localInitialRect,
        )
    }

    // Helper function for testing drag RESIZING with various constraints
    private fun handleAndAssertDragResizing(
        initialRect: Rect,
        resizeZone: ResizeZone,
        dragAmount: Offset,
        expectedResizedRect: Rect,
    ) {
        state.rect = initialRect

        // Determine the drag start point on the resize zone
        val dragStartOffsetInBox =
            when (resizeZone) {
                ResizeZone.Corner.TopLeft -> Offset.Zero
                ResizeZone.Corner.TopRight -> Offset(initialRect.width, 0f)
                ResizeZone.Corner.BottomLeft -> Offset(0f, initialRect.height)
                ResizeZone.Corner.BottomRight -> Offset(initialRect.width, initialRect.height)
                ResizeZone.Edge.Top -> Offset(initialRect.width / 2f, 0f)
                ResizeZone.Edge.Bottom -> Offset(initialRect.width / 2f, initialRect.height)
                ResizeZone.Edge.Left -> Offset(0f, initialRect.height / 2f)
                ResizeZone.Edge.Right -> Offset(initialRect.width, initialRect.height / 2f)
            }

        val screenDragStartOffset = initialRect.topLeft + dragStartOffsetInBox
        state.startDrag(PointerType.Mouse, screenDragStartOffset)
        assertThat(state.dragMode).isEqualTo(DragMode.RESIZING)
        assertThat(state.resizeZone).isEqualTo(resizeZone)

        // Drag the resize handle by the specified amount
        val currentPointerPosition = screenDragStartOffset + dragAmount
        state.drag(currentPointerPosition, dragAmount)

        // Assert the rect is resized and constrained as expected
        assertThat(state.rect).isEqualTo(expectedResizedRect)
    }

    @Test
    fun drag_inResizingMode_topEdgeExpands() {
        val initial = Rect(100f, 100f, 200f, 200f)
        val dragAmountOffset = Offset(0f, -30f)

        handleAndAssertDragResizing(
            initialRect = initial,
            resizeZone = ResizeZone.Edge.Top,
            dragAmount = dragAmountOffset,
            expectedResizedRect =
                Rect(
                    left = initial.left,
                    top = initial.top + dragAmountOffset.y,
                    right = initial.right,
                    bottom = initial.bottom,
                ),
        )
    }

    @Test
    fun drag_inResizingMode_bottomEdgeExpands() {
        val initial = Rect(100f, 100f, 200f, 200f)
        val dragAmountOffset = Offset(0f, 30f)

        handleAndAssertDragResizing(
            initialRect = initial,
            resizeZone = ResizeZone.Edge.Bottom,
            dragAmount = dragAmountOffset,
            expectedResizedRect =
                Rect(
                    left = initial.left,
                    top = initial.top,
                    right = initial.right,
                    bottom = initial.bottom + dragAmountOffset.y,
                ),
        )
    }

    @Test
    fun drag_inResizingMode_leftEdgeExpands() {
        val initial = Rect(100f, 100f, 200f, 200f)
        val dragAmountOffset = Offset(-30f, 0f)

        handleAndAssertDragResizing(
            initialRect = initial,
            resizeZone = ResizeZone.Edge.Left,
            dragAmount = dragAmountOffset,
            expectedResizedRect =
                Rect(
                    left = initial.left + dragAmountOffset.x,
                    top = initial.top,
                    right = initial.right,
                    bottom = initial.bottom,
                ),
        )
    }

    @Test
    fun drag_inResizingMode_rightEdgeExpands() {
        val initial = Rect(100f, 100f, 200f, 200f)
        val dragAmountOffset = Offset(30f, 0f)

        handleAndAssertDragResizing(
            initialRect = initial,
            resizeZone = ResizeZone.Edge.Right,
            dragAmount = dragAmountOffset,
            expectedResizedRect =
                Rect(
                    left = initial.left,
                    top = initial.top,
                    right = initial.right + dragAmountOffset.x,
                    bottom = initial.bottom,
                ),
        )
    }

    @Test
    fun drag_inResizingMode_topEdgeShrinks() {
        val initial = Rect(100f, 100f, 300f, 300f)
        val dragAmountOffset = Offset(0f, 30f) // Drag moves top edge down (shrinks from top)

        handleAndAssertDragResizing(
            initialRect = initial,
            resizeZone = ResizeZone.Edge.Top,
            dragAmount = dragAmountOffset,
            expectedResizedRect =
                Rect(
                    left = initial.left,
                    top = initial.top + dragAmountOffset.y,
                    right = initial.right,
                    bottom = initial.bottom,
                ),
        )
    }

    @Test
    fun drag_inResizingMode_bottomEdgeShrinks() {
        val initial = Rect(100f, 100f, 300f, 300f)
        val dragAmountOffset = Offset(0f, -30f) // Drag moves bottom edge up (shrinks from bottom)

        handleAndAssertDragResizing(
            initialRect = initial,
            resizeZone = ResizeZone.Edge.Bottom,
            dragAmount = dragAmountOffset,
            expectedResizedRect =
                Rect(
                    left = initial.left,
                    top = initial.top,
                    right = initial.right,
                    bottom = initial.bottom + dragAmountOffset.y,
                ),
        )
    }

    @Test
    fun drag_inResizingMode_leftEdgeShrinks() {
        val initial = Rect(100f, 100f, 300f, 300f)
        val dragAmountOffset = Offset(30f, 0f) // Drag moves left edge right (shrinks from left)

        handleAndAssertDragResizing(
            initialRect = initial,
            resizeZone = ResizeZone.Edge.Left,
            dragAmount = dragAmountOffset,
            expectedResizedRect =
                Rect(
                    left = initial.left + dragAmountOffset.x,
                    top = initial.top,
                    right = initial.right,
                    bottom = initial.bottom,
                ),
        )
    }

    @Test
    fun drag_inResizingMode_rightEdgeShrinks() {
        val initial = Rect(100f, 100f, 300f, 300f)
        val dragAmountOffset = Offset(-30f, 0f) // Drag moves right edge left (shrinks from right)

        handleAndAssertDragResizing(
            initialRect = initial,
            resizeZone = ResizeZone.Edge.Right,
            dragAmount = dragAmountOffset,
            expectedResizedRect =
                Rect(
                    left = initial.left,
                    top = initial.top,
                    right = initial.right + dragAmountOffset.x,
                    bottom = initial.bottom,
                ),
        )
    }

    @Test
    fun drag_inResizingMode_topEdgeExpandsToScreenBoundary() {
        val initialRect = Rect(100f, 30f, 200f, 80f)
        val dragAmountOffset = Offset(0f, -60f) // Drag upwards enough to hit boundary

        handleAndAssertDragResizing(
            initialRect = initialRect,
            resizeZone = ResizeZone.Edge.Top,
            dragAmount = dragAmountOffset,
            expectedResizedRect =
                Rect(
                    left = initialRect.left,
                    top = 0f, // Expected to clamp to screen top
                    right = initialRect.right,
                    bottom = initialRect.bottom,
                ),
        )
    }

    @Test
    fun drag_inResizingMode_bottomEdgeExpandsToScreenBoundary() {
        val initialRect = Rect(100f, SCREEN_HEIGHT - 80f, 200f, SCREEN_HEIGHT - 30f)
        val dragAmountOffset = Offset(0f, 60f) // Drag downwards enough to hit boundary

        handleAndAssertDragResizing(
            initialRect = initialRect,
            resizeZone = ResizeZone.Edge.Bottom,
            dragAmount = dragAmountOffset,
            expectedResizedRect =
                Rect(
                    left = initialRect.left,
                    top = initialRect.top,
                    right = initialRect.right,
                    bottom = SCREEN_HEIGHT, // Expected to clamp to screen bottom
                ),
        )
    }

    @Test
    fun drag_inResizingMode_leftEdgeExpandsToScreenBoundary() {
        val initialRect = Rect(30f, 100f, 80f, 200f)
        val dragAmountOffset = Offset(-60f, 0f) // Drag leftwards enough to hit boundary

        handleAndAssertDragResizing(
            initialRect = initialRect,
            resizeZone = ResizeZone.Edge.Left,
            dragAmount = dragAmountOffset,
            expectedResizedRect =
                Rect(
                    left = 0f, // Expected to clamp to screen left
                    top = initialRect.top,
                    right = initialRect.right,
                    bottom = initialRect.bottom,
                ),
        )
    }

    @Test
    fun drag_inResizingMode_rightEdgeExpandsToScreenBoundary() {
        val initialRect = Rect(SCREEN_WIDTH - 80f, 100f, SCREEN_WIDTH - 30f, 200f)
        val dragAmountOffset = Offset(60f, 0f) // Drag rightwards enough to hit boundary

        handleAndAssertDragResizing(
            initialRect = initialRect,
            resizeZone = ResizeZone.Edge.Right,
            dragAmount = dragAmountOffset,
            expectedResizedRect =
                Rect(
                    left = initialRect.left,
                    top = initialRect.top,
                    right = SCREEN_WIDTH, // Expected to clamp to screen right
                    bottom = initialRect.bottom,
                ),
        )
    }

    @Test
    fun drag_inResizingMode_topEdgeShrinksToMinSize() {
        val initialRect = Rect(100f, 100f, 100f + MIN_SIZE_PX + 50f, 100f + MIN_SIZE_PX + 10f)
        val dragAmountOffset = Offset(0f, 20f) // Drag top edge down

        handleAndAssertDragResizing(
            initialRect = initialRect,
            resizeZone = ResizeZone.Edge.Top,
            dragAmount = dragAmountOffset,
            expectedResizedRect =
                Rect(
                    left = initialRect.left,
                    top =
                        initialRect.bottom -
                            MIN_SIZE_PX, // Top edge moves down until height is MIN_SIZE_PX
                    right = initialRect.right,
                    bottom = initialRect.bottom,
                ),
        )
    }

    @Test
    fun drag_inResizingMode_bottomEdgeShrinksToMinSize() {
        val initialRect = Rect(100f, 100f, 100f + MIN_SIZE_PX + 50f, 100f + MIN_SIZE_PX + 10f)
        val dragAmountOffset = Offset(0f, -20f) // Drag bottom edge up

        handleAndAssertDragResizing(
            initialRect = initialRect,
            resizeZone = ResizeZone.Edge.Bottom,
            dragAmount = dragAmountOffset,
            expectedResizedRect =
                Rect(
                    left = initialRect.left,
                    top = initialRect.top,
                    right = initialRect.right,
                    bottom =
                        initialRect.top +
                            MIN_SIZE_PX, // Bottom edge moves up until height is MIN_SIZE_PX
                ),
        )
    }

    @Test
    fun drag_inResizingMode_leftEdgeShrinksToMinSize() {
        val initialRect = Rect(100f, 100f, 100f + MIN_SIZE_PX + 10f, 100f + MIN_SIZE_PX + 50f)
        val dragAmountOffset = Offset(20f, 0f) // Drag left edge right

        handleAndAssertDragResizing(
            initialRect = initialRect,
            resizeZone = ResizeZone.Edge.Left,
            dragAmount = dragAmountOffset,
            expectedResizedRect =
                Rect(
                    left =
                        initialRect.right -
                            MIN_SIZE_PX, // Left edge moves right until width is MIN_SIZE_PX
                    top = initialRect.top,
                    right = initialRect.right,
                    bottom = initialRect.bottom,
                ),
        )
    }

    @Test
    fun drag_inResizingMode_rightEdgeShrinksToMinSize() {
        val initialRect = Rect(100f, 100f, 100f + MIN_SIZE_PX + 10f, 100f + MIN_SIZE_PX + 50f)
        val dragAmountOffset = Offset(-20f, 0f) // Drag right edge left

        handleAndAssertDragResizing(
            initialRect = initialRect,
            resizeZone = ResizeZone.Edge.Right,
            dragAmount = dragAmountOffset,
            expectedResizedRect =
                Rect(
                    left = initialRect.left,
                    top = initialRect.top,
                    right =
                        initialRect.left +
                            MIN_SIZE_PX, // Right edge moves left until width is MIN_SIZE_PX
                    bottom = initialRect.bottom,
                ),
        )
    }

    @Test
    fun drag_inResizingMode_topLeftCornerExpands() {
        val initial = Rect(100f, 100f, 200f, 200f)
        val dragAmountOffset = Offset(-20f, -30f)

        handleAndAssertDragResizing(
            initialRect = initial,
            resizeZone = ResizeZone.Corner.TopLeft,
            dragAmount = dragAmountOffset,
            expectedResizedRect =
                Rect(
                    left = initial.left + dragAmountOffset.x,
                    top = initial.top + dragAmountOffset.y,
                    right = initial.right,
                    bottom = initial.bottom,
                ),
        )
    }

    @Test
    fun drag_inResizingMode_topRightCornerExpands() {
        val initial = Rect(100f, 100f, 200f, 200f)
        val dragAmountOffset = Offset(20f, -30f)

        handleAndAssertDragResizing(
            initialRect = initial,
            resizeZone = ResizeZone.Corner.TopRight,
            dragAmount = dragAmountOffset,
            expectedResizedRect =
                Rect(
                    left = initial.left,
                    top = initial.top + dragAmountOffset.y,
                    right = initial.right + dragAmountOffset.x,
                    bottom = initial.bottom,
                ),
        )
    }

    @Test
    fun drag_inResizingMode_bottomLeftCornerExpands() {
        val initial = Rect(100f, 100f, 200f, 200f)
        val dragAmountOffset = Offset(-20f, 30f)

        handleAndAssertDragResizing(
            initialRect = initial,
            resizeZone = ResizeZone.Corner.BottomLeft,
            dragAmount = dragAmountOffset,
            expectedResizedRect =
                Rect(
                    left = initial.left + dragAmountOffset.x,
                    top = initial.top,
                    right = initial.right,
                    bottom = initial.bottom + dragAmountOffset.y,
                ),
        )
    }

    @Test
    fun drag_inResizingMode_bottomRightCornerExpands() {
        val initial = Rect(100f, 100f, 200f, 200f)
        val dragAmountOffset = Offset(20f, 30f)

        handleAndAssertDragResizing(
            initialRect = initial,
            resizeZone = ResizeZone.Corner.BottomRight,
            dragAmount = dragAmountOffset,
            expectedResizedRect =
                Rect(
                    left = initial.left,
                    top = initial.top,
                    right = initial.right + dragAmountOffset.x,
                    bottom = initial.bottom + dragAmountOffset.y,
                ),
        )
    }

    @Test
    fun drag_inResizingMode_topLeftCornerShrinks() {
        val initial = Rect(100f, 100f, 300f, 300f)
        val dragAmountOffset = Offset(20f, 30f) // Dragging towards bottom-right

        handleAndAssertDragResizing(
            initialRect = initial,
            resizeZone = ResizeZone.Corner.TopLeft,
            dragAmount = dragAmountOffset,
            expectedResizedRect =
                Rect(
                    left = initial.left + dragAmountOffset.x,
                    top = initial.top + dragAmountOffset.y,
                    right = initial.right,
                    bottom = initial.bottom,
                ),
        )
    }

    @Test
    fun drag_inResizingMode_topRightCornerShrinks() {
        val initial = Rect(100f, 100f, 300f, 300f)
        val dragAmountOffset = Offset(-20f, 30f) // Dragging towards bottom-left

        handleAndAssertDragResizing(
            initialRect = initial,
            resizeZone = ResizeZone.Corner.TopRight,
            dragAmount = dragAmountOffset,
            expectedResizedRect =
                Rect(
                    left = initial.left,
                    top = initial.top + dragAmountOffset.y,
                    right = initial.right + dragAmountOffset.x,
                    bottom = initial.bottom,
                ),
        )
    }

    @Test
    fun drag_inResizingMode_bottomLeftCornerShrinks() {
        val initial = Rect(100f, 100f, 300f, 300f)
        val dragAmountOffset = Offset(20f, -30f) // Dragging towards top-right

        handleAndAssertDragResizing(
            initialRect = initial,
            resizeZone = ResizeZone.Corner.BottomLeft,
            dragAmount = dragAmountOffset,
            expectedResizedRect =
                Rect(
                    left = initial.left + dragAmountOffset.x,
                    top = initial.top,
                    right = initial.right,
                    bottom = initial.bottom + dragAmountOffset.y,
                ),
        )
    }

    @Test
    fun drag_inResizingMode_bottomRightCornerShrinks() {
        val initial = Rect(100f, 100f, 300f, 300f)
        val dragAmountOffset = Offset(-20f, -30f) // Dragging towards top-left

        handleAndAssertDragResizing(
            initialRect = initial,
            resizeZone = ResizeZone.Corner.BottomRight,
            dragAmount = dragAmountOffset,
            expectedResizedRect =
                Rect(
                    left = initial.left,
                    top = initial.top,
                    right = initial.right + dragAmountOffset.x,
                    bottom = initial.bottom + dragAmountOffset.y,
                ),
        )
    }

    @Test
    fun drag_inResizingMode_topLeftCornerExpandsToScreenBoundary() {
        val initialRect = Rect(20f, 30f, 70f, 80f)
        val dragAmountOffset = Offset(-50f, -60f) // Drag far enough to hit 0,0

        handleAndAssertDragResizing(
            initialRect = initialRect,
            resizeZone = ResizeZone.Corner.TopLeft,
            dragAmount = dragAmountOffset,
            expectedResizedRect =
                Rect(
                    left = 0f, // Expected to clamp to screen left
                    top = 0f, // Expected to clamp to screen top
                    right = initialRect.right,
                    bottom = initialRect.bottom,
                ),
        )
    }

    @Test
    fun drag_inResizingMode_topRightCornerExpandsToScreenBoundary() {
        val initialRect = Rect(SCREEN_WIDTH - 70f, 30f, SCREEN_WIDTH - 20f, 80f)
        val dragAmountOffset = Offset(50f, -60f) // Drag to hit SCREEN_WIDTH and 0

        handleAndAssertDragResizing(
            initialRect = initialRect,
            resizeZone = ResizeZone.Corner.TopRight,
            dragAmount = dragAmountOffset,
            expectedResizedRect =
                Rect(
                    left = initialRect.left,
                    top = 0f, // Expected to clamp to screen top
                    right = SCREEN_WIDTH, // Expected to clamp to screen right
                    bottom = initialRect.bottom,
                ),
        )
    }

    @Test
    fun drag_inResizingMode_bottomLeftCornerExpandsToScreenBoundary() {
        val initialRect = Rect(20f, SCREEN_HEIGHT - 80f, 70f, SCREEN_HEIGHT - 30f)
        val dragAmountOffset = Offset(-50f, 60f) // Drag to hit 0 and SCREEN_HEIGHT

        handleAndAssertDragResizing(
            initialRect = initialRect,
            resizeZone = ResizeZone.Corner.BottomLeft,
            dragAmount = dragAmountOffset,
            expectedResizedRect =
                Rect(
                    left = 0f, // Expected to clamp to screen left
                    top = initialRect.top,
                    right = initialRect.right,
                    bottom = SCREEN_HEIGHT, // Expected to clamp to screen bottom
                ),
        )
    }

    @Test
    fun drag_inResizingMode_bottomRightCornerExpandsToScreenBoundary() {
        val initialRect =
            Rect(SCREEN_WIDTH - 70f, SCREEN_HEIGHT - 80f, SCREEN_WIDTH - 20f, SCREEN_HEIGHT - 30f)
        val dragAmountOffset = Offset(50f, 60f) // Drag to hit SCREEN_WIDTH and SCREEN_HEIGHT

        handleAndAssertDragResizing(
            initialRect = initialRect,
            resizeZone = ResizeZone.Corner.BottomRight,
            dragAmount = dragAmountOffset,
            expectedResizedRect =
                Rect(
                    left = initialRect.left,
                    top = initialRect.top,
                    right = SCREEN_WIDTH, // Expected to clamp to screen right
                    bottom = SCREEN_HEIGHT, // Expected to clamp to screen bottom
                ),
        )
    }

    @Test
    fun drag_inResizingMode_topLeftCornerShrinksToMinSize() {
        val initialRect = Rect(100f, 100f, 100f + MIN_SIZE_PX + 10f, 100f + MIN_SIZE_PX + 20f)
        val dragAmountOffset = Offset(20f, 30f)

        handleAndAssertDragResizing(
            initialRect = initialRect,
            resizeZone = ResizeZone.Corner.TopLeft,
            dragAmount = dragAmountOffset,
            expectedResizedRect =
                Rect(
                    left = initialRect.right - MIN_SIZE_PX,
                    top = initialRect.bottom - MIN_SIZE_PX,
                    right = initialRect.right,
                    bottom = initialRect.bottom,
                ),
        )
    }

    @Test
    fun drag_inResizingMode_topRightCornerShrinksToMinSize() {
        val initialRect = Rect(100f, 100f, 100f + MIN_SIZE_PX + 10f, 100f + MIN_SIZE_PX + 20f)
        val dragAmountOffset = Offset(-20f, 30f)

        handleAndAssertDragResizing(
            initialRect = initialRect,
            resizeZone = ResizeZone.Corner.TopRight,
            dragAmount = dragAmountOffset,
            expectedResizedRect =
                Rect(
                    left = initialRect.left,
                    top = initialRect.bottom - MIN_SIZE_PX,
                    right = initialRect.left + MIN_SIZE_PX,
                    bottom = initialRect.bottom,
                ),
        )
    }

    @Test
    fun drag_inResizingMode_bottomLeftCornerShrinksToMinSize() {
        val initialRect = Rect(100f, 100f, 100f + MIN_SIZE_PX + 10f, 100f + MIN_SIZE_PX + 20f)
        val dragAmountOffset = Offset(20f, -30f)

        handleAndAssertDragResizing(
            initialRect = initialRect,
            resizeZone = ResizeZone.Corner.BottomLeft,
            dragAmount = dragAmountOffset,
            expectedResizedRect =
                Rect(
                    left = initialRect.right - MIN_SIZE_PX,
                    top = initialRect.top,
                    right = initialRect.right,
                    bottom = initialRect.top + MIN_SIZE_PX,
                ),
        )
    }

    @Test
    fun drag_inResizingMode_bottomRightCornerShrinksToMinSize() {
        val initialRect = Rect(100f, 100f, 100f + MIN_SIZE_PX + 10f, 100f + MIN_SIZE_PX + 20f)
        val dragAmountOffset = Offset(-20f, -30f)

        handleAndAssertDragResizing(
            initialRect = initialRect,
            resizeZone = ResizeZone.Corner.BottomRight,
            dragAmount = dragAmountOffset,
            expectedResizedRect =
                Rect(
                    left = initialRect.left,
                    top = initialRect.top,
                    right = initialRect.left + MIN_SIZE_PX,
                    bottom = initialRect.top + MIN_SIZE_PX,
                ),
        )
    }

    @Test
    fun drag_inResizingMode_onCorner_withZeroAmount_noChange() {
        val initialRect = Rect(100f, 100f, 200f, 200f)

        handleAndAssertDragResizing(
            initialRect = initialRect,
            resizeZone = ResizeZone.Corner.TopLeft,
            dragAmount = Offset.Zero,
            expectedResizedRect = initialRect,
        )
    }

    @Test
    fun drag_inResizingMode_onEdge_withZeroAmount_noChange() {
        val initialRect = Rect(100f, 100f, 200f, 200f)

        handleAndAssertDragResizing(
            initialRect = initialRect,
            resizeZone = ResizeZone.Edge.Right,
            dragAmount = Offset.Zero,
            expectedResizedRect = initialRect,
        )
    }

    @Test
    fun dragEnd_resetsState() {
        state.rect = Rect(50f, 50f, 150f, 150f)
        state.dragMode = DragMode.MOVING
        state.resizeZone = ResizeZone.Edge.Left
        state.dragEnd()

        assertThat(state.dragMode).isEqualTo(DragMode.NONE)
        assertThat(state.resizeZone).isNull()
        assertThat(state.rect).isEqualTo(Rect(50f, 50f, 150f, 150f))
    }

    @Test
    fun dragEnd_rectWidthSmallerThanMinSize_clampsWidthToMinSize() {
        val lessMinSize = MIN_SIZE_PX - 10f
        val initialNarrow = Rect(50f, 50f, 50f + lessMinSize, 150f)

        state.rect = initialNarrow
        state.dragMode = DragMode.RESIZING
        state.resizeZone = ResizeZone.Edge.Right
        state.dragEnd()

        val expectedRect = Rect(50f, 50f, 50f + MIN_SIZE_PX, 150f)
        assertThat(state.rect).isEqualTo(expectedRect)
        assertThat(state.dragMode).isEqualTo(DragMode.NONE)
        assertThat(state.resizeZone).isNull()
    }

    @Test
    fun dragEnd_rectHeightSmallerThanMinSize_clampsHeightToMinSize() {
        val lessMinSize = MIN_SIZE_PX - 10f
        val initialShort = Rect(50f, 50f, 150f, 50f + lessMinSize)

        state.rect = initialShort
        state.dragMode = DragMode.RESIZING
        state.resizeZone = ResizeZone.Edge.Bottom
        state.dragEnd()

        val expectedRect = Rect(50f, 50f, 150f, 50f + MIN_SIZE_PX)
        assertThat(state.rect).isEqualTo(expectedRect)
        assertThat(state.dragMode).isEqualTo(DragMode.NONE)
        assertThat(state.resizeZone).isNull()
    }

    @Test
    fun dragEnd_rectWidthAndHeightSmallerThanMinSize_clampsBothToMinSize() {
        val lessMinSize = MIN_SIZE_PX - 10f
        val initialNarrowShort = Rect(50f, 50f, 50f + lessMinSize, 50f + lessMinSize)

        state.rect = initialNarrowShort
        state.dragMode = DragMode.RESIZING
        state.resizeZone = ResizeZone.Corner.BottomRight
        state.dragEnd()

        val expectedRect = Rect(50f, 50f, 50f + MIN_SIZE_PX, 50f + MIN_SIZE_PX)
        assertThat(state.rect).isEqualTo(expectedRect)
        assertThat(state.dragMode).isEqualTo(DragMode.NONE)
        assertThat(state.resizeZone).isNull()
    }

    @Test
    fun dragEnd_rectWiderThanScreen_clampsWidthToScreenSize() {
        val greaterScreenWidth = SCREEN_WIDTH + 10f
        val initialWide = Rect(0f, 50f, greaterScreenWidth, 150f)

        state.rect = initialWide
        state.dragMode = DragMode.RESIZING
        state.resizeZone = ResizeZone.Edge.Right
        state.dragEnd()

        val expectedRect = Rect(0f, 50f, SCREEN_WIDTH, 150f)
        assertThat(state.rect).isEqualTo(expectedRect)
        assertThat(state.dragMode).isEqualTo(DragMode.NONE)
        assertThat(state.resizeZone).isNull()
    }

    @Test
    fun dragEnd_rectTallerThanScreen_clampsHeightToScreenSize() {
        val greaterScreenHeight = SCREEN_HEIGHT + 10f
        val initialTall = Rect(50f, 0f, 150f, greaterScreenHeight)

        state.rect = initialTall
        state.dragMode = DragMode.RESIZING
        state.resizeZone = ResizeZone.Edge.Bottom
        state.dragEnd()

        val expectedRect = Rect(50f, 0f, 150f, SCREEN_HEIGHT)
        assertThat(state.rect).isEqualTo(expectedRect)
        assertThat(state.dragMode).isEqualTo(DragMode.NONE)
        assertThat(state.resizeZone).isNull()
    }

    @Test
    fun dragEnd_rectWiderAndTallerThanScreen_clampsBothToScreenSize() {
        val greaterScreenWidth = SCREEN_WIDTH + 10f
        val greaterScreenHeight = SCREEN_HEIGHT + 10f
        val initialWideTall = Rect(0f, 0f, greaterScreenWidth, greaterScreenHeight)

        state.rect = initialWideTall
        state.dragMode = DragMode.RESIZING
        state.resizeZone = ResizeZone.Corner.BottomRight
        state.dragEnd()

        val expectedRect = Rect(0f, 0f, SCREEN_WIDTH, SCREEN_HEIGHT)
        assertThat(state.rect).isEqualTo(expectedRect)
        assertThat(state.dragMode).isEqualTo(DragMode.NONE)
        assertThat(state.resizeZone).isNull()
    }

    @Test
    fun dragEnd_rectTooWideAndOffsetRight_adjustsLeftAndClampsWidth() {
        val initialRect = Rect(50f, 50f, 50f + SCREEN_WIDTH, 150f)

        state.rect = initialRect
        state.dragMode = DragMode.RESIZING
        state.resizeZone = ResizeZone.Edge.Right
        state.dragEnd()

        val expectedRect = Rect(0f, 50f, SCREEN_WIDTH, 150f)
        assertThat(state.rect).isEqualTo(expectedRect)
        assertThat(state.dragMode).isEqualTo(DragMode.NONE)
        assertThat(state.resizeZone).isNull()
    }

    @Test
    fun dragEnd_rectTooTallAndOffsetDown_adjustsTopAndClampsHeight() {
        val initialRect = Rect(50f, 50f, 150f, 50f + SCREEN_HEIGHT)

        state.rect = initialRect
        state.dragMode = DragMode.RESIZING
        state.resizeZone = ResizeZone.Edge.Bottom
        state.dragEnd()

        val expectedRect = Rect(50f, 0f, 150f, SCREEN_HEIGHT)
        assertThat(state.rect).isEqualTo(expectedRect)
        assertThat(state.dragMode).isEqualTo(DragMode.NONE)
        assertThat(state.resizeZone).isNull()
    }

    @Test
    fun dragEnd_rectTooWideAndTallAndOffset_adjustsPositionAndClampsSize() {
        val initialRect = Rect(50f, 50f, 50f + SCREEN_WIDTH, 50f + SCREEN_HEIGHT)

        state.rect = initialRect
        state.dragMode = DragMode.RESIZING
        state.resizeZone = ResizeZone.Corner.BottomRight
        state.dragEnd()

        val expectedRect = Rect(0f, 0f, SCREEN_WIDTH, SCREEN_HEIGHT)
        assertThat(state.rect).isEqualTo(expectedRect)
        assertThat(state.dragMode).isEqualTo(DragMode.NONE)
        assertThat(state.resizeZone).isNull()
    }

    @Test
    fun dragEnd_validRect_noChange() {
        val initial = Rect(50f, 50f, 150f, 150f)
        state.rect = initial
        state.dragMode = DragMode.MOVING
        state.resizeZone = null

        state.dragEnd()

        assertThat(state.dragMode).isEqualTo(DragMode.NONE)
        assertThat(state.resizeZone).isNull()
        assertThat(state.rect).isEqualTo(initial)
    }

    companion object {
        private const val DENSITY = 1f
        private const val MIN_SIZE_PX = 50f
        private const val TOUCH_TARGET_SIZE_PX = 48f

        private const val SCREEN_WIDTH = 800f
        private const val SCREEN_HEIGHT = 600f

        private const val RECT_HEIGHT = 100f
        private const val RECT_WIDTH = 200f

        private const val INITIAL_TOP = 50f
        private const val INITIAL_BOTTOM = 150f
        private const val INITIAL_LEFT = 50f
        private const val INITIAL_RIGHT = 150f
    }
}
