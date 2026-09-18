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

package com.android.server.companion.datatransfer.continuity;

import android.util.ArrayMap;

/**
 * Maintains a cache of user-specific resources, lazily instantiating them as needed.
 *
 * @param <T> The type of resource.
 */
public abstract class MultiUserResourceCache<T> {

    private final ArrayMap<Integer, T> mResources = new ArrayMap<>(0);

    /**
     * Returns the resource for the given user. If the resource does not exist, it is created.
     *
     * @param userId The user ID of the feature controller.
     * @return The resource for the given user.
     */
    public T getOrCreateResource(int userId) {
        T resource = mResources.get(userId);
        if (resource == null) {
            resource = createResourceForUser(userId);
            mResources.put(userId, resource);
        }
        return resource;
    }

    /**
     * Creates a resource for the given user.
     *
     * @param userId The user ID of the resource.
     * @return The resource for the given user.
     */
    protected abstract T createResourceForUser(int userId);
}
