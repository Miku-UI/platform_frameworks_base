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

/**
 * Local system service implementation for interaction provider service. This will be used by system
 * services to provide interaction data to the attention manager service.
 *
 * @hide Only for use within the system server.
 */
public abstract class InteractionProviderInternal {
    /**
     * Register provider hooks that will be used to fetch user interaction information by the
     * AttentionService from other system components like InputManager. These will be called by the
     * AttentionService periodically.
     */
    public abstract boolean registerInteractionProvider(InteractionProvider provider);

    /**
     * Unregister a previously registered interaction provider. This should be done when provider
     * is terminating or data is no longer available.
     */
    public abstract boolean unregisterInteractionProvider(InteractionProvider provider);
}
