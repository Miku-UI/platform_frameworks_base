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

package com.android.server.wm;

import android.annotation.NonNull;
import android.util.Slog;
import android.util.SparseArray;
import android.view.Display;
import android.view.Surface;

import com.android.internal.annotations.VisibleForTesting;

/**
 * Singleton for coordinating rotation across multiple displays. Used to notify non-default
 * displays when the default display rotates.
 *
 * Note that this class does not need locking because it is always protected by WindowManagerService
 * mGlobalLock.
 */
class DisplayRotationCoordinator {

    private static final String TAG = "DisplayRotationCoordinator";

    @Surface.Rotation
    private int mDefaultDisplayDefaultRotation;

    @Surface.Rotation
    private int mDefaultDisplayCurrentRotation;

    @VisibleForTesting
    @NonNull
    final SparseArray<Runnable> mDefaultDisplayRotationChangedCallbacks = new SparseArray<>();

    /**
     * Notifies clients when the default display rotation changes.
     */
    void onDefaultDisplayRotationChanged(@Surface.Rotation int rotation) {
        mDefaultDisplayCurrentRotation = rotation;

        for (int i = 0; i < mDefaultDisplayRotationChangedCallbacks.size(); i++) {
            mDefaultDisplayRotationChangedCallbacks.valueAt(i).run();
        }
    }

    void setDefaultDisplayDefaultRotation(@Surface.Rotation int rotation) {
        mDefaultDisplayDefaultRotation = rotation;
    }

    @Surface.Rotation
    int getDefaultDisplayCurrentRotation() {
        return mDefaultDisplayCurrentRotation;
    }

    /**
     * Register a callback to be notified when the default display's rotation changes. Clients can
     * query the default display's current rotation via {@link #getDefaultDisplayCurrentRotation()}.
     */
    void setDefaultDisplayRotationChangedCallback(int displayId, @NonNull Runnable callback) {
        mDefaultDisplayRotationChangedCallbacks.put(displayId, callback);

        if (mDefaultDisplayCurrentRotation != mDefaultDisplayDefaultRotation) {
            callback.run();
        }
    }

    /**
     * Removes the callback that was added via
     * {@link #setDefaultDisplayRotationChangedCallback(int, Runnable)}.
     */
    void removeDefaultDisplayRotationChangedCallback(int displayId, @NonNull Runnable callback) {
        Runnable currentCallback = mDefaultDisplayRotationChangedCallbacks.get(displayId);
        if (!callback.equals(currentCallback)) {
            Slog.w(TAG, "Attempted to remove non-matching callback. DisplayId: " + displayId);
            return;
        }
        mDefaultDisplayRotationChangedCallbacks.remove(displayId);
    }

    static boolean isSecondaryInternalDisplay(@NonNull DisplayContent displayContent) {
        if (displayContent.isDefaultDisplay) {
            return false;
        } else if (displayContent.mDisplay == null) {
            return false;
        }
        return displayContent.mDisplay.getType() == Display.TYPE_INTERNAL;
    }
}
