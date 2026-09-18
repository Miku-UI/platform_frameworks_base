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

package com.android.server.appfunctions;

import static com.android.server.appfunctions.AppFunctionExecutors.SHARED_BACKGROUND_EXECUTOR;

import android.annotation.NonNull;
import android.app.AppInteractionAttribution;
import android.app.appfunctions.AppFunctionMetadata;
import android.app.appfunctions.ExecuteAppFunctionAidlRequest;
import android.app.appfunctions.ExecuteAppFunctionResponse;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;
import java.util.concurrent.Executor;

/** Wraps AppFunctionsStatsLog. */
public class AppFunctionsLoggerWrapper {
    private static final String TAG = AppFunctionsLoggerWrapper.class.getSimpleName();

    @VisibleForTesting static final int SUCCESS_RESPONSE_CODE = -1;

    @VisibleForTesting static final int INTERACTION_TYPE_UNSPECIFIED = 0;
    @VisibleForTesting static final int INTERACTION_TYPE_OTHER = 1;
    @VisibleForTesting static final int INTERACTION_TYPE_USER_QUERY = 2;
    @VisibleForTesting static final int INTERACTION_TYPE_USER_SCHEDULED = 3;

    @VisibleForTesting static final int FUNCTION_TYPE_UNSPECIFIED = 0;
    @VisibleForTesting static final int FUNCTION_TYPE_STATIC = 1;
    @VisibleForTesting static final int FUNCTION_TYPE_DYNAMIC_GLOBAL = 2;
    @VisibleForTesting static final int FUNCTION_TYPE_DYNAMIC_ACTIVITY = 3;

    private final PackageManager mPackageManager;
    private final Executor mLoggingExecutor;
    private final AppFunctionsLoggerClock mLoggerClock;

    AppFunctionsLoggerWrapper(@NonNull Context context) {
        this(context.getPackageManager(), SHARED_BACKGROUND_EXECUTOR, SystemClock::elapsedRealtime);
    }

    @VisibleForTesting
    AppFunctionsLoggerWrapper(
            @NonNull PackageManager packageManager,
            @NonNull Executor executor,
            AppFunctionsLoggerClock loggerClock) {
        mLoggingExecutor = Objects.requireNonNull(executor);
        mPackageManager = Objects.requireNonNull(packageManager);
        mLoggerClock = loggerClock;
    }

    void logAppFunctionSuccess(
            ExecuteAppFunctionAidlRequest request,
            ExecuteAppFunctionResponse response,
            int callingUid,
            long executionStartTimeMillis,
            @AppFunctionMetadata.AppFunctionType int appFunctionType) {
        logAppFunctionsRequestReported(
                request,
                SUCCESS_RESPONSE_CODE,
                response.getResponseDataSize(),
                callingUid,
                executionStartTimeMillis,
                appFunctionType);
    }

    void logAppFunctionError(
            ExecuteAppFunctionAidlRequest request,
            int errorCode,
            int callingUid,
            long executionStartTimeMillis,
            @AppFunctionMetadata.AppFunctionType int appFunctionType) {
        logAppFunctionsRequestReported(
                request,
                errorCode,
                /* responseSizeBytes= */ 0,
                callingUid,
                executionStartTimeMillis,
                appFunctionType);
    }

    private void logAppFunctionsRequestReported(
            ExecuteAppFunctionAidlRequest request,
            int errorCode,
            int responseSizeBytes,
            int callingUid,
            long executionStartTimeMillis,
            @AppFunctionMetadata.AppFunctionType int appFunctionType) {
        final long e2eRequestLatencyMillis =
                mLoggerClock.getCurrentTimeMillis() - request.getRequestTime();
        final long requestOverheadMillis =
                executionStartTimeMillis > 0
                        ? (executionStartTimeMillis - request.getRequestTime())
                        : e2eRequestLatencyMillis;
        final int functionType;
        switch (appFunctionType) {
            case AppFunctionMetadata.APP_FUNCTION_TYPE_STATIC:
                functionType = FUNCTION_TYPE_STATIC;
                break;
            case AppFunctionMetadata.APP_FUNCTION_TYPE_DYNAMIC_GLOBAL:
                functionType = FUNCTION_TYPE_DYNAMIC_GLOBAL;
                break;
            case AppFunctionMetadata.APP_FUNCTION_TYPE_DYNAMIC_ACTIVITY:
                functionType = FUNCTION_TYPE_DYNAMIC_ACTIVITY;
                break;
            default:
                functionType = FUNCTION_TYPE_UNSPECIFIED;
                break;
        }
        mLoggingExecutor.execute(
                () ->
                        AppFunctionsStatsLog.write(
                                AppFunctionsStatsLog.APP_FUNCTIONS_REQUEST_REPORTED,
                                /* callerPackageUid= */ callingUid,
                                /* targetPackageUid= */ getPackageUid(
                                        request.getClientRequest().getTargetPackageName()),
                                /* errorCode= */ errorCode,
                                /* requestSizeBytes= */ request.getClientRequest()
                                        .getRequestDataSize(),
                                /* responseSizeBytes= */ responseSizeBytes,
                                /* requestDurationMs= */ e2eRequestLatencyMillis,
                                /* requestOverheadMs= */ requestOverheadMillis,
                                /* interactionType= */ getInteractionType(request),
                                /* functionType= */ functionType));
    }

    private int getPackageUid(String packageName) {
        try {
            return mPackageManager.getPackageUid(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Slog.e(TAG, "Package uid not found for " + packageName);
        }
        return 0;
    }

    private int getInteractionType(@NonNull ExecuteAppFunctionAidlRequest aidlRequest) {
        if (!android.app.appfunctions.flags.Flags.enableAppInteractionApi()) {
            return INTERACTION_TYPE_UNSPECIFIED;
        }

        final AppInteractionAttribution attribution =
                aidlRequest.getClientRequest().getAttribution();
        if (attribution == null) {
            return INTERACTION_TYPE_UNSPECIFIED;
        }
        final int interactionType = attribution.getInteractionType();
        return switch (interactionType) {
            case AppInteractionAttribution.INTERACTION_TYPE_OTHER -> INTERACTION_TYPE_OTHER;
            case AppInteractionAttribution.INTERACTION_TYPE_USER_QUERY ->
                    INTERACTION_TYPE_USER_QUERY;
            case AppInteractionAttribution.INTERACTION_TYPE_USER_SCHEDULED ->
                    INTERACTION_TYPE_USER_SCHEDULED;
            default -> INTERACTION_TYPE_UNSPECIFIED;
        };
    }

    /** Wraps a custom clock for easier testing. */
    interface AppFunctionsLoggerClock {
        long getCurrentTimeMillis();
    }
}
