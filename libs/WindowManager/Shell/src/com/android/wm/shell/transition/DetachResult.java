/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.wm.shell.transition;

import android.annotation.NonNull;
import android.os.Handler;
import android.window.WindowAnimationState;

import com.android.internal.infra.AndroidFuture;

import java.util.List;

/**
 * The result of a {@link TransitionMixpatcher} detach operation. It can represent an
 * immediate/synchronous result or act as a Promise that the detachment will be completed later.
 */
public class DetachResult extends AndroidFuture<List<WindowAnimationState>> {
    /** No result yet, so this is a promise by default. */
    private DetachResult() {
    }

    /** Construct an immediate/synchronous result. */
    public DetachResult(@NonNull List<WindowAnimationState> states) {
        super.complete(states);
    }

    /**
     * Builds an async result that promises to complete later. In this case, the creator is
     * expected to call {@link #complete} when finished.
     *
     * @deprecated Prefer a synchronous detach if possible.
     */
    @Deprecated
    public static DetachResult promise(@NonNull Handler timeoutHandler) {
        final DetachResult out = new DetachResult();
        out.setTimeoutHandler(timeoutHandler);
        return out;
    }
}
