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

package com.android.server.appinteraction;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AppInteractionContract;
import android.app.appfunctions.flags.Flags;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.UserHandle;

import java.util.Objects;

/** The provider for App Interaction history. */
public class AppInteractionHistoryProvider extends ContentProvider {
    private static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    private static final int USER = 0;

    static {
        sURIMatcher.addURI(AppInteractionContract.AUTHORITY, "user/#", USER);
    }

    @Nullable private MultiUserAppInteractionHistory mMultiUserAppInteractionHistory;

    @Override
    public boolean onCreate() {
        if (!Flags.enableAppInteractionApi()) return true;
        mMultiUserAppInteractionHistory =
                MultiUserAppInteractionHistory.getInstance(Objects.requireNonNull(getContext()));
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
        if (!Flags.enableAppInteractionApi()) return null;

        checkCallerPermission();

        final int targetUserId = getTargetUserId(uri);
        checkTargetUserPermission(targetUserId);

        return Objects.requireNonNull(mMultiUserAppInteractionHistory)
                .asUser(targetUserId)
                .queryAppInteractionHistories(projection, selection, selectionArgs, sortOrder);
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
                "Insert is not supported with AppInteractionHistoryProvider");
    }

    @Override
    public int delete(
            @NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException(
                "Delete is not supported with AppInteractionHistoryProvider");
    }

    @Override
    public int update(
            @NonNull Uri uri,
            @Nullable ContentValues values,
            @Nullable String selection,
            @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException(
                "Update is not supported with AppInteractionHistoryProvider");
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

    /** Checks if the caller has permission to read interaction history. */
    private void checkCallerPermission() {
        Objects.requireNonNull(getContext())
                .enforceCallingOrSelfPermission(
                        Manifest.permission.READ_APP_INTERACTION,
                        "No permission to read App Interaction history");
    }

    /** Checks the caller has permission to access the data in {@code targetUserId}. */
    private void checkTargetUserPermission(int targetUserId) {
        Context context = Objects.requireNonNull(getContext());

        final int callingUid = Binder.getCallingUid();

        UserHandle targetUserHandle = UserHandle.of(targetUserId);
        UserHandle callingUserHandle = UserHandle.getUserHandleForUid(callingUid);
        if (callingUserHandle.equals(targetUserHandle)) {
            return;
        }

        // Duplicates UserController#ensureNotSpecialUser
        if (targetUserHandle.getIdentifier() < 0) {
            throw new IllegalArgumentException(
                    "Call does not support special user " + targetUserHandle);
        }

        context.enforceCallingOrSelfPermission(
                Manifest.permission.INTERACT_ACROSS_USERS_FULL,
                "Permission denied while calling from uid "
                        + callingUid
                        + " with "
                        + targetUserHandle
                        + "; Requires permission: "
                        + Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }
}
