/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.accessibility;

import android.view.MotionEvent;

import androidx.test.core.view.MotionEventBuilder;
import androidx.test.core.view.PointerPropertiesBuilder;

import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.List;

public class MotionEventHelper {
    @GuardedBy("this")
    private final List<MotionEvent> mMotionEvents = new ArrayList<>();

    public void recycleEvents() {
        for (MotionEvent event:mMotionEvents) {
            event.recycle();
        }
        synchronized (this) {
            mMotionEvents.clear();
        }
    }

    public MotionEvent obtainMotionEvent(long downTime, long eventTime, int action, float x,
            float y) {
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0);
        synchronized (this) {
            mMotionEvents.add(event);
        }
        return event;
    }

    /**
     * Creates a {@link MotionEvent} with a batch of pointers. Call this with one or more pointer
     * coordinates, which will be added to the batch of the event.
     */
    public MotionEvent obtainBatchedMotionEvent(long downTime, int action, int source,
            int toolType, MotionEvent.PointerCoords... coords) {
        if (coords.length == 0) {
            throw new IllegalArgumentException("coords cannot be empty");
        }
        MotionEvent event = MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(downTime)
                .setAction(action)
                .setSource(source)
                .setPointer(
                        PointerPropertiesBuilder.newBuilder().setToolType(toolType).build(),
                        coords[0])
                .build();
        for (int i = 1; i < coords.length; i++) {
            event.addBatch(downTime, new MotionEvent.PointerCoords[]{coords[i]}, /* metaState= */
                    0);
        }
        synchronized (this) {
            mMotionEvents.add(event);
        }
        return event;
    }
}
