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

package com.android.server.appwindowlayout;

import android.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.content.PackageMonitor;

/**
 * {@link PackageMonitor} for {@link AppWindowLayoutSettingsService}.
 *
 * @hide
 */
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
public class AppWindowLayoutSettingsPackageMonitor extends PackageMonitor {
    private PackageAddedCallback mPackageAddedCallback;

    void setCallback(@NonNull PackageAddedCallback packageAddedCallback) {
        mPackageAddedCallback = packageAddedCallback;
    }

    @Override
    public void onPackageAdded(String packageName, int uid) {
        super.onPackageAdded(packageName, uid);
        final int userId = getChangingUserId();
        mPackageAddedCallback.onPackageAdded(packageName, userId);
    }

    interface PackageAddedCallback {
        void onPackageAdded(String packageName, int userId);
    }
}
