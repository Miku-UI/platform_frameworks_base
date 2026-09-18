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
package com.android.server.appfunctions.observer;

import static android.app.appfunctions.AppFunctionStaticMetadataHelper.APP_FUNCTION_STATIC_METADATA_DB;
import static android.app.appfunctions.AppFunctionStaticMetadataHelper.APP_FUNCTION_STATIC_NAMESPACE;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.appfunctions.AppFunctionObserver;
import android.app.appfunctions.AppFunctionStaticMetadataHelper;
import android.app.appsearch.observer.DocumentChangeInfo;
import android.app.appsearch.observer.ObserverCallback;
import android.app.appsearch.observer.SchemaChangeInfo;
import android.os.UserHandle;
import android.util.Slog;

import com.android.server.appfunctions.MetadataSyncAdapter;
import com.android.server.appfunctions.reader.AppFunctionMetadataReader;

/**
 * An {@link ObserverCallback} that manages the synchronization lifecycle for app function metadata.
 *
 * <p>The observer is registered continuously while the user is unlocked. It maintains consistency
 * between static and runtime app function metadata sources. When static metadata changes, it
 * triggers a metadata sync request to unify these data sources.
 *
 * <p>Upon completion, it dispatches updates to {@link AppFunctionObserver} listeners. This
 * sequencing ensures that by the time a client receives a notification, the metadata is fully
 * indexed and available for subsequent queries via {@link
 * android.app.appfunctions.AppFunctionManager#searchAppFunctions}.
 */
public class AppFunctionMetadataObserverCallback2 implements ObserverCallback {
    private static final String TAG = AppFunctionMetadataObserverCallback2.class.getSimpleName();

    @Nullable private final MetadataSyncAdapter mPerUserMetadataSyncAdapter;

    @NonNull private final InternalObserverCallbackRouter mCallbackRouter;
    @NonNull private final AppFunctionMetadataReader mAppFunctionMetadataReader;
    @NonNull private final UserHandle mUserHandle;

    public AppFunctionMetadataObserverCallback2(
            @Nullable MetadataSyncAdapter perUserMetadataSyncAdapter,
            @NonNull InternalObserverCallbackRouter callbackRouter,
            @NonNull AppFunctionMetadataReader appFunctionMetadataReader,
            @NonNull UserHandle userHandle) {
        mPerUserMetadataSyncAdapter = perUserMetadataSyncAdapter;
        mCallbackRouter = requireNonNull(callbackRouter);
        mAppFunctionMetadataReader = requireNonNull(appFunctionMetadataReader);
        mUserHandle = requireNonNull(userHandle);
    }

    @Override
    public void onDocumentChanged(@NonNull DocumentChangeInfo documentChangeInfo) {
        if (mPerUserMetadataSyncAdapter == null
                || !isAppFunctionStaticMetadataChange(documentChangeInfo)) {
            return;
        }
        var unused =
                mPerUserMetadataSyncAdapter
                        .submitSyncRequest(
                                /* shouldSetRuntimeMetadataSchemaUnconditionally= */ false)
                        .whenComplete(
                                (isSyncSuccessful, ex) -> {
                                    if (ex != null || !isSyncSuccessful) {
                                        Slog.d(TAG, "Failed to sync metadata", ex);
                                        return;
                                    }
                                    // Notify per-app observers of updates after the metadata sync
                                    // completes. This ensures runtime metadata is already
                                    // up-to-date if the user queries the AppFunctionMetadata as a
                                    // result of this callback.
                                    mCallbackRouter.onDocumentChanged(documentChangeInfo);
                                });
        mAppFunctionMetadataReader.onStaticMetadataDocumentsChanged(
                mUserHandle, documentChangeInfo);
    }

    @Override
    public void onSchemaChanged(@NonNull SchemaChangeInfo schemaChangeInfo) {
        if (mPerUserMetadataSyncAdapter == null
                || !isAppFunctionDb(schemaChangeInfo.getDatabaseName())) {
            return;
        }
        boolean shouldInitiateSync = false;
        for (String schemaName : schemaChangeInfo.getChangedSchemaNames()) {
            if (schemaName.startsWith(AppFunctionStaticMetadataHelper.STATIC_SCHEMA_TYPE)) {
                shouldInitiateSync = true;
                break;
            }
        }
        if (shouldInitiateSync) {
            var unused =
                    mPerUserMetadataSyncAdapter
                            .submitSyncRequest(
                                    /* shouldSetRuntimeMetadataSchemaUnconditionally= */ false)
                            .whenComplete(
                                    (isSyncSuccessful, ex) -> {
                                        if (!isSyncSuccessful) {
                                            Slog.d(TAG, "Failed to sync metadata", ex);
                                            return;
                                        }
                                        mCallbackRouter.onSchemaChanged(schemaChangeInfo);
                                    });
            mAppFunctionMetadataReader.onMetadataSchemaChangedForUser(mUserHandle);
        }
    }

    private boolean isAppFunctionStaticMetadataChange(DocumentChangeInfo documentChangeInfo) {
        return isAppFunctionDb(documentChangeInfo.getDatabaseName())
                && isAppFunctionNamespace(documentChangeInfo.getNamespace());
    }

    private boolean isAppFunctionDb(@NonNull String dbName) {
        return dbName.equals(APP_FUNCTION_STATIC_METADATA_DB);
    }

    private boolean isAppFunctionNamespace(@NonNull String namespace) {
        return namespace.equals(APP_FUNCTION_STATIC_NAMESPACE);
    }
}
