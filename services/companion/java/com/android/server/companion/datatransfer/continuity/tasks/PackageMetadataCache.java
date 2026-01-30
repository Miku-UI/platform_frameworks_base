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

package com.android.server.companion.datatransfer.continuity.tasks;

import static com.android.server.companion.datatransfer.contextsync.BitmapUtils.renderDrawableToByteArray;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.graphics.drawable.Drawable;
import android.util.Slog;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.concurrent.GuardedBy;

public class PackageMetadataCache {

    private static final String TAG = "PackageMetadataCache";

    private final PackageManager mPackageManager;

    @GuardedBy("this")
    private final Map<String, PackageMetadata> mPackageMetadataMap = new HashMap<>();

    public PackageMetadataCache(@NonNull PackageManager packageManager) {
        mPackageManager = Objects.requireNonNull(packageManager);
    }

    @Nullable
    public PackageMetadata getMetadataForPackage(@NonNull String packageName) {
        Objects.requireNonNull(packageName);

        synchronized (this) {
            if (mPackageMetadataMap.containsKey(packageName)) {
                return mPackageMetadataMap.get(packageName);
            }

            PackageInfo packageInfo;
            try {
                packageInfo = mPackageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_META_DATA);
            } catch (PackageManager.NameNotFoundException e) {
                Slog.e(TAG, "Failed to get package info for package: " + packageName, e);
                return null;
            }

            CharSequence label = mPackageManager.getApplicationLabel(packageInfo.applicationInfo);
            if (label == null) {
                Slog.e(TAG, "PackageManager returned null label for package: " + packageName);
                return null;
            }

            Drawable icon = mPackageManager.getApplicationIcon(packageInfo.applicationInfo);
            if (icon == null) {
                Slog.e(TAG, "PackageManager returned null icon for package: " + packageName);
                return null;
            }

            byte[] serializedIcon = renderDrawableToByteArray(icon);
            if (serializedIcon == null) {
                Slog.e(TAG, "Failed to serialize icon for package: " + packageName);
                return null;
            }

            PackageMetadata metadata = new PackageMetadata(label.toString(), serializedIcon);
            mPackageMetadataMap.put(packageName, metadata);
            return metadata;
        }
    }
}