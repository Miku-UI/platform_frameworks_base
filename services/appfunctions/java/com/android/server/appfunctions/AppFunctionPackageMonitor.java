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

package com.android.server.appfunctions;

import android.annotation.MainThread;
import android.annotation.NonNull;
import android.app.appfunctions.AppFunctionRuntimeMetadata;
import android.app.appsearch.AppSearchManager;
import android.app.appsearch.PutDocumentsRequest;
import android.app.appsearch.SearchResult;
import android.app.appsearch.SearchSpec;
import android.content.Context;
import android.os.HandlerThread;
import android.os.Process;
import android.os.UserHandle;
import android.util.Slog;

import com.android.internal.content.PackageMonitor;
import com.android.internal.os.BackgroundThread;
import com.android.server.SystemService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Monitors package events and manages {@link AppFunctionRuntimeMetadata} entries stored in
 * AppSearch when package data is cleared.
 */
public class AppFunctionPackageMonitor extends PackageMonitor {
    private static final String TAG = AppFunctionPackageMonitor.class.getSimpleName();

    private final AppSearchManager mAppSearchManager;

    /**
     * Creates a new AppFunctionPackageMonitor instance.
     *
     * @param context The context used to retrieve {@link AppSearchManager}.
     * @param userHandle The user whose package events should be monitored.
     */
    public AppFunctionPackageMonitor(@NonNull Context context, @NonNull UserHandle userHandle) {
        super(/* supportsPackageRestartQuery= */ true);
        mAppSearchManager =
                context.createContextAsUser(userHandle, /* flags= */ 0)
                        .getSystemService(AppSearchManager.class);
    }

    /**
     * Callback triggered when an app's data is cleared. This will attempt to find all {@link
     * AppFunctionRuntimeMetadata} entries associated with the given package and reset them in
     * AppSearch.
     *
     * @param packageName The name of the package whose data was cleared.
     * @param uid The UID of the package.
     */
    @Override
    public void onPackageDataCleared(String packageName, int uid) {
        String packageQuery =
                AppFunctionRuntimeMetadata.PROPERTY_PACKAGE_NAME + ":\"" + packageName + "\"";
        SearchSpec runtimeSearchSpec =
                new SearchSpec.Builder()
                        .addFilterSchemas(AppFunctionRuntimeMetadata.RUNTIME_SCHEMA_TYPE)
                        .setVerbatimSearchEnabled(true)
                        .build();
        AppSearchManager.SearchContext searchContext =
                new AppSearchManager.SearchContext.Builder(
                                AppFunctionRuntimeMetadata.APP_FUNCTION_RUNTIME_METADATA_DB)
                        .build();

        try (FutureAppSearchSession searchSession =
                new FutureAppSearchSessionImpl(mAppSearchManager, Runnable::run, searchContext)) {
            List<AppFunctionRuntimeMetadata> updatedRuntimeMetadataList = new ArrayList<>();

            try (FutureSearchResults futureSearchResults =
                    searchSession.search(packageQuery, runtimeSearchSpec).get()) {

                List<SearchResult> results;
                do {
                    results = futureSearchResults.getNextPage().get();
                    for (SearchResult result : results) {
                        String functionId =
                                result.getGenericDocument()
                                        .getPropertyString(
                                                AppFunctionRuntimeMetadata.PROPERTY_FUNCTION_ID);
                        updatedRuntimeMetadataList.add(
                                new AppFunctionRuntimeMetadata.Builder(
                                                packageName, Objects.requireNonNull(functionId))
                                        .build());
                    }
                } while (!results.isEmpty());

                PutDocumentsRequest putRequest =
                        new PutDocumentsRequest.Builder()
                                .addGenericDocuments(updatedRuntimeMetadataList)
                                .build();

                searchSession.put(putRequest).get();
            } catch (Exception e) {
                Slog.e(
                        TAG,
                        "Unable to reset the AppFunctionRuntimeMetadata when clearing data for "
                                + "package: "
                                + packageName,
                        e);
            }
        }
    }

    /**
     * Registers a {@link AppFunctionPackageMonitor} instance for the given user context. This
     * allows it to observe package-related events and react accordingly.
     *
     * @param context The base context.
     * @param user The target user whose package events should be monitored.
     */
    @MainThread
    public static PackageMonitor registerPackageMonitorForUser(
            @NonNull Context context, @NonNull SystemService.TargetUser user) {
        AppFunctionPackageMonitor monitor =
                new AppFunctionPackageMonitor(context, user.getUserHandle());

        monitor.register(
                context, user.getUserHandle(), BackgroundThread.getHandler());
        return monitor;
    }
}
