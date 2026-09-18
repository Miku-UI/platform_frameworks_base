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
import android.os.IBinder;
import android.view.SurfaceControl;
import android.window.TransitionInfo;

/**
 * Inspects a provided {@link TransitionInfo} and chooses animation(s) for a subset of the
 * changes. Used by the {@link TransitionMixpatcher} system.
 *
 * @see TransitionMixpatcher
 */
public interface ITransitionPlanner {
    /**
     * Distributes a subset of changes in {@param plannableInfo} to animators via
     * {@link AnimationPlan#setAnimation}.
     *
     * May optionally perform custom detach preparation via {@link AnimationPlan#detach}
     * or {@link AnimationPlan#detachAsync}. Since this aspect usually cares about containers that
     * are "leaving" the responsibility of this planner (and thus may have already been planned),
     * the {@param fullInfo} is made available.
     *
     * @param plannableInfo Contains changes which haven't been assigned an animation yet.
     * @param fullInfo This is the full info as received from WM (including changes which have
     *                 already been assigned to animations).
     */
    void plan(@NonNull AnimationPlan plan, @NonNull TransitionInfo fullInfo,
            @NonNull IBinder transition, @NonNull TransitionInfo plannableInfo,
            @NonNull SurfaceControl.Transaction startTransaction);

    /** for debugging/logging */
    @NonNull String getDebugName();
}
