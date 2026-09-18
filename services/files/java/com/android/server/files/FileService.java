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
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresNoPermission;
import android.annotation.RequiresPermission;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManagerInternal;
import android.os.Binder;
import android.os.UserHandle;
import android.os.storage.FileManager;
import android.os.storage.IFileService;
import android.os.storage.operations.FileOperationEnqueueResult;
import android.os.storage.operations.FileOperationRequest;
import android.os.storage.operations.FileOperationResult;
import android.util.ArrayMap;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.LocalServices;
import com.android.server.SystemService;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for handling file operations.
 *
 * <p>See {@code services/files/README.md} for architectural details.
 */
@FlaggedApi(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public class FileService extends SystemService {
    private static final String TAG = "FileService";
    private static final int MAX_PENDING_REQUESTS = 50;
    private static final int MAX_HISTORY_SIZE = MAX_PENDING_REQUESTS + 10;

    @VisibleForTesting final FileServiceStub mBinderService;

    // Active requests, used for admission control to prevent system overload.
    @VisibleForTesting
    final Map<String, RequestContext> mPendingRequests =
            Collections.synchronizedMap(new ArrayMap<>());

    // History of operation results. Keeps track of active and recently completed/failed
    // operations.
    // Uses an LRU eviction policy but protects active operations from being removed.
    @VisibleForTesting
    final Map<String, FileOperationResult> mResults =
            Collections.synchronizedMap(
                    new LinkedHashMap<String, FileOperationResult>(
                            MAX_HISTORY_SIZE + 1, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(
                                Map.Entry<String, FileOperationResult> eldest) {
                            return size() > MAX_HISTORY_SIZE
                                    && (eldest.getValue().getStatus()
                                                    == FileOperationResult.STATUS_FINISHED
                                            || eldest.getValue().getStatus()
                                                    == FileOperationResult.STATUS_FAILED);
                        }
                    });

    // Maps request IDs to subscribers listening for completion broadcasts.
    private final Map<String, Set<Subscriber>> mSubscribers = new ConcurrentHashMap<>();

    // Dispatches requests to the appropriate backend processor.
    private final FileOperationDispatcher mDispatcher;

    /** injector for testing. */
    @VisibleForTesting
    public static class Injector {
        public FileOperationDispatcher getFileOperationDispatcher() {
            return new FileOperationDispatcher();
        }

        public int getCallingUid() {
            return Binder.getCallingUid();
        }
    }

    public FileService(Context context) {
        this(context, new Injector());
    }

    @VisibleForTesting
    public FileService(Context context, Injector injector) {
        super(context);
        mBinderService = new FileServiceStub(injector);
        mDispatcher = injector.getFileOperationDispatcher();
        mDispatcher.registerProcessor(new InstalldProcessor(context));
    }

    @Override
    public void onStart() {
        Slog.d(TAG, "onStart: Publishing binder service: " + Context.FILE_SERVICE);
        publishBinderService(Context.FILE_SERVICE, mBinderService);
    }

    /**
     * Holds details of a request currently in the queue or being processed.
     *
     * @param requestId The unique identifier for the request.
     * @param request The original operation request.
     * @param initiatorUid The UID of the app that initiated the request.
     * @param packageName the packageName of the initiator.
     */
    @VisibleForTesting
    record RequestContext(
            String requestId, FileOperationRequest request, int initiatorUid, String packageName) {
        static String getNextUniqueId() {
            return UUID.randomUUID().toString();
        }
    }

    /**
     * Represents a subscriber listening for the completion of a file operation.
     *
     * @param uid The UID of the subscriber.
     * @param packageName The package name of the subscriber.
     */
    private record Subscriber(int uid, String packageName) {}

    private final class FileServiceStub extends IFileService.Stub {
        private final Injector mInjector;

        FileServiceStub(Injector injector) {
            mInjector = injector;
        }

        @Override
        @RequiresNoPermission
        @NonNull
        public FileOperationEnqueueResult enqueueOperation(
                FileOperationRequest request, String packageName) {
            int callingUid = mInjector.getCallingUid();
            Slog.d(
                    TAG,
                    "enqueueOperation: uid="
                            + callingUid
                            + " src="
                            + request.getSource()
                            + " tgt="
                            + request.getTarget());

            final long token = Binder.clearCallingIdentity();
            try {
                // Validate the source and target of the request.
                final boolean sourceIsValid = request.getSource().isValid();
                final boolean targetIsValid = request.getTarget().isValid();

                if (!sourceIsValid) {
                    return new FileOperationEnqueueResult(
                            FileOperationResult.ERROR_UNSUPPORTED_SOURCE);
                }
                if (!targetIsValid) {
                    return new FileOperationEnqueueResult(
                            FileOperationResult.ERROR_UNSUPPORTED_TARGET);
                }

                // Fail early if no processor can handle this request.
                if (mDispatcher.findProcessor(request) == null) {
                    Slog.w(TAG, "No processor found for request");
                    return new FileOperationEnqueueResult(
                            FileOperationResult.ERROR_INVALID_REQUEST);
                }

                // Admission control: check if the system is too busy.
                synchronized (mPendingRequests) {
                    if (mPendingRequests.size() >= MAX_PENDING_REQUESTS) {
                        return new FileOperationEnqueueResult(FileOperationResult.ERROR_BUSY);
                    }
                }

                // Generate a unique ID for the request.
                String requestId = RequestContext.getNextUniqueId();

                // Initialize result state.
                FileOperationResult initialResult =
                        new FileOperationResult.Builder(requestId, request)
                                .setStatus(FileOperationResult.STATUS_QUEUED)
                                .setErrorCode(FileOperationResult.ERROR_UNKNOWN)
                                .build();
                mResults.put(requestId, initialResult);

                RequestContext ctx =
                        new RequestContext(requestId, request, callingUid, packageName);

                try {
                    // Dispatch the operation asynchronously.
                    mDispatcher.dispatch(
                            ctx,
                            result -> {
                                final long callbackToken = Binder.clearCallingIdentity();
                                try {
                                    // Update result map on status changes.
                                    mResults.put(requestId, result);
                                    if (result.getStatus() == FileOperationResult.STATUS_FINISHED
                                            || result.getStatus()
                                                    == FileOperationResult.STATUS_FAILED) {
                                        // Request is done, remove from active set and notify
                                        // listeners.
                                        mPendingRequests.remove(requestId);
                                        notifySubscribers(requestId, result);
                                    }
                                } finally {
                                    Binder.restoreCallingIdentity(callbackToken);
                                }
                            });
                    mPendingRequests.put(requestId, ctx);
                } catch (Exception e) {
                    Slog.e(TAG, "Exception while enqueuing operation: " + requestId, e);
                    return new FileOperationEnqueueResult(FileOperationResult.ERROR_UNKNOWN);
                }

                return new FileOperationEnqueueResult(requestId);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        @Override
        @RequiresNoPermission
        @Nullable
        public FileOperationResult fetchResult(String requestId) {
            return mResults.get(requestId);
        }

        @Override
        @RequiresNoPermission
        public void registerCompletionListener(String requestId, String packageName) {
            int callingUid = mInjector.getCallingUid();
            final long token = Binder.clearCallingIdentity();
            try {
                final PackageManagerInternal packageManagerInternal =
                        LocalServices.getService(PackageManagerInternal.class);
                if (packageManagerInternal == null) {
                    Slog.wtf(
                            TAG,
                            "Failed to acquire PackageManagerInternal in"
                                    + " FileService#registerCompletionListener");
                    return;
                }
                if (!packageManagerInternal.isSameApp(
                        packageName, callingUid, UserHandle.getUserId(callingUid))) {
                    throw new SecurityException(
                            "Package " + packageName + " does not belong to uid " + callingUid);
                }
                synchronized (mSubscribers) {
                    FileOperationResult result = mResults.get(requestId);
                    if (result != null
                            && (result.getStatus() == FileOperationResult.STATUS_FINISHED
                                    || result.getStatus() == FileOperationResult.STATUS_FAILED)) {
                        // Operation already finished, notify immediately.
                        notifySingleSubscriber(
                                new Subscriber(callingUid, packageName), requestId, result);
                        return;
                    }

                    mSubscribers
                            .computeIfAbsent(
                                    requestId, k -> Collections.synchronizedSet(new HashSet<>()))
                            .add(new Subscriber(callingUid, packageName));
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        @Override
        @RequiresNoPermission
        public void unregisterCompletionListener(String requestId) {
            int callingUid = mInjector.getCallingUid();
            final long token = Binder.clearCallingIdentity();
            try {
                synchronized (mSubscribers) {
                    Set<Subscriber> subscribers = mSubscribers.get(requestId);
                    if (subscribers != null) {
                        subscribers.removeIf(sub -> sub.uid() == callingUid);
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }
    }

    @RequiresPermission(android.Manifest.permission.INTERACT_ACROSS_USERS)
    private void notifySubscribers(String requestId, FileOperationResult result) {
        Set<Subscriber> subscribers;
        synchronized (mSubscribers) {
            subscribers = mSubscribers.remove(requestId);
        }

        if (subscribers == null) return;

        for (Subscriber sub : subscribers) {
            notifySingleSubscriber(sub, requestId, result);
        }
    }

    @RequiresPermission(android.Manifest.permission.INTERACT_ACROSS_USERS)
    private void notifySingleSubscriber(
            Subscriber sub, String requestId, FileOperationResult result) {
        Intent intent = new Intent(FileManager.ACTION_FILE_OPERATION_COMPLETED);
        intent.setPackage(sub.packageName());
        intent.putExtra(FileManager.EXTRA_REQUEST_ID, requestId);
        intent.putExtra(FileManager.EXTRA_RESULT, result);

        getContext().sendBroadcastAsUser(intent, UserHandle.getUserHandleForUid(sub.uid()));
    }
}
