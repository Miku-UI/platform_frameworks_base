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
package com.android.server.companion.datatransfer.crossdevicesync.metadata;

import com.android.server.companion.datatransfer.crossdevicesync.common.Dumpable;

/** Interface responsible for writing CDM metadata and pushing it to remote devices. */
public interface MetadataPublisher extends Dumpable {

    /** Initializes the metadata publisher. */
    void init();

    /** Destroys the metadata publisher. */
    void destroy();

    /** Puts a boolean metadata and publish it to remote devices. */
    void putBooleanMetaData(int userId, String key, boolean val);

    /** Puts an int metadata and publish it to remote devices. */
    void putIntMetaData(int userId, String key, int val);

    /** Puts a string metadata and publish it to remote devices. */
    void putStringMetaData(int userId, String key, String val);
}
