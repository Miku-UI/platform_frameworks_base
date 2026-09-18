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

/**
 * @hide
 */
oneway interface IDeleteRecordedContentsCallback {
    /**
     * Callback method invoked to provide the results of a delete operation.
     *
     * @param contentUris An array of String objects representing the URIs of the contents targeted
     *                    for deletion, where each one is from the recorded_programs table in tv.db.
     * @param result      An array of integers where each element represents the result for the URI
     *                    at the corresponding index in the contentUris array. Each of the result
     *                    value must be in {@code RecordConstants.DeletionResult}.
     */
    void onRecordedContentsDeleted(in String[] contentUri, in int[] result);
}
