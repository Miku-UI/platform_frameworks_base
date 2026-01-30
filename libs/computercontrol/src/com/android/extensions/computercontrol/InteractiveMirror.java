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

package com.android.extensions.computercontrol;

import android.companion.virtual.computercontrol.InteractiveMirrorDisplay;
import android.hardware.input.VirtualTouchEvent;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Allows interactive mirroring of a {@link ComputerControlSession}.
 */
public final class InteractiveMirror implements AutoCloseable {
    private final InteractiveMirrorDisplay mIInteractiveMirrorDisplay;

    InteractiveMirror(@NonNull InteractiveMirrorDisplay interactiveMirrorDisplay) {
        mIInteractiveMirrorDisplay = Objects.requireNonNull(interactiveMirrorDisplay);
    }

    /**
     * Resize the display mirroring the {@link ComputerControlSession}.
     */
    public void resize(int width, int height) {
        mIInteractiveMirrorDisplay.resize(width, height);
    }

    /**
     * Inject input into the {@link ComputerControlSession} via its mirror display.
     */
    public void sendTouchEvent(@NonNull MotionEvent event) {
        for (int pointerIndex = 0; pointerIndex < event.getPointerCount(); pointerIndex++) {
            VirtualTouchEvent touchEvent = motionEventToVirtualTouchEvent(event, pointerIndex);
            mIInteractiveMirrorDisplay.sendTouchEvent(touchEvent);
        }
    }

    @Override
    public void close() {
        mIInteractiveMirrorDisplay.close();
    }

    private static VirtualTouchEvent motionEventToVirtualTouchEvent(
            MotionEvent event, int pointerIndex) {
        return new VirtualTouchEvent.Builder()
                .setEventTimeNanos((long) (event.getEventTime() * 1e6))
                .setPointerId(event.getPointerId(pointerIndex))
                .setAction(getVirtualTouchEventAction(event.getActionMasked()))
                .setPressure(event.getPressure(pointerIndex) * 255f)
                .setToolType(getVirtualTouchEventToolType(event.getActionMasked()))
                .setX(event.getX(pointerIndex))
                .setY(event.getY(pointerIndex))
                .build();
    }

    private static int getVirtualTouchEventAction(int action) {
        return switch (action) {
            case MotionEvent.ACTION_POINTER_DOWN -> VirtualTouchEvent.ACTION_DOWN;
            case MotionEvent.ACTION_POINTER_UP -> VirtualTouchEvent.ACTION_UP;
            default -> action;
        };
    }

    private static int getVirtualTouchEventToolType(int action) {
        return switch (action) {
            case MotionEvent.ACTION_CANCEL -> VirtualTouchEvent.TOOL_TYPE_PALM;
            default -> VirtualTouchEvent.TOOL_TYPE_FINGER;
        };
    }
}
