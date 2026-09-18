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

package android.media.tv.extension.servicedb;

import android.os.Bundle;

/**
 * Listener interface for receiving callbacks from an import session.
 * @hide
 */
interface IServiceListImportListener {
    /**
     * Called when the final import process is complete.
     * <p>
     * This is triggered after calling #importServiceList(pfd).
     *
     * @param importResult The result of the import operation, @ServicedbConstants.ResultCode.
     */
    void onImported(int importResult);
    /**
     * Called when the preload phase is complete.
     * <p>
     * This is typically triggered after calling #preload(pfd).
     *
     * @param preloadResult The result of the preload operation, @ServicedbConstants.ResultCode.
     */
    void onPreloaded(int preloadResult);
}