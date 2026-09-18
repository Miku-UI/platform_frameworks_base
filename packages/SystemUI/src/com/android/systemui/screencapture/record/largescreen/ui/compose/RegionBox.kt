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
import android.view.InputDevice
import android.view.MotionEvent
import android.view.PointerIcon as AndroidPointerIcon
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.res.R
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

/** Types of devices that move the pointer/cursor */
enum class PointerDevice {
    Mouse,
    Touchpad,
    Touchscreen,
    Stylus,
    Other,
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
     * Tracks which type of device is used to move the pointer. This will determine where the resize
     * zone is.
     */
    var pointerDevice by mutableStateOf(PointerDevice.Mouse)

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
    fun startDrag(pointerPosition: Offset) {
        val (newDragMode, newResizeZone) = getDragModeForPointer(pointerDevice, pointerPosition)
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
     * Handles resizing the region box by a zone (corner) with the provided offset e.g. from
     * pressing arrow keys.
     *
     * @param zone The resize zone that is currently focused.
     * @param offset The amount to resize the region box by.
     */
    fun adjustZone(zone: ResizeZone, offset: Offset) {
        val currentRect = rect ?: return
        rect = zone.processResizeDrag(currentRect, offset, minSizePx, screenWidth, screenHeight)
    }

    /** Determines which pointer device is being used based on the given motion event. */
    fun setPointerDevice(motionEvent: MotionEvent?) {
        motionEvent ?: return

        // Touchpads that are used to move the mouse cursor will have SOURCE_MOUSE and not
        // SOURCE_TOUCHPAD. Use the tool type to distinguish touchpads from a normal mouse.
        val toolType = motionEvent.getToolType(0)
        val isMouse =
            motionEvent.isFromSource(InputDevice.SOURCE_MOUSE) &&
                toolType == MotionEvent.TOOL_TYPE_MOUSE
        val isTouchpad =
            motionEvent.isFromSource(InputDevice.SOURCE_MOUSE) &&
                toolType == MotionEvent.TOOL_TYPE_FINGER
        val isTouchscreen = motionEvent.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)
        val isStylus = motionEvent.isFromSource(InputDevice.SOURCE_STYLUS)

        pointerDevice =
            when {
                isMouse -> PointerDevice.Mouse
                isTouchpad -> PointerDevice.Touchpad
                isTouchscreen -> PointerDevice.Touchscreen
                isStylus -> PointerDevice.Stylus
                else -> PointerDevice.Other
            }
    }

    /**
     * Determines which part of the region box is being hovered based on the given `pointerType` and
     * the `pointerPosition` relative to the box bounds and tap targets.
     */
    fun updateHoverState(pointerChange: PointerInputChange) {
        // If there is no box, then there is nothing to hover.
        val currentRect = rect ?: return

        // Don't update hover state if the pointer is pressed to prevent flicker during drags.
        if (pointerChange.pressed) {
            return
        }

        val pointerPosition = pointerChange.position

        hoveredZone = getResizeZone(pointerDevice, pointerPosition)
        isHoveringBox = currentRect.contains(pointerPosition)
        captureButtonBounds?.let { buttonBounds ->
            val globalButtonBounds = buttonBounds.translate(currentRect.topLeft)
            isHoveringButton = globalButtonBounds.contains(pointerPosition)
        }
    }

    private fun getDragModeForPointer(
        pointerDevice: PointerDevice,
        pointerPosition: Offset,
    ): Pair<DragMode, ResizeZone?> {
        // If the box is not yet created, it is a drawing drag.
        val currentRect = rect ?: return Pair(DragMode.DRAWING, null)

        val currentResizeZone = getResizeZone(pointerDevice, pointerPosition)
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

    private fun getResizeZone(device: PointerDevice, pointerPosition: Offset): ResizeZone? {
        val currentRect = rect ?: return null

        return getResizeZone(
            boxWidth = currentRect.width,
            boxHeight = currentRect.height,
            pointerOffset = pointerPosition - currentRect.topLeft,
            tapTargetSizePx = getTapTargetSize(device),
        )
    }

    private fun getTapTargetSize(device: PointerDevice): Float {
        return with(density) { if (isPrecisePointerDevice(device)) 24.dp.toPx() else 48.dp.toPx() }
    }

    private fun isPrecisePointerDevice(device: PointerDevice): Boolean {
        return when (device) {
            PointerDevice.Mouse,
            PointerDevice.Touchpad,
            PointerDevice.Stylus -> true
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
    val focusRequester = remember { FocusRequester() }

    // Ensure the region box grabs focus as soon as a selection is made,
    // allowing Enter/Spacebar to trigger the capture.
    LaunchedEffect(state.rect) {
        if (state.rect != null) {
            focusRequester.requestFocus()
        }
    }
    fun notifyRegionSelected() {
        state.rect?.let { rect: Rect ->
            onRegionSelected(
                IntRect(
                    rect.left.roundToInt(),
                    rect.top.roundToInt(),
                    rect.right.roundToInt(),
                    rect.bottom.roundToInt(),
                )
            )
        }
    }

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
                .onKeyEvent { event ->
                    val isActionKey = event.key == Key.Enter || event.key == Key.Spacebar
                    if (isActionKey && event.type == KeyEventType.KeyDown && state.rect != null) {
                        onCaptureClick()
                        true
                    } else {
                        false
                    }
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val pointerEvent = awaitPointerEvent(PointerEventPass.Main)
                            // Do not update hover state if the pointer was not moved.
                            if (pointerEvent.type != PointerEventType.Move) {
                                continue
                            }

                            state.setPointerDevice(pointerEvent.motionEvent)
                            state.updateHoverState(pointerEvent.changes.first())
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        orientationLock = null,
                        onDragStart = { pointerChange: PointerInputChange, _, _ ->
                            state.startDrag(pointerChange.position)
                        },
                        onDrag = { pointerChange: PointerInputChange, dragAmount: Offset ->
                            pointerChange.consume()
                            state.drag(pointerChange.position, dragAmount)
                        },
                        onDragEnd = {
                            state.dragEnd()
                            notifyRegionSelected()
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
                    val pillVerticalSpacingDp = 16.dp
                    val pillVerticalSpacingPx = with(density) { pillVerticalSpacingDp.toPx() }

                    // First, measure the pill [RegionDimensionsPill] to get its actual height.
                    val dimensionPillPlaceable =
                        subcompose("dimensionPill") {
                                RegionDimensionsPill(
                                    widthPx = currentRect.width.roundToInt(),
                                    heightPx = currentRect.height.roundToInt(),
                                    modifier =
                                        Modifier.layout { measurable, _ ->
                                            val pillInnerPlaceable =
                                                measurable.measure(Constraints())

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
                                                pillInnerPlaceable.place(
                                                    pillX.roundToInt(),
                                                    pillY.roundToInt(),
                                                )
                                            }
                                        },
                                )
                            }
                            .first()
                            .measure(constraints)

                    val dimensionPillHeightDp =
                        with(density) { dimensionPillPlaceable.height.toDp() }
                    val dimensionPillHeightPx = with(density) { dimensionPillHeightDp.toPx() }

                    // To determine the button's placement, we first need to know its size. We
                    // subcompose the button once just to measure it.
                    val buttonMeasurable =
                        subcompose("buttonMeasurable") {
                                PrimaryButton(
                                    text = buttonText,
                                    icon = buttonIcon,
                                    onClick = onCaptureClick,
                                )
                            }
                            .first()
                            .measure(constraints)

                    val buttonWidthDp = with(density) { buttonMeasurable.width.toDp() }
                    val buttonHeightDp = with(density) { buttonMeasurable.height.toDp() }

                    // Now that we have the button's size, we can calculate its actual placement.
                    val captureButtonPlacement =
                        if (boxWidthDp > buttonWidthDp && boxHeightDp > buttonHeightDp) {
                            ButtonPlacement.Inside
                        } else {
                            val screenWidth = state.screenWidth
                            val screenHeight = state.screenHeight
                            val buttonWidth = buttonMeasurable.width.toFloat()
                            val buttonHeight = buttonMeasurable.height.toFloat()

                            val topRect =
                                Rect(
                                    left =
                                        currentRect.left + (currentRect.width - buttonWidth) / 2f,
                                    top = currentRect.top - buttonHeight - pillVerticalSpacingPx,
                                    right =
                                        currentRect.left + (currentRect.width + buttonWidth) / 2f,
                                    bottom = currentRect.top - pillVerticalSpacingPx,
                                )
                            if (isRectInScreen(topRect, screenWidth, screenHeight)) {
                                ButtonPlacement.Top
                            } else {
                                val bottomRect =
                                    Rect(
                                        left = topRect.left,
                                        top =
                                            currentRect.bottom +
                                                dimensionPillHeightPx +
                                                pillVerticalSpacingPx,
                                        right = topRect.right,
                                        bottom =
                                            currentRect.bottom +
                                                dimensionPillHeightPx +
                                                pillVerticalSpacingPx +
                                                buttonHeight,
                                    )
                                if (isRectInScreen(bottomRect, screenWidth, screenHeight)) {
                                    ButtonPlacement.Bottom
                                } else {
                                    val rightRect =
                                        Rect(
                                            left = currentRect.right + pillVerticalSpacingPx,
                                            top =
                                                currentRect.top +
                                                    (currentRect.height - buttonHeight) / 2f,
                                            right =
                                                currentRect.right +
                                                    pillVerticalSpacingPx +
                                                    buttonWidth,
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
                                                    (currentRect.width - buttonMeasurable.width) /
                                                        2f
                                                ButtonPlacement.Right ->
                                                    currentRect.width + pillVerticalSpacingPx
                                                ButtonPlacement.Left ->
                                                    -buttonMeasurable.width - pillVerticalSpacingPx
                                            }
                                    )
                                val targetTranslationY by
                                    animateFloatAsState(
                                        targetValue =
                                            when (captureButtonPlacement) {
                                                ButtonPlacement.Top ->
                                                    -buttonMeasurable.height - pillVerticalSpacingPx
                                                ButtonPlacement.Bottom ->
                                                    currentRect.height +
                                                        dimensionPillHeightPx +
                                                        pillVerticalSpacingPx
                                                ButtonPlacement.Inside,
                                                ButtonPlacement.Right,
                                                ButtonPlacement.Left ->
                                                    (currentRect.height - buttonMeasurable.height) /
                                                        2f
                                            }
                                    )

                                state.captureButtonBounds =
                                    Rect(
                                        offset = Offset(targetTranslationX, targetTranslationY),
                                        size =
                                            Size(
                                                width = buttonMeasurable.width.toFloat(),
                                                height = buttonMeasurable.height.toFloat(),
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

                    val selectionBoxPlaceable =
                        subcompose("selectionBox") {
                                Box(
                                    modifier =
                                        Modifier.size(boxWidthDp, boxHeightDp)
                                            .focusRequester(focusRequester)
                                            .focusable()
                                            .border(
                                                width = 2.dp,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                )
                            }
                            .first()
                            .measure(constraints)

                    val (topLeftHandle, topRightHandle, bottomLeftHandle, bottomRightHandle) =
                        subcompose("resizeHandles") {
                                fun handleResize(zone: ResizeZone, offset: Offset) {
                                    state.adjustZone(zone, offset)
                                    notifyRegionSelected()
                                }

                                // Top left
                                ResizeHandle(
                                    rotation = 0f,
                                    contentDescription =
                                        stringResource(
                                            R.string
                                                .screen_capture_region_box_top_left_handle_description
                                        ),
                                    onResize = {
                                        handleResize(zone = ResizeZone.Corner.TopLeft, offset = it)
                                    },
                                )
                                // Top right
                                ResizeHandle(
                                    rotation = 90f,
                                    contentDescription =
                                        stringResource(
                                            R.string
                                                .screen_capture_region_box_top_right_handle_description
                                        ),
                                    onResize = {
                                        handleResize(zone = ResizeZone.Corner.TopRight, offset = it)
                                    },
                                )
                                // Bottom left
                                ResizeHandle(
                                    rotation = 270f,
                                    contentDescription =
                                        stringResource(
                                            R.string
                                                .screen_capture_region_box_bottom_left_handle_description
                                        ),
                                    onResize = {
                                        handleResize(
                                            zone = ResizeZone.Corner.BottomLeft,
                                            offset = it,
                                        )
                                    },
                                )
                                // Bottom right
                                ResizeHandle(
                                    rotation = 180f,
                                    contentDescription =
                                        stringResource(
                                            R.string
                                                .screen_capture_region_box_bottom_right_handle_description
                                        ),
                                    onResize = {
                                        handleResize(
                                            zone = ResizeZone.Corner.BottomRight,
                                            offset = it,
                                        )
                                    },
                                )
                            }
                            .map { it.measure(constraints) }

                    layout(constraints.maxWidth, constraints.maxHeight) {
                        // Place the following placeables at (0,0) within the SubcomposeLayout.
                        // Their final positions are determined by their respective modifiers:
                        // - selectionBoxPlaceable: Sized to the region box bounds.
                        // - dimensionPillPlaceable: Positioned via its own Modifier.layout.
                        // - captureButtonPlaceable: Positioned via its graphicsLayer translations.
                        // The parent Box's graphicsLayer then translates this entire
                        // SubcomposeLayout to the correct on-screen position, ensuring all
                        // elements move as a single, synchronized unit.
                        // The order of these placeables also specify the focus order for a11y.
                        selectionBoxPlaceable.place(0, 0)

                        if (
                            state.dragMode == DragMode.RESIZING ||
                                state.dragMode == DragMode.DRAWING
                        ) {
                            dimensionPillPlaceable.place(0, 0)
                        }

                        // Show resize handles only when not interacting with the box.
                        if (state.dragMode == DragMode.NONE) {
                            val handleSizePx = with(density) { 48.dp.toPx() }
                            val handleCenterOffsetPx = handleSizePx / 2
                            val borderCenterPx = with(density) { 1.dp.toPx() }

                            // Handles should be ordered in clockwise direction which specifies the
                            // tab focus direction.
                            topLeftHandle.place(
                                (-handleCenterOffsetPx + borderCenterPx).roundToInt(),
                                (-handleCenterOffsetPx + borderCenterPx).roundToInt(),
                            )
                            topRightHandle.place(
                                (selectionBoxPlaceable.width -
                                        handleCenterOffsetPx -
                                        borderCenterPx)
                                    .roundToInt(),
                                (-handleCenterOffsetPx + borderCenterPx).roundToInt(),
                            )
                            bottomRightHandle.place(
                                (selectionBoxPlaceable.width -
                                        handleCenterOffsetPx -
                                        borderCenterPx)
                                    .roundToInt(),
                                (selectionBoxPlaceable.height -
                                        handleCenterOffsetPx -
                                        borderCenterPx)
                                    .roundToInt(),
                            )
                            bottomLeftHandle.place(
                                (-handleCenterOffsetPx + borderCenterPx).roundToInt(),
                                (selectionBoxPlaceable.height -
                                        handleCenterOffsetPx -
                                        borderCenterPx)
                                    .roundToInt(),
                            )
                        }

                        captureButtonPlaceable.place(0, 0)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResizeHandle(
    rotation: Float,
    contentDescription: String,
    onResize: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val handleSize = 48.dp
    val handleColor = MaterialTheme.colorScheme.primary
    val focusColor = MaterialTheme.colorScheme.secondary
    val density = LocalDensity.current
    val stepSize = 10.dp
    val stepSizePx = remember(density) { with(density) { stepSize.toPx() } }

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Canvas(
        modifier =
            modifier
                .size(handleSize)
                .graphicsLayer { rotationZ = rotation }
                .focusable(interactionSource = interactionSource)
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        val offset =
                            when (event.key) {
                                Key.DirectionUp -> Offset(0f, -stepSizePx)
                                Key.DirectionDown -> Offset(0f, stepSizePx)
                                Key.DirectionLeft -> Offset(-stepSizePx, 0f)
                                Key.DirectionRight -> Offset(stepSizePx, 0f)
                                else -> Offset.Zero
                            }
                        if (offset != Offset.Zero) {
                            onResize(offset)
                            return@onKeyEvent true
                        }
                    }
                    false
                }
                .semantics { this.contentDescription = contentDescription }
    ) {
        val centerX = this.size.width / 2
        val centerY = this.size.height / 2

        if (isFocused) {
            // Draw a solid circle handle when focused.
            val circleHandleRadius = 8.dp.toPx()
            drawCircle(
                color = handleColor,
                radius = circleHandleRadius,
                center = Offset(centerX, centerY),
            )

            // Draw the focus ring around the circle handle.
            val focusRingRadius = 14.dp.toPx()
            val focusRingStrokeWidth = 3.dp.toPx()
            drawCircle(
                color = focusColor,
                radius = focusRingRadius,
                center = Offset(centerX, centerY),
                style = Stroke(width = focusRingStrokeWidth),
            )
        } else {
            val strokeWidth = 6.dp.toPx()
            val handleLength = 20.dp.toPx()
            val path =
                Path().apply {
                    moveTo(centerX + handleLength, centerY)
                    lineTo(centerX, centerY)
                    lineTo(centerX, centerY + handleLength)
                }

            drawPath(
                path = path,
                color = handleColor,
                style = Stroke(width = strokeWidth, join = StrokeJoin.Round, cap = StrokeCap.Round),
            )
        }
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
