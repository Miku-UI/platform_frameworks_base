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

package com.android.extensions.computercontrol.input;

import android.annotation.IntRange;
import android.annotation.NonNull;
import android.hardware.input.VirtualTouchEvent;
import android.os.SystemClock;
import android.view.MotionEvent;

import com.android.extensions.computercontrol.ComputerControlSession;

/**
 * A touch input event that can be injected into computer control sessions via
 * {@link ComputerControlSession#sendTouchEvent(TouchEvent)}.
 */
public class TouchEvent {
    private static final int MAX_POINTERS = 16;

    private final int mPointerId;
    private final int mToolType;
    private final int mAction;
    private final int mX;
    private final int mY;
    private final int mPressure;
    private final int mMajorAxisSize;
    private final long mEventTimeNanos;

    private TouchEvent(int pointerId, int toolType, int action, int x, int y, int pressure,
            int majorAxisSize, long eventTimeNanos) {
        mPointerId = pointerId;
        mToolType = toolType;
        mAction = action;
        mX = x;
        mY = y;
        mPressure = pressure;
        mMajorAxisSize = majorAxisSize;
        mEventTimeNanos = eventTimeNanos;
    }

    /**
     * Returns the pointer ID of the touch event.
     *
     * @see TouchEvent.Builder#setPointerId(int)
     */
    public int getPointerId() {
        return mPointerId;
    }

    /**
     * Returns the tool type of the touch event.
     *
     * @see TouchEvent.Builder#setToolType(int)
     */
    public int getToolType() {
        return mToolType;
    }

    /**
     * Returns the action of the touch event.
     *
     * @see TouchEvent.Builder#setAction(int)
     */
    public int getAction() {
        return mAction;
    }

    /**
     * Returns the x-axis location of the touch event.
     *
     * @see TouchEvent.Builder#setX(int)
     */
    public int getX() {
        return mX;
    }

    /**
     * Returns the y-axis location of the touch event.
     *
     * @see TouchEvent.Builder#setY(int)
     */
    public int getY() {
        return mY;
    }

    /**
     * Returns the pressure of the touch event.
     *
     * @see TouchEvent.Builder#setPressure(int)
     */
    public int getPressure() {
        return mPressure;
    }

    /**
     * Returns the major axis size of the touch event.
     *
     * @see TouchEvent.Builder#setMajorAxisSize(int)
     */
    public int getMajorAxisSize() {
        return mMajorAxisSize;
    }

    /**
     * Returns the event time of the touch event.
     *
     * @see TouchEvent.Builder#setEventTimeNanos(long)
     */
    public long getEventTimeNanos() {
        return mEventTimeNanos;
    }

    /**
     * Builder for {@link TouchEvent}.
     */
    public static final class Builder {
        private int mToolType = MotionEvent.TOOL_TYPE_UNKNOWN;
        private int mPointerId = MotionEvent.INVALID_POINTER_ID;
        private int mAction = -1;
        private int mX = -1;
        private int mY = -1;
        private int mPressure = -1;
        private int mMajorAxisSize = -1;
        private long mEventTimeNanos = 0L;

        /**
         * Sets the pointer id of the event.
         *
         * <p>A Valid pointer id need to be in the range of 0 to 15.
         *
         * @return this builder, to allow for chaining of calls
         */
        public @NonNull Builder setPointerId(
                @IntRange(from = 0, to = MAX_POINTERS - 1) int pointerId) {
            if (pointerId < 0 || pointerId > 15) {
                throw new IllegalArgumentException("The pointer id must be in the range 0 - "
                        + (MAX_POINTERS - 1) + "inclusive, but was: " + pointerId);
            }
            mPointerId = pointerId;
            return this;
        }

        /**
         * Sets the tool type of the event.
         *
         * @return this builder, to allow for chaining of calls
         */
        public @NonNull Builder setToolType(int toolType) {
            if (toolType != MotionEvent.TOOL_TYPE_FINGER
                    && toolType != MotionEvent.TOOL_TYPE_PALM) {
                throw new IllegalArgumentException("Unsupported touch event tool type");
            }
            mToolType = toolType;
            return this;
        }

        /**
         * Sets the action of the event.
         *
         * @return this builder, to allow for chaining of calls
         */
        public @NonNull Builder setAction(int action) {
            if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_UP
                    && action != MotionEvent.ACTION_MOVE && action != MotionEvent.ACTION_CANCEL) {
                throw new IllegalArgumentException(
                        "Unsupported touch event action type: " + action);
            }
            mAction = action;
            return this;
        }

        /**
         * Sets the x-axis location of the event. Value needs to be a positive integer within the
         * pixel width of the target display.
         *
         * @return this builder, to allow for chaining of calls
         */
        public @NonNull Builder setX(@IntRange(from = 0) int x) {
            if (x < 0) {
                throw new IllegalArgumentException("x-axis location cannot be negative");
            }
            mX = x;
            return this;
        }

        /**
         * Sets the y-axis location of the event. Value needs to be a positive integer within the
         * pixel height of the target display.
         *
         * @return this builder, to allow for chaining of calls
         */
        public @NonNull Builder setY(@IntRange(from = 0) int y) {
            if (y < 0) {
                throw new IllegalArgumentException("y-axis location cannot be negative");
            }
            mY = y;
            return this;
        }

        /**
         * Sets the pressure of the event. This field is optional and can be omitted.
         *
         * @param pressure The pressure of the touch.
         *                 Note: The VirtualTouchscreen, consuming VirtualTouchEvents, is
         *                 configured with a pressure axis range from 0 to 255. Only the
         *                 lower end of the range is enforced.
         * @return this builder, to allow for chaining of calls
         * @throws IllegalArgumentException if the pressure is outside the expected range
         */
        public @NonNull Builder setPressure(@IntRange(from = 0, to = 255) int pressure) {
            if (pressure < 0 || pressure > 255) {
                throw new IllegalArgumentException(
                        "Touch event pressure cannot be negative or above 255");
            }
            mPressure = pressure;
            return this;
        }

        /**
         * Sets the major axis size of the event. This field is optional and can be omitted.
         * Value needs to be a positive integer within the pixel width of the target display.
         *
         * @return this builder, to allow for chaining of calls
         */
        public @NonNull Builder setMajorAxisSize(@IntRange(from = 0) int majorAxisSize) {
            if (majorAxisSize < 0f) {
                throw new IllegalArgumentException(
                        "Touch event major axis size cannot be negative");
            }
            mMajorAxisSize = majorAxisSize;
            return this;
        }

        /**
         * Sets the time (in nanoseconds) when this specific event was generated. This may be
         * obtained from {@link SystemClock#uptimeMillis()} (with nanosecond precision instead
         * of
         * millisecond), but can be different depending on the use case.
         * This field is optional and can be omitted.
         * <p>
         * If this field is unset, then the time at which this event is sent to the framework
         * would
         * be considered as the event time (even though
         * {@link VirtualTouchEvent#getEventTimeNanos()}) would return {@code 0L}).
         *
         * @return this builder, to allow for chaining of calls
         */
        public @NonNull Builder setEventTimeNanos(long eventTimeNanos) {
            if (eventTimeNanos < 0L) {
                throw new IllegalArgumentException("Event time cannot be negative");
            }
            mEventTimeNanos = eventTimeNanos;
            return this;
        }

        /**
         * Creates a {@link TouchEvent} object with the current builder configuration.
         *
         * @throws IllegalArgumentException if one of the required arguments is missing or if
         *                                  ACTION_CANCEL is not set in combination with
         *                                  TOOL_TYPE_PALM. See
         *                                  {@link VirtualTouchEvent} for a detailed
         *                                  explanation.
         */
        public @NonNull TouchEvent build() {
            if (mToolType == MotionEvent.TOOL_TYPE_UNKNOWN
                    || mPointerId == MotionEvent.INVALID_POINTER_ID || mAction == -1 || mX < 0
                    || mY < 0) {
                throw new IllegalStateException(
                        "Cannot build virtual touch event with unset required fields");
            }
            if ((mToolType == MotionEvent.TOOL_TYPE_PALM && mAction != MotionEvent.ACTION_CANCEL)
                    || (mAction == MotionEvent.ACTION_CANCEL
                            && mToolType != MotionEvent.TOOL_TYPE_PALM)) {
                throw new IllegalStateException(
                        "ACTION_CANCEL and TOOL_TYPE_PALM must always appear together");
            }
            return new TouchEvent(mPointerId, mToolType, mAction, mX, mY, mPressure, mMajorAxisSize,
                    mEventTimeNanos);
        }
    }
}
