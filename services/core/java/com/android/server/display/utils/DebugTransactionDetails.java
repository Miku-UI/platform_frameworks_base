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

package com.android.server.display.utils;

import android.annotation.NonNull;
import android.graphics.Rect;

/**
 * Contains information about fields set to a SurfaceControl.Transaction for logging/testing
 * purposes.
 */
public class DebugTransactionDetails {
    private static final int UNSET_VALUE = -1;

    private int mOrientation = UNSET_VALUE;

    private final Rect mLayersRect = new Rect(UNSET_VALUE, UNSET_VALUE, UNSET_VALUE, UNSET_VALUE);

    private final Rect mDisplayRect = new Rect(UNSET_VALUE, UNSET_VALUE, UNSET_VALUE, UNSET_VALUE);

    private int mDisplayWidth = UNSET_VALUE;
    private int mDisplayHeight = UNSET_VALUE;

    public void setProjection(int orientation, @NonNull Rect layersRect,
            @NonNull Rect displayRect) {
        mOrientation = orientation;
        mLayersRect.set(layersRect);
        mDisplayRect.set(displayRect);
    }

    public void setDisplaySize(int width, int height) {
        mDisplayWidth = width;
        mDisplayHeight = height;
    }

    @Override
    public String toString() {
        final String displaySize =
                (mDisplayWidth == UNSET_VALUE && mDisplayHeight == UNSET_VALUE) ? "unset"
                        : mDisplayWidth + "x" + mDisplayHeight;
        return "TransactionDetails{" +
                "orientation=" + (mOrientation == UNSET_VALUE ? "unset" : mOrientation) +
                ", layersRect=" + (isUnset(mLayersRect) ? "unset" : mLayersRect) +
                ", displayRect=" + (isUnset(mDisplayRect) ? "unset" : mDisplayRect) +
                ", displaySize=" + displaySize +
                '}';
    }

    private boolean isUnset(Rect rect) {
        return rect.left == UNSET_VALUE && rect.top == UNSET_VALUE && rect.right == UNSET_VALUE
                && rect.bottom == UNSET_VALUE;
    }
}
