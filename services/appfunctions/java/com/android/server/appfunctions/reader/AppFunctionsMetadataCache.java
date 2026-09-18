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

package com.android.server.appfunctions.reader;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.WorkerThread;
import android.app.appfunctions.AppFunctionMetadata;
import android.app.appfunctions.AppFunctionStaticMetadataHelper;
import android.app.appsearch.AppSearchManager;
import android.app.appsearch.PropertyPath;
import android.app.appsearch.SearchResult;
import android.app.appsearch.SearchSpec;
import android.content.Context;
import android.os.Build;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.server.appfunctions.FutureAppSearchSession;
import com.android.server.appfunctions.FutureAppSearchSessionImpl;
import com.android.server.appfunctions.FutureSearchResults;
import com.android.server.appfunctions.NamedThreadFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Provides an in-memory cache for {@code AppFunction} types. This class is thread-safe. */
public class AppFunctionsMetadataCache {

    private static final boolean DEBUG = Build.TYPE.equals("eng");

    private static final String TAG = "AppFuncsMetadataCache";

    private static final SearchSpec APP_SEARCH_FN_SPEC =
            new SearchSpec.Builder()
                    .addFilterSchemas(AppFunctionStaticMetadataHelper.STATIC_SCHEMA_TYPE)
                    .addProjectionPaths(
                            AppFunctionStaticMetadataHelper.STATIC_SCHEMA_TYPE,
                            List.of(
                                    new PropertyPath(AppFunctionMetadata.PROPERTY_SERVICE_NAME),
                                    new PropertyPath(AppFunctionMetadata.PROPERTY_SCOPE)))
                    .setVerbatimSearchEnabled(true)
                    .build();

    private static final String DYNAMIC_APP_FUNCTIONS_QUERY =
            TextUtils.formatSimple(
                    "%s:\"%s\"",
                    AppFunctionMetadata.PROPERTY_SERVICE_NAME,
                    AppFunctionMetadata.DYNAMIC_APP_FUNCTIONS_SERVICE_NAME);

    private final Context mContext;

    private final ExecutorService mExecutor;

    private final Object mCrossUserLock = new Object();

    @GuardedBy("mCrossUserLock")
    private final SparseArray<DynamicFunctionsCache> mPerUserDynamicFunctionsCache =
            new SparseArray<>();

    /** Constructs a new {@link AppFunctionsMetadataCache}. */
    public AppFunctionsMetadataCache(Context context) {
        mContext = context;
        mExecutor =
                Executors.newSingleThreadExecutor(
                        new NamedThreadFactory("AppFunctionCacheExecutors"));
    }

    void populateDataForUser(UserHandle user) {
        synchronized (mCrossUserLock) {
            mPerUserDynamicFunctionsCache.put(user.getIdentifier(), new DynamicFunctionsCache());
        }
        populateAllDynamicAppFunctions(user);
    }

    void removeDataForUser(UserHandle user) {
        synchronized (mCrossUserLock) {
            mPerUserDynamicFunctionsCache.remove(user.getIdentifier());
        }
    }

    /**
     * Returns whether the function is dynamic. Dynamic app-functions are declared on the app level
     * and executed directly via AIDL.
     *
     * @param packageName The package name of the application containing the function.
     * @param functionIdentifier The unique identifier for the function within the package.
     * @return true if the function is dynamic. false otherwise.
     * @throws IllegalArgumentException if the user is not unlocked.
     */
    boolean isDynamicFunction(String packageName, String functionIdentifier, UserHandle user)
            throws IllegalArgumentException {
        synchronized (mCrossUserLock) {
            if (!mPerUserDynamicFunctionsCache.contains(user.getIdentifier())) {
                throw new IllegalArgumentException("User is not unlocked");
            }

            return mPerUserDynamicFunctionsCache
                    .get(user.getIdentifier())
                    .isDynamicFunction(packageName, functionIdentifier);
        }
    }

    /**
     * Returns whether the function is dynamic and global scoped.
     *
     * @param packageName The package name of the application containing the function.
     * @param functionIdentifier The unique identifier for the function within the package.
     * @return true if the function is dynamic and global scoped. false otherwise.
     * @throws IllegalArgumentException if the user is not unlocked.
     */
    boolean isGlobalScopedDynamicFunction(
            String packageName, String functionIdentifier, UserHandle user)
            throws IllegalArgumentException {
        synchronized (mCrossUserLock) {
            if (!mPerUserDynamicFunctionsCache.contains(user.getIdentifier())) {
                throw new IllegalArgumentException("User is not unlocked");
            }
            return mPerUserDynamicFunctionsCache
                    .get(user.getIdentifier())
                    .isGlobalScopedDynamicFunction(packageName, functionIdentifier);
        }
    }

    /**
     * Returns whether the function is dynamic and activity scoped.
     *
     * @param packageName The package name of the application containing the function.
     * @param functionIdentifier The unique identifier for the function within the package.
     * @return true if the function is dynamic and activity scoped. false otherwise.
     * @throws IllegalArgumentException if the user is not unlocked.
     */
    boolean isActivityScopedDynamicFunction(
            String packageName, String functionIdentifier, UserHandle user)
            throws IllegalArgumentException {
        synchronized (mCrossUserLock) {
            if (!mPerUserDynamicFunctionsCache.contains(user.getIdentifier())) {
                throw new IllegalArgumentException("User is not unlocked");
            }
            return mPerUserDynamicFunctionsCache
                    .get(user.getIdentifier())
                    .isActivityScopedDynamicFunction(packageName, functionIdentifier);
        }
    }

    void populateAllDynamicAppFunctions(UserHandle user) {
        var unused =
                mExecutor.submit(
                        () -> {
                            DynamicFunctionsSearchResult dynamicFunctions =
                                    searchForDynamicFunctions(user, /* idsToFilter= */ null);
                            if (dynamicFunctions == null) {
                                return;
                            }

                            int userId = user.getIdentifier();
                            synchronized (mCrossUserLock) {
                                if (!mPerUserDynamicFunctionsCache.contains(userId)) {
                                    Slog.e(
                                            TAG,
                                            "Cache is not available for user "
                                                    + userId
                                                    + ". Ignoring update.");
                                    return;
                                }

                                mPerUserDynamicFunctionsCache.put(
                                        userId,
                                        new DynamicFunctionsCache(
                                                dynamicFunctions.mGlobalScopeFunctions,
                                                dynamicFunctions.mActivityScopeFunctions));

                                maybeLogForDebug(
                                        "Cache after update for user " + userId,
                                        mPerUserDynamicFunctionsCache.get(userId));
                            }
                        });
    }

    void updateDynamicFunctions(UserHandle user, @NonNull Set<String> idsToFilter) {
        var unused =
                mExecutor.submit(
                        () -> {
                            DynamicFunctionsSearchResult dynamicFunctions =
                                    searchForDynamicFunctions(user, idsToFilter);
                            if (dynamicFunctions == null) {
                                return;
                            }

                            int userId = user.getIdentifier();
                            synchronized (mCrossUserLock) {
                                if (!mPerUserDynamicFunctionsCache.contains(userId)) {
                                    Slog.w(TAG, "Cache is not available for user " + userId);
                                    return;
                                }

                                mPerUserDynamicFunctionsCache
                                        .get(userId)
                                        .updateCacheLocked(dynamicFunctions);

                                maybeLogForDebug(
                                        "Cache after update for user " + userId,
                                        mPerUserDynamicFunctionsCache.get(userId));
                            }
                        });
    }

    @Nullable
    @WorkerThread
    private DynamicFunctionsSearchResult searchForDynamicFunctions(
            UserHandle user, @Nullable Set<String> idsToFilter) {
        AppSearchManager appSearchManager =
                mContext.createContextAsUser(user, /* flags= */ 0)
                        .getSystemService(AppSearchManager.class);
        if (appSearchManager == null) {
            Slog.e(TAG, "AppSearchManager not found for user: " + user.getIdentifier());
            return null;
        }
        AppSearchManager.SearchContext staticMetadataSearchContext =
                new AppSearchManager.SearchContext.Builder(
                                AppFunctionStaticMetadataHelper.APP_FUNCTION_STATIC_METADATA_DB)
                        .build();
        SearchSpec searchFnSpec = APP_SEARCH_FN_SPEC;
        if (idsToFilter != null) {
            searchFnSpec =
                    new SearchSpec.Builder(searchFnSpec).addFilterDocumentIds(idsToFilter).build();
        }
        ArraySet<String> populatedGlobalFunctions = new ArraySet<>();
        ArraySet<String> populatedActivityFunctions = new ArraySet<>();
        try (FutureAppSearchSession staticMetadataSearchSession =
                        new FutureAppSearchSessionImpl(
                                appSearchManager, Runnable::run, staticMetadataSearchContext);
                FutureSearchResults futureSearchResults =
                        staticMetadataSearchSession
                                .search(DYNAMIC_APP_FUNCTIONS_QUERY, searchFnSpec)
                                .get()) {
            List<SearchResult> searchResultsList = futureSearchResults.getNextPage().get();
            while (!searchResultsList.isEmpty()) {
                for (SearchResult searchResult : searchResultsList) {
                    String scopeType =
                            searchResult
                                    .getGenericDocument()
                                    .getPropertyString(AppFunctionMetadata.PROPERTY_SCOPE);
                    if (scopeType == null) {
                        Log.w(
                                TAG,
                                "Scope type is not present for "
                                        + searchResult.getGenericDocument().getId());
                    } else if (scopeType.equals(AppFunctionMetadata.PROPERTY_VALUE_SCOPE_GLOBAL)) {
                        populatedGlobalFunctions.add(searchResult.getGenericDocument().getId());
                    } else {
                        populatedActivityFunctions.add(searchResult.getGenericDocument().getId());
                    }
                }
                searchResultsList = futureSearchResults.getNextPage().get();
            }
        } catch (Exception ex) {
            Log.e(TAG, "Error while populating cache: " + ex.getMessage());
        }
        return new DynamicFunctionsSearchResult(
                populatedGlobalFunctions, populatedActivityFunctions, idsToFilter);
    }

    private static class DynamicFunctionsSearchResult {
        @NonNull final ArraySet<String> mGlobalScopeFunctions;
        @NonNull final ArraySet<String> mActivityScopeFunctions;

        @Nullable final Set<String> mRequestedIds;

        DynamicFunctionsSearchResult(
                @NonNull ArraySet<String> globalScopeFunctions,
                @NonNull ArraySet<String> activityScopeFunctions,
                @Nullable Set<String> requestedIds) {
            mGlobalScopeFunctions = globalScopeFunctions;
            mActivityScopeFunctions = activityScopeFunctions;
            mRequestedIds = requestedIds;
        }

        ArraySet<String> extractNonDynamicAppFunctions() {
            if (mRequestedIds == null) {
                return new ArraySet<>();
            }
            ArraySet<String> nonDynamicFunctions = new ArraySet<>(mRequestedIds);
            nonDynamicFunctions.removeAll(mGlobalScopeFunctions);
            nonDynamicFunctions.removeAll(mActivityScopeFunctions);
            return nonDynamicFunctions;
        }
    }

    private static class DynamicFunctionsCache {
        @NonNull final ArraySet<String> mGlobalScopeFunctions;
        @NonNull final ArraySet<String> mActivityScopeFunctions;

        DynamicFunctionsCache() {
            mGlobalScopeFunctions = new ArraySet<>();
            mActivityScopeFunctions = new ArraySet<>();
        }

        DynamicFunctionsCache(
                @NonNull Set<String> globalScopeFunctions,
                @NonNull Set<String> activityScopeFunctions) {
            mGlobalScopeFunctions = new ArraySet<>(globalScopeFunctions);
            mActivityScopeFunctions = new ArraySet<>(activityScopeFunctions);
        }

        @GuardedBy("mCrossUserLock")
        boolean isDynamicFunction(String packageName, String functionIdentifier) {
            String documentId =
                    AppFunctionStaticMetadataHelper.getDocumentIdForAppFunction(
                            packageName, functionIdentifier);
            return mGlobalScopeFunctions.contains(documentId)
                    || mActivityScopeFunctions.contains(documentId);
        }

        @GuardedBy("mCrossUserLock")
        boolean isGlobalScopedDynamicFunction(String packageName, String functionIdentifier) {
            String documentId =
                    AppFunctionStaticMetadataHelper.getDocumentIdForAppFunction(
                            packageName, functionIdentifier);
            return mGlobalScopeFunctions.contains(documentId);
        }

        @GuardedBy("mCrossUserLock")
        boolean isActivityScopedDynamicFunction(String packageName, String functionIdentifier) {
            String documentId =
                    AppFunctionStaticMetadataHelper.getDocumentIdForAppFunction(
                            packageName, functionIdentifier);
            return mActivityScopeFunctions.contains(documentId);
        }

        @Override
        public String toString() {
            return TextUtils.formatSimple(
                    "globalScopeFunctions: %s, activityScopeFunctions: %s",
                    mGlobalScopeFunctions, mActivityScopeFunctions);
        }

        @GuardedBy("mCrossUserLock")
        void updateCacheLocked(DynamicFunctionsSearchResult updates) {
            mGlobalScopeFunctions.addAll(updates.mGlobalScopeFunctions);
            mActivityScopeFunctions.addAll(updates.mActivityScopeFunctions);
            ArraySet<String> nonDynamicFunctions = updates.extractNonDynamicAppFunctions();
            mActivityScopeFunctions.removeAll(nonDynamicFunctions);
            mGlobalScopeFunctions.removeAll(nonDynamicFunctions);
        }
    }

    private void maybeLogForDebug(String message, DynamicFunctionsCache cache) {
        if (DEBUG) {
            Log.d(TAG, TextUtils.formatSimple("%s: %s", message, cache));
        }
    }
}
