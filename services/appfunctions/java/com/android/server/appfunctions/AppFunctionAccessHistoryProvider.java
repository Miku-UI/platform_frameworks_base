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

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.appfunctions.AppFunctionAccessServiceInterface;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.UserManager;

import com.android.server.LocalServices;

import java.util.Objects;

/** The provider for AppFunction access history. */
public class AppFunctionAccessHistoryProvider extends ContentProvider {
    private static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    private static final int USER = 0;

    static {
        sURIMatcher.addURI("com.android.appfunction.accesshistory", "user/#", USER);
    }

    @Nullable private MultiUserAppFunctionAccessHistory mMultiUserAccessHistory;

    @Nullable private CallerValidator mCallerValidator;

    @Override
    public boolean onCreate() {
        final Context context = Objects.requireNonNull(getContext());
        mMultiUserAccessHistory =
                MultiUserAppFunctionAccessHistory.getInstance(Objects.requireNonNull(getContext()));
        mCallerValidator =
                new CallerValidatorImpl(
                        context,
                        LocalServices.getService(AppFunctionAccessServiceInterface.class),
                        Objects.requireNonNull(context.getSystemService(UserManager.class)));
        return true;
    }

    @Nullable
    @Override
    public Cursor query(
            @NonNull Uri uri,
            @Nullable String[] projection,
            @Nullable String selection,
            @Nullable String[] selectionArgs,
            @Nullable String sortOrder) {
        if (!accessCheckFlagsEnabled()) return null;

        checkCallerPermission();

        final int targetUserId = getTargetUserId(uri);
        checkTargetUserPermission(targetUserId);

        return Objects.requireNonNull(mMultiUserAccessHistory)
                .asUser(targetUserId)
                .queryAppFunctionAccessHistory(projection, selection, selectionArgs, sortOrder);
    }

    @Override
    @Nullable
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        throw new UnsupportedOperationException(
                "Insert is not supported with AppFunctionAccessHistoryProvider");
    }

    @Override
    public int delete(
            @NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException(
                "Delete is not supported with AppFunctionAccessHistoryProvider");
    }

    @Override
    public int update(
            @NonNull Uri uri,
            @Nullable ContentValues values,
            @Nullable String selection,
            @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException(
                "Update is not supported with AppFunctionAccessHistoryProvider");
    }

    private int getTargetUserId(@NonNull Uri uri) {
        if (sURIMatcher.match(uri) == USER) {
            try {
                return Integer.parseInt(uri.getPathSegments().get(1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Unable to parse valid target user from uri " + uri);
            }
        } else {
            throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }

    /** Checks if the caller has permission to read access history. */
    private void checkCallerPermission() {
        Objects.requireNonNull(getContext())
                .enforceCallingOrSelfPermission(
                        Manifest.permission.MANAGE_APP_FUNCTION_ACCESS,
                        "No permission to read AppFunction access history");
    }

    /** Checks the caller has permission to access the data in {@code targetUserId}. */
    private void checkTargetUserPermission(int targetUserId) {
        final int callingUid = Binder.getCallingUid();
        final int callingPid = Binder.getCallingPid();
        Objects.requireNonNull(mCallerValidator)
                .verifyUserInteraction(targetUserId, callingUid, callingPid);
    }

    private boolean accessCheckFlagsEnabled() {
        return android.permission.flags.Flags.appFunctionAccessApiEnabled()
                && android.permission.flags.Flags.appFunctionAccessServiceEnabled();
    }
}
