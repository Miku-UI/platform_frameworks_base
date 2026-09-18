/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.companion.devicepresence;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.os.ParcelUuid;
import android.os.PersistableBundle;

public record ObservableUuid(
        @UserIdInt int userId,
        @NonNull ParcelUuid uuid,
        @NonNull String packageName,
        long timeApprovedMs) {

    private static final String KEY_USER_ID = "USER_ID";
    private static final String KEY_UUID = "UUID";
    static final String KEY_PACKAGE_NAME = "PACKAGE_NAME";
    private static final String KEY_TIME_APPROVED = "TIME_APPROVED";

    /** Convert to a PersistableBundle. */
    public PersistableBundle toPersistableBundle() {
        final PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(KEY_USER_ID, userId);
        bundle.putString(KEY_UUID, uuid.toString());
        bundle.putString(KEY_PACKAGE_NAME, packageName);
        bundle.putLong(KEY_TIME_APPROVED, timeApprovedMs);

        return bundle;
    }

    public ObservableUuid(PersistableBundle bundle) {
        this(bundle.getInt(KEY_USER_ID),
                ParcelUuid.fromString(bundle.getString(KEY_UUID)),
                bundle.getString(KEY_PACKAGE_NAME),
                bundle.getLong(KEY_TIME_APPROVED));
    }
}
