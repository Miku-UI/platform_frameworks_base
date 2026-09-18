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
import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.SystemProperties;
import android.util.Slog;

import androidx.annotation.VisibleForTesting;

import com.android.server.SystemService;

/**
 * ThemeManagerService is the main entry point for the theming system in Android.
 * <p>
 * Its purpose is to ensure that the system theme (colors, shapes, etc.) correctly reflects
 * the user's wallpaper, settings, and other system states (like Dark Mode).
 * <p>
 * Internally, it acts as a coordinator that initializes and connects the specialized components
 * that perform the actual work:
 * <ul>
 *     <li>{@link ThemeStateManager}: Manages the current and pending theme state for each user
 *     .</li>
 *     <li>{@link ThemeUserLifecycle}: Handles user-related events (start, switch, setup).</li>
 *     <li>{@link ThemeEventObserver}: Listens for system events (wallpaper, settings, lock
 *     state).</li>
 *     <li>{@link ThemeManagerImpl}: Provides a local interface for other system services.</li>
 *     <li>{@link ThemeEnvironment}: Provides access to system properties and state.</li>
 *     <li>{@link ThemeBinderService}: Provides the public Binder interface for apps.</li>
 * </ul>
 *
 * @hide
 */
@FlaggedApi(android.server.Flags.FLAG_ENABLE_THEME_SERVICE)
public class ThemeManagerService extends SystemService {
    private static final String TAG = "ThemeManagerService";

    private final ThemeManagerImpl mImpl;
    private final ThemeBinderService mPublic;
    private final ThemeStateManager mStateManager;
    private final ThemeUserLifecycle mUserLifecycle;
    private final ThemeEventObserver mEventObserver;
    private final ThemeInitializationObserver mInitializationObserver;
    private final ThemeWallpaperManager mThemeWallpaperManager;
    private final ThemeEnvironment mEnvironment;

    public ThemeManagerService(@NonNull Context context) {
        this(context, SystemProperties::get, null, null, null, null);
    }

    @VisibleForTesting
    ThemeManagerService(@NonNull Context context,
            @NonNull SystemPropertiesReader systemPropertiesReader,
            @Nullable ThemeEnvironment themeEnvironment,
            @Nullable ThemeStateManager themeStateManager,
            @Nullable ThemeUserLifecycle userLifecycle,
            @Nullable ThemeEventObserver eventObserver) {
        super(context);

        mEnvironment = themeEnvironment != null ? themeEnvironment
                : new ThemeEnvironment(context, systemPropertiesReader);

        ThemeOverlayHelper overlayHelper = new ThemeOverlayHelper();
        mStateManager = themeStateManager != null ? themeStateManager : new ThemeStateManager(
                context, mEnvironment);
        mThemeWallpaperManager = new ThemeWallpaperManager();
        ThemeSettingsManager themeSettingsManager = new ThemeSettingsManager(mThemeWallpaperManager,
                mEnvironment.getConfig());

        mUserLifecycle = userLifecycle != null ? userLifecycle : new ThemeUserLifecycle(context,
                mEnvironment);
        mEventObserver = eventObserver != null ? eventObserver : new ThemeEventObserver(context,
                mEnvironment);

        mImpl = new ThemeManagerImpl(context, themeSettingsManager, mStateManager, overlayHelper,
                mEnvironment, mThemeWallpaperManager, systemPropertiesReader, mUserLifecycle,
                mEventObserver);
        mPublic = new ThemeBinderService(context, mImpl);

        mInitializationObserver = new ThemeInitializationObserver(context, mImpl,
                mThemeWallpaperManager,
                mEnvironment);
    }

    @Override
    public void onStart() {
        publishLocalService(ThemeManagerInternal.class, mImpl);
        publishBinderService(Context.THEME_SERVICE, mPublic.asBinder());

        mInitializationObserver.onStart();
    }

    @Override
    @RequiresPermission(Manifest.permission.SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE)
    public void onBootPhase(@BootPhase int phase) {
        if (phase == SystemService.PHASE_SYSTEM_SERVICES_READY) {
            Slog.d(TAG, "Booth Phase SystemServices Ready: Wiring Components and Listeners.");
            KeyguardManager keyGuardManager = getContext().getSystemService(KeyguardManager.class);

            mEnvironment.onServicesReady(keyGuardManager);
            mStateManager.onServicesReady();
            mEventObserver.onServicesReady(mThemeWallpaperManager);

            mInitializationObserver.registerListeners();
        }

        mInitializationObserver.onBootPhase(phase);
    }

    @Override
    public void onUserStarting(@NonNull TargetUser user) {
        // Only process users immediately if the initialization sequence has finished.
        if (!mEnvironment.isBooting()) {
            mUserLifecycle.onUserStarting(user);
        }
    }

    @Override
    public void onUserSwitching(@Nullable TargetUser from, @NonNull TargetUser to) {
        if (!mEnvironment.isBooting()) {
            mUserLifecycle.onUserSwitching(from, to);
        }
    }

    /** @hide */
    @VisibleForTesting
    ThemeManagerInternal getLocalService() {
        return mImpl;
    }
}
