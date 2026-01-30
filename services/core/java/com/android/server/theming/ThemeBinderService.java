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

package com.android.server.theming;

import android.annotation.EnforcePermission;
import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.content.Context;
import android.content.theming.IThemeManager;
import android.content.theming.IThemeSettingsCallback;
import android.content.theming.ThemeSettings;
import android.os.Binder;
import android.os.UserHandle;

/**
 * Public implementation of {@link IThemeManager}.
 *
 * <p>This class provides the public API for interacting with the theming service.
 * It handles incoming requests from other system services and applications,
 * delegating the actual theming operations to the {@link ThemeManagerInternal} class.
 *
 * @hide
 */
@FlaggedApi(android.server.Flags.FLAG_ENABLE_THEME_SERVICE)
public class ThemeBinderService extends IThemeManager.Stub {
    private final Context mContext;
    private final ThemeManagerInternal mLocalService;

    public ThemeBinderService(Context context, ThemeManagerInternal localService) {
        mContext = context;
        mLocalService = localService;
    }

    @Override
    public boolean registerThemeSettingsCallback(@NonNull IThemeSettingsCallback callback) {
        return mLocalService.registerThemeSettingsCallback(getCallingUserId(), callback);
    }

    @Override
    public boolean unregisterThemeSettingsCallback(@NonNull IThemeSettingsCallback callback) {
        return mLocalService.unregisterThemeSettingsCallback(getCallingUserId(), callback);
    }

    @Override
    @EnforcePermission(android.Manifest.permission.UPDATE_THEME_SETTINGS)
    public boolean updateThemeSettings(@NonNull ThemeSettings newSettings) {
        updateThemeSettings_enforcePermission();
        return mLocalService.updateThemeSettings(getCallingUserId(), newSettings);
    }

    @Override
    public ThemeSettings getThemeSettings() {
        return mLocalService.getThemeSettings(getCallingUserId());
    }

    @Override
    public ThemeSettings getThemeSettingsOrDefault() {
        return mLocalService.getThemeSettingsOrDefault(getCallingUserId());
    }

    private int getCallingUserId() {
        return UserHandle.getUserId(Binder.getCallingUid());
    }
}
