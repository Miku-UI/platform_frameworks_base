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

package com.android.server.accessibility;

import static org.junit.Assert.assertThrows;

import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.MotionEvent.PointerCoords;
import android.view.MotionEvent.PointerProperties;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link AccessibilityMotionEventBuilder}.
 */
@RunWith(AndroidJUnit4.class)
public class AccessibilityMotionEventBuilderTest {
    @Rule
    public final Expect expect = Expect.create();

    private static final float TOLERANCE = 0.001f;

    @Test
    public void fromBaseEvent_preservesAllProperties() {
        long downTime = 1000;
        long eventTime = 2000;
        int action = MotionEvent.ACTION_UP;
        int metaState = 15;

        int pointerCount = 2;
        PointerProperties[] props = new PointerProperties[pointerCount];
        PointerCoords[] coords = new PointerCoords[pointerCount];

        // Pointer 0
        props[0] = new PointerProperties();
        props[0].id = 1;
        props[0].toolType = MotionEvent.TOOL_TYPE_FINGER;
        coords[0] = new PointerCoords();
        coords[0].x = 100f;
        coords[0].y = 200f;

        // Pointer 1
        props[1] = new PointerProperties();
        props[1].id = 2;
        props[1].toolType = MotionEvent.TOOL_TYPE_STYLUS;
        coords[1] = new PointerCoords();
        coords[1].x = 300f;
        coords[1].y = 400f;

        int buttonState = MotionEvent.BUTTON_PRIMARY;
        float xPrecision = 1.0f;
        float yPrecision = 1.0f;
        int deviceId = 5;
        int edgeFlags = 0;
        int source = InputDevice.SOURCE_ANY;
        int displayId = 1;
        int flags = MotionEvent.FLAG_WINDOW_IS_OBSCURED;
        int classification = MotionEvent.CLASSIFICATION_NONE;

        MotionEvent originalEvent = MotionEvent.obtain(
                downTime, eventTime, action, pointerCount, props, coords,
                metaState, buttonState, xPrecision, yPrecision, deviceId, edgeFlags, source,
                displayId, flags);

        MotionEvent rebasedEvent = AccessibilityMotionEventBuilder.fromBaseEvent(
                originalEvent).build();

        expect.that(rebasedEvent.getDownTime()).isEqualTo(downTime);
        expect.that(rebasedEvent.getEventTime()).isEqualTo(eventTime);
        expect.that(rebasedEvent.getAction()).isEqualTo(action);
        expect.that(rebasedEvent.getPointerCount()).isEqualTo(pointerCount);
        expect.that(rebasedEvent.getPointerId(0)).isEqualTo(props[0].id);
        expect.that(rebasedEvent.getToolType(0)).isEqualTo(props[0].toolType);
        expect.that(rebasedEvent.getX(0)).isWithin(TOLERANCE).of(coords[0].x);
        expect.that(rebasedEvent.getY(0)).isWithin(TOLERANCE).of(coords[0].y);
        expect.that(rebasedEvent.getPointerId(1)).isEqualTo(props[1].id);
        expect.that(rebasedEvent.getToolType(1)).isEqualTo(props[1].toolType);
        expect.that(rebasedEvent.getX(1)).isWithin(TOLERANCE).of(coords[1].x);
        expect.that(rebasedEvent.getY(1)).isWithin(TOLERANCE).of(coords[1].y);
        expect.that(rebasedEvent.getMetaState()).isEqualTo(metaState);
        expect.that(rebasedEvent.getButtonState()).isEqualTo(buttonState);
        expect.that(rebasedEvent.getXPrecision()).isEqualTo(xPrecision);
        expect.that(rebasedEvent.getYPrecision()).isEqualTo(yPrecision);
        expect.that(rebasedEvent.getDeviceId()).isEqualTo(deviceId);
        expect.that(rebasedEvent.getEdgeFlags()).isEqualTo(edgeFlags);
        expect.that(rebasedEvent.getSource()).isEqualTo(source);
        expect.that(rebasedEvent.getDisplayId()).isEqualTo(displayId);
        expect.that(rebasedEvent.getFlags()).isEqualTo(flags);
        expect.that(rebasedEvent.getClassification()).isEqualTo(classification);

        originalEvent.recycle();
        rebasedEvent.recycle();
    }

    @Test
    public void setDownTime_updatesCorrectly() {
        long testDownTime = 6000;
        MotionEvent originalEvent = MotionEvent.obtain(5000, 5500, MotionEvent.ACTION_MOVE,
                100f, 100f, 0);

        MotionEvent rebasedEvent = AccessibilityMotionEventBuilder.fromBaseEvent(originalEvent)
                .setDownTime(testDownTime)
                .build();

        expect.that(rebasedEvent.getDownTime()).isEqualTo(testDownTime);

        originalEvent.recycle();
        rebasedEvent.recycle();
    }

    @Test
    public void setDeviceId_updatesCorrectly() {
        int testDeviceId = 99;
        MotionEvent originalEvent = MotionEvent.obtain(
                5000, 5500, MotionEvent.ACTION_MOVE, 1,
                100f, 100f, 0, MotionEvent.BUTTON_PRIMARY, 50.5f, 100.5f, 5, 0,
                InputDevice.SOURCE_MOUSE, MotionEvent.FLAG_WINDOW_IS_OBSCURED);

        MotionEvent rebasedEvent = AccessibilityMotionEventBuilder.fromBaseEvent(originalEvent)
                .setDeviceId(testDeviceId)
                .build();

        expect.that(rebasedEvent.getDeviceId()).isEqualTo(testDeviceId);

        originalEvent.recycle();
        rebasedEvent.recycle();
    }

    @Test
    public void setSource_updatesCorrectly() {
        int testSource = InputDevice.SOURCE_KEYBOARD;
        MotionEvent originalEvent = MotionEvent.obtain(
                5000, 5500, MotionEvent.ACTION_MOVE, 1,
                100f, 100f, 0, MotionEvent.BUTTON_PRIMARY, 50.5f, 100.5f, 5, 0,
                InputDevice.SOURCE_MOUSE, MotionEvent.FLAG_WINDOW_IS_OBSCURED);

        MotionEvent rebasedEvent = AccessibilityMotionEventBuilder.fromBaseEvent(originalEvent)
                .setSource(testSource)
                .build();

        expect.that(rebasedEvent.getSource()).isEqualTo(testSource);

        originalEvent.recycle();
        rebasedEvent.recycle();
    }

    @Test
    public void setTimeOffset_positiveOffset_shiftsTimestampsCorrectly() {
        long downTime = 1000;
        long eventTime = 2000;
        long offset = 500;
        MotionEvent originalEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_DOWN,
                100f, 100f, 0);

        MotionEvent rebasedEvent = AccessibilityMotionEventBuilder.fromBaseEvent(originalEvent)
                .setTimeOffset(offset)
                .build();

        expect.that(rebasedEvent.getDownTime()).isEqualTo(downTime + offset);
        expect.that(rebasedEvent.getEventTime()).isEqualTo(eventTime + offset);

        originalEvent.recycle();
        rebasedEvent.recycle();
    }

    @Test
    public void setTimeOffset_negativeOffset_shiftsTimestampsCorrectly() {
        long downTime = 5000;
        long eventTime = 6000;
        long offset = -1000;
        MotionEvent originalEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE,
                100f, 100f, 0);

        MotionEvent rebasedEvent = AccessibilityMotionEventBuilder.fromBaseEvent(originalEvent)
                .setTimeOffset(offset)
                .build();

        expect.that(rebasedEvent.getDownTime()).isEqualTo(downTime + offset);
        expect.that(rebasedEvent.getEventTime()).isEqualTo(eventTime + offset);

        originalEvent.recycle();
        rebasedEvent.recycle();
    }

    @Test
    public void setTimeOffset_eventTimeClampedToDownTime() {
        long downTime = 1000;
        long eventTime = 1000;
        long offset = 500;
        MotionEvent originalEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_DOWN,
                100f, 100f, 0);

        MotionEvent rebasedEvent = AccessibilityMotionEventBuilder.fromBaseEvent(originalEvent)
                .setTimeOffset(offset)
                .build();

        expect.that(rebasedEvent.getDownTime()).isEqualTo(downTime + offset);
        expect.that(rebasedEvent.getEventTime()).isEqualTo(eventTime + offset);
        expect.that(rebasedEvent.getEventTime() >= rebasedEvent.getDownTime()).isTrue();

        originalEvent.recycle();
        rebasedEvent.recycle();
    }

    @Test
    public void setAction_updatesCorrectly() {
        MotionEvent originalEvent = null;
        MotionEvent rebasedEvent = null;
        try {
            int testAction = MotionEvent.ACTION_CANCEL;
            originalEvent = MotionEvent.obtain(5000, 5500, MotionEvent.ACTION_MOVE,
                    100f, 100f, 0);

            rebasedEvent = AccessibilityMotionEventBuilder.fromBaseEvent(originalEvent)
                    .setAction(testAction)
                    .build();

            expect.that(rebasedEvent.getAction()).isEqualTo(testAction);
        } finally {
            if (originalEvent != null) {
                originalEvent.recycle();
            }
            if (rebasedEvent != null) {
                rebasedEvent.recycle();
            }
        }
    }

    @Test
    public void setDownTimeNanos_updatesCorrectly() {
        MotionEvent originalEvent = null;
        MotionEvent rebasedEvent = null;
        try {
            long testDownTimeNanos = 6000123456L;
            originalEvent = MotionEvent.obtain(5000, 5500, MotionEvent.ACTION_MOVE,
                    100f, 100f, 0);

            rebasedEvent = AccessibilityMotionEventBuilder.fromBaseEvent(originalEvent)
                    .setDownTimeNanos(testDownTimeNanos)
                    .build();

            expect.that(rebasedEvent.getDownTimeNanos()).isEqualTo(testDownTimeNanos);
        } finally {
            if (originalEvent != null) {
                originalEvent.recycle();
            }
            if (rebasedEvent != null) {
                rebasedEvent.recycle();
            }
        }
    }

    @Test
    public void excludePointer_removesCorrectPointer() {
        MotionEvent originalEvent = null;
        MotionEvent rebasedEvent = null;
        try {
            // Create a 3-pointer event
            PointerProperties[] props = new PointerProperties[3];
            PointerCoords[] coords = new PointerCoords[3];

            props[0] = new PointerProperties();
            props[0].id = 0;
            coords[0] = new PointerCoords();
            coords[0].x = 100f;
            coords[0].y = 200f;

            props[1] = new PointerProperties();
            props[1].id = 1;
            coords[1] = new PointerCoords();
            coords[1].x = 200f;
            coords[1].y = 400f;

            props[2] = new PointerProperties();
            props[2].id = 2;
            coords[2] = new PointerCoords();
            coords[2].x = 300f;
            coords[2].y = 600f;

            originalEvent = MotionEvent.obtain(1000, 2000, MotionEvent.ACTION_MOVE, 3, props,
                    coords, 0, 0, 1, 1, 0, 0, 0, 0, 0);

            // Exclude the middle pointer (index 1, id 1)
            rebasedEvent = AccessibilityMotionEventBuilder.fromBaseEvent(originalEvent)
                    .excludePointer(1)
                    .build();

            // Verify pointer count is reduced
            expect.that(rebasedEvent.getPointerCount()).isEqualTo(2);

            // Verify the remaining pointers are the original pointer 0 and 2
            // The new pointer at index 0 should be the original pointer with id 0
            expect.that(rebasedEvent.getPointerId(0)).isEqualTo(0);
            expect.that(rebasedEvent.getX(0)).isWithin(TOLERANCE).of(100f);
            expect.that(rebasedEvent.getY(0)).isWithin(TOLERANCE).of(200f);

            // The new pointer at index 1 should be the original pointer with id 2
            expect.that(rebasedEvent.getPointerId(1)).isEqualTo(2);
            expect.that(rebasedEvent.getX(1)).isWithin(TOLERANCE).of(300f);
            expect.that(rebasedEvent.getY(1)).isWithin(TOLERANCE).of(600f);
        } finally {
            if (originalEvent != null) {
                originalEvent.recycle();
            }
            if (rebasedEvent != null) {
                rebasedEvent.recycle();
            }
        }
    }

    @Test
    public void excludePointer_withInvalidIndex_throwsException() {
        MotionEvent originalEvent = null;
        MotionEvent rebasedEvent = null;
        try {
            // Create a 2-pointer event
            PointerProperties[] props = new PointerProperties[2];
            PointerCoords[] coords = new PointerCoords[2];

            props[0] = new PointerProperties();
            props[0].id = 0;
            coords[0] = new PointerCoords();

            props[1] = new PointerProperties();
            props[1].id = 1;
            coords[1] = new PointerCoords();

            originalEvent = MotionEvent.obtain(1000, 2000, MotionEvent.ACTION_MOVE, 2, props,
                    coords, 0, 0, 1, 1, 0, 0, 0, 0, 0);
            AccessibilityMotionEventBuilder builder =
                    AccessibilityMotionEventBuilder.fromBaseEvent(originalEvent);

            // Verify that excluding a pointer with a negative index throws an exception
            assertThrows(IllegalArgumentException.class, () -> builder.excludePointer(-1));

            // Verify that excluding a pointer with an out-of-bounds index throws an exception
            assertThrows(IllegalArgumentException.class, () -> builder.excludePointer(2));

            // Exclude a valid pointer, reducing the count to 1
            builder.excludePointer(0);

            // Verify that an index that was previously valid is now out-of-bounds
            assertThrows(IllegalArgumentException.class, () -> builder.excludePointer(1));

            // The builder should still be usable after invalid calls, with 1 pointer remaining
            rebasedEvent = builder.build();
            expect.that(rebasedEvent.getPointerCount()).isEqualTo(1);
        } finally {
            if (originalEvent != null) {
                originalEvent.recycle();
            }
            if (rebasedEvent != null) {
                rebasedEvent.recycle();
            }
        }
    }

    @Test
    public void build_calledTwice_throwsIllegalStateException() {
        MotionEvent originalEvent = null;
        MotionEvent rebasedEvent = null;
        try {
            originalEvent = MotionEvent.obtain(5000, 5500, MotionEvent.ACTION_MOVE,
                    100f, 100f, 0);
            AccessibilityMotionEventBuilder builder =
                    AccessibilityMotionEventBuilder.fromBaseEvent(originalEvent);

            // First build should succeed
            rebasedEvent = builder.build();

            // Second build should throw
            assertThrows(IllegalStateException.class, builder::build);
        } finally {
            if (originalEvent != null) {
                originalEvent.recycle();
            }
            if (rebasedEvent != null) {
                rebasedEvent.recycle();
            }
        }
    }
}
