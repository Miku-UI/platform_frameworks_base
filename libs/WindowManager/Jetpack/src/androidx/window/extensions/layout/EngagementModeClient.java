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

import static androidx.window.extensions.layout.WindowLayoutInfo.ENGAGEMENT_MODE_FLAG_AUDIO_ON;
import static androidx.window.extensions.layout.WindowLayoutInfo.ENGAGEMENT_MODE_FLAG_VISUALS_ON;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Interface for a client that provides engagement mode information.
 *
 * TODO(b/444335819): Deprecate this client once migrated to the system API solution.
 */
interface EngagementModeClient {

    /**
     * The default engagement mode, indicating that both visual and audio presentations are active.
     * This is the fallback value used when the engagement mode service is not available or the
     * feature is disabled.
     */
    int DEFAULT_ENGAGEMENT_MODE =
            ENGAGEMENT_MODE_FLAG_VISUALS_ON | ENGAGEMENT_MODE_FLAG_AUDIO_ON;

    /**
     * Returns the current engagement mode flags.
     */
    int getEngagementModeFlags();

    /**
     * Adds a callback to be invoked when the engagement mode changes.
     * @param executor the executor on which the callback will be invoked.
     * @param callback the callback to be invoked.
     */
    void addUpdateCallback(@NonNull Executor executor, @NonNull Consumer<Integer> callback);

    /**
     * Removes a callback that was previously added.
     * @param callback the callback to be removed.
     */
    void removeUpdateCallback(@NonNull Consumer<Integer> callback);
}
