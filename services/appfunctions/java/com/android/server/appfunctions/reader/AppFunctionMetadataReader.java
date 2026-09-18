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

import static android.app.appfunctions.AppFunctionManager.APP_FUNCTION_STATE_DEFAULT;
import static android.app.appfunctions.AppFunctionManager.APP_FUNCTION_STATE_ENABLED;
import static android.app.appfunctions.AppFunctionMetadata.APP_FUNCTION_TYPE_DYNAMIC_ACTIVITY;
import static android.app.appfunctions.AppFunctionMetadata.APP_FUNCTION_TYPE_DYNAMIC_GLOBAL;
import static android.app.appfunctions.AppFunctionMetadata.APP_FUNCTION_TYPE_STATIC;
import static android.app.appfunctions.AppFunctionRuntimeMetadata.APP_FUNCTION_RUNTIME_NAMESPACE;
import static android.app.appfunctions.AppFunctionRuntimeMetadata.PROPERTY_ENABLED;
import static android.app.appfunctions.AppFunctionStaticMetadataHelper.APP_FUNCTION_INDEXER_PACKAGE;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.PermissionManuallyEnforced;
import android.annotation.WorkerThread;
import android.app.appfunctions.AppFunctionActivityId;
import android.app.appfunctions.AppFunctionActivityState;
import android.app.appfunctions.AppFunctionManagerHelper;
import android.app.appfunctions.AppFunctionMetadata;
import android.app.appfunctions.AppFunctionMetadata.AppFunctionType;
import android.app.appfunctions.AppFunctionName;
import android.app.appfunctions.AppFunctionPackageMetadata;
import android.app.appfunctions.AppFunctionRuntimeMetadata;
import android.app.appfunctions.AppFunctionSearchSpec;
import android.app.appfunctions.AppFunctionState;
import android.app.appfunctions.AppFunctionStaticMetadataHelper;
import android.app.appfunctions.flags.Flags;
import android.app.appsearch.GenericDocument;
import android.app.appsearch.GetByDocumentIdRequest;
import android.app.appsearch.JoinSpec;
import android.app.appsearch.PropertyPath;
import android.app.appsearch.SearchResult;
import android.app.appsearch.SearchSpec;
import android.app.appsearch.observer.DocumentChangeInfo;
import android.os.Binder;
import android.os.ParcelableException;
import android.os.RemoteException;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.infra.AndroidFuture;
import com.android.server.appfunctions.FutureAppSearchSession;
import com.android.server.appfunctions.FutureGlobalSearchSession;
import com.android.server.appfunctions.FutureSearchResults;
import com.android.server.appfunctions.ServiceConfig;
import com.android.server.appfunctions.dynamic.MultiUserDynamicAppFunctionRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/** Manages app functions search requests. */
public final class AppFunctionMetadataReader {
    private static final String TAG = AppFunctionMetadataReader.class.getSimpleName();

    private static final SearchSpec RUNTIME_SEARCH_SPEC =
            new SearchSpec.Builder()
                    .addFilterNamespaces(APP_FUNCTION_RUNTIME_NAMESPACE)
                    .addFilterPackageNames(APP_FUNCTION_INDEXER_PACKAGE)
                    .setVerbatimSearchEnabled(true)
                    .build();
    private static final JoinSpec JOIN_SPEC =
            new JoinSpec.Builder(
                            AppFunctionRuntimeMetadata
                                    .PROPERTY_APP_FUNCTION_STATIC_METADATA_QUALIFIED_ID)
                    .setNestedSearch("", RUNTIME_SEARCH_SPEC)
                    .build();

    /**
     * The ranking strategy for App Function search query to sort the function documents by the
     * package name hash.
     *
     * <p>`sum` is used since AppSearch store property values as a list of values. However, in App
     * Function's case, this is simply the only hash value of package name.
     */
    private static final String PACKAGE_NAME_RANKING_STRATEGY =
            TextUtils.formatSimple(
                    "sum(getScorableProperty(\"%s\", \"%s\"))",
                    AppFunctionStaticMetadataHelper.STATIC_SCHEMA_TYPE,
                    AppFunctionMetadata.PROPERTY_PACKAGE_NAME_HASH);

    private final MultiUserDynamicAppFunctionRegistry mMultiUserDynamicAppFunctionRegistry;
    private final AppFunctionsMetadataCache mCache;
    private final ServiceConfig mServiceConfig;

    public AppFunctionMetadataReader(
            @NonNull MultiUserDynamicAppFunctionRegistry multiUserDynamicAppFunctionRegistry,
            @NonNull AppFunctionsMetadataCache metadataCache,
            @NonNull ServiceConfig serviceConfig) {
        mMultiUserDynamicAppFunctionRegistry =
                Objects.requireNonNull(multiUserDynamicAppFunctionRegistry);
        mCache = Objects.requireNonNull(metadataCache);
        mServiceConfig = Objects.requireNonNull(serviceConfig);
    }

    /** Called when observation of the user AppSearch app functions data has started. */
    public void onMetadataObserveStartedForUser(@NonNull UserHandle user) {
        mCache.populateDataForUser(user);
    }

    /** Called when schema is changed for user. */
    public void onMetadataSchemaChangedForUser(@NonNull UserHandle user) {
        // To optimise further, consider extract specific package from the schema name instead
        // of the full re-indexation
        mCache.populateDataForUser(user);
    }

    /** Called when observation of the user AppSearch app functions data has finished. */
    public void onMetadataObserveFinishedForUser(@NonNull UserHandle user) {
        mCache.removeDataForUser(user);
    }

    /** Called when there are changes in the static metadata DB. */
    public void onStaticMetadataDocumentsChanged(UserHandle user, DocumentChangeInfo changeInfo) {
        mCache.updateDynamicFunctions(user, changeInfo.getChangedDocumentIds());
    }

    /**
     * Returns whether the function is dynamic. Dynamic app-functions are declared on the app level
     * and executed directly via AIDL.
     *
     * @param packageName The package name of the application containing the function.
     * @param functionIdentifier The unique identifier for the function within the package.
     * @return {boolean} Whether app function is dynamic.
     * @throws IllegalArgumentException if the user is not unlocked.
     */
    public boolean isDynamicFunction(String packageName, String functionIdentifier, UserHandle user)
            throws IllegalArgumentException {
        if (!Flags.enableDynamicAppFunctions()) {
            return false;
        }
        return mCache.isDynamicFunction(packageName, functionIdentifier, user);
    }

    /**
     * Returns the type of an app function.
     *
     * @param packageName The package name of the application containing the function.
     * @param functionIdentifier The unique identifier for the function within the package.
     * @param user The user for which to check the function type.
     * @return The {@link AppFunctionType} of the function.
     * @throws IllegalArgumentException if the user is not unlocked.
     */
    @AppFunctionType
    public int getAppFunctionType(
            @NonNull String packageName,
            @NonNull String functionIdentifier,
            @NonNull UserHandle user)
            throws IllegalArgumentException {
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(functionIdentifier);
        Objects.requireNonNull(user);
        if (!Flags.enableDynamicAppFunctions()) {
            return APP_FUNCTION_TYPE_STATIC;
        }
        if (mCache.isActivityScopedDynamicFunction(packageName, functionIdentifier, user)) {
            return APP_FUNCTION_TYPE_DYNAMIC_ACTIVITY;
        }
        if (mCache.isGlobalScopedDynamicFunction(packageName, functionIdentifier, user)) {
            return APP_FUNCTION_TYPE_DYNAMIC_GLOBAL;
        }
        return APP_FUNCTION_TYPE_STATIC;
    }

    /**
     * Returns the {@link AppFunctionEnabledState} for the given app function.
     *
     * @param futureGlobalSearchSession The session to search AppFunctions.
     * @param targetAppFunction The target AppFunction.
     * @param userId The target user id.
     * @return The detailed enabled state for the given app function.
     */
    public CompletableFuture<AppFunctionEnabledState> getAppFunctionEnabledState(
            @NonNull FutureGlobalSearchSession futureGlobalSearchSession,
            @NonNull AppFunctionName targetAppFunction,
            int userId) {
        if (mCache.isDynamicFunction(
                targetAppFunction.getPackageName(),
                targetAppFunction.getFunctionIdentifier(),
                UserHandle.of(userId))) {
            boolean isRegistered =
                    mMultiUserDynamicAppFunctionRegistry.hasRegistrations(
                            targetAppFunction.getPackageName(),
                            targetAppFunction.getFunctionIdentifier(),
                            UserHandle.of(userId));
            return AndroidFuture.completedFuture(
                    new AppFunctionEnabledState(
                            /* isEffectivelyEnabled= */ isRegistered,
                            /* isEnabledByDefault= */ false));
        }
        SearchSpec appFunctionJoinedStaticWithRuntimeSearchSpec =
                new SearchSpec.Builder()
                        .addFilterDocumentIds(List.of(targetAppFunction.getQualifiedId()))
                        .addFilterPackageNames(APP_FUNCTION_INDEXER_PACKAGE)
                        .addFilterSchemas(
                                AppFunctionStaticMetadataHelper.getStaticSchemaNameForPackage(
                                        targetAppFunction.getPackageName()))
                        .addProjectionPaths(
                                SearchSpec.SCHEMA_TYPE_WILDCARD,
                                List.of(
                                        new PropertyPath(
                                                AppFunctionMetadata.PROPERTY_ENABLED_BY_DEFAULT)))
                        .setJoinSpec(JOIN_SPEC)
                        .setVerbatimSearchEnabled(true)
                        .build();
        return futureGlobalSearchSession
                .search("", appFunctionJoinedStaticWithRuntimeSearchSpec)
                .thenCompose(FutureSearchResults::getNextPage)
                .thenCompose(
                        result -> {
                            if (result.size() != 1) {
                                return AndroidFuture.failedFuture(
                                        new AppFunctionManagerHelper.AppFunctionNotFoundException(
                                                "App function not found."));
                            }
                            List<SearchResult> joinedResult = result.getFirst().getJoinedResults();
                            if (joinedResult.size() != 1) {
                                return AndroidFuture.failedFuture(
                                        new RuntimeException(
                                                TextUtils.formatSimple(
                                                        "Expected 1 GenericDocument for"
                                                                + " runtimeMetadata, found %d",
                                                        joinedResult.size())));
                            }

                            GenericDocument staticDocument = result.getFirst().getGenericDocument();
                            GenericDocument runtimeDocument =
                                    joinedResult.getFirst().getGenericDocument();
                            return AndroidFuture.completedFuture(
                                    calculateAppFunctionEnabledState(
                                            staticDocument, runtimeDocument, userId));
                        });
    }

    /** Gets a list of {@link AppFunctionState}. */
    @NonNull
    public AndroidFuture<List<AppFunctionState>> getAppFunctionStates(
            @NonNull FutureGlobalSearchSession futureGlobalSearchSession,
            @NonNull Set<AppFunctionName> appFunctionNames,
            int userId) {
        Objects.requireNonNull(futureGlobalSearchSession);
        Objects.requireNonNull(appFunctionNames);

        if (appFunctionNames.isEmpty()) {
            return AndroidFuture.completedFuture(List.of());
        }

        List<String> documentIds = new ArrayList<>();
        for (AppFunctionName name : appFunctionNames) {
            documentIds.add(name.getQualifiedId());
        }

        SearchSpec appFunctionJoinedStaticWithRuntime =
                new SearchSpec.Builder()
                        .addFilterDocumentIds(documentIds)
                        .addFilterPackageNames(APP_FUNCTION_INDEXER_PACKAGE)
                        .addFilterSchemas(AppFunctionStaticMetadataHelper.STATIC_SCHEMA_TYPE)
                        .addProjectionPaths(
                                SearchSpec.SCHEMA_TYPE_WILDCARD,
                                List.of(
                                        new PropertyPath(
                                                AppFunctionMetadata.PROPERTY_ENABLED_BY_DEFAULT)))
                        .setJoinSpec(JOIN_SPEC)
                        .setVerbatimSearchEnabled(true)
                        .setResultCountPerPage(
                                mServiceConfig.getSearchAppFunctionInternalPageSize())
                        .build();

        return futureGlobalSearchSession
                .search("", appFunctionJoinedStaticWithRuntime)
                .thenCompose(
                        searchResults -> {
                            List<AppFunctionState> accumulator = new ArrayList<>();
                            return parseAppFunctionStates(searchResults, userId, accumulator)
                                    .whenComplete(
                                            (result, exception) -> {
                                                searchResults.close();
                                            });
                        });
    }

    /** Gets a list of {@link AppFunctionActivityState}. */
    @WorkerThread
    @NonNull
    public AndroidFuture<List<AppFunctionActivityState>> getAppFunctionActivityStates(
            @NonNull List<AppFunctionActivityId> activityIds, int userId) {
        return AndroidFuture.completedFuture(
                mMultiUserDynamicAppFunctionRegistry.getAppFunctionActivityStates(
                        activityIds, UserHandle.of(userId)));
    }

    @NonNull
    private AndroidFuture<List<AppFunctionState>> parseAppFunctionStates(
            @NonNull FutureSearchResults searchResults,
            int userId,
            @NonNull List<AppFunctionState> accumulator) {
        Objects.requireNonNull(searchResults);
        Objects.requireNonNull(accumulator);

        return searchResults
                .getNextPage()
                .thenCompose(
                        page -> {
                            if (page.isEmpty()) {
                                return AndroidFuture.completedFuture(accumulator);
                            }

                            for (SearchResult result : page) {
                                AppFunctionState state = parseAppFunctionState(result, userId);
                                if (state != null) accumulator.add(state);
                            }

                            return parseAppFunctionStates(searchResults, userId, accumulator);
                        });
    }

    @Nullable
    private AppFunctionState parseAppFunctionState(@NonNull SearchResult searchResult, int userId) {
        Objects.requireNonNull(searchResult);

        try {
            GenericDocument staticDocument = requireNonNull(searchResult.getGenericDocument());
            if (requireNonNull(searchResult.getJoinedResults()).size() != 1) {
                Slog.w(
                        TAG,
                        TextUtils.formatSimple(
                                "Expected 1 GenericDocument for runtimeMetadata, found %d",
                                searchResult.getJoinedResults().size()));
                return null;
            }
            GenericDocument runtimeDocument =
                    requireNonNull(searchResult.getJoinedResults().getFirst().getGenericDocument());
            AppFunctionName appFunctionName =
                    AppFunctionName.fromQualifiedId(staticDocument.getId());

            return new AppFunctionState(
                    appFunctionName,
                    calculateAppFunctionEnabledState(staticDocument, runtimeDocument, userId)
                            .isEffectivelyEnabled(),
                    getActivityIdInfo(appFunctionName, UserHandle.of(userId)));
        } catch (RuntimeException e) {
            Slog.e(TAG, "Failed to convert SearchResult to AppFunctionState.", e);
            return null;
        }
    }

    @Nullable
    private ArraySet<AppFunctionActivityId> getActivityIdInfo(
            @NonNull AppFunctionName functionName, @NonNull UserHandle user) {
        if (!mCache.isActivityScopedDynamicFunction(
                functionName.getPackageName(), functionName.getFunctionIdentifier(), user)) {
            return null;
        }
        return mMultiUserDynamicAppFunctionRegistry.getRegisteredActivityIds(functionName, user);
    }

    /** Fetches the service class name for a given function. */
    @NonNull
    public CompletableFuture<String> getAppFunctionServiceClassName(
            @NonNull FutureAppSearchSession futureAppSearchSession,
            @NonNull AppFunctionName targetAppFunction) {
        String qualifiedId = targetAppFunction.getQualifiedId();
        GetByDocumentIdRequest request =
                new GetByDocumentIdRequest.Builder(
                                AppFunctionStaticMetadataHelper.APP_FUNCTION_STATIC_NAMESPACE)
                        .addIds(qualifiedId)
                        .addProjectionPaths(
                                AppFunctionStaticMetadataHelper.getStaticSchemaNameForPackage(
                                        targetAppFunction.getPackageName()),
                                List.of(
                                        new PropertyPath(
                                                AppFunctionMetadata.PROPERTY_SERVICE_NAME)))
                        .build();
        return futureAppSearchSession
                .getByDocumentId(request)
                .thenApply(
                        result -> {
                            GenericDocument staticDocument = result.getSuccesses().get(qualifiedId);
                            if (staticDocument == null) {
                                return null;
                            }
                            return staticDocument.getPropertyString(
                                    AppFunctionMetadata.PROPERTY_SERVICE_NAME);
                        });
    }

    // TODO(b/467317154): Take into account AppFunctionService availability for static
    //  app functions
    @NonNull
    private AppFunctionEnabledState calculateAppFunctionEnabledState(
            @NonNull GenericDocument staticDocument,
            @NonNull GenericDocument runtimeDocument,
            int userId) {
        Objects.requireNonNull(staticDocument);
        Objects.requireNonNull(runtimeDocument);

        AppFunctionName appFunctionName = AppFunctionName.fromQualifiedId(staticDocument.getId());
        String packageName = appFunctionName.getPackageName();
        String functionId = appFunctionName.getFunctionIdentifier();

        if (mCache.isDynamicFunction(packageName, functionId, UserHandle.of(userId))) {
            return new AppFunctionEnabledState(
                    mMultiUserDynamicAppFunctionRegistry.hasRegistrations(
                            packageName, functionId, UserHandle.of(userId)),
                    false);
        }

        boolean isEnabledByDefault =
                staticDocument.getPropertyBoolean(AppFunctionMetadata.PROPERTY_ENABLED_BY_DEFAULT);

        boolean isRuntimeEnabled;
        if (runtimeDocument.getPropertyLong(PROPERTY_ENABLED) == APP_FUNCTION_STATE_DEFAULT) {
            isRuntimeEnabled =
                    staticDocument.getPropertyBoolean(
                            AppFunctionMetadata.PROPERTY_ENABLED_BY_DEFAULT);
        } else {
            isRuntimeEnabled =
                    runtimeDocument.getPropertyLong(PROPERTY_ENABLED) == APP_FUNCTION_STATE_ENABLED;
        }

        return new AppFunctionEnabledState(
                /* isEffectivelyEnabled= */ isRuntimeEnabled,
                /* isEnabledByDefault= */ isEnabledByDefault);
    }

    /**
     * Wraps the comprehensive enabled state of an app function resulting from both the static and
     * runtime metadata content.
     */
    public static class AppFunctionEnabledState {
        private final boolean mIsEnabledByDefault;
        private final boolean mIsEffectivelyEnabled;

        AppFunctionEnabledState(boolean isEffectivelyEnabled, boolean isEnabledByDefault) {
            this.mIsEnabledByDefault = isEnabledByDefault;
            this.mIsEffectivelyEnabled = isEffectivelyEnabled;
        }

        /** Returns true if the function is effectively enabled. */
        public boolean isEffectivelyEnabled() {
            return mIsEffectivelyEnabled;
        }

        /** Returns the function's default enabled state. */
        public boolean isEnabledByDefault() {
            return mIsEnabledByDefault;
        }
    }
}
