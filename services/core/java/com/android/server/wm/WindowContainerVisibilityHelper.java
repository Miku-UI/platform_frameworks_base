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

package com.android.server.wm;


import android.annotation.NonNull;
import android.annotation.Nullable;

/**
 * Centralizes visibility-related logic for window containers. This helper determines visibility
 * states, occlusion, and whether content is filling its container.
 */
interface WindowContainerVisibilityHelper {

    /**
     * Returns the visibility state of the given {@link TaskFragment}.
     *
     * @param current the {@link TaskFragment} to check visibility for.
     * @param starting the currently starting activity or {@code null} if there is none.
     */
    @TaskFragment.TaskFragmentVisibility
    int getTaskFragmentVisibility(@NonNull TaskFragment current, @Nullable ActivityRecord starting);

    /**
     * Whether the given {@link ActivityRecord} should be made visible.
     *
     * @param current the {@link ActivityRecord} to check visibility for.
     * @param ignoringKeyguard if {@code true}, returns the result ignoring the keyguard state.
     */
    boolean shouldActivityBeVisible(@NonNull ActivityRecord current, boolean ignoringKeyguard);

    /**
     * Whether this container or its children have content that fills it.
     *
     * Note: a container that fills its parent may not occlude its siblings, such as when it is
     * translucent.
     *
     * @param current the {@link WindowContainer} to check.
     */
    boolean hasFillingContent(@NonNull WindowContainer current);

    /**
     * Whether the container is opaque.
     *
     * @param current the {@link WindowContainer} to check.
     * @param starting the currently starting activity or {@code null} if there is none.
     * @param ignoringKeyguard if {@code true}, returns the result ignoring the keyguard state.
     * @param ignoringInvisibleActivity if {@code true}, only including visible activities in the
     *                                  calculation.
     * @param ignoringFinishing if {@code true}, only including activities that are not finishing in
     *                          the calculation. Note: when {@code ignoringInvisibleActivity} is
     *                          {@code true}, this param will also be treated as {@code true} since
     *                          finishing activity is invisible; when this param is {@code true}
     *                          while {@code ignoringInvisibleActivity} is {@code false}, it will
     *                          take invisible activity into account, but ignore finishing activity.
     */
    boolean isOpaque(@NonNull WindowContainer<?> current, @Nullable ActivityRecord starting,
            boolean ignoringKeyguard, boolean ignoringInvisibleActivity,
            boolean ignoringFinishing);
}
