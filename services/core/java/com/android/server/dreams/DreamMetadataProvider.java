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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.dreams.DreamItem;
import android.service.dreams.DreamService;
import android.util.IndentingPrintWriter;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Provider for {@link DreamItem}s, handling parsing and caching. */
class DreamMetadataProvider {
    private static final String TAG = "DreamMetadataProvider";

    private final PackageManager mPackageManager;
    private final DreamMetadataParser mMetadataParser;
    private final ConcurrentHashMap<ComponentName, Optional<DreamItem>> mCache =
            new ConcurrentHashMap<>();

    interface DreamMetadataParser {
        @Nullable
        DreamService.DreamMetadata getDreamMetadata(
                @NonNull PackageManager packageManager, @Nullable ServiceInfo serviceInfo);
    }

    DreamMetadataProvider(@NonNull Context context) {
        this(context, DreamService::getDreamMetadata);
    }

    @VisibleForTesting
    DreamMetadataProvider(@NonNull Context context, @NonNull DreamMetadataParser metadataParser) {
        mPackageManager = context.getPackageManager();
        mMetadataParser = metadataParser;
    }

    @Nullable
    DreamItem getDreamItem(@NonNull ComponentName component) {
        return mCache.computeIfAbsent(component, this::loadDreamItem).orElse(null);
    }

    /** Invalidates the cache for the given package. */
    void invalidatePackage(String packageName) {
        mCache.entrySet().removeIf(entry -> entry.getKey().getPackageName().equals(packageName));
    }

    void invalidateCache() {
        mCache.clear();
    }

    void dump(@NonNull PrintWriter pw) {
        IndentingPrintWriter ipw = new IndentingPrintWriter(pw, "  ");
        ipw.println("DreamMetadataProvider:");
        ipw.increaseIndent();

        ipw.println("mCache:");
        ipw.increaseIndent();
        mCache.forEach(
                (component, item) -> {
                    ipw.println(
                            ComponentName.flattenToShortString(component)
                                    + ": "
                                    + item.orElse(null));
                });
        ipw.decreaseIndent();

        ipw.decreaseIndent();
    }

    private Optional<DreamItem> loadDreamItem(ComponentName component) {
        ServiceInfo serviceInfo = null;
        try {
            serviceInfo = mPackageManager.getServiceInfo(component, PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            // Component not found
        }

        if (serviceInfo == null) {
            return Optional.empty();
        }

        if (serviceInfo.applicationInfo.targetSdkVersion >= Build.VERSION_CODES.LOLLIPOP
                && !android.Manifest.permission.BIND_DREAM_SERVICE.equals(serviceInfo.permission)) {
            Slog.w(
                    TAG,
                    "Dream "
                            + component
                            + " is not available because its manifest is missing the "
                            + android.Manifest.permission.BIND_DREAM_SERVICE
                            + " permission on the dream service declaration.");
            return Optional.empty();
        }

        DreamService.DreamMetadata metadata =
                mMetadataParser.getDreamMetadata(mPackageManager, serviceInfo);

        ComponentName settingsActivity = null;
        Icon previewImage = null;

        if (metadata != null) {
            settingsActivity = metadata.settingsActivity;
            if (metadata.previewImageResId != 0) {
                previewImage =
                        Icon.createWithResource(
                                component.getPackageName(), metadata.previewImageResId);
            }
        }

        CharSequence title = serviceInfo.loadLabel(mPackageManager);
        CharSequence description = null;
        if (serviceInfo.descriptionRes != 0) {
            // Load description from the package of the service
            description =
                    mPackageManager.getText(
                            component.getPackageName(),
                            serviceInfo.descriptionRes,
                            serviceInfo.applicationInfo);
        }

        Icon icon = null;
        if (serviceInfo.icon != 0) {
            icon = Icon.createWithResource(component.getPackageName(), serviceInfo.icon);
        } else if (serviceInfo.applicationInfo.icon != 0) {
            icon =
                    Icon.createWithResource(
                            component.getPackageName(), serviceInfo.applicationInfo.icon);
        } else {
            // Fallback to system default if no icon is defined
            icon =
                    Icon.createWithResource(
                            Resources.getSystem(),
                            com.android.internal.R.drawable.sym_def_app_icon);
        }

        return Optional.of(
                new DreamItem.Builder(component)
                        .setSettingsActivity(settingsActivity)
                        .setPreviewImage(previewImage)
                        .setTitle(title)
                        .setDescription(description)
                        .setIcon(icon)
                        .build());
    }
}
