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
package com.android.server.companion.datatransfer.crossdevicesync.data;

import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Denotes the type of a record, which can be a register, a set, or an unmerged entry. */
@Retention(RetentionPolicy.SOURCE)
@IntDef({RecordType.TYPE_REGISTER, RecordType.TYPE_SET, RecordType.TYPE_UNMERGED})
public @interface RecordType {
    /**
     * A record type that represents a single, last-write-wins value. This type corresponds to
     * {@link SharedDataStore.Record}.
     */
    int TYPE_REGISTER = 0;

    /**
     * A record type that represents a collection of unique values. This type corresponds to {@link
     * SharedDataStore.SetRecord}.
     */
    int TYPE_SET = 1;

    /**
     * A record type that represents a value that will not merge. Because the data is unmerged, each
     * device writes into its own copy. As a result, the record maintains a map between device node
     * id and the last known value. This type corresponds to {@link SharedDataStore.UnmergedRecord}.
     */
    int TYPE_UNMERGED = 2;
}
