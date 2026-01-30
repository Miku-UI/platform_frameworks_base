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

package android.companion.virtual.computercontrol;

import android.annotation.IntRange;
import android.annotation.NonNull;
import android.hardware.input.VirtualTouchEvent;
import android.os.RemoteException;

import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/**
 * A display, mirroring a computer control session display, and its associated touchscreen.
 *
 * @hide
 */
public final class InteractiveMirrorDisplay implements AutoCloseable {

    private final IInteractiveMirrorDisplay mDisplay;

    /** @hide */
    @VisibleForTesting
    public InteractiveMirrorDisplay(@NonNull IInteractiveMirrorDisplay display) {
        mDisplay = Objects.requireNonNull(display);
    }

    /** Resizes the mirror display and updates the associated touchscreen. */
    public void resize(@IntRange(from = 1) int width, @IntRange(from = 1) int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Display dimensions must be positive");
        }
        try {
            mDisplay.resize(width, height);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /** Injects a touch event into the mirror display. */
    public void sendTouchEvent(@NonNull VirtualTouchEvent event) {
        try {
            mDisplay.sendTouchEvent(Objects.requireNonNull(event));
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    @Override
    public void close() {
        try {
            mDisplay.close();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
