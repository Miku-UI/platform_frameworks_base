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

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * A no-op implementation of {@link EngagementModeClient} that always returns the default
 * engagement mode.
 */
class NoOpEngagementModeClient implements EngagementModeClient {

    @Override
    public int getEngagementModeFlags() {
        return DEFAULT_ENGAGEMENT_MODE;
    }

    @Override
    public void addUpdateCallback(@NonNull Executor executor, @NonNull Consumer<Integer> callback) {
        // Do nothing
    }

    @Override
    public void removeUpdateCallback(@NonNull Consumer<Integer> callback) {
        // Do nothing
    }
}
