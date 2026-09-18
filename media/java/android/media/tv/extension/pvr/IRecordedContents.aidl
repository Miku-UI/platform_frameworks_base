/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.media.tv.extension.pvr;

import android.media.tv.extension.pvr.IDeleteRecordedContentsCallback;
import android.media.tv.extension.pvr.IGetInfoRecordedContentsCallback;


/**
 * @hide
 */
interface IRecordedContents {
    /**
     * Asynchronously deletes one or more recorded contents identified by their URIs.
     * Using callback to notify the result or any errors during the deletion process.
     *
     * @param contentUris An array of URIs from the recorded_programs table in tv.db.
     * @param callback    An  IDeleteRecordedContentsCallback that will be invoked
     *                    to notify the caller of the deletion results.
     */
    void deleteRecordedContents(in String[] contentUri,
        in IDeleteRecordedContentsCallback callback);
    /**
     * Synchronously gets the channel lock status for a specific piece of recorded content.
     *
     * This method blocks until the lock status is retrieved. For a non-blocking version, use
     * {@link #getRecordedContentLockStatus(Uri, IGetInfoRecordedContentsCallback)}.
     *
     * @param contentUri The URI from the recorded_programs table in tv.db.
     * @return {@code RecordConstants.LockStatus} 0 for unlocked, 1 for locked.
     */
    int getRecordedContentsLockInfoSync(String contentUri);
    /**
     * Asynchronously gets the channel lock status for a specific piece of recorded content.
     *
     * @param contentUri The URI from the from the recorded_programs table in tv.db.
     * @param callback   An IGetInfoRecordedContentsCallback that will be invoked
     *                   with the lock status result.
     */
    void getRecordedContentsLockInfoAsync(String contentUri,
        in IGetInfoRecordedContentsCallback callback);
}
