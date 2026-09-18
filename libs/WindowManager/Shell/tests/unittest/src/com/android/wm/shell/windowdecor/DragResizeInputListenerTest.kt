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
package com.android.wm.shell.windowdecor

import android.content.Context
import android.graphics.Rect
import android.hardware.input.InputManager
import android.os.Looper
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper
import android.util.Size
import android.view.Choreographer
import android.view.Display
import android.view.IWindowSession
import android.view.InputChannel
import android.view.InputDevice
import android.view.InputEventReceiver
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.SurfaceControl
import android.view.WindowInputChannelParams
import androidx.test.core.view.MotionEventBuilder
import androidx.test.core.view.PointerCoordsBuilder
import androidx.test.core.view.PointerPropertiesBuilder
import androidx.test.filters.SmallTest
import com.android.testing.wm.util.StubTransaction
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestHandler
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.google.common.truth.Truth.assertThat
import java.util.function.Consumer
import kotlin.test.assertNotNull
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [DragResizeInputListener].
 *
 * Build/Install/Run: atest WMShellUnitTests:DragResizeInputListenerTest
 */
@SmallTest
@TestableLooper.RunWithLooper
@RunWith(AndroidTestingRunner::class)
class DragResizeInputListenerTest : ShellTestCase() {
    private val testMainExecutor = TestShellExecutor()
    private val testBgExecutor = TestShellExecutor()
    private val mockWindowSession = mock<IWindowSession>()
    private val mockInputManager = mock<InputManager>()
    private val mockDragPositioningCallback = mock<DragPositioningCallback>()
    private val mockGeometry = mock<DragResizeWindowGeometry>()
    private val mockDisplayController = mock<DisplayController>()
    private val mockPreDragEventConduit = mock<Consumer<MotionEvent>>()
    private val inputChannelPairs = ArrayList<Array<InputChannel>>()
    private val mockOnInputEventReceiverDisposed = mock<Runnable>()
    private lateinit var receiver: InputEventReceiver
    private val decorationSurface = SurfaceControl.Builder().setName("decoration surface").build()
    private val createdSurfaces = ArrayList<SurfaceControl>()
    private val removedSurfaces = ArrayList<SurfaceControl>()

    @Before
    fun setUp() {
        whenever(
                mockWindowSession.grantInputChannel(any())
            )
            .thenAnswer {
                inputChannelPairs.add(
                    InputChannel.openInputChannelPair(
                        "${DragResizeInputListenerTest::class}#${inputChannelPairs.size}"
                    )
                )
                return@thenAnswer inputChannelPairs.last()[1]
            }

        if (Looper.myLooper() == null) {
            // Prepare a looper in the test thread, but we never call Looper.loop on it.
            Looper.prepare()
        }

        mContext.addMockSystemService(Context.INPUT_SERVICE, mockInputManager)
    }

    @After
    fun tearDown() {
        createdSurfaces.clear()
        removedSurfaces.clear()
        decorationSurface.release()

        inputChannelPairs.forEach { channels ->
            channels[0].dispose()
            channels[1].dispose()
        }
    }

    @Test
    fun testGrantInputChannelOffMainThread() {
        create()
        testMainExecutor.flushAll()

        verifyNoInputChannelGrantRequests()
    }

    @Test
    fun testGrantInputChannelAfterDecorSurfaceReleased() {
        // Keep tracking the underlying surface that the decorationSurface points to.
        val forVerification = SurfaceControl(decorationSurface, "forVerification")
        try {
            create()
            decorationSurface.release()
            testBgExecutor.flushAll()

            val paramsCaptor = argumentCaptor<WindowInputChannelParams>()

            // This is called for both the decoration & input sink
            verify(mockWindowSession, times(2)).grantInputChannel(paramsCaptor.capture())

            val surface = paramsCaptor.firstValue.surface
            assertThat(surface.isValid).isTrue()
            assertThat(surface.isSameSurface(forVerification)).isTrue()
        } finally {
            forVerification.release()
        }
    }

    @Test
    fun testInitializationCallback_waitsForBgSetup() {
        val inputListener = create()

        val callback = TestInitializationCallback()
        inputListener.addInitializedCallback(callback)
        assertThat(callback.initialized).isFalse()

        testBgExecutor.flushAll()
        testMainExecutor.flushAll()

        assertThat(callback.initialized).isTrue()
    }

    @Test
    fun testInitializationCallback_alreadyInitialized_callsBackImmediately() {
        val inputListener = create()
        testBgExecutor.flushAll()
        testMainExecutor.flushAll()

        val callback = TestInitializationCallback()
        inputListener.addInitializedCallback(callback)

        assertThat(callback.initialized).isTrue()
    }

    @Test
    fun testClose_beforeBgSetup_cancelsBgSetup() {
        val inputListener = create()

        inputListener.close()
        testBgExecutor.flushAll()

        verifyNoInputChannelGrantRequests()
    }

    @Test
    fun testClose_beforeBgSetupResultSet_cancelsInit() {
        val inputListener = create()
        val callback = TestInitializationCallback()
        inputListener.addInitializedCallback(callback)

        testBgExecutor.flushAll()
        inputListener.close()
        testMainExecutor.flushAll()

        assertThat(callback.initialized).isFalse()
    }

    @Test
    fun testClose_afterInit_disposesOfReceiver() {
        val inputListener = create()

        testBgExecutor.flushAll()
        testMainExecutor.flushAll()
        inputListener.close()

        verify(mockOnInputEventReceiverDisposed).run()
    }

    @Test
    fun testClose_afterInit_removesTokens() {
        val inputListener = create()

        inputListener.close()
        testBgExecutor.flushAll()

        verify(mockWindowSession).remove(inputListener.mClientToken)
        verify(mockWindowSession).remove(inputListener.mSinkClientToken)
    }

    @Test
    fun testClose_afterBgSetup_disposesOfInputChannels() {
        val inputListener = create()
        testBgExecutor.flushAll()
        inputListener.close()
        testMainExecutor.flushAll()

        assertThat(inputChannelPairs.size).isEqualTo(2)
        try {
            inputChannelPairs[0][1].token
            fail("InputChannel should have been disposed")
        } catch (_: Exception) {
            // Expected
        }
        try {
            inputChannelPairs[1][1].token
            fail("InputChannel should have been disposed")
        } catch (_: Exception) {
            // Expected
        }
    }

    @Test
    fun testClose_beforeBgSetup_releaseSurfaces() {
        val inputListener = create()
        inputListener.close()
        testBgExecutor.flushAll()
        testMainExecutor.flushAll()

        assertThat(createdSurfaces).hasSize(1)
        assertThat(createdSurfaces[0].isValid).isFalse()
    }

    @Test
    fun testClose_afterBgSetup_releaseSurfaces() {
        val inputListener = create()
        testBgExecutor.flushAll()
        inputListener.close()
        testMainExecutor.flushAll()
        testBgExecutor.flushAll()

        assertThat(createdSurfaces).hasSize(2)
        assertThat(createdSurfaces[0].isValid).isFalse()
        assertThat(createdSurfaces[1].isValid).isFalse()
    }

    @Test
    fun testClose_releasesDecorationSurfaceWithoutRemoval() {
        val inputListener = create()
        testBgExecutor.flushAll()
        inputListener.close()
        testMainExecutor.flushAll()
        testBgExecutor.flushAll()

        val decorationSurface = assertNotNull(createdSurfaces[0])
        assertThat(decorationSurface.isValid).isFalse()
        assertThat(removedSurfaces.contains(decorationSurface)).isFalse()
    }

    @Test
    fun testDispatchNonDraggingEventsToPreDragEventConduit() {
        val inputListener = create()
        testBgExecutor.flushAll()
        testMainExecutor.flushAll()

        whenever(
                mockDragPositioningCallback.onDragPositioningStart(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            )
            .thenReturn(Rect(0, 0, TASK_WIDTH, TASK_HEIGHT))
        whenever(mockDragPositioningCallback.onDragPositioningMove(eq(DISPLAY_ID), any(), any()))
            .thenReturn(Rect(0, 0, TASK_WIDTH, TASK_HEIGHT))
        whenever(mockDragPositioningCallback.onDragPositioningEnd(eq(DISPLAY_ID), any(), any()))
            .thenReturn(Rect(0, 0, TASK_WIDTH, TASK_HEIGHT))

        val mockDisplayLayout = mock<DisplayLayout>()
        whenever(mockDisplayLayout.width()).thenReturn(DISPLAY_WIDTH)
        whenever(mockDisplayLayout.height()).thenReturn(DISPLAY_HEIGHT)
        whenever(mockDisplayController.getDisplayLayout(DISPLAY_ID)).thenReturn(mockDisplayLayout)

        whenever(mockGeometry.taskSize).thenReturn(Size(TASK_WIDTH, TASK_HEIGHT))
        whenever(mockGeometry.shouldHandleEvent(any(), any())).thenReturn(true)
        inputListener.setGeometry(mockGeometry, 5 /* touchSlop */)

        val pointerCoordsOfDowns = ArrayList<MotionEvent.PointerCoords>()
        val pointerCoordsOfMoves = ArrayList<MotionEvent.PointerCoords>()
        val pointerCoordsOfUps = ArrayList<MotionEvent.PointerCoords>()
        val pointerCoordsOfCancels = ArrayList<MotionEvent.PointerCoords>()
        whenever(mockPreDragEventConduit.accept(any())).thenAnswer { invocation ->
            val event = invocation.arguments[0] as MotionEvent
            val pointerCoordsArray =
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> pointerCoordsOfDowns
                    MotionEvent.ACTION_MOVE -> pointerCoordsOfMoves
                    MotionEvent.ACTION_UP -> pointerCoordsOfUps
                    MotionEvent.ACTION_CANCEL -> pointerCoordsOfCancels
                    else -> throw IllegalStateException("Unknown event $event")
                }
            pointerCoordsArray.add(MotionEvent.PointerCoords())
            event.getPointerCoords(0, pointerCoordsArray[pointerCoordsArray.size - 1])
        }

        val down =
            MotionEventBuilder.newBuilder()
                .setAction(MotionEvent.ACTION_DOWN)
                .setSource(InputDevice.SOURCE_MOUSE)
                .setDeviceId(DEVICE_ID)
                .setActionIndex(0)
                .setPointer(
                    PointerPropertiesBuilder.newBuilder()
                        .setId(POINTER_ID)
                        .setToolType(MotionEvent.TOOL_TYPE_MOUSE)
                        .build(),
                    PointerCoordsBuilder.newBuilder().setCoords(X, Y).build(),
                )
                .build()
        receiver.onInputEvent(down)
        assertThat(pointerCoordsOfDowns.size).isEqualTo(1)
        assertThat(pointerCoordsOfDowns[0].y).isEqualTo(Y)
        assertThat(pointerCoordsOfMoves.size).isEqualTo(0)
        assertThat(pointerCoordsOfUps.size).isEqualTo(0)
        assertThat(pointerCoordsOfCancels.size).isEqualTo(0)

        val move =
            MotionEventBuilder.newBuilder()
                .setAction(MotionEvent.ACTION_MOVE)
                .setSource(InputDevice.SOURCE_MOUSE)
                .setDeviceId(DEVICE_ID)
                .setActionIndex(0)
                .setPointer(
                    PointerPropertiesBuilder.newBuilder()
                        .setId(POINTER_ID)
                        .setToolType(MotionEvent.TOOL_TYPE_MOUSE)
                        .build(),
                    PointerCoordsBuilder.newBuilder().setCoords(X, Y + 3).build(), // within slop
                )
                .build()
        receiver.onInputEvent(move)
        assertThat(pointerCoordsOfDowns.size).isEqualTo(1)
        assertThat(pointerCoordsOfMoves.size).isEqualTo(1)
        assertThat(pointerCoordsOfMoves[0].y).isEqualTo(Y + 3)
        assertThat(pointerCoordsOfUps.size).isEqualTo(0)
        assertThat(pointerCoordsOfCancels.size).isEqualTo(0)

        val up =
            MotionEventBuilder.newBuilder()
                .setAction(MotionEvent.ACTION_UP)
                .setSource(InputDevice.SOURCE_MOUSE)
                .setDeviceId(DEVICE_ID)
                .setActionIndex(0)
                .setPointer(
                    PointerPropertiesBuilder.newBuilder()
                        .setId(POINTER_ID)
                        .setToolType(MotionEvent.TOOL_TYPE_MOUSE)
                        .build(),
                    PointerCoordsBuilder.newBuilder().setCoords(X, Y + 3).build(), // within slop
                )
                .build()
        receiver.onInputEvent(up)
        assertThat(pointerCoordsOfDowns.size).isEqualTo(1)
        assertThat(pointerCoordsOfMoves.size).isEqualTo(1)
        assertThat(pointerCoordsOfUps.size).isEqualTo(1)
        assertThat(pointerCoordsOfUps[0].y).isEqualTo(Y + 3)
        assertThat(pointerCoordsOfCancels.size).isEqualTo(0)
    }

    @Test
    fun testDispatchNonDraggingEventsToPreDragEventConduit_cancelledGesture() {
        val inputListener = create()
        testBgExecutor.flushAll()
        testMainExecutor.flushAll()

        whenever(
                mockDragPositioningCallback.onDragPositioningStart(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            )
            .thenReturn(Rect(0, 0, TASK_WIDTH, TASK_HEIGHT))
        whenever(mockDragPositioningCallback.onDragPositioningMove(eq(DISPLAY_ID), any(), any()))
            .thenReturn(Rect(0, 0, TASK_WIDTH, TASK_HEIGHT))
        whenever(mockDragPositioningCallback.onDragPositioningEnd(eq(DISPLAY_ID), any(), any()))
            .thenReturn(Rect(0, 0, TASK_WIDTH, TASK_HEIGHT))

        val mockDisplayLayout = mock<DisplayLayout>()
        whenever(mockDisplayLayout.width()).thenReturn(DISPLAY_WIDTH)
        whenever(mockDisplayLayout.height()).thenReturn(DISPLAY_HEIGHT)
        whenever(mockDisplayController.getDisplayLayout(DISPLAY_ID)).thenReturn(mockDisplayLayout)

        whenever(mockGeometry.taskSize).thenReturn(Size(TASK_WIDTH, TASK_HEIGHT))
        whenever(mockGeometry.shouldHandleEvent(any(), any())).thenReturn(true)
        inputListener.setGeometry(mockGeometry, 5 /* touchSlop */)

        val pointerCoordsOfDowns = ArrayList<MotionEvent.PointerCoords>()
        val pointerCoordsOfMoves = ArrayList<MotionEvent.PointerCoords>()
        val pointerCoordsOfUps = ArrayList<MotionEvent.PointerCoords>()
        val pointerCoordsOfCancels = ArrayList<MotionEvent.PointerCoords>()
        whenever(mockPreDragEventConduit.accept(any())).thenAnswer { invocation ->
            val event = invocation.arguments[0] as MotionEvent
            val pointerCoordsArray =
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> pointerCoordsOfDowns
                    MotionEvent.ACTION_MOVE -> pointerCoordsOfMoves
                    MotionEvent.ACTION_UP -> pointerCoordsOfUps
                    MotionEvent.ACTION_CANCEL -> pointerCoordsOfCancels
                    else -> throw IllegalStateException("Unknown event $event")
                }
            pointerCoordsArray.add(MotionEvent.PointerCoords())
            event.getPointerCoords(0, pointerCoordsArray[pointerCoordsArray.size - 1])
        }

        val down =
            MotionEventBuilder.newBuilder()
                .setAction(MotionEvent.ACTION_DOWN)
                .setSource(InputDevice.SOURCE_MOUSE)
                .setDeviceId(DEVICE_ID)
                .setActionIndex(0)
                .setPointer(
                    PointerPropertiesBuilder.newBuilder()
                        .setId(POINTER_ID)
                        .setToolType(MotionEvent.TOOL_TYPE_MOUSE)
                        .build(),
                    PointerCoordsBuilder.newBuilder().setCoords(X, Y).build(),
                )
                .build()
        receiver.onInputEvent(down)
        assertThat(pointerCoordsOfDowns.size).isEqualTo(1)
        assertThat(pointerCoordsOfDowns[0].y).isEqualTo(Y)
        assertThat(pointerCoordsOfMoves.size).isEqualTo(0)
        assertThat(pointerCoordsOfUps.size).isEqualTo(0)
        assertThat(pointerCoordsOfCancels.size).isEqualTo(0)

        val move =
            MotionEventBuilder.newBuilder()
                .setAction(MotionEvent.ACTION_MOVE)
                .setSource(InputDevice.SOURCE_MOUSE)
                .setDeviceId(DEVICE_ID)
                .setActionIndex(0)
                .setPointer(
                    PointerPropertiesBuilder.newBuilder()
                        .setId(POINTER_ID)
                        .setToolType(MotionEvent.TOOL_TYPE_MOUSE)
                        .build(),
                    PointerCoordsBuilder.newBuilder().setCoords(X, Y + 3).build(), // within slop
                )
                .build()
        receiver.onInputEvent(move)
        assertThat(pointerCoordsOfDowns.size).isEqualTo(1)
        assertThat(pointerCoordsOfMoves.size).isEqualTo(1)
        assertThat(pointerCoordsOfMoves[0].y).isEqualTo(Y + 3)
        assertThat(pointerCoordsOfUps.size).isEqualTo(0)
        assertThat(pointerCoordsOfCancels.size).isEqualTo(0)

        val cancel =
            MotionEventBuilder.newBuilder()
                .setAction(MotionEvent.ACTION_CANCEL)
                .setSource(InputDevice.SOURCE_MOUSE)
                .setDeviceId(DEVICE_ID)
                .setActionIndex(0)
                .setPointer(
                    PointerPropertiesBuilder.newBuilder()
                        .setId(POINTER_ID)
                        .setToolType(MotionEvent.TOOL_TYPE_MOUSE)
                        .build(),
                    PointerCoordsBuilder.newBuilder().setCoords(X, Y + 3).build(), // within slop
                )
                .build()
        receiver.onInputEvent(cancel)
        assertThat(pointerCoordsOfDowns.size).isEqualTo(1)
        assertThat(pointerCoordsOfMoves.size).isEqualTo(1)
        assertThat(pointerCoordsOfUps.size).isEqualTo(0)
        assertThat(pointerCoordsOfCancels.size).isEqualTo(1)
        assertThat(pointerCoordsOfCancels[0].y).isEqualTo(Y + 3)
    }

    @Test
    fun testCancelDraggingEventsToPreDragEventConduit() {
        val inputListener = create()
        testBgExecutor.flushAll()
        testMainExecutor.flushAll()

        whenever(
                mockDragPositioningCallback.onDragPositioningStart(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            )
            .thenReturn(Rect(0, 0, TASK_WIDTH, TASK_HEIGHT))
        whenever(mockDragPositioningCallback.onDragPositioningMove(eq(DISPLAY_ID), any(), any()))
            .thenReturn(Rect(0, 0, TASK_WIDTH, TASK_HEIGHT))
        whenever(mockDragPositioningCallback.onDragPositioningEnd(eq(DISPLAY_ID), any(), any()))
            .thenReturn(Rect(0, 0, TASK_WIDTH, TASK_HEIGHT))

        val mockDisplayLayout = mock<DisplayLayout>()
        whenever(mockDisplayLayout.width()).thenReturn(DISPLAY_WIDTH)
        whenever(mockDisplayLayout.height()).thenReturn(DISPLAY_HEIGHT)
        whenever(mockDisplayController.getDisplayLayout(DISPLAY_ID)).thenReturn(mockDisplayLayout)

        whenever(mockGeometry.taskSize).thenReturn(Size(TASK_WIDTH, TASK_HEIGHT))
        whenever(mockGeometry.shouldHandleEvent(any(), any())).thenReturn(true)
        inputListener.setGeometry(mockGeometry, 5 /* touchSlop */)

        val pointerCoordsOfDowns = ArrayList<MotionEvent.PointerCoords>()
        val pointerCoordsOfMoves = ArrayList<MotionEvent.PointerCoords>()
        val pointerCoordsOfUps = ArrayList<MotionEvent.PointerCoords>()
        val pointerCoordsOfCancels = ArrayList<MotionEvent.PointerCoords>()
        whenever(mockPreDragEventConduit.accept(any())).thenAnswer { invocation ->
            val event = invocation.arguments[0] as MotionEvent
            val pointerCoordsArray =
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> pointerCoordsOfDowns
                    MotionEvent.ACTION_MOVE -> pointerCoordsOfMoves
                    MotionEvent.ACTION_UP -> pointerCoordsOfUps
                    MotionEvent.ACTION_CANCEL -> pointerCoordsOfCancels
                    else -> throw IllegalStateException("Unknown event $event")
                }
            pointerCoordsArray.add(MotionEvent.PointerCoords())
            event.getPointerCoords(0, pointerCoordsArray[pointerCoordsArray.size - 1])
        }

        val down =
            MotionEventBuilder.newBuilder()
                .setAction(MotionEvent.ACTION_DOWN)
                .setSource(InputDevice.SOURCE_MOUSE)
                .setDeviceId(DEVICE_ID)
                .setActionIndex(0)
                .setPointer(
                    PointerPropertiesBuilder.newBuilder()
                        .setId(POINTER_ID)
                        .setToolType(MotionEvent.TOOL_TYPE_MOUSE)
                        .build(),
                    PointerCoordsBuilder.newBuilder().setCoords(X, Y).build(),
                )
                .build()
        receiver.onInputEvent(down)
        assertThat(pointerCoordsOfDowns.size).isEqualTo(1)
        assertThat(pointerCoordsOfDowns[0].y).isEqualTo(Y)
        assertThat(pointerCoordsOfMoves.size).isEqualTo(0)
        assertThat(pointerCoordsOfUps.size).isEqualTo(0)
        assertThat(pointerCoordsOfCancels.size).isEqualTo(0)

        val move =
            MotionEventBuilder.newBuilder()
                .setAction(MotionEvent.ACTION_MOVE)
                .setSource(InputDevice.SOURCE_MOUSE)
                .setDeviceId(DEVICE_ID)
                .setActionIndex(0)
                .setPointer(
                    PointerPropertiesBuilder.newBuilder()
                        .setId(POINTER_ID)
                        .setToolType(MotionEvent.TOOL_TYPE_MOUSE)
                        .build(),
                    PointerCoordsBuilder.newBuilder().setCoords(X, Y + 3).build(), // within slop
                )
                .build()
        receiver.onInputEvent(move)
        assertThat(pointerCoordsOfDowns.size).isEqualTo(1)
        assertThat(pointerCoordsOfMoves.size).isEqualTo(1)
        assertThat(pointerCoordsOfMoves[0].y).isEqualTo(Y + 3)
        assertThat(pointerCoordsOfUps.size).isEqualTo(0)
        assertThat(pointerCoordsOfCancels.size).isEqualTo(0)

        val move2 =
            MotionEventBuilder.newBuilder()
                .setAction(MotionEvent.ACTION_MOVE)
                .setSource(InputDevice.SOURCE_MOUSE)
                .setDeviceId(DEVICE_ID)
                .setActionIndex(0)
                .setPointer(
                    PointerPropertiesBuilder.newBuilder()
                        .setId(POINTER_ID)
                        .setToolType(MotionEvent.TOOL_TYPE_MOUSE)
                        .build(),
                    PointerCoordsBuilder.newBuilder().setCoords(X, Y + 10).build(), // out of slop
                )
                .build()
        receiver.onInputEvent(move2)
        assertThat(pointerCoordsOfDowns.size).isEqualTo(1)
        assertThat(pointerCoordsOfMoves.size).isEqualTo(1)
        assertThat(pointerCoordsOfUps.size).isEqualTo(0)
        assertThat(pointerCoordsOfCancels.size).isEqualTo(1)
        assertThat(pointerCoordsOfCancels[0].y).isEqualTo(Y + 10)

        val move3 =
            MotionEventBuilder.newBuilder()
                .setAction(MotionEvent.ACTION_MOVE)
                .setSource(InputDevice.SOURCE_MOUSE)
                .setDeviceId(DEVICE_ID)
                .setActionIndex(0)
                .setPointer(
                    PointerPropertiesBuilder.newBuilder()
                        .setId(POINTER_ID)
                        .setToolType(MotionEvent.TOOL_TYPE_MOUSE)
                        .build(),
                    PointerCoordsBuilder.newBuilder()
                        .setCoords(X, Y + 4)
                        .build(), // within slop again
                )
                .build()
        receiver.onInputEvent(move3)
        assertThat(pointerCoordsOfDowns.size).isEqualTo(1)
        assertThat(pointerCoordsOfMoves.size).isEqualTo(1)
        assertThat(pointerCoordsOfUps.size).isEqualTo(0)
        assertThat(pointerCoordsOfCancels.size).isEqualTo(1)

        val up =
            MotionEventBuilder.newBuilder()
                .setAction(MotionEvent.ACTION_UP)
                .setSource(InputDevice.SOURCE_MOUSE)
                .setDeviceId(DEVICE_ID)
                .setActionIndex(0)
                .setPointer(
                    PointerPropertiesBuilder.newBuilder()
                        .setId(POINTER_ID)
                        .setToolType(MotionEvent.TOOL_TYPE_MOUSE)
                        .build(),
                    PointerCoordsBuilder.newBuilder().setCoords(X, Y + 3).build(), // within slop
                )
                .build()
        receiver.onInputEvent(up)
        assertThat(pointerCoordsOfDowns.size).isEqualTo(1)
        assertThat(pointerCoordsOfMoves.size).isEqualTo(1)
        assertThat(pointerCoordsOfUps.size).isEqualTo(0)
        assertThat(pointerCoordsOfCancels.size).isEqualTo(1)
    }

    @Test
    fun testSkipUpdatingCursorIcon_nonPointingDevice() {
        val inputListener = create()
        testBgExecutor.flushAll()
        testMainExecutor.flushAll()

        whenever(mockGeometry.taskSize).thenReturn(Size(300, 200))
        inputListener.setGeometry(mockGeometry, 5 /* touchSlop */)

        whenever(
                mockGeometry.calculateCtrlType(
                    false /* isTouchscreen */,
                    true /* isEdgeResizePermitted */,
                    X,
                    Y,
                )
            )
            .thenReturn(DragPositioningCallback.CTRL_TYPE_LEFT)

        val hoverEnter =
            MotionEventBuilder.newBuilder()
                .setAction(MotionEvent.ACTION_HOVER_ENTER)
                .setSource(InputDevice.SOURCE_JOYSTICK)
                .setDeviceId(DEVICE_ID)
                .setActionIndex(0)
                .setPointer(
                    PointerPropertiesBuilder.newBuilder()
                        .setId(POINTER_ID)
                        .setToolType(MotionEvent.TOOL_TYPE_FINGER)
                        .build(),
                    PointerCoordsBuilder.newBuilder().setCoords(X, Y).build(),
                )
                .build()

        receiver.onInputEvent(hoverEnter)

        verify(mockInputManager, never()).setPointerIcon(any(), any(), any(), any(), any())
    }

    @Test
    fun testSkipUpdatingCursorIcon_stylus() {
        val inputListener = create()
        testBgExecutor.flushAll()
        testMainExecutor.flushAll()

        whenever(mockGeometry.taskSize).thenReturn(Size(300, 200))
        inputListener.setGeometry(mockGeometry, 5 /* touchSlop */)

        whenever(
                mockGeometry.calculateCtrlType(
                    false /* isTouchscreen */,
                    true /* isEdgeResizePermitted */,
                    X,
                    Y,
                )
            )
            .thenReturn(DragPositioningCallback.CTRL_TYPE_LEFT)

        val hoverEnter =
            MotionEventBuilder.newBuilder()
                .setAction(MotionEvent.ACTION_HOVER_ENTER)
                .setSource(InputDevice.SOURCE_MOUSE)
                .setDeviceId(DEVICE_ID)
                .setActionIndex(0)
                .setPointer(
                    PointerPropertiesBuilder.newBuilder()
                        .setId(POINTER_ID)
                        .setToolType(MotionEvent.TOOL_TYPE_STYLUS)
                        .build(),
                    PointerCoordsBuilder.newBuilder().setCoords(X, Y).build(),
                )
                .build()

        receiver.onInputEvent(hoverEnter)

        verify(mockInputManager, never()).setPointerIcon(any(), any(), any(), any(), any())
    }

    @Test
    fun testUpdateCursorIcon_leftEdge() {
        val inputListener = create()
        testBgExecutor.flushAll()
        testMainExecutor.flushAll()

        whenever(mockGeometry.taskSize).thenReturn(Size(300, 200))
        inputListener.setGeometry(mockGeometry, 5 /* touchSlop */)

        whenever(
                mockGeometry.calculateCtrlType(
                    false /* isTouchscreen */,
                    true /* isEdgeResizePermitted */,
                    X,
                    Y,
                )
            )
            .thenReturn(DragPositioningCallback.CTRL_TYPE_LEFT)

        val hoverEnter =
            MotionEventBuilder.newBuilder()
                .setAction(MotionEvent.ACTION_HOVER_ENTER)
                .setSource(InputDevice.SOURCE_MOUSE)
                .setDeviceId(DEVICE_ID)
                .setActionIndex(0)
                .setPointer(
                    PointerPropertiesBuilder.newBuilder()
                        .setId(POINTER_ID)
                        .setToolType(MotionEvent.TOOL_TYPE_MOUSE)
                        .build(),
                    PointerCoordsBuilder.newBuilder().setCoords(X, Y).build(),
                )
                .build()

        receiver.onInputEvent(hoverEnter)

        val iconCaptor = ArgumentCaptor.forClass(PointerIcon::class.java)
        verify(mockInputManager)
            .setPointerIcon(
                iconCaptor.capture(),
                eq(DISPLAY_ID),
                eq(DEVICE_ID),
                eq(POINTER_ID),
                any(),
            )
        assertThat(iconCaptor.value.type).isEqualTo(PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW)
    }

    @Test
    fun testUpdateCursorIcon_rightEdge() {
        val inputListener = create()
        testBgExecutor.flushAll()
        testMainExecutor.flushAll()

        whenever(mockGeometry.taskSize).thenReturn(Size(300, 200))
        inputListener.setGeometry(mockGeometry, 5 /* touchSlop */)

        whenever(
                mockGeometry.calculateCtrlType(
                    false /* isTouchscreen */,
                    true /* isEdgeResizePermitted */,
                    X,
                    Y,
                )
            )
            .thenReturn(DragPositioningCallback.CTRL_TYPE_RIGHT)

        val hoverEnter =
            MotionEventBuilder.newBuilder()
                .setAction(MotionEvent.ACTION_HOVER_ENTER)
                .setSource(InputDevice.SOURCE_MOUSE)
                .setDeviceId(DEVICE_ID)
                .setActionIndex(0)
                .setPointer(
                    PointerPropertiesBuilder.newBuilder()
                        .setId(POINTER_ID)
                        .setToolType(MotionEvent.TOOL_TYPE_FINGER)
                        .build(),
                    PointerCoordsBuilder.newBuilder().setCoords(X, Y).build(),
                )
                .build()

        receiver.onInputEvent(hoverEnter)

        val iconCaptor = ArgumentCaptor.forClass(PointerIcon::class.java)
        verify(mockInputManager)
            .setPointerIcon(
                iconCaptor.capture(),
                eq(DISPLAY_ID),
                eq(DEVICE_ID),
                eq(POINTER_ID),
                any(),
            )
        assertThat(iconCaptor.value.type).isEqualTo(PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW)
    }

    @Test
    fun testUpdateCursorIcon_topEdge() {
        val inputListener = create()
        testBgExecutor.flushAll()
        testMainExecutor.flushAll()

        whenever(mockGeometry.taskSize).thenReturn(Size(300, 200))
        inputListener.setGeometry(mockGeometry, 5 /* touchSlop */)

        whenever(
                mockGeometry.calculateCtrlType(
                    false /* isTouchscreen */,
                    true /* isEdgeResizePermitted */,
                    X,
                    Y,
                )
            )
            .thenReturn(DragPositioningCallback.CTRL_TYPE_TOP)

        val hoverEnter =
            MotionEventBuilder.newBuilder()
                .setAction(MotionEvent.ACTION_HOVER_ENTER)
                .setSource(InputDevice.SOURCE_MOUSE)
                .setDeviceId(DEVICE_ID)
                .setActionIndex(0)
                .setPointer(
                    PointerPropertiesBuilder.newBuilder()
                        .setId(POINTER_ID)
                        .setToolType(MotionEvent.TOOL_TYPE_MOUSE)
                        .build(),
                    PointerCoordsBuilder.newBuilder().setCoords(X, Y).build(),
                )
                .build()

        receiver.onInputEvent(hoverEnter)

        val iconCaptor = ArgumentCaptor.forClass(PointerIcon::class.java)
        verify(mockInputManager)
            .setPointerIcon(
                iconCaptor.capture(),
                eq(DISPLAY_ID),
                eq(DEVICE_ID),
                eq(POINTER_ID),
                any(),
            )
        assertThat(iconCaptor.value.type).isEqualTo(PointerIcon.TYPE_VERTICAL_DOUBLE_ARROW)
    }

    @Test
    fun testUpdateCursorIcon_bottomEdge() {
        val inputListener = create()
        testBgExecutor.flushAll()
        testMainExecutor.flushAll()

        whenever(mockGeometry.taskSize).thenReturn(Size(300, 200))
        inputListener.setGeometry(mockGeometry, 5 /* touchSlop */)

        whenever(
                mockGeometry.calculateCtrlType(
                    false /* isTouchscreen */,
                    true /* isEdgeResizePermitted */,
                    X,
                    Y,
                )
            )
            .thenReturn(DragPositioningCallback.CTRL_TYPE_BOTTOM)

        val hoverEnter =
            MotionEventBuilder.newBuilder()
                .setAction(MotionEvent.ACTION_HOVER_ENTER)
                .setSource(InputDevice.SOURCE_MOUSE)
                .setDeviceId(DEVICE_ID)
                .setActionIndex(0)
                .setPointer(
                    PointerPropertiesBuilder.newBuilder()
                        .setId(POINTER_ID)
                        .setToolType(MotionEvent.TOOL_TYPE_MOUSE)
                        .build(),
                    PointerCoordsBuilder.newBuilder().setCoords(X, Y).build(),
                )
                .build()

        receiver.onInputEvent(hoverEnter)

        val iconCaptor = ArgumentCaptor.forClass(PointerIcon::class.java)
        verify(mockInputManager)
            .setPointerIcon(
                iconCaptor.capture(),
                eq(DISPLAY_ID),
                eq(DEVICE_ID),
                eq(POINTER_ID),
                any(),
            )
        assertThat(iconCaptor.value.type).isEqualTo(PointerIcon.TYPE_VERTICAL_DOUBLE_ARROW)
    }

    @Test
    fun testUpdateCursorIcon_topLeftCorner() {
        val inputListener = create()
        testBgExecutor.flushAll()
        testMainExecutor.flushAll()

        whenever(mockGeometry.taskSize).thenReturn(Size(300, 200))
        inputListener.setGeometry(mockGeometry, 5 /* touchSlop */)

        whenever(
                mockGeometry.calculateCtrlType(
                    false /* isTouchscreen */,
                    true /* isEdgeResizePermitted */,
                    X,
                    Y,
                )
            )
            .thenReturn(
                DragPositioningCallback.CTRL_TYPE_TOP or DragPositioningCallback.CTRL_TYPE_LEFT
            )

        val hoverEnter =
            MotionEventBuilder.newBuilder()
                .setAction(MotionEvent.ACTION_HOVER_ENTER)
                .setSource(InputDevice.SOURCE_MOUSE)
                .setDeviceId(DEVICE_ID)
                .setActionIndex(0)
                .setPointer(
                    PointerPropertiesBuilder.newBuilder()
                        .setId(POINTER_ID)
                        .setToolType(MotionEvent.TOOL_TYPE_MOUSE)
                        .build(),
                    PointerCoordsBuilder.newBuilder().setCoords(X, Y).build(),
                )
                .build()

        receiver.onInputEvent(hoverEnter)

        val iconCaptor = ArgumentCaptor.forClass(PointerIcon::class.java)
        verify(mockInputManager)
            .setPointerIcon(
                iconCaptor.capture(),
                eq(DISPLAY_ID),
                eq(DEVICE_ID),
                eq(POINTER_ID),
                any(),
            )
        assertThat(iconCaptor.value.type).isEqualTo(PointerIcon.TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW)
    }

    @Test
    fun testUpdateCursorIcon_bottomRightCorner() {
        val inputListener = create()
        testBgExecutor.flushAll()
        testMainExecutor.flushAll()

        whenever(mockGeometry.taskSize).thenReturn(Size(300, 200))
        inputListener.setGeometry(mockGeometry, 5 /* touchSlop */)

        whenever(
                mockGeometry.calculateCtrlType(
                    false /* isTouchscreen */,
                    true /* isEdgeResizePermitted */,
                    X,
                    Y,
                )
            )
            .thenReturn(
                DragPositioningCallback.CTRL_TYPE_BOTTOM or DragPositioningCallback.CTRL_TYPE_RIGHT
            )

        val hoverEnter =
            MotionEventBuilder.newBuilder()
                .setAction(MotionEvent.ACTION_HOVER_ENTER)
                .setSource(InputDevice.SOURCE_MOUSE)
                .setDeviceId(DEVICE_ID)
                .setActionIndex(0)
                .setPointer(
                    PointerPropertiesBuilder.newBuilder()
                        .setId(POINTER_ID)
                        .setToolType(MotionEvent.TOOL_TYPE_MOUSE)
                        .build(),
                    PointerCoordsBuilder.newBuilder().setCoords(X, Y).build(),
                )
                .build()

        receiver.onInputEvent(hoverEnter)

        val iconCaptor = ArgumentCaptor.forClass(PointerIcon::class.java)
        verify(mockInputManager)
            .setPointerIcon(
                iconCaptor.capture(),
                eq(DISPLAY_ID),
                eq(DEVICE_ID),
                eq(POINTER_ID),
                any(),
            )
        assertThat(iconCaptor.value.type).isEqualTo(PointerIcon.TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW)
    }

    @Test
    fun testUpdateCursorIcon_topRightCorner() {
        val inputListener = create()
        testBgExecutor.flushAll()
        testMainExecutor.flushAll()

        whenever(mockGeometry.taskSize).thenReturn(Size(300, 200))
        inputListener.setGeometry(mockGeometry, 5 /* touchSlop */)

        whenever(
                mockGeometry.calculateCtrlType(
                    false /* isTouchscreen */,
                    true /* isEdgeResizePermitted */,
                    X,
                    Y,
                )
            )
            .thenReturn(
                DragPositioningCallback.CTRL_TYPE_TOP or DragPositioningCallback.CTRL_TYPE_RIGHT
            )

        val hoverEnter =
            MotionEventBuilder.newBuilder()
                .setAction(MotionEvent.ACTION_HOVER_ENTER)
                .setSource(InputDevice.SOURCE_MOUSE)
                .setDeviceId(DEVICE_ID)
                .setActionIndex(0)
                .setPointer(
                    PointerPropertiesBuilder.newBuilder()
                        .setId(POINTER_ID)
                        .setToolType(MotionEvent.TOOL_TYPE_MOUSE)
                        .build(),
                    PointerCoordsBuilder.newBuilder().setCoords(X, Y).build(),
                )
                .build()

        receiver.onInputEvent(hoverEnter)

        val iconCaptor = ArgumentCaptor.forClass(PointerIcon::class.java)
        verify(mockInputManager)
            .setPointerIcon(
                iconCaptor.capture(),
                eq(DISPLAY_ID),
                eq(DEVICE_ID),
                eq(POINTER_ID),
                any(),
            )
        assertThat(iconCaptor.value.type)
            .isEqualTo(PointerIcon.TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW)
    }

    @Test
    fun testUpdateCursorIcon_bottomLeftCorner() {
        val inputListener = create()
        testBgExecutor.flushAll()
        testMainExecutor.flushAll()

        whenever(mockGeometry.taskSize).thenReturn(Size(300, 200))
        inputListener.setGeometry(mockGeometry, 5 /* touchSlop */)

        whenever(
                mockGeometry.calculateCtrlType(
                    false /* isTouchscreen */,
                    true /* isEdgeResizePermitted */,
                    X,
                    Y,
                )
            )
            .thenReturn(
                DragPositioningCallback.CTRL_TYPE_BOTTOM or DragPositioningCallback.CTRL_TYPE_LEFT
            )

        val hoverEnter =
            MotionEventBuilder.newBuilder()
                .setAction(MotionEvent.ACTION_HOVER_ENTER)
                .setSource(InputDevice.SOURCE_MOUSE)
                .setDeviceId(DEVICE_ID)
                .setActionIndex(0)
                .setPointer(
                    PointerPropertiesBuilder.newBuilder()
                        .setId(POINTER_ID)
                        .setToolType(MotionEvent.TOOL_TYPE_MOUSE)
                        .build(),
                    PointerCoordsBuilder.newBuilder().setCoords(X, Y).build(),
                )
                .build()

        receiver.onInputEvent(hoverEnter)

        val iconCaptor = ArgumentCaptor.forClass(PointerIcon::class.java)
        verify(mockInputManager)
            .setPointerIcon(
                iconCaptor.capture(),
                eq(DISPLAY_ID),
                eq(DEVICE_ID),
                eq(POINTER_ID),
                any(),
            )
        assertThat(iconCaptor.value.type)
            .isEqualTo(PointerIcon.TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW)
    }

    private fun verifyNoInputChannelGrantRequests() {
        verify(mockWindowSession, never()).grantInputChannel(any())
    }

    private fun create(): DragResizeInputListener =
        DragResizeInputListener(
            context,
            mockWindowSession,
            testMainExecutor,
            testBgExecutor,
            TestRunningTaskInfoBuilder().build(),
            TestHandler(Looper.myLooper()),
            mock<Choreographer>(),
            DISPLAY_ID,
            decorationSurface,
            mockDragPositioningCallback,
            {
                object : SurfaceControl.Builder() {
                    override fun build(): SurfaceControl {
                        return super.build().also { createdSurfaces.add(it) }
                    }
                }
            },
            {
                object : StubTransaction() {
                    override fun remove(sc: SurfaceControl): SurfaceControl.Transaction {
                        return super.remove(sc).also {
                            sc.release()
                            removedSurfaces.add(sc)
                        }
                    }
                }
            },
            mockDisplayController,
            mockPreDragEventConduit,
            mockOnInputEventReceiverDisposed,
        ) { r ->
            receiver = r
        }

    private class TestInitializationCallback : Runnable {
        var initialized: Boolean = false
            private set

        override fun run() {
            initialized = true
        }
    }

    companion object {
        val DISPLAY_ID = Display.DEFAULT_DISPLAY
        val DEVICE_ID = 10
        val POINTER_ID = 6
        val X = 30f
        val Y = 40f

        val DISPLAY_WIDTH = 1920
        val DISPLAY_HEIGHT = 1080
        val TASK_WIDTH = 300
        val TASK_HEIGHT = 200
    }
}
