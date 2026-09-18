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

import android.annotation.NonNull;
import android.util.Log;
import android.view.MotionEvent;
import android.view.MotionEvent.PointerCoords;
import android.view.MotionEvent.PointerProperties;

/**
 * Helper class for creating new {@link MotionEvent} instances by modifying specific properties
 * of an existing event.
 *
 * <p>This class implements the Builder pattern to simplify the modification of {@code MotionEvent}
 * properties (such as time, source, or device ID) while preserving other attributes (such as
 * coordinates, pointer count, and flags) from a base event.
 */
public final class AccessibilityMotionEventBuilder {
    private static final String TAG = AccessibilityMotionEventBuilder.class.getSimpleName();

    private final MotionEvent mBaseEvent;

    // Overridable parameters. Initialized in the constructor with mBaseEvent's values.
    private int mAction;
    private long mDownTimeNanos;
    private long mEventTimeNanos;
    private int mDeviceId;
    private int mSource;
    private int mPointerCount;
    private PointerProperties[] mPointerProperties;
    private PointerCoords[] mPointerCoords;
    private boolean mUsed = false;

    /**
     * Private constructor for the Builder. All building must start via {@link #fromBaseEvent}.
     */
    private AccessibilityMotionEventBuilder(@NonNull MotionEvent baseEvent) {
        mBaseEvent = baseEvent;

        // Initialize all fields with the base event's values
        mAction = baseEvent.getAction();
        mDownTimeNanos = baseEvent.getDownTimeNanos();
        mEventTimeNanos = baseEvent.getEventTimeNanos();
        mDeviceId = baseEvent.getDeviceId();
        mSource = baseEvent.getSource();
        mPointerCount = baseEvent.getPointerCount();
        mPointerProperties = getPointerProperties(baseEvent);
        mPointerCoords = getPointerCoords(baseEvent);
    }

    /**
     * Factory method to start building a {@link MotionEvent} based on a source event.
     *
     * <p>This method creates a copy of the source event to ensure thread safety and immutability
     * during the building process.
     */
    @NonNull
    public static AccessibilityMotionEventBuilder fromBaseEvent(@NonNull MotionEvent event) {
        // Create a copy of the event to protect against external modifications.
        return new AccessibilityMotionEventBuilder(MotionEvent.obtain(event));
    }

    /** Sets the action. */
    @NonNull
    public AccessibilityMotionEventBuilder setAction(int action) {
        mAction = action;
        return this;
    }

    /** Sets the down time. */
    @NonNull
    public AccessibilityMotionEventBuilder setDownTime(long downTime) {
        mDownTimeNanos = downTime * 1000000;
        return this;
    }

    /** Sets the down time in nanoseconds. */
    @NonNull
    public AccessibilityMotionEventBuilder setDownTimeNanos(long downTimeNanos) {
        mDownTimeNanos = downTimeNanos;
        return this;
    }

    /** Sets the device ID. */
    @NonNull
    public AccessibilityMotionEventBuilder setDeviceId(int deviceId) {
        mDeviceId = deviceId;
        return this;
    }

    /** Sets the source (e.g., InputDevice.SOURCE_TOUCHSCREEN). */
    @NonNull
    public AccessibilityMotionEventBuilder setSource(int source) {
        mSource = source;
        return this;
    }

    /**
     * Applies a time offset to both the down time and the event time.
     */
    @NonNull
    public AccessibilityMotionEventBuilder setTimeOffset(long offset) {
        mDownTimeNanos = mBaseEvent.getDownTimeNanos() + offset * 1000000;
        // Ensure the new event time is never earlier than the new down time.
        mEventTimeNanos = Math.max(mBaseEvent.getEventTimeNanos() + offset * 1000000,
                mDownTimeNanos);
        return this;
    }

    /**
     * Excludes a specific pointer from the event being built.
     *
     * @param pointerIndex The index of the pointer to exclude.
     * @return This builder.
     */
    @NonNull
    public AccessibilityMotionEventBuilder excludePointer(int pointerIndex) {
        if (pointerIndex < 0 || pointerIndex >= mPointerCount) {
            throw new IllegalArgumentException("Invalid pointer index: " + pointerIndex);
        }

        PointerProperties[] newProperties = new PointerProperties[mPointerCount - 1];
        PointerCoords[] newCoords = new PointerCoords[mPointerCount - 1];

        int newIndex = 0;
        for (int i = 0; i < mPointerCount; i++) {
            if (i == pointerIndex) {
                continue;
            }
            newProperties[newIndex] = mPointerProperties[i];
            newCoords[newIndex] = mPointerCoords[i];
            newIndex++;
        }

        mPointerCount--;
        mPointerProperties = newProperties;
        mPointerCoords = newCoords;
        return this;
    }

    /**
     * Builds and returns the new {@link MotionEvent}.
     *
     * <p>This method creates a new {@link MotionEvent} with the properties configured in this
     * builder. It uses the internal base event copy as a template for properties that were not
     * explicitly set.
     *
     * <p><b>Note:</b> The caller is responsible for calling {@link MotionEvent#recycle()}
     * on the returned event when it is no longer needed to avoid memory leaks.
     *
     * <p><b>Builder Lifecycle:</b> This method recycles the internal base event copy. Therefore,
     * this builder instance becomes invalid after this method is called and should not be used
     * to build further events.
     *
     * @return A new {@link MotionEvent} instance containing the modified properties.
     * @throws IllegalStateException If the new event could not be created (e.g. if
     * {@link MotionEvent#obtain} returned {@code null}). This should not happen with valid inputs.
     * If this exception is thrown, please file a bug to the Input team (go/input-bug).
     */
    public MotionEvent build() {
        if (mUsed) {
            throw new IllegalStateException("Builder can only be used once.");
        }
        MotionEvent rebaseEvent;
        try {
            rebaseEvent = MotionEvent.obtainNanoseconds(
                    mDownTimeNanos,
                    mEventTimeNanos,
                    mAction,
                    mPointerCount,
                    mPointerProperties,
                    mPointerCoords,
                    mBaseEvent.getMetaState(),
                    mBaseEvent.getButtonState(),
                    mBaseEvent.getXPrecision(),
                    mBaseEvent.getYPrecision(),
                    mDeviceId,
                    mBaseEvent.getEdgeFlags(),
                    mSource,
                    mBaseEvent.getDisplayId(),
                    mBaseEvent.getFlags(),
                    mBaseEvent.getClassification());
        } catch (IllegalArgumentException e) {
            String errorMsg = "Failed to rebase MotionEvent. "
                    + "BaseEvent: " + mBaseEvent
                    + ", Target Action: " + mAction
                    + ", Target DownTimeNanos: " + mDownTimeNanos
                    + ", Target EventTimeNanos: " + mEventTimeNanos
                    + ", Target DeviceId: " + mDeviceId
                    + ", Target Source: " + mSource
                    + ", Target PointerCount: " + mPointerCount;
            Log.e(TAG, errorMsg, e);
            throw new IllegalStateException(errorMsg, e);
        }
        mBaseEvent.recycle();
        mUsed = true;
        return rebaseEvent;
    }

    private static PointerProperties[] getPointerProperties(MotionEvent event) {
        final int count = event.getPointerCount();
        PointerProperties[] properties = new PointerProperties[count];
        for (int i = 0; i < count; i++) {
            properties[i] = new PointerProperties();
            event.getPointerProperties(i, properties[i]);
        }
        return properties;
    }

    private static PointerCoords[] getPointerCoords(MotionEvent event) {
        final int count = event.getPointerCount();
        PointerCoords[] coords = new PointerCoords[count];
        for (int i = 0; i < count; i++) {
            coords[i] = new PointerCoords();
            event.getPointerCoords(i, coords[i]);
        }
        return coords;
    }
}
