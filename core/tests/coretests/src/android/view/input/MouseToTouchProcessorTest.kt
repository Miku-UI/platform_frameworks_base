/*
* Copyright 2025 The Android Open Source Project
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

package android.view.input

import android.compat.testing.PlatformCompatChangeRule
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.FeatureInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.PointF
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.Presubmit
import android.platform.test.flag.junit.SetFlagsRule
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.cts.input.MotionEventBuilder
import com.android.cts.input.PointerBuilder
import com.android.cts.input.inputeventmatchers.withActionButton
import com.android.cts.input.inputeventmatchers.withButtonState
import com.android.cts.input.inputeventmatchers.withClassification
import com.android.cts.input.inputeventmatchers.withCoords
import com.android.cts.input.inputeventmatchers.withCoordsForHistoryPos
import com.android.cts.input.inputeventmatchers.withCoordsForPointerIndex
import com.android.cts.input.inputeventmatchers.withHistorySize
import com.android.cts.input.inputeventmatchers.withMotionAction
import com.android.cts.input.inputeventmatchers.withPointerCount
import com.android.cts.input.inputeventmatchers.withSource
import com.android.cts.input.inputeventmatchers.withToolType
import com.android.hardware.input.Flags
import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.collection.IsCollectionWithSize.hasSize
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Tests for [MouseToTouchProcessor].
 *
 * Build/Install/Run:
 * atest FrameworksCoreTests:MouseToTouchProcessorTest
 */
@SmallTest
@Presubmit
class MouseToTouchProcessorTest {
    private lateinit var processor: MouseToTouchProcessor
    private lateinit var context: Context

    @get:Rule
    val setFlagsRule: SetFlagsRule = SetFlagsRule()

    @get:Rule
    val compatChangeRule = PlatformCompatChangeRule()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        processor = MouseToTouchProcessor(context, null)
    }

    @Test
    @DisableFlags(Flags.FLAG_MOUSE_TO_TOUCH_PER_APP_COMPAT)
    fun compatibilityNotNeededIfFlagIsDisabled() {
        assertThat(MouseToTouchProcessor.isCompatibilityNeeded(context), equalTo(false))
    }

    @Test
    @EnableFlags(Flags.FLAG_MOUSE_TO_TOUCH_PER_APP_COMPAT)
    @DisableCompatChanges(ActivityInfo.OVERRIDE_MOUSE_TO_TOUCH)
    fun compatibilityNotNeededIfCompatChangesDisabled() {
        assertThat(MouseToTouchProcessor.isCompatibilityNeeded(context), equalTo(false))
    }

    @Test
    @EnableFlags(Flags.FLAG_MOUSE_TO_TOUCH_PER_APP_COMPAT)
    @EnableCompatChanges(ActivityInfo.OVERRIDE_MOUSE_TO_TOUCH)
    fun compatibilityNotNeededIfCompatChangesEnabled() {
        assertThat(MouseToTouchProcessor.isCompatibilityNeeded(context), equalTo(true))
    }

    @Test
    @EnableFlags(Flags.FLAG_MOUSE_TO_TOUCH_PER_APP_COMPAT)
    @EnableCompatChanges(ActivityInfo.OVERRIDE_MOUSE_TO_TOUCH)
    fun compatibilityNotNeededIfFeaturePCPresent() {
        val mockPackageInfo = PackageInfo().apply {
            reqFeatures = arrayOf(FeatureInfo().apply { name = PackageManager.FEATURE_PC })
        }
        val packageManager = mock<PackageManager> {
            on { getPackageInfo(anyOrNull<String>(), any<Int>()) } doReturn mockPackageInfo
        }
        val mockContext = mock<Context> {
            on { getPackageManager() } doReturn packageManager
        }

        assertThat(MouseToTouchProcessor.isCompatibilityNeeded(mockContext), equalTo(false))
    }

    @Test
    @EnableFlags(Flags.FLAG_MOUSE_TO_TOUCH_PER_APP_COMPAT)
    @EnableCompatChanges(ActivityInfo.OVERRIDE_MOUSE_TO_TOUCH)
    fun compatibilityNeededIfFeaturePCNotPresent() {
        val mockPackageInfo = PackageInfo().apply {
            reqFeatures = arrayOf(FeatureInfo().apply { name = PackageManager.FEATURE_TOUCHSCREEN })
        }
        val packageManager = mock<PackageManager> {
            on { getPackageInfo(anyOrNull<String>(), any<Int>()) } doReturn mockPackageInfo
        }
        val mockContext = mock<Context> {
            on { getPackageManager() } doReturn packageManager
        }

        assertThat(MouseToTouchProcessor.isCompatibilityNeeded(mockContext), equalTo(true))
    }

    @Test
    fun processInputEventForCompatibilityReturnsNullForNonMotionEvent() {
        val keyEvent =
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)
        val result = processor.processInputEventForCompatibility(keyEvent)
        assertThat(result, nullValue())
    }

    @Test
    fun processInputEventForCompatibilityReturnsNullForNonMouseSource() {
        val touchEvent = MotionEventBuilder(MotionEvent.ACTION_DOWN, InputDevice.SOURCE_TOUCHSCREEN)
            .pointer(PointerBuilder(0, MotionEvent.TOOL_TYPE_FINGER).x(0f).y(0f))
            .build()
        val result = processor.processInputEventForCompatibility(touchEvent)
        assertThat(result, nullValue())
    }

    @Test
    fun processInputEventForCompatibilityReturnsNullForScrollEvent() {
        val scrollEvent = MotionEventBuilder(MotionEvent.ACTION_SCROLL, InputDevice.SOURCE_MOUSE)
            .pointer(PointerBuilder(0, MotionEvent.TOOL_TYPE_MOUSE).x(0f).y(0f))
            .build()
        val result = processor.processInputEventForCompatibility(scrollEvent)
        assertThat(result, nullValue())
    }

    @Test
    fun processInputEventForCompatibilityReturnsNullForHoverEvents() {
        val pointer = PointerBuilder(0, MotionEvent.TOOL_TYPE_MOUSE).x(0f).y(0f)
        val enterEvent =
            MotionEventBuilder(MotionEvent.ACTION_HOVER_ENTER, InputDevice.SOURCE_MOUSE)
                .pointer(pointer)
                .build()
        val enterResult = processor.processInputEventForCompatibility(enterEvent)
        assertThat(enterResult, nullValue())

        val moveEvent = MotionEventBuilder(MotionEvent.ACTION_HOVER_MOVE, InputDevice.SOURCE_MOUSE)
            .pointer(pointer)
            .build()
        val moveResult = processor.processInputEventForCompatibility(moveEvent)
        assertThat(moveResult, nullValue())

        val exitEvent = MotionEventBuilder(MotionEvent.ACTION_HOVER_EXIT, InputDevice.SOURCE_MOUSE)
            .pointer(pointer)
            .build()
        val exitResult = processor.processInputEventForCompatibility(exitEvent)
        assertThat(exitResult, nullValue())
    }

    @Test
    fun processInputEventForCompatibilityConvertsMouseEvents() {
        val pointer = PointerBuilder(0, MotionEvent.TOOL_TYPE_MOUSE).x(100f).y(200f)
        // Process ACTION_DOWN
        val mouseDownEvent = MotionEventBuilder(MotionEvent.ACTION_DOWN, InputDevice.SOURCE_MOUSE)
            .pointer(pointer)
            .buttonState(MotionEvent.BUTTON_PRIMARY)
            .build()
        val downResult = processor.processInputEventForCompatibility(mouseDownEvent)

        assertThat(downResult, hasSize(1))
        assertThat(
            downResult!![0] as MotionEvent, allOf(
                withMotionAction(MotionEvent.ACTION_DOWN),
                withSource(InputDevice.SOURCE_TOUCHSCREEN),
                withToolType(MotionEvent.TOOL_TYPE_FINGER),
                withButtonState(0),
                withActionButton(0),
                withCoords(PointF(100f, 200f)),
            )
        )

        // Process ACTION_BUTTON_PRESS
        val buttonPressEvent =
            MotionEventBuilder(MotionEvent.ACTION_BUTTON_PRESS, InputDevice.SOURCE_MOUSE)
                .pointer(pointer)
                .buttonState(MotionEvent.BUTTON_PRIMARY)
                .actionButton(MotionEvent.BUTTON_PRIMARY)
                .build()
        val pressResult = processor.processInputEventForCompatibility(buttonPressEvent)
        assertThat(pressResult, hasSize(0))

        // Process ACTION_MOVE
        pointer.x(110f).y(220f)
        val mouseMoveEvent =
            MotionEventBuilder(MotionEvent.ACTION_MOVE, InputDevice.SOURCE_MOUSE)
                .pointer(pointer)
                .buttonState(MotionEvent.BUTTON_PRIMARY)
                .build()
        val moveResult = processor.processInputEventForCompatibility(mouseMoveEvent)

        assertThat(moveResult, hasSize(1))
        assertThat(
            moveResult!![0] as MotionEvent, allOf(
                withMotionAction(MotionEvent.ACTION_MOVE),
                withSource(InputDevice.SOURCE_TOUCHSCREEN),
                withToolType(MotionEvent.TOOL_TYPE_FINGER),
                withCoords(PointF(110f, 220f)),
                withButtonState(0),
                withActionButton(0),
            )
        )

        // Process ACTION_BUTTON_RELEASE
        val buttonReleaseEvent =
            MotionEventBuilder(MotionEvent.ACTION_BUTTON_RELEASE, InputDevice.SOURCE_MOUSE)
                .pointer(pointer)
                .actionButton(MotionEvent.BUTTON_PRIMARY)
                .build()
        val releaseResult = processor.processInputEventForCompatibility(buttonReleaseEvent)
        assertThat(releaseResult, hasSize(0))

        // Process ACTION_UP
        val mouseUpEvent = MotionEventBuilder(MotionEvent.ACTION_UP, InputDevice.SOURCE_MOUSE)
            .pointer(pointer)
            .build()
        val upResult = processor.processInputEventForCompatibility(mouseUpEvent)

        assertThat(upResult, hasSize(1))
        assertThat(
            upResult!![0] as MotionEvent, allOf(
                withMotionAction(MotionEvent.ACTION_UP),
                withSource(InputDevice.SOURCE_TOUCHSCREEN),
                withToolType(MotionEvent.TOOL_TYPE_FINGER),
                withCoords(PointF(110f, 220f)),
                withButtonState(0),
                withActionButton(0),
            )
        )
    }

    @Test
    fun processInputEventForCompatibilityPreservesHistory() {
        val pointer = PointerBuilder(0, MotionEvent.TOOL_TYPE_MOUSE)

        val mouseDownEvent = MotionEventBuilder(MotionEvent.ACTION_DOWN, InputDevice.SOURCE_MOUSE)
            .pointer(pointer.x(100f).y(200f))
            .buttonState(MotionEvent.BUTTON_PRIMARY)
            .build()
        processor.processInputEventForCompatibility(mouseDownEvent)

        // Process ACTION_MOVE with history
        val mouseMoveEvent =
            MotionEventBuilder(MotionEvent.ACTION_MOVE, InputDevice.SOURCE_MOUSE)
                .pointer(pointer.x(110f).y(220f))
                .buttonState(MotionEvent.BUTTON_PRIMARY)
                .build()
        mouseMoveEvent.addBatch(
            System.currentTimeMillis(),
            arrayOf(pointer.x(115f).y(230f).buildCoords()),
            0
        )
        mouseMoveEvent.addBatch(
            System.currentTimeMillis(),
            arrayOf(pointer.x(120f).y(240f).buildCoords()),
            0
        )
        val result = processor.processInputEventForCompatibility(mouseMoveEvent)

        assertThat(result, hasSize(1))
        assertThat(
            result!![0] as MotionEvent, allOf(
                withMotionAction(MotionEvent.ACTION_MOVE),
                withHistorySize(2),
                withCoordsForHistoryPos(0, PointF(110f, 220f)),
                withCoordsForHistoryPos(1, PointF(115f, 230f)),
                withCoords(PointF(120f, 240f)),
            )
        )
    }

    @Test
    fun processInputEventForCompatibilityConvertsTouchpadSynthesizedPinch() {
        val pointer0 = PointerBuilder(0, MotionEvent.TOOL_TYPE_FINGER).x(750f).y(530f)
        val pointer1 = PointerBuilder(1, MotionEvent.TOOL_TYPE_FINGER).x(1000f).y(530f)

        // Process ACTION_DOWN for pointer0 with classification PINCH
        val downEvent =
            MotionEventBuilder(MotionEvent.ACTION_DOWN, InputDevice.SOURCE_MOUSE)
                .pointer(pointer0)
                .classification(MotionEvent.CLASSIFICATION_PINCH)
                .build()
        val downResult = processor.processInputEventForCompatibility(downEvent)

        assertThat(downResult, hasSize(1))
        assertThat(
            downResult!![0] as MotionEvent, allOf(
                withMotionAction(MotionEvent.ACTION_DOWN),
                withSource(InputDevice.SOURCE_TOUCHSCREEN),
                withToolType(MotionEvent.TOOL_TYPE_FINGER),
                withPointerCount(1),
                withCoords(PointF(750f, 530f)),
                withClassification(MotionEvent.CLASSIFICATION_PINCH),
            )
        )

        // Process ACTION_POINTER_DOWN for pointer1
        val pointerDownEvent =
            MotionEventBuilder(
                MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                InputDevice.SOURCE_MOUSE
            )
                .pointer(pointer0)
                .pointer(pointer1)
                .classification(MotionEvent.CLASSIFICATION_PINCH)
                .build()
        val pointerDownResult = processor.processInputEventForCompatibility(pointerDownEvent)

        assertThat(pointerDownResult, hasSize(1))
        assertThat(
            pointerDownResult!![0] as MotionEvent, allOf(
                withMotionAction(MotionEvent.ACTION_POINTER_DOWN, 1),
                withSource(InputDevice.SOURCE_TOUCHSCREEN),
                withToolType(MotionEvent.TOOL_TYPE_FINGER),
                withPointerCount(2),
                withCoordsForPointerIndex(0, PointF(750f, 530f)),
                withCoordsForPointerIndex(1, PointF(1000f, 530f)),
                withClassification(MotionEvent.CLASSIFICATION_PINCH),
            )
        )

        pointer0.x(700f) // Pinch out
        pointer1.x(1050f)

        // Process ACTION_MOVE
        val moveEvent =
            MotionEventBuilder(MotionEvent.ACTION_MOVE, InputDevice.SOURCE_MOUSE)
                .pointer(pointer0)
                .pointer(pointer1)
                .classification(MotionEvent.CLASSIFICATION_PINCH)
                .build()
        val moveResult = processor.processInputEventForCompatibility(moveEvent)

        assertThat(moveResult, hasSize(1))
        assertThat(
            moveResult!![0] as MotionEvent, allOf(
                withMotionAction(MotionEvent.ACTION_MOVE),
                withSource(InputDevice.SOURCE_TOUCHSCREEN),
                withToolType(MotionEvent.TOOL_TYPE_FINGER),
                withPointerCount(2),
                withCoordsForPointerIndex(0, PointF(700f, 530f)),
                withCoordsForPointerIndex(1, PointF(1050f, 530f)),
                withClassification(MotionEvent.CLASSIFICATION_PINCH),
            )
        )

        // Process ACTION_POINTER_UP for pointer1
        val pointerUpEvent =
            MotionEventBuilder(
                MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                InputDevice.SOURCE_MOUSE
            )
                .pointer(pointer0)
                .pointer(pointer1)
                .classification(MotionEvent.CLASSIFICATION_PINCH)
                .build()
        val pointerUpResult = processor.processInputEventForCompatibility(pointerUpEvent)

        assertThat(pointerUpResult, hasSize(1))
        assertThat(
            pointerUpResult!![0] as MotionEvent, allOf(
                withMotionAction(MotionEvent.ACTION_POINTER_UP, 1),
                withSource(InputDevice.SOURCE_TOUCHSCREEN),
                withToolType(MotionEvent.TOOL_TYPE_FINGER),
                withPointerCount(2),
                withClassification(MotionEvent.CLASSIFICATION_PINCH),
            )
        )

        // Process ACTION_UP for pointer0
        val upEvent = MotionEventBuilder(MotionEvent.ACTION_UP, InputDevice.SOURCE_MOUSE)
            .pointer(pointer0)
            .classification(MotionEvent.CLASSIFICATION_PINCH)
            .build()
        val upResult = processor.processInputEventForCompatibility(upEvent)

        assertThat(upResult, hasSize(1))
        assertThat(
            upResult!![0] as MotionEvent, allOf(
                withMotionAction(MotionEvent.ACTION_UP),
                withSource(InputDevice.SOURCE_TOUCHSCREEN),
                withToolType(MotionEvent.TOOL_TYPE_FINGER),
                withPointerCount(1),
                withClassification(MotionEvent.CLASSIFICATION_PINCH),
            )
        )
    }

    @Test
    fun processInputEventForCompatibilityConvertsMouseCancel() {
        val mouseDownEvent = MotionEventBuilder(MotionEvent.ACTION_DOWN, InputDevice.SOURCE_MOUSE)
            .pointer(PointerBuilder(0, MotionEvent.TOOL_TYPE_MOUSE).x(100f).y(200f))
            .buttonState(MotionEvent.BUTTON_PRIMARY)
            .build()
        processor.processInputEventForCompatibility(mouseDownEvent)

        // Process ACTION_CANCEL
        val mouseCancelEvent =
            MotionEventBuilder(MotionEvent.ACTION_CANCEL, InputDevice.SOURCE_MOUSE)
                .pointer(PointerBuilder(0, MotionEvent.TOOL_TYPE_MOUSE).x(110f).y(220f))
                .buttonState(MotionEvent.BUTTON_PRIMARY)
                .build()
        val result = processor.processInputEventForCompatibility(mouseCancelEvent)

        assertThat(result, hasSize(1))
        assertThat(
            result!![0] as MotionEvent, allOf(
                withMotionAction(MotionEvent.ACTION_CANCEL),
                withSource(InputDevice.SOURCE_TOUCHSCREEN),
                withToolType(MotionEvent.TOOL_TYPE_FINGER),
                withCoords(PointF(110f, 220f)),
                withButtonState(0),
                withActionButton(0),
            )
        )
    }

    @Test
    fun processInputEventForCompatibilityDoesNotConvertDuringSecondaryClick() {
        val pointer = PointerBuilder(0, MotionEvent.TOOL_TYPE_MOUSE).x(100f).y(200f)

        // Process ACTION_DOWN
        val secondaryDownEvent =
            MotionEventBuilder(MotionEvent.ACTION_DOWN, InputDevice.SOURCE_MOUSE)
                .pointer(pointer)
                .buttonState(MotionEvent.BUTTON_SECONDARY)
                .build()
        val resultDown = processor.processInputEventForCompatibility(secondaryDownEvent)
        assertThat(resultDown, nullValue())

        // Process ACTION_BUTTON_PRESS
        val buttonPressEvent =
            MotionEventBuilder(MotionEvent.ACTION_BUTTON_PRESS, InputDevice.SOURCE_MOUSE)
                .pointer(pointer)
                .buttonState(MotionEvent.BUTTON_SECONDARY)
                .actionButton(MotionEvent.BUTTON_SECONDARY)
                .build()
        val pressResult = processor.processInputEventForCompatibility(buttonPressEvent)
        assertThat(pressResult, nullValue())

        // Process ACTION_MOVE
        pointer.x(110f).y(220f)
        val mouseMoveEvent =
            MotionEventBuilder(MotionEvent.ACTION_MOVE, InputDevice.SOURCE_MOUSE)
                .pointer(pointer)
                .buttonState(MotionEvent.BUTTON_SECONDARY)
                .build()
        val moveResult = processor.processInputEventForCompatibility(mouseMoveEvent)
        assertThat(moveResult, nullValue())

        // Process ACTION_BUTTON_RELEASE
        val buttonReleaseEvent =
            MotionEventBuilder(MotionEvent.ACTION_BUTTON_RELEASE, InputDevice.SOURCE_MOUSE)
                .pointer(pointer)
                .actionButton(MotionEvent.BUTTON_SECONDARY)
                .build()
        val releaseResult = processor.processInputEventForCompatibility(buttonReleaseEvent)
        assertThat(releaseResult, nullValue())

        // Process ACTION_UP
        val mouseUpEvent = MotionEventBuilder(MotionEvent.ACTION_UP, InputDevice.SOURCE_MOUSE)
            .pointer(pointer)
            .build()
        val upResult = processor.processInputEventForCompatibility(mouseUpEvent)
        assertThat(upResult, nullValue())
    }

    @Test
    fun processInputEventForCompatibilityConsumesBackButtonEvents() {
        val pointer = PointerBuilder(0, MotionEvent.TOOL_TYPE_MOUSE).x(100f).y(200f)

        // Process ACTION_BUTTON_PRESS
        val buttonPressEvent =
            MotionEventBuilder(MotionEvent.ACTION_BUTTON_PRESS, InputDevice.SOURCE_MOUSE)
                .pointer(pointer)
                .buttonState(MotionEvent.BUTTON_BACK)
                .actionButton(MotionEvent.BUTTON_BACK)
                .build()
        val pressResult = processor.processInputEventForCompatibility(buttonPressEvent)
        assertThat(pressResult, hasSize(0))

        // Process ACTION_MOVE with BACK button
        pointer.x(110f).y(220f)
        val mouseMoveEvent =
            MotionEventBuilder(MotionEvent.ACTION_HOVER_MOVE, InputDevice.SOURCE_MOUSE)
                .pointer(pointer)
                .buttonState(MotionEvent.BUTTON_BACK)
                .build()
        val hoverMoveResult = processor.processInputEventForCompatibility(mouseMoveEvent)
        assertThat(hoverMoveResult, hasSize(1))
        assertThat(
            hoverMoveResult!![0] as MotionEvent, allOf(
                withMotionAction(MotionEvent.ACTION_HOVER_MOVE),
                withSource(InputDevice.SOURCE_MOUSE),
                withToolType(MotionEvent.TOOL_TYPE_MOUSE),
                withButtonState(0),
                withActionButton(0),
            )
        )

        // Process ACTION_BUTTON_RELEASE
        val buttonReleaseEvent =
            MotionEventBuilder(MotionEvent.ACTION_BUTTON_RELEASE, InputDevice.SOURCE_MOUSE)
                .pointer(pointer)
                .actionButton(MotionEvent.BUTTON_BACK)
                .build()
        val releaseResult = processor.processInputEventForCompatibility(buttonReleaseEvent)
        assertThat(releaseResult, hasSize(0))
    }

    @Test
    fun processInputEventForCompatibilityConvertsMouseEventsWhileForwardPressed() {
        val pointer = PointerBuilder(0, MotionEvent.TOOL_TYPE_MOUSE).x(100f).y(200f)

        // Process ACTION_BUTTON_PRESS
        val forwardButtonPressEvent =
            MotionEventBuilder(MotionEvent.ACTION_BUTTON_PRESS, InputDevice.SOURCE_MOUSE)
                .pointer(pointer)
                .buttonState(MotionEvent.BUTTON_FORWARD)
                .actionButton(MotionEvent.BUTTON_FORWARD)
                .build()
        processor.processInputEventForCompatibility(forwardButtonPressEvent)

        // Process ACTION_DOWN with FORWARD pressed
        val mouseDownEvent = MotionEventBuilder(MotionEvent.ACTION_DOWN, InputDevice.SOURCE_MOUSE)
            .pointer(pointer)
            .buttonState(MotionEvent.BUTTON_PRIMARY or MotionEvent.BUTTON_FORWARD)
            .build()
        val downResult = processor.processInputEventForCompatibility(mouseDownEvent)

        assertThat(downResult, hasSize(1))
        assertThat(
            downResult!![0] as MotionEvent, allOf(
                withMotionAction(MotionEvent.ACTION_DOWN),
                withButtonState(0),
                withActionButton(0),
            )
        )

        // Process ACTION_BUTTON_PRESS with FORWARD pressed
        val buttonPressEvent =
            MotionEventBuilder(MotionEvent.ACTION_BUTTON_PRESS, InputDevice.SOURCE_MOUSE)
                .pointer(pointer)
                .buttonState(MotionEvent.BUTTON_PRIMARY or MotionEvent.BUTTON_FORWARD)
                .actionButton(MotionEvent.BUTTON_PRIMARY)
                .build()
        val pressResult = processor.processInputEventForCompatibility(buttonPressEvent)
        assertThat(pressResult, hasSize(0))

        pointer.x(110f).y(220f)
        // Process ACTION_MOVE with FORWARD pressed
        val mouseMoveEvent =
            MotionEventBuilder(MotionEvent.ACTION_MOVE, InputDevice.SOURCE_MOUSE)
                .pointer(pointer)
                .buttonState(MotionEvent.BUTTON_PRIMARY or MotionEvent.BUTTON_FORWARD)
                .build()
        val moveResult = processor.processInputEventForCompatibility(mouseMoveEvent)

        assertThat(moveResult, hasSize(1))
        assertThat(
            moveResult!![0] as MotionEvent, allOf(
                withMotionAction(MotionEvent.ACTION_MOVE),
                withButtonState(0),
                withActionButton(0),
            )
        )

        // Process ACTION_BUTTON_RELEASE with FORWARD pressed
        val buttonReleaseEvent =
            MotionEventBuilder(MotionEvent.ACTION_BUTTON_RELEASE, InputDevice.SOURCE_MOUSE)
                .pointer(pointer)
                .buttonState(MotionEvent.BUTTON_FORWARD)
                .actionButton(MotionEvent.BUTTON_PRIMARY)
                .build()
        val releaseResult = processor.processInputEventForCompatibility(buttonReleaseEvent)
        assertThat(releaseResult, hasSize(0))

        // Process ACTION_UP with FORWARD pressed
        val mouseUpEvent = MotionEventBuilder(MotionEvent.ACTION_UP, InputDevice.SOURCE_MOUSE)
            .pointer(pointer)
            .buttonState(MotionEvent.BUTTON_FORWARD)
            .build()
        val upResult = processor.processInputEventForCompatibility(mouseUpEvent)

        assertThat(upResult, hasSize(1))
        assertThat(
            upResult!![0] as MotionEvent, allOf(
                withMotionAction(MotionEvent.ACTION_UP),
                withButtonState(0),
                withActionButton(0),
            )
        )

        // Process ACTION_BUTTON_RELEASE
        val forwardButtonReleaseEvent =
            MotionEventBuilder(MotionEvent.ACTION_BUTTON_RELEASE, InputDevice.SOURCE_MOUSE)
                .pointer(pointer)
                .actionButton(MotionEvent.BUTTON_FORWARD)
                .build()
        processor.processInputEventForCompatibility(forwardButtonReleaseEvent)
    }

    @Test
    fun processInputEventForCompatibilityConvertsMousePrimaryButtonEventsThenSecondary() {
        val pointer = PointerBuilder(0, MotionEvent.TOOL_TYPE_MOUSE).x(100f).y(200f)

        // Process ACTION_DOWN
        val mouseDownEvent = MotionEventBuilder(MotionEvent.ACTION_DOWN, InputDevice.SOURCE_MOUSE)
            .pointer(pointer)
            .buttonState(MotionEvent.BUTTON_PRIMARY)
            .build()
        val downResult = processor.processInputEventForCompatibility(mouseDownEvent)
        assertThat(downResult, hasSize(1))

        // Process ACTION_BUTTON_PRESS
        val buttonPressEvent =
            MotionEventBuilder(MotionEvent.ACTION_BUTTON_PRESS, InputDevice.SOURCE_MOUSE)
                .pointer(pointer)
                .buttonState(MotionEvent.BUTTON_PRIMARY)
                .actionButton(MotionEvent.BUTTON_PRIMARY)
                .build()
        val pressResult = processor.processInputEventForCompatibility(buttonPressEvent)
        assertThat(pressResult, hasSize(0))

        // Process ACTION_BUTTON_PRESS of the secondary button
        val secondaryPressEvent =
            MotionEventBuilder(MotionEvent.ACTION_BUTTON_PRESS, InputDevice.SOURCE_MOUSE)
                .pointer(pointer)
                .buttonState(MotionEvent.BUTTON_PRIMARY or MotionEvent.BUTTON_SECONDARY)
                .actionButton(MotionEvent.BUTTON_SECONDARY)
                .build()
        val secondaryPressResult = processor.processInputEventForCompatibility(secondaryPressEvent)
        assertThat(secondaryPressResult, hasSize(0))

        // Process ACTION_MOVE with FORWARD pressed
        val mouseMoveEvent =
            MotionEventBuilder(MotionEvent.ACTION_MOVE, InputDevice.SOURCE_MOUSE)
                .pointer(pointer)
                .buttonState(MotionEvent.BUTTON_PRIMARY or MotionEvent.BUTTON_FORWARD)
                .build()
        val moveResult = processor.processInputEventForCompatibility(mouseMoveEvent)
        assertThat(moveResult, hasSize(1))
        assertThat(
            moveResult!![0] as MotionEvent, allOf(
                withMotionAction(MotionEvent.ACTION_MOVE),
                withButtonState(0),
            )
        )
        // Process ACTION_BUTTON_RELEASE of the primary button
        val releasePrimaryEvent =
            MotionEventBuilder(MotionEvent.ACTION_BUTTON_RELEASE, InputDevice.SOURCE_MOUSE)
                .pointer(pointer)
                .buttonState(MotionEvent.BUTTON_SECONDARY)
                .actionButton(MotionEvent.BUTTON_PRIMARY)
                .build()
        val releasePrimaryResult = processor.processInputEventForCompatibility(releasePrimaryEvent)
        assertThat(releasePrimaryResult, hasSize(0))

        // Process ACTION_MOVE with FORWARD pressed
        val mouseMoveEvent2 =
            MotionEventBuilder(MotionEvent.ACTION_MOVE, InputDevice.SOURCE_MOUSE)
                .pointer(pointer)
                .buttonState(MotionEvent.BUTTON_PRIMARY or MotionEvent.BUTTON_FORWARD)
                .build()
        val moveResult2 = processor.processInputEventForCompatibility(mouseMoveEvent2)
        assertThat(
            moveResult2!![0] as MotionEvent, allOf(
                withMotionAction(MotionEvent.ACTION_MOVE),
                withButtonState(0),
            )
        )

        // Process ACTION_BUTTON_RELEASE of the secondary button
        val releaseSecondaryEvent =
            MotionEventBuilder(MotionEvent.ACTION_BUTTON_RELEASE, InputDevice.SOURCE_MOUSE)
                .pointer(pointer)
                .actionButton(MotionEvent.BUTTON_SECONDARY)
                .build()
        val releaseSecondaryResult =
            processor.processInputEventForCompatibility(releaseSecondaryEvent)
        assertThat(releaseSecondaryResult, hasSize(0))

        // Process ACTION_UP
        val mouseUpEvent = MotionEventBuilder(MotionEvent.ACTION_UP, InputDevice.SOURCE_MOUSE)
            .pointer(pointer)
            .build()
        val upResult = processor.processInputEventForCompatibility(mouseUpEvent)
        assertThat(upResult, hasSize(1))
        assertThat(
            upResult!![0] as MotionEvent, allOf(
                withMotionAction(MotionEvent.ACTION_UP),
                withButtonState(0),
                withActionButton(0),
            )
        )
    }
}
