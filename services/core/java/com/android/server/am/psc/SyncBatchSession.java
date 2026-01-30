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
package com.android.server.am.psc;

import android.ravenwood.annotation.RavenwoodKeepWholeClass;
import android.util.Slog;

import com.android.server.am.ProcessStateController;

import java.util.function.IntConsumer;

/**
 * A {@link BatchSession} that will synchronously trigger a {@link ProcessStateController} update
 * at the end of the session.
 */
@RavenwoodKeepWholeClass
public class SyncBatchSession extends BatchSession {
    private static final String TAG = "SyncBatchSession";
    private final IntConsumer mFullUpdateRunnable;
    private final IntConsumer mPartialUpdateRunnable;
    private boolean mFullUpdate = false;

    public SyncBatchSession(IntConsumer fullUpdateRunnable, IntConsumer partialUpdateRunnable) {
        mFullUpdateRunnable = fullUpdateRunnable;
        mPartialUpdateRunnable = partialUpdateRunnable;
    }

    private boolean mOutOfBoundsSetFullUpdateWtfArmed = true;
    /** Note that the update at the end of the session needs to be a full update. */
    public void setFullUpdate() {
        if (!isActive()) {
            if (mOutOfBoundsSetFullUpdateWtfArmed) {
                Slog.wtfStack(TAG, "Unexpected setFullUpdate called while session is not active");
                mOutOfBoundsSetFullUpdateWtfArmed = false;
            }
            return;
        }
        mFullUpdate = true;
    }

    @Override
    protected void onClose() {
        if (mFullUpdate) {
            // Full update was triggered, reset the flag and run a full update.
            mFullUpdate = false;
            mFullUpdateRunnable.accept(mUpdateReason);
        } else {
            // Otherwise, run a partial update on anything that may have been enqueued.
            mPartialUpdateRunnable.accept(mUpdateReason);
        }
    }
}

