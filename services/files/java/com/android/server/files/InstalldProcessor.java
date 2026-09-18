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

import static com.android.os.privatecompute.PrivateComputeAtomsLog.PCC_DATA_MIGRATION_STATE_CHANGED__MIGRATION_STATE__DATA_MIGRATION_COMPLETE;
import static com.android.os.privatecompute.PrivateComputeAtomsLog.PCC_DATA_MIGRATION_STATE_CHANGED__MIGRATION_STATE__DATA_MIGRATION_FAILED;
import static com.android.os.privatecompute.PrivateComputeAtomsLog.PCC_DATA_MIGRATION_STATE_CHANGED__SOURCE_TYPE__APP_DATA_FILE_SOURCE;
import static com.android.os.privatecompute.PrivateComputeAtomsLog.PCC_DATA_MIGRATION_STATE_CHANGED__TARGET_TYPE__TARGET_PCC;

import android.annotation.RequiresNoPermission;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.IInstalld;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.storage.operations.FileOperationRequest;
import android.os.storage.operations.FileOperationResult;
import android.os.storage.operations.sources.AppDataFileSource;
import android.os.storage.operations.targets.PccTarget;
import android.util.Pair;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.pm.Installer;
import com.android.server.pm.Installer.InstallerException;
import com.android.server.privatecompute.PrivateComputeStatsLogUtil;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Processor for private app data file operations (via installd). */
public class InstalldProcessor implements FileOperationProcessor {
    private static final String TAG = "InstalldProcessor";

    private static final Set<Pair<Class<?>, Class<?>>> SUPPORTED_PAIRS = new HashSet<>();

    private final Context mContext;
    private final Installer mInstaller;

    static {
        SUPPORTED_PAIRS.add(new Pair<>(AppDataFileSource.class, PccTarget.class));
    }

    public InstalldProcessor(Context context) {
        this(context, new Installer(context));
        mInstaller.onStart();
    }

    @VisibleForTesting
    InstalldProcessor(Context context, Installer installer) {
        mContext = context;
        mInstaller = installer;
    }

    private static final class InstalldCallback extends IInstalld.IAppDataOperationCallback.Stub {
        final StatusCallback mStatusCallback;

        final FileOperationResult.Builder mResultBuilder;

        InstalldCallback(FileService.RequestContext ctx, StatusCallback callback) {
            mStatusCallback = callback;
            mResultBuilder = new FileOperationResult.Builder(ctx.requestId(), ctx.request());
        }

        @Override
        @RequiresNoPermission
        public void onStatusChanged(int status, String message, String[] failedFiles)
                throws RemoteException {
            if (failedFiles != null) {
                mResultBuilder.setFailedPaths(Arrays.asList(failedFiles));
            }
            switch (status) {
                case IInstalld.IAppDataOperationCallback.STATUS_RUNNING -> {
                    mResultBuilder.setStatus(FileOperationResult.STATUS_IN_PROGRESS);
                    mStatusCallback.onResult(mResultBuilder.build());
                }
                case IInstalld.IAppDataOperationCallback.STATUS_FAILURE -> {
                    mResultBuilder.setStatus(FileOperationResult.STATUS_FAILED);
                    mResultBuilder.setErrorCode(FileOperationResult.ERROR_UNKNOWN);
                    mResultBuilder.setErrorMessage(message);
                    mStatusCallback.onResult(mResultBuilder.build());
                }
                case IInstalld.IAppDataOperationCallback.STATUS_SUCCESS -> {
                    mResultBuilder.setStatus(FileOperationResult.STATUS_FINISHED);
                    mStatusCallback.onResult(mResultBuilder.build());
                }
                default -> {
                    Slog.w(TAG, "Unknown result for installd operation: " + status);
                    mResultBuilder.setStatus(FileOperationResult.STATUS_UNKNOWN);
                    mStatusCallback.onResult(mResultBuilder.build());
                }
            }
        }
    }

    /**
     * Dispatches the file operation request to installd for processing.
     *
     * @param ctx The request context containing metadata about the operation.
     * @param callback The callback to notify when the operation status changes.
     * @throws IOException If a local I/O error occurs before the operation is dispatched.
     */
    @Override
    public void process(FileService.RequestContext ctx, StatusCallback callback)
            throws IOException {
        Slog.d(TAG, "Processing via InstalldProcessor for uid " + ctx.initiatorUid());
        final FileOperationRequest req = ctx.request();
        final String requestId = ctx.requestId();
        final FileOperationResult.Builder result = new FileOperationResult.Builder(requestId, req);

        if (!(req.getSource() instanceof AppDataFileSource source)) {
            result.setStatus(FileOperationResult.STATUS_FAILED);
            result.setErrorCode(FileOperationResult.ERROR_UNSUPPORTED_SOURCE);
            callback.onResult(result.build());
            return;
        }
        if (!(req.getTarget() instanceof PccTarget target)) {
            result.setStatus(FileOperationResult.STATUS_FAILED);
            result.setErrorCode(FileOperationResult.ERROR_UNSUPPORTED_TARGET);
            callback.onResult(result.build());
            return;
        }

        final int userId = UserHandle.getUserId(ctx.initiatorUid());

        final PackageManager pm = mContext.getPackageManager();
        ApplicationInfo aInfo;
        try {
            aInfo = pm.getApplicationInfoAsUser(ctx.packageName(),
                    PackageManager.ApplicationInfoFlags.of(0), userId);
        } catch (PackageManager.NameNotFoundException e) {
            result.setStatus(FileOperationResult.STATUS_FAILED);
            result.setErrorCode(FileOperationResult.ERROR_INVALID_REQUEST);
            result.setErrorMessage("Package not found: " + ctx.packageName());
            callback.onResult(result.build());
            return;
        }

        if (!Process.isPrivateComputeCoreUid(aInfo.pccUid)) {
            result.setStatus(FileOperationResult.STATUS_FAILED);
            result.setErrorCode(FileOperationResult.ERROR_INVALID_REQUEST);
            result.setErrorMessage("Package " + ctx.packageName()
                    + " does not have a valid PCC uid");
            callback.onResult(result.build());
            return;
        }

        final File sourcePath = source.getFile();
        final File deDirectory = Environment.getDataUserDeDirectory(aInfo.volumeUuid, userId);
        final boolean isDeviceEncrypted =
                sourcePath.getAbsolutePath().startsWith(deDirectory.getAbsolutePath());
        final File destPath = target.getTargetPath(
                aInfo.volumeUuid, isDeviceEncrypted, userId, ctx.packageName());

        if (destPath == null) {
            result.setStatus(FileOperationResult.STATUS_FAILED);
            result.setErrorCode(FileOperationResult.ERROR_INVALID_REQUEST);
            result.setErrorMessage("Could not construction destination path for "
                    + ctx.packageName());
            callback.onResult(result.build());
            return;
        }
        try {
            final StatusCallback loggingWrapperCallback = new StatusCallback() {
                @Override
                public void onResult(FileOperationResult res) {
                    if (res.getStatus() == FileOperationResult.STATUS_FINISHED) {
                        PrivateComputeStatsLogUtil.logPccDataMigrationStateChanged(
                                PCC_DATA_MIGRATION_STATE_CHANGED__MIGRATION_STATE__DATA_MIGRATION_COMPLETE,
                                PCC_DATA_MIGRATION_STATE_CHANGED__SOURCE_TYPE__APP_DATA_FILE_SOURCE,
                                PCC_DATA_MIGRATION_STATE_CHANGED__TARGET_TYPE__TARGET_PCC);
                    } else if (res.getStatus() == FileOperationResult.STATUS_FAILED) {
                        PrivateComputeStatsLogUtil.logPccDataMigrationStateChanged(
                                PCC_DATA_MIGRATION_STATE_CHANGED__MIGRATION_STATE__DATA_MIGRATION_FAILED,
                                PCC_DATA_MIGRATION_STATE_CHANGED__SOURCE_TYPE__APP_DATA_FILE_SOURCE,
                                PCC_DATA_MIGRATION_STATE_CHANGED__TARGET_TYPE__TARGET_PCC);
                    }
                    callback.onResult(res);
                }
            };

            final InstalldCallback installdCallback = new InstalldCallback(ctx,
                    loggingWrapperCallback);
            final int appId = UserHandle.getAppId(aInfo.pccUid);
            switch (req.getMode()) {
                case FileOperationRequest.OPERATION_MOVE -> {
                    mInstaller.moveAppDataPath(aInfo.volumeUuid, sourcePath.getPath(),
                            destPath.getPath(), userId, appId, aInfo.seInfo, 0,
                            ctx.initiatorUid(), installdCallback);

                }
                case FileOperationRequest.OPERATION_COPY -> {
                    mInstaller.copyAppDataPath(aInfo.volumeUuid, sourcePath.getPath(),
                            destPath.getPath(), userId, appId, aInfo.seInfo, 0,
                            ctx.initiatorUid(), installdCallback);
                }
                default -> {
                    result.setStatus(FileOperationResult.STATUS_UNKNOWN);
                    callback.onResult(result.build());
                }
            }
        } catch (InstallerException e) {
            Slog.e(TAG, "Exception calling installer: " + e);
            result.setStatus(FileOperationResult.STATUS_FAILED);
            result.setErrorCode(FileOperationResult.ERROR_UNKNOWN);
            result.setErrorMessage("Exception calling installer: " + e.getMessage());
            callback.onResult(result.build());
        }
    }

    @Override
    public boolean canHandle(FileOperationRequest request) {
        return SUPPORTED_PAIRS.contains(
                new Pair<>(request.getSource().getClass(), request.getTarget().getClass()));
    }
}
