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
package com.android.server.companion.datatransfer.crossdevicesync.feature.mode;

/** Utility class containing mode sync metadata constants. */
public final class ModeSyncMetadata {

    /** Metadata key storing a boolean value indicating if contextual mode sync is enabled. */
    public static final String METADATA_CONTEXTUAL_MODE_SYNC_ENABLED =
            "contextual_mode_sync_enabled";

    /** Metadata key storing a boolean value indicating if contextual mode sync is supported. */
    public static final String METADATA_CONTEXTUAL_MODE_SYNC_SUPPORTED =
            "contextual_mode_sync_supported";

    /**
     * Metadata key storing an integer value representing the contextual modes document schema
     * version. See {@link ModeSyncDocs#CURRENT_SCHEMA_VERSION}.
     */
    public static final String METADATA_CONTEXTUAL_MODES_DOC_SCHEMA_VERSION =
            "contextual_modes_doc_schema_version";

    private ModeSyncMetadata() {}
}
