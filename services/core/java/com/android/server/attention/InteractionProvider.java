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

package com.android.server.attention;

import android.attention.InteractionState;

import java.util.List;

/**
 * Interface for interaction data providers. This will be used by AttentionManager to periodically
 * fetch interaction data from other system components like InputManager.
 * @hide
 */
public interface InteractionProvider {
    /**
     * This will be called periodically by the attention-service.
     * @return list of all available user activity types along with most recent time
     * when it happened. The list should include at most one entry per user activity type.
     */
    List<InteractionState> getSourceInteractions();

    /**
     * Called by the attention-service to request a notification when new interaction-data is
     * available to wake the attention-service.
     * In response attention-service may call {@link #getSourceInteractions()} asynchronously.
     */
    void requestWakeupCallback(InteractionWakeupCallback callback);
}
