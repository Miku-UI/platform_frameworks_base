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

package com.android.server.companion.virtual.computercontrol;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.app.IApplicationThread;
import android.app.NotificationManager;
import android.companion.virtual.computercontrol.ComputerControlSessionParams;
import android.companion.virtual.computercontrol.IComputerControlSessionCallback;
import android.content.AttributionSource;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.UserHandle;

import java.util.Objects;

/**
 * Data class encapsulating a request for the creation of a new
 * {@link android.companion.virtual.computercontrol.ComputerControlSession}
 */
public record ComputerControlSessionRequest(
        // Provided fields
        @NonNull IApplicationThread appThread,
        @NonNull AttributionSource attributionSource,
        @NonNull ComputerControlSessionParams params,
        @NonNull IComputerControlSessionCallback callback,

        // Derived fields
        @NonNull String name,
        @NonNull IBinder appToken,
        @NonNull UserHandle ownerUser,
        @UserIdInt int ownerUserId,
        int ownerUid,
        @NonNull String ownerPackageName,
        @NonNull Context ownerContext,
        @NonNull PackageManager ownerPackageManager,
        @NonNull NotificationManager ownerNotificationManager) {

    /** Creates a new request. */
    public static ComputerControlSessionRequest create(
            @NonNull Context context,
            @NonNull IApplicationThread appThread,
            @NonNull AttributionSource attributionSource,
            @NonNull ComputerControlSessionParams params,
            @NonNull IComputerControlSessionCallback callback) {
        UserHandle ownerUser = UserHandle.getUserHandleForUid(attributionSource.getUid());
        Context ownerContext = Objects.requireNonNull(Binder.withCleanCallingIdentity(
                () -> context.createContextAsUser(ownerUser, /* flags= */ 0)));

        return new ComputerControlSessionRequest(
                appThread,
                attributionSource,
                params,
                callback,
                /* name= */ params.getName(),
                /* appToken= */ callback.asBinder(),
                ownerUser,
                /* ownerUserId= */ ownerUser.getIdentifier(),
                /* ownerUid= */ attributionSource.getUid(),
                /* ownerPackageName= */ Objects.requireNonNull(attributionSource.getPackageName()),
                ownerContext,
                /* ownerPackageManager= */ ownerContext.getPackageManager(),
                /* ownerNotificationManager= */ ownerContext.getSystemService(
                        NotificationManager.class));
    }
}
