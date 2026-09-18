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

package com.android.wm.shell.windowdecor

import android.os.SystemClock
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTestCase
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [DragDetector].
 *
 * Build/Install/Run: atest WMShellUnitTests:DragDetectorTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class DragDetectorTest : ShellTestCase() {
    private val motionEvents = mutableListOf<MotionEvent>()

    @Mock private val viewGroup = mock<ViewGroup>()
    @Mock private val eventHandler = mock<DragDetector.MotionEventHandler>()

    @Before
    fun setUp() {
        whenever(eventHandler.handleMotionEvent(anyOrNull(), any())).thenReturn(true)
        whenever(viewGroup.id).thenReturn(VIEW_GROUP_ID)
    }

    @After
    fun tearDown() {
        motionEvents.forEach { it.recycle() }
        motionEvents.clear()
    }

    @Test
    fun testNoMove_touch_passesDownAndUp() {
        val dragDetector = createDragDetector()
        assertTrue(dragDetector.onMotionEvent(createMotionEvent(MotionEvent.ACTION_DOWN)))
        verify(eventHandler)
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_DOWN &&
                        x == X &&
                        y == Y &&
                        source == InputDevice.SOURCE_TOUCHSCREEN
                },
            )

        assertTrue(dragDetector.onMotionEvent(createMotionEvent(MotionEvent.ACTION_UP)))
        verify(eventHandler)
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_UP &&
                        x == X &&
                        y == Y &&
                        source == InputDevice.SOURCE_TOUCHSCREEN
                },
            )
    }

    @Test
    fun testNoMove_touch_notInterceptsDownAndUp() {
        val dragDetector = createDragDetector()
        assertFalse(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(MotionEvent.ACTION_DOWN),
            )
        )
        verify(eventHandler, never())
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_DOWN &&
                        x == X &&
                        y == Y &&
                        source == InputDevice.SOURCE_TOUCHSCREEN
                },
            )

        assertFalse(
            dragDetector.onInterceptTouchEvent(viewGroup, createMotionEvent(MotionEvent.ACTION_UP))
        )
        verify(eventHandler, never())
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_UP &&
                        x == X &&
                        y == Y &&
                        source == InputDevice.SOURCE_TOUCHSCREEN
                },
            )
    }

    @Test
    fun testNoMove_mouse_passesDownAndUp() {
        val dragDetector = createDragDetector()
        assertTrue(
            dragDetector.onMotionEvent(createMotionEvent(MotionEvent.ACTION_DOWN, isTouch = false))
        )
        verify(eventHandler)
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_DOWN &&
                        x == X &&
                        y == Y &&
                        source == InputDevice.SOURCE_MOUSE
                },
            )

        assertTrue(
            dragDetector.onMotionEvent(createMotionEvent(MotionEvent.ACTION_UP, isTouch = false))
        )
        verify(eventHandler)
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_UP &&
                        x == X &&
                        y == Y &&
                        source == InputDevice.SOURCE_MOUSE
                },
            )
    }

    @Test
    fun testNoMove_mouse_notInterceptsDownAndUp() {
        val dragDetector = createDragDetector()
        assertFalse(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(MotionEvent.ACTION_DOWN, isTouch = false),
            )
        )
        verify(eventHandler, never())
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_DOWN &&
                        x == X &&
                        y == Y &&
                        source == InputDevice.SOURCE_MOUSE
                },
            )

        assertFalse(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(MotionEvent.ACTION_UP, isTouch = false),
            )
        )
        verify(eventHandler, never())
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_UP &&
                        x == X &&
                        y == Y &&
                        source == InputDevice.SOURCE_MOUSE
                },
            )
    }

    @Test
    fun testMoveInSlop_touch_passesDownAndUp() {
        val dragDetector = createDragDetector()
        whenever(
                eventHandler.handleMotionEvent(
                    anyOrNull(),
                    argThat {
                        return@argThat action == MotionEvent.ACTION_DOWN
                    },
                )
            )
            .thenReturn(false)

        assertFalse(dragDetector.onMotionEvent(createMotionEvent(MotionEvent.ACTION_DOWN)))
        verify(eventHandler)
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_DOWN &&
                        x == X &&
                        y == Y &&
                        source == InputDevice.SOURCE_TOUCHSCREEN
                },
            )

        val newX = X + SLOP - 1
        assertFalse(dragDetector.onMotionEvent(createMotionEvent(MotionEvent.ACTION_MOVE, newX, Y)))
        verify(eventHandler, never())
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_MOVE
                },
            )

        assertTrue(dragDetector.onMotionEvent(createMotionEvent(MotionEvent.ACTION_UP, newX, Y)))
        verify(eventHandler)
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_UP &&
                        x == newX &&
                        y == Y &&
                        source == InputDevice.SOURCE_TOUCHSCREEN
                },
            )
    }

    @Test
    fun testMoveInSlop_touch_notInterceptsDownAndUp() {
        val dragDetector = createDragDetector()
        whenever(
                eventHandler.handleMotionEvent(
                    anyOrNull(),
                    argThat {
                        return@argThat action == MotionEvent.ACTION_DOWN
                    },
                )
            )
            .thenReturn(false)

        assertFalse(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(MotionEvent.ACTION_DOWN),
            )
        )
        verify(eventHandler, never())
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_DOWN &&
                        x == X &&
                        y == Y &&
                        source == InputDevice.SOURCE_TOUCHSCREEN
                },
            )

        val newX = X + SLOP - 1
        assertFalse(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(MotionEvent.ACTION_MOVE, newX, Y),
            )
        )
        verify(eventHandler, never())
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_MOVE
                },
            )

        assertFalse(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(MotionEvent.ACTION_UP, newX, Y),
            )
        )
        verify(eventHandler, never())
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_UP &&
                        x == newX &&
                        y == Y &&
                        source == InputDevice.SOURCE_TOUCHSCREEN
                },
            )
    }

    @Test
    fun testMoveInSlop_mouse_passesDownAndUp() {
        val dragDetector = createDragDetector()
        whenever(
                eventHandler.handleMotionEvent(
                    anyOrNull(),
                    argThat { action == MotionEvent.ACTION_DOWN },
                )
            )
            .thenReturn(false)

        assertFalse(
            dragDetector.onMotionEvent(createMotionEvent(MotionEvent.ACTION_DOWN, isTouch = false))
        )
        verify(eventHandler)
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_DOWN &&
                        x == X &&
                        y == Y &&
                        source == InputDevice.SOURCE_MOUSE
                },
            )

        val newX = X + SLOP - 1
        assertFalse(
            dragDetector.onMotionEvent(
                createMotionEvent(MotionEvent.ACTION_MOVE, newX, Y, isTouch = false)
            )
        )
        verify(eventHandler, never())
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_MOVE
                },
            )

        assertTrue(
            dragDetector.onMotionEvent(
                createMotionEvent(MotionEvent.ACTION_UP, newX, Y, isTouch = false)
            )
        )
        verify(eventHandler)
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_UP &&
                        x == newX &&
                        y == Y &&
                        source == InputDevice.SOURCE_MOUSE
                },
            )
    }

    @Test
    fun testMoveInSlop_mouse_notInterceptsDownAndUp() {
        val dragDetector = createDragDetector()
        whenever(
                eventHandler.handleMotionEvent(
                    anyOrNull(),
                    argThat { action == MotionEvent.ACTION_DOWN },
                )
            )
            .thenReturn(false)

        assertFalse(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(MotionEvent.ACTION_DOWN, isTouch = false),
            )
        )
        verify(eventHandler, never())
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_DOWN &&
                        x == X &&
                        y == Y &&
                        source == InputDevice.SOURCE_MOUSE
                },
            )

        val newX = X + SLOP - 1
        assertFalse(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(MotionEvent.ACTION_MOVE, newX, Y, isTouch = false),
            )
        )
        verify(eventHandler, never())
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_MOVE
                },
            )

        assertFalse(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(MotionEvent.ACTION_UP, newX, Y, isTouch = false),
            )
        )
        verify(eventHandler, never())
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_UP &&
                        x == newX &&
                        y == Y &&
                        source == InputDevice.SOURCE_MOUSE
                },
            )
    }

    @Test
    fun testMoveBeyondSlop_touch_passesDownMoveAndUp() {
        val dragDetector = createDragDetector()
        whenever(
                eventHandler.handleMotionEvent(
                    anyOrNull(),
                    argThat { action == MotionEvent.ACTION_DOWN },
                )
            )
            .thenReturn(false)

        assertFalse(dragDetector.onMotionEvent(createMotionEvent(MotionEvent.ACTION_DOWN)))
        verify(eventHandler)
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_DOWN &&
                        x == X &&
                        y == Y &&
                        source == InputDevice.SOURCE_TOUCHSCREEN
                },
            )

        val newX = X + SLOP + 1
        assertTrue(dragDetector.onMotionEvent(createMotionEvent(MotionEvent.ACTION_MOVE, newX, Y)))
        verify(eventHandler)
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_MOVE &&
                        x == newX &&
                        y == Y &&
                        source == InputDevice.SOURCE_TOUCHSCREEN
                },
            )

        assertTrue(dragDetector.onMotionEvent(createMotionEvent(MotionEvent.ACTION_UP, newX, Y)))
        verify(eventHandler)
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_UP &&
                        x == newX &&
                        y == Y &&
                        source == InputDevice.SOURCE_TOUCHSCREEN
                },
            )
    }

    @Test
    fun testMoveBeyondSlop_touch_interceptsAndPassesDownMoveAndUp() {
        val dragDetector = createDragDetector()
        whenever(
                eventHandler.handleMotionEvent(
                    anyOrNull(),
                    argThat { action == MotionEvent.ACTION_DOWN },
                )
            )
            .thenReturn(false)

        assertFalse(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(MotionEvent.ACTION_DOWN),
            )
        )
        verify(eventHandler, never())
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_DOWN &&
                        x == X &&
                        y == Y &&
                        source == InputDevice.SOURCE_TOUCHSCREEN
                },
            )

        val newX = X + SLOP + 1
        assertTrue(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(MotionEvent.ACTION_MOVE, newX, Y),
            )
        )
        verify(eventHandler)
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_DOWN &&
                        x == X &&
                        y == Y &&
                        source == InputDevice.SOURCE_TOUCHSCREEN
                },
            )
        // Mimic the redispatching of the first intercepted event
        assertTrue(
            dragDetector.onMotionEvent(
                viewGroup,
                createMotionEvent(MotionEvent.ACTION_MOVE, newX, Y),
            )
        )
        verify(eventHandler)
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_MOVE &&
                        x == newX &&
                        y == Y &&
                        source == InputDevice.SOURCE_TOUCHSCREEN
                },
            )

        assertTrue(
            dragDetector.onMotionEvent(viewGroup, createMotionEvent(MotionEvent.ACTION_UP, newX, Y))
        )
        verify(eventHandler)
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_UP &&
                        x == newX &&
                        y == Y &&
                        source == InputDevice.SOURCE_TOUCHSCREEN
                },
            )
    }

    @Test
    fun testMoveBeyondSlop_touch_blocksCancelsFromIrrelevantViews() {
        val irrelevantView = mock<View>()
        whenever(irrelevantView.id).thenReturn(VIEW_GROUP_ID + 1)

        val dragDetector = createDragDetector()
        whenever(
                eventHandler.handleMotionEvent(
                    anyOrNull(),
                    argThat { action == MotionEvent.ACTION_DOWN },
                )
            )
            .thenReturn(false)

        assertFalse(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(MotionEvent.ACTION_DOWN),
            )
        )

        val newX = X + SLOP + 1
        assertTrue(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(MotionEvent.ACTION_MOVE, newX, Y),
            )
        )
        assertTrue(
            dragDetector.onMotionEvent(
                viewGroup,
                createMotionEvent(MotionEvent.ACTION_MOVE, newX, Y),
            )
        )
        assertFalse(
            dragDetector.onMotionEvent(
                irrelevantView,
                createMotionEvent(MotionEvent.ACTION_CANCEL, newX, Y),
            )
        )
        assertTrue(
            dragDetector.onMotionEvent(viewGroup, createMotionEvent(MotionEvent.ACTION_UP, newX, Y))
        )

        verify(eventHandler, never())
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_CANCEL
                },
            )
    }

    @Test
    fun testMoveBeyondSlop_mouse_passesDownMoveAndUp() {
        val dragDetector = createDragDetector()
        whenever(
                eventHandler.handleMotionEvent(
                    anyOrNull(),
                    argThat { action == MotionEvent.ACTION_DOWN },
                )
            )
            .thenReturn(false)

        assertFalse(
            dragDetector.onMotionEvent(createMotionEvent(MotionEvent.ACTION_DOWN, isTouch = false))
        )
        verify(eventHandler)
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_DOWN &&
                        x == X &&
                        y == Y &&
                        source == InputDevice.SOURCE_MOUSE
                },
            )

        val newX = X + SLOP + 1
        assertTrue(
            dragDetector.onMotionEvent(
                createMotionEvent(MotionEvent.ACTION_MOVE, newX, Y, isTouch = false)
            )
        )
        verify(eventHandler)
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_MOVE &&
                        x == newX &&
                        y == Y &&
                        source == InputDevice.SOURCE_MOUSE
                },
            )

        assertTrue(
            dragDetector.onMotionEvent(
                createMotionEvent(MotionEvent.ACTION_UP, newX, Y, isTouch = false)
            )
        )
        verify(eventHandler)
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_UP &&
                        x == newX &&
                        y == Y &&
                        source == InputDevice.SOURCE_MOUSE
                },
            )
    }

    @Test
    fun testMoveBeyondSlop_mouse_interceptsAndPassesDownMoveAndUp() {
        val dragDetector = createDragDetector()
        whenever(
                eventHandler.handleMotionEvent(
                    anyOrNull(),
                    argThat { action == MotionEvent.ACTION_DOWN },
                )
            )
            .thenReturn(false)

        assertFalse(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(MotionEvent.ACTION_DOWN, isTouch = false),
            )
        )
        verify(eventHandler, never())
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_DOWN &&
                        x == X &&
                        y == Y &&
                        source == InputDevice.SOURCE_MOUSE
                },
            )

        val newX = X + SLOP + 1
        assertTrue(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(MotionEvent.ACTION_MOVE, newX, Y, isTouch = false),
            )
        )
        verify(eventHandler)
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_DOWN &&
                        x == X &&
                        y == Y &&
                        source == InputDevice.SOURCE_MOUSE
                },
            )
        assertTrue(
            dragDetector.onMotionEvent(
                viewGroup,
                createMotionEvent(MotionEvent.ACTION_MOVE, newX, Y, isTouch = false),
            )
        )
        verify(eventHandler)
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_MOVE &&
                        x == newX &&
                        y == Y &&
                        source == InputDevice.SOURCE_MOUSE
                },
            )

        assertTrue(
            dragDetector.onMotionEvent(
                viewGroup,
                createMotionEvent(MotionEvent.ACTION_UP, newX, Y, isTouch = false),
            )
        )
        verify(eventHandler)
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_UP &&
                        x == newX &&
                        y == Y &&
                        source == InputDevice.SOURCE_MOUSE
                },
            )
    }

    @Test
    fun testMoveBeyondSlop_mouse_blocksCancelsFromIrrelevantViews() {
        val irrelevantView = mock<View>()
        whenever(irrelevantView.id).thenReturn(VIEW_GROUP_ID + 1)

        val dragDetector = createDragDetector()
        whenever(
                eventHandler.handleMotionEvent(
                    anyOrNull(),
                    argThat { action == MotionEvent.ACTION_DOWN },
                )
            )
            .thenReturn(false)

        assertFalse(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(MotionEvent.ACTION_DOWN, isTouch = false),
            )
        )

        val newX = X + SLOP + 1
        assertTrue(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(MotionEvent.ACTION_MOVE, newX, Y, isTouch = false),
            )
        )
        // Mimic the redispatching of the first intercepted event
        assertTrue(
            dragDetector.onMotionEvent(
                viewGroup,
                createMotionEvent(MotionEvent.ACTION_MOVE, newX, Y, isTouch = false),
            )
        )
        assertFalse(
            dragDetector.onMotionEvent(
                irrelevantView,
                createMotionEvent(MotionEvent.ACTION_CANCEL, newX, Y, isTouch = false),
            )
        )
        assertTrue(
            dragDetector.onMotionEvent(
                viewGroup,
                createMotionEvent(MotionEvent.ACTION_UP, newX, Y, isTouch = false),
            )
        )

        verify(eventHandler, never())
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_CANCEL
                },
            )
    }

    @Test
    fun testPassesHoverEnter() {
        val dragDetector = createDragDetector()
        whenever(
                eventHandler.handleMotionEvent(
                    anyOrNull(),
                    argThat { action == MotionEvent.ACTION_HOVER_ENTER },
                )
            )
            .thenReturn(false)

        assertFalse(dragDetector.onMotionEvent(createMotionEvent(MotionEvent.ACTION_HOVER_ENTER)))
        verify(eventHandler)
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_HOVER_ENTER && x == X && y == Y
                },
            )
    }

    @Test
    fun testPassesHoverMove() {
        val dragDetector = createDragDetector()
        assertTrue(dragDetector.onMotionEvent(createMotionEvent(MotionEvent.ACTION_HOVER_MOVE)))
        verify(eventHandler)
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_HOVER_MOVE && x == X && y == Y
                },
            )
    }

    @Test
    fun testPassesHoverExit() {
        val dragDetector = createDragDetector()
        assertTrue(dragDetector.onMotionEvent(createMotionEvent(MotionEvent.ACTION_HOVER_EXIT)))
        verify(eventHandler)
            .handleMotionEvent(
                anyOrNull(),
                argThat {
                    return@argThat action == MotionEvent.ACTION_HOVER_EXIT && x == X && y == Y
                },
            )
    }

    @Test
    fun testHoldToDrag_holdsWithMovementWithinSlop_passesDragMoveEvents() {
        val dragDetector = createDragDetector(holdToDragMinDurationMs = 100, slop = 20)
        val downTime = SystemClock.uptimeMillis()
        dragDetector.onMotionEvent(
            createMotionEvent(
                action = MotionEvent.ACTION_DOWN,
                x = 500f,
                y = 10f,
                isTouch = true,
                downTime = downTime,
                eventTime = downTime,
            )
        )

        // Couple of movements within the slop, still counting as "holding"
        dragDetector.onMotionEvent(
            createMotionEvent(
                action = MotionEvent.ACTION_MOVE,
                x = 500f + 10f, // within slop
                y = 10f + 10f, // within slop
                isTouch = true,
                downTime = downTime,
                eventTime = downTime + 30,
            )
        )
        dragDetector.onMotionEvent(
            createMotionEvent(
                action = MotionEvent.ACTION_MOVE,
                x = 500f - 10f, // within slop
                y = 10f - 5f, // within slop
                isTouch = true,
                downTime = downTime,
                eventTime = downTime + 70,
            )
        )
        // Now go beyond slop, but after the required holding period.
        dragDetector.onMotionEvent(
            createMotionEvent(
                action = MotionEvent.ACTION_MOVE,
                x = 500f + 50f, // beyond slop
                y = 10f + 50f, // beyond slop
                isTouch = true,
                downTime = downTime,
                eventTime = downTime + 101, // after hold period
            )
        )

        // Had a valid hold, so there should be 1 "move".
        verify(eventHandler, times(1))
            .handleMotionEvent(anyOrNull(), argThat { ev -> ev.action == MotionEvent.ACTION_MOVE })
    }

    @Test
    fun testHoldToDrag_holdsWithMovementWithinSlop_interceptsAndPassesDragMoveEvents() {
        val dragDetector = createDragDetector(holdToDragMinDurationMs = 100, slop = 20)
        val downTime = SystemClock.uptimeMillis()
        assertFalse(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(
                    action = MotionEvent.ACTION_DOWN,
                    x = 500f,
                    y = 10f,
                    isTouch = true,
                    downTime = downTime,
                    eventTime = downTime,
                ),
            )
        )

        // Couple of movements within the slop, still counting as "holding"
        assertFalse(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(
                    action = MotionEvent.ACTION_MOVE,
                    x = 500f + 10f, // within slop
                    y = 10f + 10f, // within slop
                    isTouch = true,
                    downTime = downTime,
                    eventTime = downTime + 30,
                ),
            )
        )
        assertFalse(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(
                    action = MotionEvent.ACTION_MOVE,
                    x = 500f - 10f, // within slop
                    y = 10f - 5f, // within slop
                    isTouch = true,
                    downTime = downTime,
                    eventTime = downTime + 70,
                ),
            )
        )
        // Now go beyond slop, but after the required holding period.
        assertTrue(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(
                    action = MotionEvent.ACTION_MOVE,
                    x = 500f + 50f, // beyond slop
                    y = 10f + 50f, // beyond slop
                    isTouch = true,
                    downTime = downTime,
                    eventTime = downTime + 101, // after hold period
                ),
            )
        )
        // Mimic the redispatching of the first intercepted event
        dragDetector.onMotionEvent(
            viewGroup,
            createMotionEvent(
                action = MotionEvent.ACTION_MOVE,
                x = 500f + 50f, // beyond slop
                y = 10f + 50f, // beyond slop
                isTouch = true,
                downTime = downTime,
                eventTime = downTime + 101, // after hold period
            ),
        )

        // Had a valid hold, so there should be 1 "move".
        verify(eventHandler, times(1))
            .handleMotionEvent(anyOrNull(), argThat { ev -> ev.action == MotionEvent.ACTION_MOVE })
    }

    @Test
    fun testHoldToDrag_holdsWithoutAnyMovement_passesMoveEvents() {
        val dragDetector = createDragDetector(holdToDragMinDurationMs = 100, slop = 20)
        val downTime = SystemClock.uptimeMillis()
        dragDetector.onMotionEvent(
            createMotionEvent(
                action = MotionEvent.ACTION_DOWN,
                x = 500f,
                y = 10f,
                isTouch = true,
                downTime = downTime,
                eventTime = downTime,
            )
        )

        // First |move| is already beyond slop and after holding period.
        dragDetector.onMotionEvent(
            createMotionEvent(
                action = MotionEvent.ACTION_MOVE,
                x = 500f + 50f, // beyond slop
                y = 10f + 50f, // beyond slop
                isTouch = true,
                downTime = downTime,
                eventTime = downTime + 101, // after hold period
            )
        )

        // Considered a valid hold, so there should be 1 "move".
        verify(eventHandler, times(1))
            .handleMotionEvent(anyOrNull(), argThat { ev -> ev.action == MotionEvent.ACTION_MOVE })
    }

    @Test
    fun testHoldToDrag_holdsWithoutAnyMovement_interceptsAndPassesMoveEvents() {
        val dragDetector = createDragDetector(holdToDragMinDurationMs = 100, slop = 20)
        val downTime = SystemClock.uptimeMillis()
        assertFalse(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(
                    action = MotionEvent.ACTION_DOWN,
                    x = 500f,
                    y = 10f,
                    isTouch = true,
                    downTime = downTime,
                    eventTime = downTime,
                ),
            )
        )

        // First |move| is already beyond slop and after holding period.
        assertTrue(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(
                    action = MotionEvent.ACTION_MOVE,
                    x = 500f + 50f, // beyond slop
                    y = 10f + 50f, // beyond slop
                    isTouch = true,
                    downTime = downTime,
                    eventTime = downTime + 101, // after hold period
                ),
            )
        )
        // Mimic the redispatching of the first intercepted event
        dragDetector.onMotionEvent(
            viewGroup,
            createMotionEvent(
                action = MotionEvent.ACTION_MOVE,
                x = 500f + 50f, // beyond slop
                y = 10f + 50f, // beyond slop
                isTouch = true,
                downTime = downTime,
                eventTime = downTime + 101, // after hold period
            ),
        )

        // Considered a valid hold, so there should be 1 "move".
        verify(eventHandler, times(1))
            .handleMotionEvent(anyOrNull(), argThat { ev -> ev.action == MotionEvent.ACTION_MOVE })
    }

    @Test
    fun testHoldToDrag_returnsWithinSlopAfterHoldPeriod_passesDragMoveEvents() {
        val dragDetector = createDragDetector(holdToDragMinDurationMs = 100, slop = 20)
        val downTime = SystemClock.uptimeMillis()
        dragDetector.onMotionEvent(
            createMotionEvent(
                action = MotionEvent.ACTION_DOWN,
                x = 500f,
                y = 10f,
                isTouch = true,
                downTime = downTime,
                eventTime = downTime,
            )
        )
        // Go beyond slop after the required holding period.
        dragDetector.onMotionEvent(
            createMotionEvent(
                action = MotionEvent.ACTION_MOVE,
                x = 500f + 50f, // beyond slop
                y = 10f + 50f, // beyond slop
                isTouch = true,
                downTime = downTime,
                eventTime = downTime + 101, // after hold period
            )
        )

        // Return to original coordinates after holding period.
        dragDetector.onMotionEvent(
            createMotionEvent(
                action = MotionEvent.ACTION_MOVE,
                x = 500f, // within slop
                y = 10f, // within slop
                isTouch = true,
                downTime = downTime,
                eventTime = downTime + 102, // after hold period
            )
        )

        // Both |moves| should be passed, even the one in the slop region since it was after the
        // holding period. (e.g. after you drag the handle you may return to its original position).
        verify(eventHandler, times(2))
            .handleMotionEvent(anyOrNull(), argThat { ev -> ev.action == MotionEvent.ACTION_MOVE })
    }

    @Test
    fun testHoldToDrag_returnsWithinSlopAfterHoldPeriod_interceptsAndPassesDragMoveEvents() {
        val dragDetector = createDragDetector(holdToDragMinDurationMs = 100, slop = 20)
        val downTime = SystemClock.uptimeMillis()
        assertFalse(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(
                    action = MotionEvent.ACTION_DOWN,
                    x = 500f,
                    y = 10f,
                    isTouch = true,
                    downTime = downTime,
                    eventTime = downTime,
                ),
            )
        )
        // Go beyond slop after the required holding period.
        assertTrue(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(
                    action = MotionEvent.ACTION_MOVE,
                    x = 500f + 50f, // beyond slop
                    y = 10f + 50f, // beyond slop
                    isTouch = true,
                    downTime = downTime,
                    eventTime = downTime + 101, // after hold period
                ),
            )
        )
        // Mimic the redispatching of the first intercepted event
        dragDetector.onMotionEvent(
            viewGroup,
            createMotionEvent(
                action = MotionEvent.ACTION_MOVE,
                x = 500f + 50f, // beyond slop
                y = 10f + 50f, // beyond slop
                isTouch = true,
                downTime = downTime,
                eventTime = downTime + 101, // after hold period
            ),
        )

        // Return to original coordinates after holding period.
        dragDetector.onMotionEvent(
            viewGroup,
            createMotionEvent(
                action = MotionEvent.ACTION_MOVE,
                x = 500f, // within slop
                y = 10f, // within slop
                isTouch = true,
                downTime = downTime,
                eventTime = downTime + 102, // after hold period
            ),
        )

        // Both |moves| should be passed, even the one in the slop region since it was after the
        // holding period. (e.g. after you drag the handle you may return to its original position).
        verify(eventHandler, times(2))
            .handleMotionEvent(anyOrNull(), argThat { ev -> ev.action == MotionEvent.ACTION_MOVE })
    }

    @Test
    fun testHoldToDrag_straysDuringHoldPeriod_skipsMoveEvents() {
        val dragDetector = createDragDetector(holdToDragMinDurationMs = 100, slop = 20)
        val downTime = SystemClock.uptimeMillis()
        dragDetector.onMotionEvent(
            createMotionEvent(
                action = MotionEvent.ACTION_DOWN,
                x = 500f,
                y = 10f,
                isTouch = true,
                downTime = downTime,
                eventTime = downTime,
            )
        )

        // Go beyond slop before the required holding period.
        dragDetector.onMotionEvent(
            createMotionEvent(
                action = MotionEvent.ACTION_MOVE,
                x = 500f + 50f, // beyond slop
                y = 10f + 50f, // beyond slop
                isTouch = true,
                downTime = downTime,
                eventTime = downTime + 30, // during hold period
            )
        )

        // The |move| was too quick and did not held, do not pass it to the handler.
        verify(eventHandler, never())
            .handleMotionEvent(anyOrNull(), argThat { ev -> ev.action == MotionEvent.ACTION_MOVE })
    }

    @Test
    fun testHoldToDrag_straysDuringHoldPeriod_notInterceptMoveEvents() {
        val dragDetector = createDragDetector(holdToDragMinDurationMs = 100, slop = 20)
        val downTime = SystemClock.uptimeMillis()
        assertFalse(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(
                    action = MotionEvent.ACTION_DOWN,
                    x = 500f,
                    y = 10f,
                    isTouch = true,
                    downTime = downTime,
                    eventTime = downTime,
                ),
            )
        )

        // Go beyond slop before the required holding period.
        assertFalse(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(
                    action = MotionEvent.ACTION_MOVE,
                    x = 500f + 50f, // beyond slop
                    y = 10f + 50f, // beyond slop
                    isTouch = true,
                    downTime = downTime,
                    eventTime = downTime + 30, // during hold period
                ),
            )
        )

        // The |move| was too quick and did not held, do not pass it to the handler.
        verify(eventHandler, never())
            .handleMotionEvent(anyOrNull(), argThat { ev -> ev.action == MotionEvent.ACTION_MOVE })
    }

    @Test
    fun testHoldToDrag_straysDuringHoldPeriodAndReturnsWithinSlop_skipsMoveEvents() {
        val dragDetector = createDragDetector(holdToDragMinDurationMs = 100, slop = 20)
        val downTime = SystemClock.uptimeMillis()
        dragDetector.onMotionEvent(
            createMotionEvent(
                action = MotionEvent.ACTION_DOWN,
                x = 500f,
                y = 10f,
                isTouch = true,
                downTime = downTime,
                eventTime = downTime,
            )
        )
        // Go beyond slop before the required holding period.
        dragDetector.onMotionEvent(
            createMotionEvent(
                action = MotionEvent.ACTION_MOVE,
                x = 500f + 50f, // beyond slop
                y = 10f + 50f, // beyond slop
                isTouch = true,
                downTime = downTime,
                eventTime = downTime + 30, // during hold period
            )
        )

        // Return to slop area during holding period.
        dragDetector.onMotionEvent(
            createMotionEvent(
                action = MotionEvent.ACTION_MOVE,
                x = 500f + 10f, // within slop
                y = 10f + 10f, // within slop
                isTouch = true,
                downTime = downTime,
                eventTime = downTime + 50, // during hold period
            )
        )

        // The first |move| invalidates the drag even if you return within the hold period, so the
        // |move| should not be passed to the handler.
        verify(eventHandler, never())
            .handleMotionEvent(anyOrNull(), argThat { ev -> ev.action == MotionEvent.ACTION_MOVE })
    }

    @Test
    fun testHoldToDrag_straysDuringHoldPeriodAndReturnsWithinSlop_notInterceptMoveEvents() {
        val dragDetector = createDragDetector(holdToDragMinDurationMs = 100, slop = 20)
        val downTime = SystemClock.uptimeMillis()
        assertFalse(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(
                    action = MotionEvent.ACTION_DOWN,
                    x = 500f,
                    y = 10f,
                    isTouch = true,
                    downTime = downTime,
                    eventTime = downTime,
                ),
            )
        )
        // Go beyond slop before the required holding period.
        assertFalse(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(
                    action = MotionEvent.ACTION_MOVE,
                    x = 500f + 50f, // beyond slop
                    y = 10f + 50f, // beyond slop
                    isTouch = true,
                    downTime = downTime,
                    eventTime = downTime + 30, // during hold period
                ),
            )
        )

        // Return to slop area during holding period.
        assertFalse(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(
                    action = MotionEvent.ACTION_MOVE,
                    x = 500f + 10f, // within slop
                    y = 10f + 10f, // within slop
                    isTouch = true,
                    downTime = downTime,
                    eventTime = downTime + 50, // during hold period
                ),
            )
        )

        // The first |move| invalidates the drag even if you return within the hold period, so the
        // |move| should not be passed to the handler.
        verify(eventHandler, never())
            .handleMotionEvent(anyOrNull(), argThat { ev -> ev.action == MotionEvent.ACTION_MOVE })
    }

    @Test
    fun testHoldToDrag_noHoldRequired_passesMoveEvents() {
        val dragDetector = createDragDetector(holdToDragMinDurationMs = 0, slop = 20)
        val downTime = SystemClock.uptimeMillis()
        dragDetector.onMotionEvent(
            createMotionEvent(
                action = MotionEvent.ACTION_DOWN,
                x = 500f,
                y = 10f,
                isTouch = true,
                downTime = downTime,
                eventTime = downTime,
            )
        )

        dragDetector.onMotionEvent(
            createMotionEvent(
                action = MotionEvent.ACTION_MOVE,
                x = 500f + 50f, // beyond slop
                y = 10f + 50f, // beyond slop
                isTouch = true,
                downTime = downTime,
                eventTime = downTime + 1,
            )
        )

        // The |move| should be passed to the handler as no hold period was needed.
        verify(eventHandler, times(1))
            .handleMotionEvent(anyOrNull(), argThat { ev -> ev.action == MotionEvent.ACTION_MOVE })
    }

    @Test
    fun testHoldToDrag_noHoldRequired_interceptsAndPassesMoveEvents() {
        val dragDetector = createDragDetector(holdToDragMinDurationMs = 0, slop = 20)
        val downTime = SystemClock.uptimeMillis()
        assertFalse(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(
                    action = MotionEvent.ACTION_DOWN,
                    x = 500f,
                    y = 10f,
                    isTouch = true,
                    downTime = downTime,
                    eventTime = downTime,
                ),
            )
        )

        assertTrue(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(
                    action = MotionEvent.ACTION_MOVE,
                    x = 500f + 50f, // beyond slop
                    y = 10f + 50f, // beyond slop
                    isTouch = true,
                    downTime = downTime,
                    eventTime = downTime + 1,
                ),
            )
        )
        // Mimic the redispatching of the first intercepted event
        dragDetector.onMotionEvent(
            viewGroup,
            createMotionEvent(
                action = MotionEvent.ACTION_MOVE,
                x = 500f + 50f, // beyond slop
                y = 10f + 50f, // beyond slop
                isTouch = true,
                downTime = downTime,
                eventTime = downTime + 1,
            ),
        )

        // The |move| should be passed to the handler as no hold period was needed.
        verify(eventHandler, times(1))
            .handleMotionEvent(anyOrNull(), argThat { ev -> ev.action == MotionEvent.ACTION_MOVE })
    }

    @Test
    fun testHoldToDrag_mouse_passesMoveEvents() {
        // Specify a positive hold period.
        val dragDetector = createDragDetector(holdToDragMinDurationMs = 100, slop = 20)
        val downTime = SystemClock.uptimeMillis()
        dragDetector.onMotionEvent(
            createMotionEvent(
                action = MotionEvent.ACTION_DOWN,
                x = 500f,
                y = 10f,
                isTouch = false,
                downTime = downTime,
                eventTime = downTime,
            )
        )

        dragDetector.onMotionEvent(
            createMotionEvent(
                action = MotionEvent.ACTION_MOVE,
                x = 500f + 50f, // beyond slop
                y = 10f + 50f, // beyond slop
                isTouch = false,
                downTime = downTime,
                eventTime = downTime + 1, // during hold period
            )
        )

        // The |move| should be passed to the handler as the hold period is ignored for
        // non-touchscreen events.
        verify(eventHandler, times(1))
            .handleMotionEvent(anyOrNull(), argThat { ev -> ev.action == MotionEvent.ACTION_MOVE })
    }

    @Test
    fun testHoldToDrag_mouse_interceptAndPassesMoveEvents() {
        // Specify a positive hold period.
        val dragDetector = createDragDetector(holdToDragMinDurationMs = 100, slop = 20)
        val downTime = SystemClock.uptimeMillis()
        assertFalse(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(
                    action = MotionEvent.ACTION_DOWN,
                    x = 500f,
                    y = 10f,
                    isTouch = false,
                    downTime = downTime,
                    eventTime = downTime,
                ),
            )
        )

        assertTrue(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(
                    action = MotionEvent.ACTION_MOVE,
                    x = 500f + 50f, // beyond slop
                    y = 10f + 50f, // beyond slop
                    isTouch = false,
                    downTime = downTime,
                    eventTime = downTime + 1, // during hold period
                ),
            )
        )
        // Mimic the redispatching of the first intercepted event
        dragDetector.onMotionEvent(
            viewGroup,
            createMotionEvent(
                action = MotionEvent.ACTION_MOVE,
                x = 500f + 50f, // beyond slop
                y = 10f + 50f, // beyond slop
                isTouch = false,
                downTime = downTime,
                eventTime = downTime + 1, // during hold period
            ),
        )

        // The |move| should be passed to the handler as the hold period is ignored for
        // non-touchscreen events.
        verify(eventHandler, times(1))
            .handleMotionEvent(anyOrNull(), argThat { ev -> ev.action == MotionEvent.ACTION_MOVE })
    }

    @Test
    fun testEventWithMotionClassification_doesNothing() {
        val dragDetector = createDragDetector()
        assertFalse(
            dragDetector.onMotionEvent(
                createMotionEvent(
                    MotionEvent.ACTION_DOWN,
                    isTouch = false,
                    classification = MotionEvent.CLASSIFICATION_TWO_FINGER_SWIPE,
                )
            )
        )
        verify(eventHandler, never()).handleMotionEvent(anyOrNull(), any())

        assertFalse(
            dragDetector.onMotionEvent(
                createMotionEvent(
                    MotionEvent.ACTION_UP,
                    isTouch = false,
                    classification = MotionEvent.CLASSIFICATION_TWO_FINGER_SWIPE,
                )
            )
        )
        verify(eventHandler, never()).handleMotionEvent(anyOrNull(), any())
    }

    @Test
    fun testEventWithMotionClassification_notIntercept() {
        val dragDetector = createDragDetector()
        assertFalse(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(
                    MotionEvent.ACTION_DOWN,
                    isTouch = false,
                    classification = MotionEvent.CLASSIFICATION_TWO_FINGER_SWIPE,
                ),
            )
        )
        verify(eventHandler, never()).handleMotionEvent(anyOrNull(), any())

        assertFalse(
            dragDetector.onInterceptTouchEvent(
                viewGroup,
                createMotionEvent(
                    MotionEvent.ACTION_UP,
                    isTouch = false,
                    classification = MotionEvent.CLASSIFICATION_TWO_FINGER_SWIPE,
                ),
            )
        )
        verify(eventHandler, never()).handleMotionEvent(anyOrNull(), any())
    }

    @Test
    fun testDirectDispatchIntoDragDetector() {
        val dragDetector = createDragDetector()

        val down = createMotionEvent(MotionEvent.ACTION_DOWN)
        assertFalse(dragDetector.onInterceptTouchEvent(viewGroup, down))

        // If no child views can handle the gesture, the entire gesture is dispatched to the view
        // group after the first down is passed to View#onInterceptTouchEvent.
        assertTrue(dragDetector.onMotionEvent(viewGroup, down))

        // Make sure only one down is passed
        verify(eventHandler)
            .handleMotionEvent(anyOrNull(), argThat { ev -> ev.action == MotionEvent.ACTION_DOWN })

        val moveWithinSlop = createMotionEvent(MotionEvent.ACTION_MOVE)
        assertTrue(dragDetector.onMotionEvent(viewGroup, moveWithinSlop))
        verify(eventHandler, never())
            .handleMotionEvent(anyOrNull(), argThat { ev -> ev.action == MotionEvent.ACTION_MOVE })

        val moveBeyondSlop = createMotionEvent(MotionEvent.ACTION_MOVE, x = X + SLOP + 1)
        assertTrue(dragDetector.onMotionEvent(viewGroup, moveBeyondSlop))
        verify(eventHandler)
            .handleMotionEvent(
                eq(viewGroup),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_MOVE && ev.x == X + SLOP + 1 && ev.y == Y
                },
            )

        val up = createMotionEvent(MotionEvent.ACTION_UP, x = X + SLOP + 1)
        assertTrue(dragDetector.onMotionEvent(viewGroup, up))
        verify(eventHandler)
            .handleMotionEvent(
                eq(viewGroup),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_UP && ev.x == X + SLOP + 1 && ev.y == Y
                },
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_FILTER_EVENTS_FROM_IRRELEVANT_DEVICES_IN_DRAG_MOVE_RESIZE)
    fun testIgnoreIrrelevantDevice_irrelevantGestureEndsFirst_directDispatch() {
        val dragDetector = createDragDetector()

        val relevantDown = createMotionEvent(MotionEvent.ACTION_DOWN, deviceId = DEFAULT_DEVICE_ID)
        assertFalse(dragDetector.onInterceptTouchEvent(viewGroup, relevantDown))
        assertTrue(dragDetector.onMotionEvent(viewGroup, relevantDown))
        verify(eventHandler)
            .handleMotionEvent(
                eq(viewGroup),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_DOWN && ev.deviceId == DEFAULT_DEVICE_ID
                },
            )

        val irrelevantDown =
            createMotionEvent(MotionEvent.ACTION_DOWN, deviceId = ALTERNATIVE_DEVICE_ID)
        assertFalse(dragDetector.onInterceptTouchEvent(viewGroup, irrelevantDown))
        assertFalse(dragDetector.onMotionEvent(viewGroup, irrelevantDown))
        verify(eventHandler, never())
            .handleMotionEvent(
                anyOrNull(),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_DOWN && ev.deviceId == ALTERNATIVE_DEVICE_ID
                },
            )

        val irrelevantMove =
            createMotionEvent(
                MotionEvent.ACTION_MOVE,
                x = X + SLOP + 1,
                deviceId = ALTERNATIVE_DEVICE_ID,
            )
        assertFalse(dragDetector.onMotionEvent(viewGroup, irrelevantMove))
        verify(eventHandler, never())
            .handleMotionEvent(
                anyOrNull(),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_MOVE && ev.deviceId == ALTERNATIVE_DEVICE_ID
                },
            )

        val relevantMove =
            createMotionEvent(
                MotionEvent.ACTION_MOVE,
                x = X + SLOP + 1,
                deviceId = DEFAULT_DEVICE_ID,
            )
        assertTrue(dragDetector.onMotionEvent(viewGroup, relevantMove))
        verify(eventHandler)
            .handleMotionEvent(
                eq(viewGroup),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_MOVE && ev.deviceId == DEFAULT_DEVICE_ID
                },
            )

        val irrelevantUp =
            createMotionEvent(
                MotionEvent.ACTION_UP,
                x = X + SLOP + 1,
                deviceId = ALTERNATIVE_DEVICE_ID,
            )
        assertFalse(dragDetector.onMotionEvent(viewGroup, irrelevantUp))
        verify(eventHandler, never())
            .handleMotionEvent(
                anyOrNull(),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_UP && ev.deviceId == ALTERNATIVE_DEVICE_ID
                },
            )

        val relevantUp =
            createMotionEvent(MotionEvent.ACTION_UP, x = X + SLOP + 1, deviceId = DEFAULT_DEVICE_ID)
        assertTrue(dragDetector.onMotionEvent(viewGroup, relevantUp))
        verify(eventHandler)
            .handleMotionEvent(
                eq(viewGroup),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_UP && ev.deviceId == DEFAULT_DEVICE_ID
                },
            )

        // New gestures from irrelevant devices are still handled
        assertFalse(dragDetector.onInterceptTouchEvent(viewGroup, irrelevantDown))
        assertTrue(dragDetector.onMotionEvent(viewGroup, irrelevantDown))
        verify(eventHandler)
            .handleMotionEvent(
                eq(viewGroup),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_DOWN && ev.deviceId == ALTERNATIVE_DEVICE_ID
                },
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_FILTER_EVENTS_FROM_IRRELEVANT_DEVICES_IN_DRAG_MOVE_RESIZE)
    fun testIgnoreIrrelevantDevice_irrelevantGestureEndsFirst_interceptsOnMove() {
        val dragDetector = createDragDetector()

        val relevantDown = createMotionEvent(MotionEvent.ACTION_DOWN, deviceId = DEFAULT_DEVICE_ID)
        assertFalse(dragDetector.onInterceptTouchEvent(viewGroup, relevantDown))
        verify(eventHandler, never())
            .handleMotionEvent(
                eq(viewGroup),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_DOWN && ev.deviceId == DEFAULT_DEVICE_ID
                },
            )

        val irrelevantDown =
            createMotionEvent(MotionEvent.ACTION_DOWN, deviceId = ALTERNATIVE_DEVICE_ID)
        assertFalse(dragDetector.onInterceptTouchEvent(viewGroup, irrelevantDown))

        val irrelevantMove =
            createMotionEvent(
                MotionEvent.ACTION_MOVE,
                x = X + SLOP + 1,
                deviceId = ALTERNATIVE_DEVICE_ID,
            )
        assertFalse(dragDetector.onMotionEvent(viewGroup, irrelevantMove))
        verify(eventHandler, never())
            .handleMotionEvent(
                anyOrNull(),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_DOWN && ev.deviceId == ALTERNATIVE_DEVICE_ID
                },
            )
        verify(eventHandler, never())
            .handleMotionEvent(
                anyOrNull(),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_MOVE && ev.deviceId == ALTERNATIVE_DEVICE_ID
                },
            )

        val relevantMove =
            createMotionEvent(
                MotionEvent.ACTION_MOVE,
                x = X + SLOP + 1,
                deviceId = DEFAULT_DEVICE_ID,
            )
        assertTrue(dragDetector.onInterceptTouchEvent(viewGroup, relevantMove))
        assertTrue(dragDetector.onMotionEvent(viewGroup, relevantMove))
        verify(eventHandler)
            .handleMotionEvent(
                eq(viewGroup),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_DOWN &&
                        ev.deviceId == DEFAULT_DEVICE_ID &&
                        ev.x == X
                },
            )
        verify(eventHandler)
            .handleMotionEvent(
                eq(viewGroup),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_MOVE &&
                        ev.deviceId == DEFAULT_DEVICE_ID &&
                        ev.x == X + SLOP + 1
                },
            )

        val irrelevantUp =
            createMotionEvent(
                MotionEvent.ACTION_UP,
                x = X + SLOP + 1,
                deviceId = ALTERNATIVE_DEVICE_ID,
            )
        assertFalse(dragDetector.onMotionEvent(viewGroup, irrelevantUp))
        verify(eventHandler, never())
            .handleMotionEvent(
                anyOrNull(),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_UP && ev.deviceId == ALTERNATIVE_DEVICE_ID
                },
            )

        val relevantUp =
            createMotionEvent(MotionEvent.ACTION_UP, x = X + SLOP + 1, deviceId = DEFAULT_DEVICE_ID)
        assertTrue(dragDetector.onMotionEvent(viewGroup, relevantUp))
        verify(eventHandler)
            .handleMotionEvent(
                anyOrNull(),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_UP && ev.deviceId == DEFAULT_DEVICE_ID
                },
            )

        // New gestures from irrelevant devices are still handled
        assertFalse(dragDetector.onInterceptTouchEvent(viewGroup, irrelevantDown))
        assertTrue(dragDetector.onMotionEvent(viewGroup, irrelevantDown))
        verify(eventHandler)
            .handleMotionEvent(
                anyOrNull(),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_DOWN && ev.deviceId == ALTERNATIVE_DEVICE_ID
                },
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_FILTER_EVENTS_FROM_IRRELEVANT_DEVICES_IN_DRAG_MOVE_RESIZE)
    fun testIgnoreIrrelevantDevice_relevantGestureEndsFirst_directDispatch() {
        val dragDetector = createDragDetector()

        val relevantDown = createMotionEvent(MotionEvent.ACTION_DOWN, deviceId = DEFAULT_DEVICE_ID)
        assertFalse(dragDetector.onInterceptTouchEvent(viewGroup, relevantDown))
        assertTrue(dragDetector.onMotionEvent(viewGroup, relevantDown))
        verify(eventHandler)
            .handleMotionEvent(
                eq(viewGroup),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_DOWN && ev.deviceId == DEFAULT_DEVICE_ID
                },
            )

        val irrelevantDown =
            createMotionEvent(MotionEvent.ACTION_DOWN, deviceId = ALTERNATIVE_DEVICE_ID)
        assertFalse(dragDetector.onInterceptTouchEvent(viewGroup, irrelevantDown))
        assertFalse(dragDetector.onMotionEvent(viewGroup, irrelevantDown))
        verify(eventHandler, never())
            .handleMotionEvent(
                anyOrNull(),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_DOWN && ev.deviceId == ALTERNATIVE_DEVICE_ID
                },
            )

        val relevantMove =
            createMotionEvent(
                MotionEvent.ACTION_MOVE,
                x = X + SLOP + 1,
                deviceId = DEFAULT_DEVICE_ID,
            )
        assertTrue(dragDetector.onMotionEvent(viewGroup, relevantMove))
        verify(eventHandler)
            .handleMotionEvent(
                eq(viewGroup),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_MOVE && ev.deviceId == DEFAULT_DEVICE_ID
                },
            )

        val irrelevantMove =
            createMotionEvent(
                MotionEvent.ACTION_MOVE,
                x = X + SLOP + 1,
                deviceId = ALTERNATIVE_DEVICE_ID,
            )
        assertFalse(dragDetector.onMotionEvent(viewGroup, irrelevantMove))
        verify(eventHandler, never())
            .handleMotionEvent(
                anyOrNull(),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_MOVE && ev.deviceId == ALTERNATIVE_DEVICE_ID
                },
            )

        val relevantUp =
            createMotionEvent(MotionEvent.ACTION_UP, x = X + SLOP + 1, deviceId = DEFAULT_DEVICE_ID)
        assertTrue(dragDetector.onMotionEvent(viewGroup, relevantUp))
        verify(eventHandler)
            .handleMotionEvent(
                eq(viewGroup),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_UP && ev.deviceId == DEFAULT_DEVICE_ID
                },
            )

        // Another gesture from the relevant device should also be discarded before all gestures
        // finish
        assertFalse(dragDetector.onInterceptTouchEvent(viewGroup, relevantDown))
        assertFalse(dragDetector.onMotionEvent(viewGroup, relevantDown))
        verify(eventHandler)
            .handleMotionEvent(
                anyOrNull(),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_DOWN && ev.deviceId == DEFAULT_DEVICE_ID
                },
            )

        assertFalse(dragDetector.onMotionEvent(viewGroup, relevantUp))
        verify(eventHandler)
            .handleMotionEvent(
                anyOrNull(),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_UP && ev.deviceId == DEFAULT_DEVICE_ID
                },
            )

        // Finish the gesture from the irrelevant device
        val irrelevantUp =
            createMotionEvent(
                MotionEvent.ACTION_UP,
                x = X + SLOP + 1,
                deviceId = ALTERNATIVE_DEVICE_ID,
            )
        assertFalse(dragDetector.onMotionEvent(viewGroup, irrelevantUp))
        verify(eventHandler, never())
            .handleMotionEvent(
                anyOrNull(),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_UP && ev.deviceId == ALTERNATIVE_DEVICE_ID
                },
            )

        // New gestures from irrelevant devices are still handled
        assertFalse(dragDetector.onInterceptTouchEvent(viewGroup, irrelevantDown))
        assertTrue(dragDetector.onMotionEvent(viewGroup, irrelevantDown))
        verify(eventHandler)
            .handleMotionEvent(
                anyOrNull(),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_DOWN && ev.deviceId == ALTERNATIVE_DEVICE_ID
                },
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_FILTER_EVENTS_FROM_IRRELEVANT_DEVICES_IN_DRAG_MOVE_RESIZE)
    fun testIgnoreIrrelevantDevice_relevantGestureEndsFirst_interceptsOnMove() {
        val dragDetector = createDragDetector()

        val relevantDown = createMotionEvent(MotionEvent.ACTION_DOWN, deviceId = DEFAULT_DEVICE_ID)
        assertFalse(dragDetector.onInterceptTouchEvent(viewGroup, relevantDown))
        verify(eventHandler, never())
            .handleMotionEvent(
                eq(viewGroup),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_DOWN && ev.deviceId == DEFAULT_DEVICE_ID
                },
            )

        val irrelevantDown =
            createMotionEvent(MotionEvent.ACTION_DOWN, deviceId = ALTERNATIVE_DEVICE_ID)
        assertFalse(dragDetector.onInterceptTouchEvent(viewGroup, irrelevantDown))

        val relevantMove =
            createMotionEvent(
                MotionEvent.ACTION_MOVE,
                x = X + SLOP + 1,
                deviceId = DEFAULT_DEVICE_ID,
            )
        assertTrue(dragDetector.onInterceptTouchEvent(viewGroup, relevantMove))
        assertTrue(dragDetector.onMotionEvent(viewGroup, relevantMove))
        verify(eventHandler)
            .handleMotionEvent(
                eq(viewGroup),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_DOWN &&
                        ev.deviceId == DEFAULT_DEVICE_ID &&
                        ev.x == X
                },
            )
        verify(eventHandler)
            .handleMotionEvent(
                eq(viewGroup),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_MOVE &&
                        ev.deviceId == DEFAULT_DEVICE_ID &&
                        ev.x == X + SLOP + 1
                },
            )

        val irrelevantMove =
            createMotionEvent(
                MotionEvent.ACTION_MOVE,
                x = X + SLOP + 1,
                deviceId = ALTERNATIVE_DEVICE_ID,
            )
        assertFalse(dragDetector.onMotionEvent(viewGroup, irrelevantMove))
        verify(eventHandler, never())
            .handleMotionEvent(
                anyOrNull(),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_DOWN && ev.deviceId == ALTERNATIVE_DEVICE_ID
                },
            )
        verify(eventHandler, never())
            .handleMotionEvent(
                anyOrNull(),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_MOVE && ev.deviceId == ALTERNATIVE_DEVICE_ID
                },
            )

        val relevantUp =
            createMotionEvent(MotionEvent.ACTION_UP, x = X + SLOP + 1, deviceId = DEFAULT_DEVICE_ID)
        assertTrue(dragDetector.onMotionEvent(viewGroup, relevantUp))
        verify(eventHandler)
            .handleMotionEvent(
                anyOrNull(),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_UP && ev.deviceId == DEFAULT_DEVICE_ID
                },
            )

        // Another gesture from the relevant device should also be discarded before all gestures
        // finish
        assertFalse(dragDetector.onInterceptTouchEvent(viewGroup, relevantDown))
        assertFalse(dragDetector.onMotionEvent(viewGroup, relevantDown))
        verify(eventHandler)
            .handleMotionEvent(
                anyOrNull(),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_DOWN && ev.deviceId == DEFAULT_DEVICE_ID
                },
            )

        assertFalse(dragDetector.onMotionEvent(viewGroup, relevantUp))
        verify(eventHandler)
            .handleMotionEvent(
                anyOrNull(),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_UP && ev.deviceId == DEFAULT_DEVICE_ID
                },
            )

        // Finish the gesture from the irrelevant device
        val irrelevantUp =
            createMotionEvent(
                MotionEvent.ACTION_UP,
                x = X + SLOP + 1,
                deviceId = ALTERNATIVE_DEVICE_ID,
            )
        assertFalse(dragDetector.onMotionEvent(viewGroup, irrelevantUp))
        verify(eventHandler, never())
            .handleMotionEvent(
                anyOrNull(),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_UP && ev.deviceId == ALTERNATIVE_DEVICE_ID
                },
            )

        // New gestures from irrelevant devices are still handled
        assertFalse(dragDetector.onInterceptTouchEvent(viewGroup, irrelevantDown))
        assertTrue(dragDetector.onMotionEvent(viewGroup, irrelevantDown))
        verify(eventHandler)
            .handleMotionEvent(
                anyOrNull(),
                argThat { ev ->
                    ev.action == MotionEvent.ACTION_DOWN && ev.deviceId == ALTERNATIVE_DEVICE_ID
                },
            )
    }

    private fun createMotionEvent(
        action: Int,
        x: Float = X,
        y: Float = Y,
        deviceId: Int = DEFAULT_DEVICE_ID,
        isTouch: Boolean = true,
        downTime: Long = SystemClock.uptimeMillis(),
        eventTime: Long = SystemClock.uptimeMillis(),
        classification: Int = MotionEvent.CLASSIFICATION_NONE,
    ): MotionEvent {
        val pointerProperties =
            arrayOf(
                MotionEvent.PointerProperties().apply {
                    this.id = 0
                    this.toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            )
        val pointerCoords =
            arrayOf(
                MotionEvent.PointerCoords().apply {
                    this.x = x
                    this.y = y
                }
            )
        val ev =
            MotionEvent.obtain(
                downTime,
                eventTime,
                action,
                /* pointerCount= */ 1,
                pointerProperties,
                pointerCoords,
                /* metaState= */ 0,
                /* buttonState= */ 0,
                /* xPrecision= */ 0f,
                /* yPrecision= */ 0f,
                deviceId,
                /* edgeFlags= */ 0,
                if (isTouch) InputDevice.SOURCE_TOUCHSCREEN else InputDevice.SOURCE_MOUSE,
                /* displayId= */ 0,
                /* flags= */ 0,
                classification,
            )!!
        motionEvents.add(ev)
        return ev
    }

    private fun createDragDetector(holdToDragMinDurationMs: Long = 0, slop: Int = SLOP) =
        DragDetector(eventHandler, holdToDragMinDurationMs, slop)

    companion object {
        private const val SLOP = 10
        private const val X = 123f
        private const val Y = 234f

        private const val VIEW_GROUP_ID = 9064

        private const val DEFAULT_DEVICE_ID = 0
        private const val ALTERNATIVE_DEVICE_ID = 1
    }
}
