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

import android.graphics.Rect as IntRect
import android.view.PointerIcon as AndroidPointerIcon
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.screencapture.common.ui.compose.PrimaryButton
import com.android.systemui.screencapture.common.ui.compose.ScreenCaptureColors
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// The different modes of interaction that the user can have with the RegionBox.
enum class DragMode {
    DRAWING,
    MOVING,
    RESIZING,
    NONE,
}

/** The different locations where the capture button can be placed relative to the selection box. */
private enum class ButtonPlacement {
    /** The button is placed inside the selection box. */
    Inside,

    /** The button is placed above the selection box. */
    Top,

    /** The button is placed below the selection box and the dimensions pill. */
    Bottom,

    /** The button is placed to the left of the selection box. */
    Left,

    /** The button is placed to the right of the selection box. */
    Right,
}

/**
 * Returns true if the given [rect] is within the bounds of the screen.
 *
 * @param rect The rectangle to check.
 * @param screenWidth The width of the screen.
 * @param screenHeight The height of the screen.
 */
private fun isRectInScreen(rect: Rect, screenWidth: Float, screenHeight: Float): Boolean {
    return rect.left >= 0 &&
        rect.top >= 0 &&
        rect.right <= screenWidth &&
        rect.bottom <= screenHeight
}

/**
 * Determines which zone (corner or edge) of the box is being tapped or hovered by the pointer.
 *
 * @param boxWidth The total width of the box.
 * @param boxHeight The total height of the box.
 * @param pointerOffset The offset position of the pointer relative to box's top-left corner.
 * @param tapTargetSizePx The size of an individual tap target in pixels.
 * @return The `ResizeZone` that is tapped or hovered, or `null` if none.
 */
private fun getResizeZone(
    boxWidth: Float,
    boxHeight: Float,
    pointerOffset: Offset,
    tapTargetSizePx: Float,
): ResizeZone? {
    val tapTargetHalfPx = floor(tapTargetSizePx / 2)

    // Check if the press is within the overall zone of the box.
    val boxZone = Rect(0f, 0f, boxWidth, boxHeight).inflate(tapTargetHalfPx)
    if (!boxZone.contains(pointerOffset)) {
        return null
    }

    val isTouchingTop = pointerOffset.y in -tapTargetHalfPx..tapTargetHalfPx
    val isTouchingBottom =
        pointerOffset.y in (boxHeight - tapTargetHalfPx)..(boxHeight + tapTargetHalfPx)
    val isTouchingLeft = pointerOffset.x in -tapTargetHalfPx..tapTargetHalfPx
    val isTouchingRight =
        pointerOffset.x in (boxWidth - tapTargetHalfPx)..(boxWidth + tapTargetHalfPx)

    return when {
        // Corners have priority over edges, as they occupy overlapping areas.
        isTouchingTop && isTouchingLeft -> ResizeZone.Corner.TopLeft
        isTouchingTop && isTouchingRight -> ResizeZone.Corner.TopRight
        isTouchingBottom && isTouchingLeft -> ResizeZone.Corner.BottomLeft
        isTouchingBottom && isTouchingRight -> ResizeZone.Corner.BottomRight

        // If not a corner, check for edges.
        isTouchingLeft -> ResizeZone.Edge.Left
        isTouchingTop -> ResizeZone.Edge.Top
        isTouchingRight -> ResizeZone.Edge.Right
        isTouchingBottom -> ResizeZone.Edge.Bottom

        else -> null
    }
}

/**
 * A class that encapsulates the state and logic for the RegionBox composable.
 *
 * @param minSizePx The minimum size of the box in pixels.
 * @param density The density of the screen. Used for the conversions between pixels and Dp.
 * @param initialRect The initial rectangle of the box.
 */
class RegionBoxState(
    private val minSizePx: Float,
    private val density: Density,
    initialRect: IntRect? = null,
) {
    var rect by
        mutableStateOf<Rect?>(
            initialRect?.let {
                Rect(
                    left = it.left.toFloat(),
                    top = it.top.toFloat(),
                    right = it.right.toFloat(),
                    bottom = it.bottom.toFloat(),
                )
            }
        )

    var dragMode by mutableStateOf(DragMode.NONE)

    /**
     * Tracks which edge or corner of the selection box the user is currently dragging to resize the
     * box.
     */
    var resizeZone by mutableStateOf<ResizeZone?>(null)

    /**
     * Tracks which edge or corner that the user is currently hovering over, without any buttons
     * being clicked. It's used to dynamically show the correct resize cursor.
     */
    var hoveredZone by mutableStateOf<ResizeZone?>(null)

    /** The bounds of the capture button, relative to the selection box. */
    var captureButtonBounds by mutableStateOf<Rect?>(null)

    /** True if the user is currently hovering over the selection box. */
    var isHoveringBox by mutableStateOf(false)

    /** True if the user is currently hovering over the capture button. */
    var isHoveringButton by mutableStateOf(false)

    /**
     * The offset of the initial press when the user starts a drag gesture. The offset is relative
     * to the overall screen bounds.
     */
    var newBoxStartOffset by mutableStateOf(Offset.Zero)

    // Must remember the screen size for the drag logic. Initial values are set to 0.
    var screenWidth by mutableFloatStateOf(0f)
    var screenHeight by mutableFloatStateOf(0f)

    /**
     * Determines which drag mode is being initiated based on the given pointer type and position.
     */
    fun startDrag(pointerType: PointerType, pointerPosition: Offset) {
        val (newDragMode, newResizeZone) = getDragModeForPointer(pointerType, pointerPosition)
        dragMode = newDragMode
        resizeZone = newResizeZone
        if (newDragMode == DragMode.DRAWING) {
            newBoxStartOffset = pointerPosition
        }
    }

    fun drag(endOffset: Offset, dragAmount: Offset) {
        val currentRect = rect
        when (dragMode) {
            DragMode.DRAWING -> {
                // Ensure that the box remains within the boundaries of the screen.
                val newBoxEndOffset =
                    Offset(
                        x = endOffset.x.coerceIn(0f, screenWidth),
                        y = endOffset.y.coerceIn(0f, screenHeight),
                    )
                rect =
                    Rect(
                        left = min(newBoxStartOffset.x, newBoxEndOffset.x),
                        top = min(newBoxStartOffset.y, newBoxEndOffset.y),
                        right = max(newBoxStartOffset.x, newBoxEndOffset.x),
                        bottom = max(newBoxStartOffset.y, newBoxEndOffset.y),
                    )
            }
            DragMode.MOVING -> {
                if (currentRect != null) {
                    val newOffset = currentRect.topLeft + dragAmount

                    // Constrain the new position within the parent's boundaries
                    val constrainedLeft = newOffset.x.coerceIn(0f, screenWidth - currentRect.width)
                    val constrainedTop = newOffset.y.coerceIn(0f, screenHeight - currentRect.height)

                    rect =
                        currentRect.translate(
                            translateX = constrainedLeft - currentRect.left,
                            translateY = constrainedTop - currentRect.top,
                        )
                }
            }
            DragMode.RESIZING -> {
                if (currentRect != null && resizeZone != null) {
                    rect =
                        resizeZone!!.processResizeDrag(
                            currentRect,
                            dragAmount,
                            minSizePx,
                            screenWidth,
                            screenHeight,
                        )
                }
            }
            DragMode.NONE -> {
                // Do nothing.
            }
        }
    }

    fun dragEnd() {
        // Apply the minimum region box size after the drag.
        rect?.let { currentRect ->
            // Coerce the box dimensions to be within the min size and screen size.
            val finalWidth = currentRect.width.coerceIn(minSizePx, screenWidth)
            val finalHeight = currentRect.height.coerceIn(minSizePx, screenHeight)

            // Ensure the box's top-left corner is positioned so the box remains
            // entirely within the screen bounds.
            val finalLeft = currentRect.left.coerceAtMost(screenWidth - finalWidth)
            val finalTop = currentRect.top.coerceAtMost(screenHeight - finalHeight)

            rect =
                Rect(
                    left = finalLeft,
                    top = finalTop,
                    right = finalLeft + finalWidth,
                    bottom = finalTop + finalHeight,
                )
        }
        dragMode = DragMode.NONE
        resizeZone = null
    }

    /**
     * Determines which part of the region box is being hovered based on the given `pointerType` and
     * the `pointerPosition` relative to the box bounds and tap targets.
     */
    fun updateHoverState(pointerType: PointerType, pointerPosition: Offset) {
        // If there is no box, then there is nothing to hover.
        val currentRect = rect ?: return

        hoveredZone = getResizeZone(pointerType, pointerPosition)
        isHoveringBox = currentRect.contains(pointerPosition)
        captureButtonBounds?.let { buttonBounds ->
            val globalButtonBounds = buttonBounds.translate(currentRect.topLeft)
            isHoveringButton = globalButtonBounds.contains(pointerPosition)
        }
    }

    private fun getDragModeForPointer(
        pointerType: PointerType,
        pointerPosition: Offset,
    ): Pair<DragMode, ResizeZone?> {
        // If the box is not yet created, it is a drawing drag.
        val currentRect = rect ?: return Pair(DragMode.DRAWING, null)

        val currentResizeZone = getResizeZone(pointerType, pointerPosition)
        return when {
            // If the drag is initiated within the box's resize zones, it is a resizing drag.
            currentResizeZone != null -> Pair(DragMode.RESIZING, currentResizeZone)
            // If the drag was initiated outside the touch zones but inside the box, it is a moving
            // drag.
            currentRect.contains(pointerPosition) -> Pair(DragMode.MOVING, null)
            // The drag is initiated outside the box and resize zones so it is a drawing drag.
            else -> Pair(DragMode.DRAWING, null)
        }
    }

    private fun getResizeZone(pointerType: PointerType, pointerPosition: Offset): ResizeZone? {
        val currentRect = rect ?: return null

        val pointerOffset = pointerPosition - currentRect.topLeft
        val tapTargetSizePx = getTapTargetSize(pointerType)

        return getResizeZone(
            boxWidth = currentRect.width,
            boxHeight = currentRect.height,
            pointerOffset = pointerOffset,
            tapTargetSizePx = tapTargetSizePx,
        )
    }

    private fun getTapTargetSize(pointerType: PointerType): Float {
        return with(density) { if (isPreciseTool(pointerType)) 36.dp.toPx() else 48.dp.toPx() }
    }

    private fun isPreciseTool(pointerType: PointerType): Boolean {
        return when (pointerType) {
            // Mouse, stylus, and touchpad are more accurate tools
            PointerType.Mouse,
            PointerType.Stylus -> true
            // Touchscreen and other types are not
            PointerType.Touch,
            PointerType.Unknown -> false
            else -> false
        }
    }
}

/**
 * A composable that allows the user to create, move, resize, and redraw a rectangular region.
 *
 * @param buttonText The text of the capture button.
 * @param buttonIcon The icon of the capture button. Can be null if the icon has not loaded yet.
 * @param onRegionSelected A callback function that is invoked with the final rectangle when the
 *   user finishes a drag gesture. This rectangle is used for taking a screenshot. The rectangle is
 *   of type [android.graphics.Rect] because the screenshot API requires int values.
 * @param onCaptureClick A callback function that is invoked when the capture button is clicked.
 * @param onInteractionStateChanged A callback function that is invoked when the user starts or
 *   stops interacting with the region box.
 * @param modifier The modifier to be applied to the composable.
 */
@Composable
fun RegionBox(
    initialRect: IntRect?,
    buttonText: String,
    buttonIcon: Icon?,
    onRegionSelected: (rect: IntRect) -> Unit,
    onCaptureClick: () -> Unit,
    onInteractionStateChanged: (isInteracting: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    // The minimum size allowed for the box.
    val minSize = 48.dp
    val minSizePx = remember(density) { with(density) { minSize.toPx() } }

    val state = remember { RegionBoxState(minSizePx, density, initialRect) }
    val scrimColor = ScreenCaptureColors.scrimColor
    val pointerIcon = rememberPointerIcon(state)

    LaunchedEffect(state.dragMode) { onInteractionStateChanged(state.dragMode != DragMode.NONE) }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onSizeChanged { sizeInPixels: IntSize ->
                    state.screenWidth = sizeInPixels.width.toFloat()
                    state.screenHeight = sizeInPixels.height.toFloat()
                }
                .pointerHoverIcon(pointerIcon)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val pointerEvent = awaitPointerEvent(PointerEventPass.Main)
                            // Do not update hover state if the pointer was not moved.
                            if (pointerEvent.type != PointerEventType.Move) {
                                continue
                            }

                            val pointerChange = pointerEvent.changes.first()
                            // Don't update hover state if the pointer is pressed to prevent flicker
                            // during drags.
                            if (pointerChange.pressed) {
                                continue
                            }

                            state.updateHoverState(pointerChange.type, pointerChange.position)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        orientationLock = null,
                        onDragStart = { pointerChange: PointerInputChange, _, _ ->
                            state.startDrag(pointerChange.type, pointerChange.position)
                        },
                        onDrag = { pointerChange: PointerInputChange, dragAmount: Offset ->
                            pointerChange.consume()
                            state.drag(pointerChange.position, dragAmount)
                        },
                        onDragEnd = {
                            state.dragEnd()
                            state.rect?.let { rect: Rect ->
                                // Store the rectangle to the ViewModel for taking a screenshot.
                                // The screenshot API requires a Rect class with int values.
                                onRegionSelected(
                                    IntRect(
                                        rect.left.roundToInt(),
                                        rect.top.roundToInt(),
                                        rect.right.roundToInt(),
                                        rect.bottom.roundToInt(),
                                    )
                                )
                            }
                        },
                        onDragCancel = { state.dragEnd() },
                    )
                }
    ) {
        // Dim the area outside the selected region by drawing a full-screen scrim,
        // and then punching a transparent hole in it that matches the selected region.
        // Before a region is drawn, the entire canvas is covered by the scrim.
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(scrimColor)
            // This clears the scrim within the bounds of the selected region, highlighting
            // the actual screenshot area.
            state.rect?.let {
                drawRect(
                    topLeft = it.topLeft,
                    size = it.size,
                    color = Color.Transparent,
                    blendMode = BlendMode.Clear,
                )
            }
        }

        // The width of the border stroke around the region box.
        val borderStrokeWidth = 2.dp

        state.rect?.let { currentRect ->
            // A parent container for the region box and its associated UI. By applying the
            // graphicsLayer modifier here, all children will be moved together as a single unit,
            // ensuring their movements are perfectly synchronized.
            Box(
                modifier =
                    Modifier.graphicsLayer(
                        translationX = currentRect.left,
                        translationY = currentRect.top,
                    )
            ) {
                val boxWidthDp = with(density) { currentRect.width.toDp() }
                val boxHeightDp = with(density) { currentRect.height.toDp() }

                // Use [SubcomposeLayout] to measure the pill and then use its measured height to
                // correctly position the button. This avoids a circular dependency where the
                // capture button's ([PrimaryButton]) position depends on the dimension pill
                // button [RegionDimensionsPill]'s size, which is only known after measurement.
                SubcomposeLayout { constraints ->
                    // First, measure the pill [RegionDimensionsPill] to get its actual height.
                    val dimensionPillMeasurables =
                        subcompose("dimensionPill") {
                            val pillVerticalSpacingDp = 16.dp
                            RegionDimensionsPill(
                                widthPx = currentRect.width.roundToInt(),
                                heightPx = currentRect.height.roundToInt(),
                                modifier =
                                    Modifier.layout { measurable, _ ->
                                        val pillInnerPlaceable = measurable.measure(Constraints())
                                        val pillVerticalSpacingPx =
                                            with(density) { pillVerticalSpacingDp.toPx() }
                                        // Center the pill horizontally relative to the region
                                        // box's width.
                                        val pillX =
                                            (currentRect.width - pillInnerPlaceable.width) / 2

                                        // Calculate the Y position of the pill, and restrict it
                                        // to stay within the screen bounds.
                                        val pillY =
                                            (currentRect.height + pillVerticalSpacingPx)
                                                .coerceAtMost(
                                                    state.screenHeight -
                                                        currentRect.top -
                                                        pillInnerPlaceable.height -
                                                        pillVerticalSpacingPx
                                                )
                                        layout(
                                            pillInnerPlaceable.width,
                                            pillInnerPlaceable.height,
                                        ) {
                                            pillInnerPlaceable.placeRelative(
                                                pillX.roundToInt(),
                                                pillY.roundToInt(),
                                            )
                                        }
                                    },
                            )
                        }

                    val dimensionPillPlaceable =
                        if (
                            state.dragMode == DragMode.RESIZING ||
                                state.dragMode == DragMode.DRAWING
                        ) {
                            dimensionPillMeasurables.first().measure(constraints)
                        } else {
                            null
                        }

                    val dimensionPillHeightDp =
                        dimensionPillPlaceable?.let { with(density) { it.height.toDp() } } ?: 0.dp

                    // To determine the button's placement, we first need to know its size. We
                    // subcompose the button once just to measure it.
                    val pillVerticalSpacingDp = 16.dp
                    val buttonMeasurable =
                        subcompose("buttonMeasurer") {
                            PrimaryButton(
                                text = buttonText,
                                icon = buttonIcon,
                                onClick = onCaptureClick,
                            )
                        }
                    val buttonSize = buttonMeasurable.first().measure(constraints)
                    val buttonWidthDp = with(density) { buttonSize.width.toDp() }
                    val buttonHeightDp = with(density) { buttonSize.height.toDp() }

                    // Now that we have the button's size, we can calculate its actual placement.
                    val captureButtonPlacement =
                        if (boxWidthDp > buttonWidthDp && boxHeightDp > buttonHeightDp) {
                            ButtonPlacement.Inside
                        } else {
                            val screenWidth = state.screenWidth
                            val screenHeight = state.screenHeight
                            val buttonWidth = buttonSize.width.toFloat()
                            val buttonHeight = buttonSize.height.toFloat()
                            val spacingPx = with(density) { pillVerticalSpacingDp.toPx() }

                            val topRect =
                                Rect(
                                    left =
                                        currentRect.left + (currentRect.width - buttonWidth) / 2f,
                                    top = currentRect.top - buttonHeight - spacingPx,
                                    right =
                                        currentRect.left + (currentRect.width + buttonWidth) / 2f,
                                    bottom = currentRect.top - spacingPx,
                                )
                            if (isRectInScreen(topRect, screenWidth, screenHeight)) {
                                ButtonPlacement.Top
                            } else {
                                val pillHeightPx = with(density) { dimensionPillHeightDp.toPx() }
                                val bottomRect =
                                    Rect(
                                        left = topRect.left,
                                        top = currentRect.bottom + pillHeightPx + spacingPx,
                                        right = topRect.right,
                                        bottom =
                                            currentRect.bottom +
                                                pillHeightPx +
                                                spacingPx +
                                                buttonHeight,
                                    )
                                if (isRectInScreen(bottomRect, screenWidth, screenHeight)) {
                                    ButtonPlacement.Bottom
                                } else {
                                    val rightRect =
                                        Rect(
                                            left = currentRect.right + spacingPx,
                                            top =
                                                currentRect.top +
                                                    (currentRect.height - buttonHeight) / 2f,
                                            right = currentRect.right + spacingPx + buttonWidth,
                                            bottom =
                                                currentRect.top +
                                                    (currentRect.height + buttonHeight) / 2f,
                                        )
                                    if (isRectInScreen(rightRect, screenWidth, screenHeight)) {
                                        ButtonPlacement.Right
                                    } else {
                                        ButtonPlacement.Left
                                    }
                                }
                            }
                        }

                    // Now that we have the correct placement, subcompose the button again to be
                    // placed.
                    val captureButtonPlaceable =
                        subcompose("captureButton") {
                                // Animate the translations based on the calculated placement.
                                // The translation is relative to the top-left corner of the
                                // selection box.
                                val targetTranslationX by
                                    animateFloatAsState(
                                        targetValue =
                                            when (captureButtonPlacement) {
                                                ButtonPlacement.Top,
                                                ButtonPlacement.Bottom,
                                                ButtonPlacement.Inside ->
                                                    (currentRect.width - buttonSize.width) / 2f
                                                ButtonPlacement.Right ->
                                                    currentRect.width +
                                                        with(density) {
                                                            pillVerticalSpacingDp.toPx()
                                                        }
                                                ButtonPlacement.Left ->
                                                    -buttonSize.width -
                                                        with(density) {
                                                            pillVerticalSpacingDp.toPx()
                                                        }
                                            }
                                    )
                                val targetTranslationY by
                                    animateFloatAsState(
                                        targetValue =
                                            when (captureButtonPlacement) {
                                                ButtonPlacement.Top ->
                                                    -buttonSize.height -
                                                        with(density) {
                                                            pillVerticalSpacingDp.toPx()
                                                        }
                                                ButtonPlacement.Bottom ->
                                                    with(density) {
                                                        currentRect.height +
                                                            dimensionPillHeightDp.toPx() +
                                                            pillVerticalSpacingDp.toPx()
                                                    }
                                                ButtonPlacement.Inside,
                                                ButtonPlacement.Right,
                                                ButtonPlacement.Left ->
                                                    (currentRect.height - buttonSize.height) / 2f
                                            }
                                    )

                                state.captureButtonBounds =
                                    Rect(
                                        offset = Offset(targetTranslationX, targetTranslationY),
                                        size =
                                            Size(
                                                width = buttonSize.width.toFloat(),
                                                height = buttonSize.height.toFloat(),
                                            ),
                                    )
                                PrimaryButton(
                                    modifier =
                                        Modifier.graphicsLayer {
                                            translationX = targetTranslationX
                                            translationY = targetTranslationY
                                        },
                                    text = buttonText,
                                    icon = buttonIcon,
                                    onClick = onCaptureClick,
                                )
                            }
                            .first()
                            .measure(constraints)

                    // Finally, measure the selection box itself.
                    val selectionBoxPlaceable =
                        subcompose("selectionBox") {
                                Box(
                                    modifier =
                                        Modifier.size(boxWidthDp, boxHeightDp)
                                            .border(
                                                borderStrokeWidth,
                                                MaterialTheme.colorScheme.primary,
                                            )
                                )
                            }
                            .first()
                            .measure(constraints)

                    layout(constraints.maxWidth, constraints.maxHeight) {
                        // Place all placeables at (0,0) within the SubcomposeLayout.
                        // Their final positions are determined by other modifiers:
                        // - selectionBoxPlaceable: Placed at (0,0) and sized to the selection.
                        // - dimensionPillPlaceable: Positioned via its own Modifier.layout.
                        // - captureButtonPlaceable: Positioned via its graphicsLayer translations.
                        // The parent Box's graphicsLayer then translates this entire
                        // SubcomposeLayout to the correct on-screen position, ensuring all
                        // elements move as a single, synchronized unit.
                        selectionBoxPlaceable.placeRelative(0, 0)
                        dimensionPillPlaceable?.placeRelative(0, 0)
                        captureButtonPlaceable.placeRelative(0, 0)
                    }
                }

                // Draw the 4 resize knobs at the 4 corners of the region box.
                val handleSize = 20.dp
                val handleSizePx = with(density) { handleSize.toPx() }.roundToInt()
                if (state.dragMode != DragMode.MOVING && state.dragMode != DragMode.RESIZING) {
                    Box {
                        ResizeHandle(
                            modifier = Modifier.offset { IntOffset(x = 0, y = 0) },
                            rotation = 0f,
                            size = handleSize,
                        )
                        ResizeHandle(
                            modifier =
                                Modifier.offset {
                                    IntOffset(
                                        x = currentRect.width.roundToInt() - handleSizePx,
                                        y = 0,
                                    )
                                },
                            rotation = 90f,
                            size = handleSize,
                        )
                        ResizeHandle(
                            modifier =
                                Modifier.offset {
                                    IntOffset(
                                        x = 0,
                                        y = currentRect.height.roundToInt() - handleSizePx,
                                    )
                                },
                            rotation = 270f,
                            size = handleSize,
                        )
                        ResizeHandle(
                            modifier =
                                Modifier.offset {
                                    IntOffset(
                                        x = currentRect.width.roundToInt() - handleSizePx,
                                        y = currentRect.height.roundToInt() - handleSizePx,
                                    )
                                },
                            rotation = 180f,
                            size = handleSize,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResizeHandle(rotation: Float, size: Dp, modifier: Modifier = Modifier) {
    val handleColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.size(size).graphicsLayer { rotationZ = rotation }) {
        val strokeWidth = 6.dp.toPx()
        val canvasSize = this.size.width
        val path =
            Path().apply {
                moveTo(canvasSize - strokeWidth / 2, strokeWidth / 2)
                lineTo(strokeWidth / 2, strokeWidth / 2)
                lineTo(strokeWidth / 2, canvasSize - strokeWidth / 2)
            }

        drawPath(
            path = path,
            color = handleColor,
            style = Stroke(width = strokeWidth, join = StrokeJoin.Round, cap = StrokeCap.Round),
        )
    }
}

/**
 * Remembers the appropriate [PointerIcon] based on the current interaction state.
 *
 * @param state The current [RegionBoxState].
 * @return The [PointerIcon] to be displayed.
 */
@Composable
private fun rememberPointerIcon(state: RegionBoxState): PointerIcon {
    val topLeftBottomRightResizeIcon =
        rememberSystemPointerIcon(AndroidPointerIcon.TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW)
    val topRightButtonLeftResizeIcon =
        rememberSystemPointerIcon(AndroidPointerIcon.TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW)
    val verticalResizeIcon =
        rememberSystemPointerIcon(AndroidPointerIcon.TYPE_VERTICAL_DOUBLE_ARROW)
    val horizontalResizeIcon =
        rememberSystemPointerIcon(AndroidPointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW)
    val fourArrowMoveIcon = rememberSystemPointerIcon(AndroidPointerIcon.TYPE_ALL_SCROLL)

    return remember(
        state.resizeZone,
        state.hoveredZone,
        state.dragMode,
        state.isHoveringBox,
        state.isHoveringButton,
    ) {
        val activeZone = state.resizeZone ?: state.hoveredZone
        when {
            state.isHoveringButton -> PointerIcon.Hand
            state.dragMode == DragMode.MOVING -> fourArrowMoveIcon
            activeZone != null ->
                when (activeZone) {
                    ResizeZone.Corner.TopLeft,
                    ResizeZone.Corner.BottomRight -> topLeftBottomRightResizeIcon
                    ResizeZone.Corner.TopRight,
                    ResizeZone.Corner.BottomLeft -> topRightButtonLeftResizeIcon
                    ResizeZone.Edge.Top,
                    ResizeZone.Edge.Bottom -> verticalResizeIcon
                    ResizeZone.Edge.Left,
                    ResizeZone.Edge.Right -> horizontalResizeIcon
                }
            state.isHoveringBox -> fourArrowMoveIcon
            else -> PointerIcon.Crosshair
        }
    }
}

/**
 * Remembers a system [PointerIcon] for the given Android pointer icon type.
 *
 * @param type The system pointer icon type from [android.view.PointerIcon].
 * @return A Compose [PointerIcon] to be used with pointerInput modifiers.
 */
@Composable
private fun rememberSystemPointerIcon(type: Int): PointerIcon {
    val context = LocalContext.current
    return remember(context, type) { PointerIcon(AndroidPointerIcon.getSystemIcon(context, type)) }
}
