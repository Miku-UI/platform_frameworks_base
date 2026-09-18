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

package com.android.server.serial;

import android.util.ArrayMap;
import android.util.SparseBooleanArray;

import java.util.concurrent.Future;

/**
 * This interface loads and saves persistent port access.
 */
public interface PortAccessSerializerInterface {
    /**
     * Loads the persistent port access for the user
     *
     * @param userId the ID of the user
     * @return the {@link Future} that returns the access map
     */
    Future<ArrayMap<String, SparseBooleanArray>> loadPortAccessForUser(int userId);

    /**
     * Saves the persistent port access for the user
     *
     * @param userId the ID of the user
     * @param accessMap the access map to save
     * @return the {@link Future} that returns {@code null} when the save task is finished
     */
    Future<Void> savePortAccessForUser(int userId, ArrayMap<String, SparseBooleanArray> accessMap);
}
