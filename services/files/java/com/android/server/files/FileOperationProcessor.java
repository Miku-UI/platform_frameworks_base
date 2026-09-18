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

package com.android.server.files;

import android.os.storage.operations.FileOperationRequest;
import android.os.storage.operations.FileOperationResult;

import java.io.IOException;

/** Interface for processing file operations. */
public interface FileOperationProcessor {

    /** Callback for operation status updates. */
    @FunctionalInterface
    interface StatusCallback {
        /**
         * Called when the progress of the operation changes.
         *
         * @param result The new operation result.
         */
        void onResult(FileOperationResult result);
    }

    /**
     * Performs the requested file operation.
     *
     * @param ctx The request context containing the operation details.
     * @param callback The callback to report progress and status.
     * @throws IOException If the operation fails.
     */
    void process(FileService.RequestContext ctx, StatusCallback callback)
            throws IOException;

    /**
     * Checks if this processor can handle the given request.
     *
     * @param request The request to check.
     * @return True if this processor can handle the request, false otherwise.
     */
    boolean canHandle(FileOperationRequest request);
}
