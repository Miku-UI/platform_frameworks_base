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
import android.content.pm.PackageManager;
import android.content.theming.IThemeChangedCallback;
import android.content.theming.IThemeManager;
import android.content.theming.IThemeSettingsCallback;
import android.content.theming.ThemeInfo;
import android.content.theming.ThemeSettings;
import android.os.Binder;
import android.os.FabricatedOverlayInternal;
import android.os.RemoteException;
import android.os.UserHandle;

import java.io.FileDescriptor;
import java.io.PrintWriter;

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

    ThemeBinderService(Context context, ThemeManagerInternal localService) {
        mContext = context;
        mLocalService = localService;
    }

    @Override
    public FabricatedOverlayInternal generateDynamicColorOverlay(ThemeInfo options) {
        return mLocalService.generateDynamicColorOverlay(getCallingUserId(), options);
    }

    @Override
    public ThemeInfo getUserThemeInfo() throws RemoteException {
        return mLocalService.getUserThemeInfo(getCallingUserId());
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
    public boolean registerThemeChangedCallback(@NonNull IThemeChangedCallback callback) {
        return mLocalService.registerThemeChangedCallback(getCallingUserId(), callback);
    }

    @Override
    public boolean unregisterThemeChangedCallback(@NonNull IThemeChangedCallback callback) {
        return mLocalService.unregisterThemeChangedCallback(getCallingUserId(), callback);
    }

    @Override
    @EnforcePermission(android.Manifest.permission.UPDATE_THEME_SETTINGS)
    public boolean updateThemeSettings(@NonNull ThemeSettings newSettings) {
        updateThemeSettings_enforcePermission();
        final int userId = getCallingUserId();
        final long identity = Binder.clearCallingIdentity();
        try {
            return mLocalService.updateThemeSettings(userId, newSettings);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public ThemeSettings getThemeSettings() {
        final int userId = getCallingUserId();
        final long identity = Binder.clearCallingIdentity();
        try {
            return mLocalService.getThemeSettings(userId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public ThemeSettings getThemeSettingsOrDefault() {
        final int userId = getCallingUserId();
        final long identity = Binder.clearCallingIdentity();
        try {
            return mLocalService.getThemeSettingsOrDefault(userId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private int getCallingUserId() {
        return UserHandle.getUserId(Binder.getCallingUid());
    }

    @Override
    public void dump(@NonNull FileDescriptor fd, @NonNull PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            pw.println(
                    "Permission Denial: can't dump theme service from pid=" + Binder.getCallingPid()
                            + ", uid=" + Binder.getCallingUid() + " without permission "
                            + android.Manifest.permission.DUMP);
            return;
        }
        super.dump(fd, pw, args);
        mLocalService.dump(pw);
    }
}
