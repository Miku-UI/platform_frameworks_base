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

import android.annotation.FlaggedApi;
import android.os.storage.operations.FileOperationRequest;
import android.os.storage.operations.FileOperationResult;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Routes file operation requests to the appropriate processor. */
@FlaggedApi(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public class FileOperationDispatcher {
    private static final String TAG = "FileOperationDispatcher";

    @GuardedBy("mProcessors")
    private final List<FileOperationProcessor> mProcessors = new ArrayList<>();

    // A single threaded executor to process requests sequentially.
    private final ExecutorService mQueueExecutor = Executors.newSingleThreadExecutor();

    /** Registers a new processor to handle file operations. */
    public void registerProcessor(FileOperationProcessor processor) {
        synchronized (mProcessors) {
            mProcessors.add(processor);
        }
    }

    /**
     * Dispatches the request to a suitable processor. The operation is performed asynchronously on
     * a background thread.
     */
    public void dispatch(
            FileService.RequestContext ctx, FileOperationProcessor.StatusCallback callback) {
        mQueueExecutor.execute(
                () -> {
                    try {
                        FileOperationProcessor processor = findProcessor(ctx.request());
                        if (processor != null) {
                            Slog.d(
                                    TAG,
                                    "Dispatching request "
                                            + ctx.requestId()
                                            + " to "
                                            + processor.getClass().getSimpleName());
                            processor.process(ctx, callback);
                        } else {
                            Slog.e(TAG, "No processor found for request " + ctx.requestId());
                            FileOperationResult result =
                                    new FileOperationResult.Builder(ctx.requestId(), ctx.request())
                                            .setStatus(FileOperationResult.STATUS_FAILED)
                                            .setErrorCode(FileOperationResult.ERROR_UNKNOWN)
                                            .setErrorMessage("No processor found")
                                            .build();
                            callback.onResult(result);
                        }
                    } catch (Exception e) {
                        Slog.e(TAG, "Operation failed: " + ctx.requestId(), e);
                        FileOperationResult result =
                                new FileOperationResult.Builder(ctx.requestId(), ctx.request())
                                        .setStatus(FileOperationResult.STATUS_FAILED)
                                        .setErrorCode(FileOperationResult.ERROR_UNKNOWN)
                                        .setErrorMessage(e.getMessage())
                                        .build();
                        callback.onResult(result);
                    }
                });
    }

    FileOperationProcessor findProcessor(FileOperationRequest req) {
        synchronized (mProcessors) {
            for (FileOperationProcessor processor : mProcessors) {
                if (processor.canHandle(req)) {
                    return processor;
                }
            }
        }
        return null;
    }
}
