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

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.app.KeyguardManager;
import android.app.WallpaperColors;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.BackgroundThread;
import com.android.server.LocalServices;
import com.android.server.UiModeManagerInternal;

import java.util.Collection;
import java.util.concurrent.Executor;

/**
 * Observes system events and settings changes to trigger theme updates.
 * <p>
 * This class registers listeners for:
 * <ul>
 *     <li>Wallpaper color changes</li>
 *     <li>Theme setting changes (style, color source)</li>
 *     <li>User setup completion</li>
 *     <li>Device lock state changes</li>
 * </ul>
 * It forwards these events to the {@link ThemeManagerImpl}.
 *
 * @hide
 */
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
public class ThemeEventObserver {
    private static final String TAG = "ThemeEventObserver";

    private final Context mContext;
    private final ThemeEnvironment mEnvironment;
    private ThemeEventDispatcher mDispatcher;

    private ThemeWallpaperManager mWallpaperManager;
    private UiModeManagerInternal mUiModeManagerInternal;
    private KeyguardManager mKeyguardManager;

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    ThemeEventObserver(Context context, ThemeEnvironment environment) {
        mContext = context;
        mEnvironment = environment;
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    void setDispatcher(ThemeEventDispatcher delegate) {
        mDispatcher = delegate;
    }

    /**
     * Called when the system is booting up.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    @RequiresPermission(Manifest.permission.SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE)
    public void onServicesReady(ThemeWallpaperManager wallpaperManager) {
        mWallpaperManager = wallpaperManager;
        mUiModeManagerInternal = LocalServices.getService(UiModeManagerInternal.class);
        mKeyguardManager = mContext.getSystemService(KeyguardManager.class);
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public final BroadcastReceiver mOverlayReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_OVERLAY_CHANGED.equals(intent.getAction())) {
                handleOverlayChanged(intent);
            }
        }
    };

    /**
     * Registers various listeners for system events and settings changes.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    @RequiresPermission(Manifest.permission.SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE)
    public void registerListeners() {
        Executor bgExecutor = BackgroundThread.getExecutor();
        Handler bgHandler = BackgroundThread.getHandler();

        // Wallpaper Color Change
        mWallpaperManager.addOnColorsChangedListener(
                (wallpaperColors, which, displayId, userId, fromForegroundApp)
                        -> handleWallpaperColorsChanged(wallpaperColors, userId, fromForegroundApp),
                bgHandler);

        // Contrast Change
        mUiModeManagerInternal.addContrastListener(this::handleContrastChanged, bgExecutor);

        // Lock State Change
        mKeyguardManager.addKeyguardLockedStateListener(bgExecutor,
                this::handleKeyguardLockedStateChanged);

        // Settings Changes (Setup Complete & Theme Style/Color)
        ContentResolver resolver = mContext.getContentResolver();
        resolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.USER_SETUP_COMPLETE), false,
                mUserSetupObserver, UserHandle.USER_ALL);

        resolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES),
                false, mThemeSettingsObserver, UserHandle.USER_ALL);

        // Overlay Changes
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_OVERLAY_CHANGED);
        filter.addDataScheme("package");
        mContext.registerReceiver(mOverlayReceiver, filter, null, bgHandler);
    }

    // Event Handlers

    private void handleOverlayChanged(Intent intent) {
        final Uri data = intent.getData();
        if (data == null) return;
        final String changedPackage = data.getSchemeSpecificPart();
        if ("android".equals(changedPackage)) {
            final int userId = intent.getIntExtra(Intent.EXTRA_USER_ID, UserHandle.USER_NULL);

            if (userId == UserHandle.USER_NULL || !mEnvironment.isManagedUser(userId)) {
                return;
            }

            Slog.i(TAG, "Theme overlays successfully applied for user " + userId);
            mDispatcher.notifyThemeChanged(userId);
        }
    }

    @RequiresPermission(Manifest.permission.SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE)
    private void handleWallpaperColorsChanged(WallpaperColors wallpaperColors, int userId,
            boolean fromForegroundApp) {
        if (mEnvironment.shouldIgnoreEventForUser(userId, "onColorsChanged")) {
            return;
        }
        if (wallpaperColors == null) {
            Slog.d(TAG,
                    "Wallpaper color change ignored due to WallpaperManager providing null "
                            + "WallpaperColors");
            return;
        }

        Slog.d(TAG, "User: " + userId + " changed wallpaper");
        mDispatcher.onWallpaperColorsChanged(userId, wallpaperColors, fromForegroundApp);
    }

    private void handleContrastChanged(int userId, float contrast) {
        if (mEnvironment.shouldIgnoreEventForUser(userId, "onContrastChange")) {
            return;
        }
        mDispatcher.onContrastChanged(userId, contrast);
    }

    private void handleKeyguardLockedStateChanged(boolean isKeyguardLocked) {
        if (isKeyguardLocked) {
            Slog.d(TAG, "Keyguard locked");
            mDispatcher.onDeviceLocked();
        }
    }

    final ContentObserver mUserSetupObserver = new ContentObserver(BackgroundThread.getHandler()) {
        @Override
        public void onChange(boolean selfChange, @NonNull Collection<Uri> uris, int flags,
                int userId) {
            handleUserSetupChanged(userId);
        }
    };

    void handleUserSetupChanged(int userId) {
        if (mEnvironment.shouldIgnoreEventForUser(userId, "onFinishSetup")) {
            return;
        }
        Slog.d(TAG, "User: " + userId + " setup complete");
        mDispatcher.onUserStart(userId);
    }

    final ContentObserver mThemeSettingsObserver = new ContentObserver(
            BackgroundThread.getHandler()) {
        @Override
        public void onChange(boolean selfChange, @NonNull Collection<Uri> uris, int flags,
                int userId) {
            handleThemeCustomizationChanged(userId);
        }
    };

    void handleThemeCustomizationChanged(int userId) {
        if (mEnvironment.shouldIgnoreEventForUser(userId, "onThemeSettingsChanged")) {
            return;
        }

        Slog.d(TAG, "User: " + userId + " updated Secure Setting directly");
        mDispatcher.onThemeSettingsChanged(userId);
    }
}
