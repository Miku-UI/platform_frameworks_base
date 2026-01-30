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

package com.android.server.am.psc;

/**
 * Interface for tracking the activity state of UIDs based on their running processes.
 * This provides a contract for implementations that store and retrieve
 * {@link UidRecordInternal} objects by UID.
 *
 * TODO(b/425766486): Mark the mutable methods as package-private after moving the user classes
 *   (e.g. OomAdjuster, ProcessList) into psc package.
 */
public interface ActiveUidsInternal {
    /** Associates the specified UID with the given {@link UidRecordInternal}. */
    void put(int uid, UidRecordInternal value);

    /** Removes the mapping for a specified UID from this tracking mechanism. */
    void remove(int uid);

    /** Clears all active UID states. */
    void clear();

    /** Returns the number of active UIDs currently being tracked. */
    int size();

    /**
     * Returns the {@link UidRecordInternal} associated with the specified UID,
     * or {@code null} if no mapping for the UID exists.
     */
    UidRecordInternal get(int uid);

    /** Returns the {@link UidRecordInternal} at the specified index. */
    UidRecordInternal valueAt(int index);

    /** Returns the UID (key) at the specified index. */
    int keyAt(int index);
}
