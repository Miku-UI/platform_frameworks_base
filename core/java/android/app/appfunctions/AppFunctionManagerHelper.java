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

package android.app.appfunctions;

import static android.app.appfunctions.AppFunctionManager.APP_FUNCTION_STATE_DEFAULT;
import static android.app.appfunctions.AppFunctionManager.APP_FUNCTION_STATE_ENABLED;
import static android.app.appfunctions.AppFunctionRuntimeMetadata.PROPERTY_APP_FUNCTION_STATIC_METADATA_QUALIFIED_ID;
import static android.app.appfunctions.AppFunctionRuntimeMetadata.PROPERTY_ENABLED;
import static android.app.appfunctions.AppFunctionStaticMetadataHelper.APP_FUNCTION_INDEXER_PACKAGE;
import static android.app.appfunctions.AppFunctionStaticMetadataHelper.STATIC_PROPERTY_ENABLED_BY_DEFAULT;
import static android.app.appfunctions.flags.Flags.FLAG_ENABLE_APP_FUNCTION_MANAGER;

import android.Manifest;
import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.appsearch.AppSearchManager;
import android.app.appsearch.AppSearchResult;
import android.app.appsearch.GenericDocument;
import android.app.appsearch.GlobalSearchSession;
import android.app.appsearch.JoinSpec;
import android.app.appsearch.PropertyPath;
import android.app.appsearch.SearchResult;
import android.app.appsearch.SearchResults;
import android.app.appsearch.SearchSpec;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.CancellationSignal;
import android.os.ICancellationSignal;
import android.os.OutcomeReceiver;
import android.os.RemoteException;
import android.text.TextUtils;
import android.app.appfunctions.flags.Flags;

import android.util.ArrayMap;

import android.util.ArraySet;
import android.util.Log;
import com.android.internal.infra.AndroidFuture;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Helper class containing utilities for {@link AppFunctionManager}.
 *
 * @hide
 */
@FlaggedApi(FLAG_ENABLE_APP_FUNCTION_MANAGER)
public class AppFunctionManagerHelper {
    private static final String TAG = "AppFunctionManagerHelper";

    /**
     * Searches app functions from AppSearch.
     *
     * @param appSearchManager the AppSearch manager to use for searching
     * @param searchSpec the search spec to use for searching
     * @param executor executor the executor to run the request
     * @return a future that resolves to a list of app function metadata
     */
    @NonNull
    public static AndroidFuture<List<AppFunctionMetadata>> searchAppFunctions(
            @NonNull Context context,
            @NonNull AppSearchManager appSearchManager,
            @NonNull AppFunctionSearchSpec searchSpec,
            @NonNull Executor executor) {
        Objects.requireNonNull(appSearchManager);
        Objects.requireNonNull(searchSpec);
        Objects.requireNonNull(executor);

        AppFunctionSearchSpec filteredSearchSpec = applyVisiblePackageFilter(searchSpec, context);
        if (filteredSearchSpec == null) {
            AndroidFuture<List<AppFunctionMetadata>> resultFuture = new AndroidFuture<>();
            resultFuture.complete(new ArrayList<>());
            return resultFuture;
        }

        AndroidFuture<List<GenericDocument>> functionDocsFuture =
                searchAppFunctionStaticMetadata(appSearchManager, filteredSearchSpec, executor);
        AndroidFuture<List<GenericDocument>> packageDocsFuture =
                searchAppFunctionPackageMetadata(appSearchManager, filteredSearchSpec, executor);

        AndroidFuture<List<AppFunctionMetadata>> resultFuture = new AndroidFuture<>();

        functionDocsFuture
                .thenCombineAsync(
                        packageDocsFuture,
                        (functionDocs, packageDocs) -> {
                            ArrayMap<String, List<GenericDocument>> docsByPackage =
                                    new ArrayMap<>();
                            for (int index = 0; index < packageDocs.size(); index++) {
                                GenericDocument pkgDoc = packageDocs.get(index);
                                String packageName =
                                        pkgDoc.getPropertyString(
                                                AppFunctionPackageMetadata.PROPERTY_PACKAGE_NAME);
                                if (packageName != null) {
                                    docsByPackage
                                            .computeIfAbsent(packageName, k -> new ArrayList<>())
                                            .add(pkgDoc);
                                }
                            }

                            ArrayMap<String, AppFunctionPackageMetadata> packageMetadataMap =
                                    new ArrayMap<>();
                            for (Map.Entry<String, List<GenericDocument>> entry :
                                    docsByPackage.entrySet()) {
                                packageMetadataMap.put(
                                        entry.getKey(),
                                        AppFunctionPackageMetadata.create(
                                                entry.getKey(), entry.getValue()));
                            }

                            List<AppFunctionMetadata> result = new ArrayList<>();
                            for (GenericDocument functionDoc : functionDocs) {
                                try {
                                    String packageName =
                                            AppFunctionName.fromQualifiedId(functionDoc.getId())
                                                    .getPackageName();
                                    AppFunctionPackageMetadata pkgMetadata =
                                            packageMetadataMap.get(packageName);
                                    if (pkgMetadata == null) {
                                        pkgMetadata =
                                                AppFunctionPackageMetadata.create(
                                                        packageName, List.of());
                                    }
                                    result.add(
                                            new AppFunctionMetadata.Builder(
                                                            functionDoc, pkgMetadata)
                                                    .build());
                                } catch (RuntimeException e) {
                                    // Ignore invalid function instead of disrupting the whole
                                    // search.
                                    Log.w(TAG, "Failed to create AppFunctionMetadata", e);
                                }
                            }
                            return result;
                        },
                        executor)
                .whenComplete(
                        (result, e) -> {
                            if (e != null) {
                                resultFuture.completeExceptionally(e);
                            } else {
                                resultFuture.complete(result);
                            }
                        });

        return resultFuture;
    }

    @NonNull
    private static AndroidFuture<List<GenericDocument>> searchAppFunctionStaticMetadata(
            @NonNull AppSearchManager appSearchManager,
            @NonNull AppFunctionSearchSpec searchSpec,
            @NonNull Executor executor) {
        Objects.requireNonNull(appSearchManager);
        Objects.requireNonNull(searchSpec);

        AndroidFuture<List<GenericDocument>> future = new AndroidFuture<>();
        appSearchManager.createGlobalSearchSession(
                executor,
                sessionResult -> {
                    if (!sessionResult.isSuccess()) {
                        future.completeExceptionally(failedResultToException(sessionResult));
                        return;
                    }
                    GlobalSearchSession session = sessionResult.getResultValue();
                    try {
                        SearchSpec staticMetadataSearchSpec =
                                new SearchSpec.Builder()
                                        .addFilterNamespaces(
                                                AppFunctionStaticMetadataHelper
                                                        .APP_FUNCTION_STATIC_NAMESPACE)
                                        .addFilterPackageNames(APP_FUNCTION_INDEXER_PACKAGE)
                                        .addFilterDocumentIds(searchSpec.getQualifiedIdsFilter())
                                        .addFilterSchemas(
                                                AppFunctionStaticMetadataHelper.STATIC_SCHEMA_TYPE)
                                        .setVerbatimSearchEnabled(true)
                                        .setNumericSearchEnabled(true)
                                        .setListFilterQueryLanguageEnabled(true)
                                        .build();

                        SearchResults results =
                                session.search(
                                        searchSpec.getStaticMetadataAppSearchQuery(),
                                        staticMetadataSearchSpec);

                        fetchAllAppSearchDocuments(
                                results, session, new ArrayList<>(), future, executor);
                    } catch (Exception e) {
                        session.close();
                        future.completeExceptionally(e);
                    }
                });
        return future;
    }

    @NonNull
    private static AndroidFuture<List<GenericDocument>> searchAppFunctionPackageMetadata(
            @NonNull AppSearchManager appSearchManager,
            @NonNull AppFunctionSearchSpec searchSpec,
            @NonNull Executor executor) {
        Objects.requireNonNull(appSearchManager);
        Objects.requireNonNull(searchSpec);

        AndroidFuture<List<GenericDocument>> future = new AndroidFuture<>();
        appSearchManager.createGlobalSearchSession(
                executor,
                sessionResult -> {
                    if (!sessionResult.isSuccess()) {
                        future.completeExceptionally(failedResultToException(sessionResult));
                        return;
                    }
                    GlobalSearchSession session = sessionResult.getResultValue();
                    try {
                        SearchSpec appFunctionDocumentSearchSpec =
                                new SearchSpec.Builder()
                                        .addFilterNamespaces(
                                                AppFunctionStaticMetadataHelper
                                                        .APP_FUNCTION_STATIC_NAMESPACE)
                                        .addFilterPackageNames(APP_FUNCTION_INDEXER_PACKAGE)
                                        .addFilterSchemas(AppFunctionPackageMetadata.SCHEMA_TYPE)
                                        .setVerbatimSearchEnabled(true)
                                        .setListFilterQueryLanguageEnabled(true)
                                        .build();
                        String query = searchSpec.getPackageAppSearchQuery();

                        SearchResults results =
                                session.search(query, appFunctionDocumentSearchSpec);
                        fetchAllAppSearchDocuments(
                                results, session, new ArrayList<>(), future, executor);
                    } catch (Exception e) {
                        session.close();
                        future.completeExceptionally(e);
                    }
                });
        return future;
    }

    private static void fetchAllAppSearchDocuments(
            @NonNull SearchResults results,
            @NonNull GlobalSearchSession session,
            @NonNull List<GenericDocument> accumulator,
            @NonNull AndroidFuture<List<GenericDocument>> future,
            @NonNull Executor executor) {
        results.getNextPage(
                executor,
                pageResult -> {
                    if (!pageResult.isSuccess()) {
                        session.close();
                        future.completeExceptionally(failedResultToException(pageResult));
                        return;
                    }
                    List<SearchResult> page = pageResult.getResultValue();
                    if (page.isEmpty()) {
                        session.close();
                        future.complete(accumulator);
                        return;
                    }
                    for (int index = 0; index < page.size(); index++) {
                        SearchResult result = page.get(index);
                        accumulator.add(result.getGenericDocument());
                    }
                    fetchAllAppSearchDocuments(results, session, accumulator, future, executor);
                });
    }

    /**
     * Returns a search spec that only includes packages and functions that this app has permissions
     * to query.
     *
     * @return null if none of the packages or functions in the search spec are visible to the
     *     caller.
     */
    @Nullable
    private static AppFunctionSearchSpec applyVisiblePackageFilter(
            @NonNull AppFunctionSearchSpec searchSpec, @NonNull Context context) {
        Objects.requireNonNull(searchSpec);
        Objects.requireNonNull(context);

        if (hasPermissionsToQueryRuntimeMetadata(context)) {
            return searchSpec;
        }

        // Caller doesn't have permission to see all AppFunctions, update the search spec to only
        // include the caller if it is in the search spec (either implicitly or explicitly).
        ArraySet<String> visiblePackages = new ArraySet<>();
        if (searchSpec.getPackageNames() == null
                || searchSpec.getPackageNames().contains(context.getPackageName())) {
            visiblePackages.add(context.getPackageName());
        } else {
            // None of the packages in the search spec are visible to the caller.
            return null;
        }

        ArraySet<AppFunctionName> visibleFunctions = null;
        if (searchSpec.getFunctionNames() == null) {
            // No wildcard available to search all AppFunctions from the same package, therefore,
            // use the package filter instead.
            visiblePackages.add(context.getPackageName());
        } else {
            visibleFunctions = new ArraySet<>();
            for (AppFunctionName functionName : searchSpec.getFunctionNames()) {
                if (functionName.getPackageName().equals(context.getPackageName())) {
                    visibleFunctions.add(functionName);
                }
            }
        }
        if (visibleFunctions != null && visibleFunctions.isEmpty()) {
            return null;
        }

        return new AppFunctionSearchSpec.Builder(searchSpec)
                .setPackageNames(visiblePackages)
                .setFunctionNames(visibleFunctions)
                .build();
    }

    private static boolean hasPermissionsToQueryRuntimeMetadata(@NonNull Context context) {
        if (Flags.enableAppFunctionPermissionV2()
                && context.checkSelfPermission(Manifest.permission.DISCOVER_APP_FUNCTIONS)
                        == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        if (Flags.enableAppFunctionPermissionV2()
                && context.checkSelfPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS_SYSTEM)
                        == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        return context.checkSelfPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Returns (through a callback) a boolean indicating whether the app function is enabled.
     *
     * <p>This method can only check app functions owned by the caller, or those where the caller
     * has visibility to the owner package and holds the {@link
     * Manifest.permission#EXECUTE_APP_FUNCTIONS} permission.
     *
     * <p>If operation fails, the callback's {@link OutcomeReceiver#onError} is called with errors:
     *
     * <ul>
     *   <li>{@link AppFunctionNotFoundException}, if the function is not found or the caller does
     *       not have access to it.
     * </ul>
     *
     * @param functionIdentifier the identifier of the app function to check (unique within the
     *     target package) and in most cases, these are automatically generated by the AppFunctions
     *     SDK
     * @param targetPackage the package name of the app function's owner
     * @param executor executor the executor to run the request
     * @param callback the callback to receive the function enabled check result
     * @hide
     */
    public static void isAppFunctionEnabled(
            @NonNull String functionIdentifier,
            @NonNull String targetPackage,
            @NonNull AppSearchManager appSearchManager,
            @NonNull Executor executor,
            @NonNull OutcomeReceiver<Boolean, Exception> callback) {
        Objects.requireNonNull(functionIdentifier);
        Objects.requireNonNull(targetPackage);
        Objects.requireNonNull(appSearchManager);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);

        appSearchManager.createGlobalSearchSession(
                executor,
                (searchSessionResult) -> {
                    if (!searchSessionResult.isSuccess()) {
                        callback.onError(failedResultToException(searchSessionResult));
                        return;
                    }
                    try (GlobalSearchSession searchSession = searchSessionResult.getResultValue()) {
                        SearchResults results =
                                searchJoinedStaticWithRuntimeAppFunctions(
                                        Objects.requireNonNull(searchSession),
                                        targetPackage,
                                        functionIdentifier);
                        results.getNextPage(
                                executor,
                                listAppSearchResult -> {
                                    if (listAppSearchResult.isSuccess()) {
                                        callback.onResult(
                                                getEffectiveEnabledStateFromSearchResults(
                                                        Objects.requireNonNull(
                                                                listAppSearchResult
                                                                        .getResultValue())));
                                    } else {
                                        callback.onError(
                                                failedResultToException(listAppSearchResult));
                                    }
                                });
                        results.close();
                    } catch (Exception e) {
                        callback.onError(e);
                    }
                });
    }

    /** Creates a {@link CancellationSignal} from a {@link ICancellationCallback}. */
    static CancellationSignal buildCancellationSignal(
            @NonNull ICancellationCallback cancellationCallback) {
        final ICancellationSignal cancellationSignalTransport =
                CancellationSignal.createTransport();
        CancellationSignal cancellationSignal =
                CancellationSignal.fromTransport(cancellationSignalTransport);
        try {
            cancellationCallback.sendCancellationTransport(cancellationSignalTransport);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }

        return cancellationSignal;
    }

    /**
     * Searches joined app function static and runtime metadata using the function Id and the
     * package.
     */
    private static @NonNull SearchResults searchJoinedStaticWithRuntimeAppFunctions(
            @NonNull GlobalSearchSession session,
            @NonNull String targetPackage,
            @NonNull String functionIdentifier) {
        SearchSpec runtimeSearchSpec =
                getAppFunctionRuntimeMetadataSearchSpecByPackageName(targetPackage);
        JoinSpec joinSpec =
                new JoinSpec.Builder(PROPERTY_APP_FUNCTION_STATIC_METADATA_QUALIFIED_ID)
                        .setNestedSearch(
                                buildFilerRuntimeMetadataByFunctionIdQuery(functionIdentifier),
                                runtimeSearchSpec)
                        .build();
        SearchSpec joinedStaticWithRuntimeSearchSpec =
                new SearchSpec.Builder()
                        .addFilterPackageNames(APP_FUNCTION_INDEXER_PACKAGE)
                        .addFilterSchemas(
                                AppFunctionStaticMetadataHelper.getStaticSchemaNameForPackage(
                                        targetPackage))
                        .addProjectionPaths(
                                SearchSpec.SCHEMA_TYPE_WILDCARD,
                                List.of(new PropertyPath(STATIC_PROPERTY_ENABLED_BY_DEFAULT)))
                        .setJoinSpec(joinSpec)
                        .setVerbatimSearchEnabled(true)
                        .build();
        return session.search(
                buildFilerStaticMetadataByFunctionIdQuery(functionIdentifier),
                joinedStaticWithRuntimeSearchSpec);
    }

    /**
     * Returns whether the function is effectively enabled or not from the search results returned
     * by {@link #searchJoinedStaticWithRuntimeAppFunctions}.
     *
     * @param joinedStaticRuntimeResults search results joining AppFunctionStaticMetadata and
     *     AppFunctionRuntimeMetadata.
     * @throws IllegalArgumentException if the function is not found in the results
     */
    private static boolean getEffectiveEnabledStateFromSearchResults(
            @NonNull List<SearchResult> joinedStaticRuntimeResults) {
        if (joinedStaticRuntimeResults.isEmpty()) {
            throw new IllegalArgumentException("App function not found.");
        } else {
            List<SearchResult> runtimeMetadataResults =
                    joinedStaticRuntimeResults.getFirst().getJoinedResults();
            if (runtimeMetadataResults.isEmpty()) {
                throw new IllegalArgumentException("App function not found.");
            }
            long enabled =
                    runtimeMetadataResults
                            .getFirst()
                            .getGenericDocument()
                            .getPropertyLong(PROPERTY_ENABLED);
            // If enabled is not equal to APP_FUNCTION_STATE_DEFAULT, it means it IS overridden and
            // we should return the overridden value.
            if (enabled != APP_FUNCTION_STATE_DEFAULT) {
                return enabled == APP_FUNCTION_STATE_ENABLED;
            }
            // Runtime metadata not found or enabled is equal to APP_FUNCTION_STATE_DEFAULT.
            // Using the default value in the static metadata.
            return joinedStaticRuntimeResults
                    .getFirst()
                    .getGenericDocument()
                    .getPropertyBoolean(STATIC_PROPERTY_ENABLED_BY_DEFAULT);
        }
    }

    /**
     * Returns search spec that queries app function metadata for a specific package name by its
     * function identifier.
     */
    private static @NonNull SearchSpec getAppFunctionRuntimeMetadataSearchSpecByPackageName(
            @NonNull String targetPackage) {
        return new SearchSpec.Builder()
                .addFilterPackageNames(APP_FUNCTION_INDEXER_PACKAGE)
                .addFilterSchemas(
                        AppFunctionRuntimeMetadata.getRuntimeSchemaNameForPackage(targetPackage))
                .setVerbatimSearchEnabled(true)
                .build();
    }

    private static String buildFilerRuntimeMetadataByFunctionIdQuery(String functionIdentifier) {
        return TextUtils.formatSimple(
                "%s:\"%s\"", AppFunctionRuntimeMetadata.PROPERTY_FUNCTION_ID, functionIdentifier);
    }

    private static String buildFilerStaticMetadataByFunctionIdQuery(String functionIdentifier) {
        return TextUtils.formatSimple(
                "%s:\"%s\"",
                AppFunctionStaticMetadataHelper.PROPERTY_FUNCTION_ID, functionIdentifier);
    }

    /** Converts a failed app search result codes into an exception. */
    private static @NonNull Exception failedResultToException(
            @NonNull AppSearchResult appSearchResult) {
        return switch (appSearchResult.getResultCode()) {
            case AppSearchResult.RESULT_INVALID_ARGUMENT ->
                    new AppFunctionNotFoundException(appSearchResult.getErrorMessage());
            case AppSearchResult.RESULT_IO_ERROR ->
                    new IOException(appSearchResult.getErrorMessage());
            case AppSearchResult.RESULT_SECURITY_ERROR ->
                    new SecurityException(appSearchResult.getErrorMessage());
            default -> new IllegalStateException(appSearchResult.getErrorMessage());
        };
    }

    /**
     * Throws when the app function is not found.
     *
     * @hide
     */
    public static class AppFunctionNotFoundException extends RuntimeException {
        public AppFunctionNotFoundException(@NonNull String errorMessage) {
            super(errorMessage);
        }
    }

    /**
     * Returns result codes from throwable.
     *
     * @hide
     */
    static @AppFunctionException.ErrorCode int executionExceptionToErrorCode(@NonNull Throwable t) {
        if (t instanceof IllegalArgumentException) {
            return AppFunctionException.ERROR_INVALID_ARGUMENT;
        }
        return AppFunctionException.ERROR_APP_UNKNOWN_ERROR;
    }
}
