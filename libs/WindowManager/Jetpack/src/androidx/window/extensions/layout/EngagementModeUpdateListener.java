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

package androidx.window.extensions.layout;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Interface for listening to engagement mode updates.
 */
interface EngagementModeUpdateListener {
    /**
     * Registers for engagement mode updates for a given display.
     * @param displayId The id of the display to register for.
     */
    void register(int displayId);

    /**
     * Unregisters for engagement mode updates.
     */
    void unregister();

    /**
     * Factory method to create an {@link EngagementModeUpdateListener}.
     *
     * @param context the application context.
     * @param onEngagementModeChangedCallback callback to be invoked when the engagement mode
     *                                        changes.
     * @param activeDisplayIdsSupplier a supplier for the set of active display IDs.
     * @return an instance of {@link EngagementModeUpdateListener}.
     */
    @NonNull
    static EngagementModeUpdateListener create(@NonNull Context context,
            @NonNull BiConsumer<Integer, Integer> onEngagementModeChangedCallback,
            @NonNull Supplier<Set<Integer>> activeDisplayIdsSupplier) {
        if (com.android.window.flags.Flags.deviceEngagementMode()) {
            return new SystemApiEngagementModeListener(context, onEngagementModeChangedCallback);
        } else {
            return new SideChannelEngagementModeListener(context, onEngagementModeChangedCallback,
                    activeDisplayIdsSupplier);
        }
    }
}
