/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.dreams;

import static android.Manifest.permission.BIND_DREAM_SERVICE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.util.Slog;

/**
 * Validates that a dream component exists and has the necessary permissions.
 *
 * @deprecated Replaced by {@link DreamMetadataProvider} when the
 *     android.service.dreams.dreams_switcher flag is enabled
 */
@Deprecated
final class DreamValidator {
    private static final String TAG = "DreamValidator";

    private final Context mContext;

    DreamValidator(Context context) {
        mContext = context;
    }

    /** returns true if the component is a valid dream. */
    public boolean validate(@Nullable ComponentName component, int userId) {
        if (component == null) return false;
        final ServiceInfo serviceInfo = getServiceInfo(component);
        if (serviceInfo == null) {
            Slog.w(TAG, "Dream " + component + " does not exist on user " + userId);
            return false;
        } else if (serviceInfo.applicationInfo.targetSdkVersion >= Build.VERSION_CODES.LOLLIPOP
                && !BIND_DREAM_SERVICE.equals(serviceInfo.permission)) {
            Slog.w(
                    TAG,
                    "Dream "
                            + component
                            + " is not available because its manifest is missing the "
                            + BIND_DREAM_SERVICE
                            + " permission on the dream service declaration.");
            return false;
        }
        return true;
    }

    @Nullable
    private ServiceInfo getServiceInfo(@NonNull ComponentName name) {
        try {
            return mContext.getPackageManager()
                    .getServiceInfo(name, PackageManager.MATCH_DEBUG_TRIAGED_MISSING);
        } catch (NameNotFoundException e) {
            return null;
        }
    }
}
