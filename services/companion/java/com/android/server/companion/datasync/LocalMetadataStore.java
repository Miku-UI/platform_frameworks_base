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

package com.android.server.companion.datasync;

import com.android.server.companion.utils.PersistableBundleStore;

/**
 * This store manages the cache and disk data for data sync.
 *
 * Metadata is stored in a per-user file named "datasync_metadata.xml".
 * The file is stored in the device-encrypted storage. The file is created lazily when the metadata
 * is first set for a user.
 */
public class LocalMetadataStore extends PersistableBundleStore {

    private static final String TAG = "CDM_LocalMetadataStore";
    // A binary file w/o file extension
    private static final String FILE_NAME = "cdm_local_metadata";

    public String getTag() {
        return TAG;
    }

    public String getFileName() {
        return FILE_NAME;
    }

    public LocalMetadataStore() {
        super();
    }
}
