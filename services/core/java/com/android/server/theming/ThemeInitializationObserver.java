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

package com.android.server.theming;

import android.Manifest;
import android.annotation.RequiresPermission;
import android.app.WallpaperColors;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.UserHandle;
import android.util.Slog;
import android.view.Display;

import com.android.internal.os.BackgroundThread;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.wallpaper.WallpaperManagerInternal;
import com.android.server.wm.WindowManagerInternal;

/**
 * Handles the events specifically required to move the theming system from
 * "Booting" to "Ready".
 * <p>
 * It listens for the first wallpaper color event to trigger system initialization
 * and the first overlay change event to confirm the theme is applied before
 * signaling the system is ready.
 *
 * @hide
 */
class ThemeInitializationObserver {
    private static final String TAG = "ThemeInitializationObserver";

    private final Context mContext;
    private final ThemeManagerImpl mImpl;
    private final ThemeWallpaperManager mWallpaperManager;
    private final ThemeEnvironment mEnvironment;

    private boolean mTriggered = false;
    private boolean mWaitingForOverlay = false;

    private final WallpaperManagerInternal.ColorsChangedCallbackInternal mWallpaperListener =
            this::onWallpaperColorsChanged;

    private final BroadcastReceiver mOverlayReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String changedPackage = intent.getData() != null
                    ? intent.getData().getSchemeSpecificPart() : "null";
            final int userId = intent.getIntExtra(Intent.EXTRA_USER_ID, UserHandle.USER_NULL);

            Slog.d(TAG, "onReceive: ACTION_OVERLAY_CHANGED for package=" + changedPackage
                    + " user=" + userId + " mWaitingForOverlay=" + mWaitingForOverlay);

            if (!mWaitingForOverlay) {
                return;
            }

            if ("android".equals(changedPackage)) {
                // Check if the overlays for the current user are now correct.
                if (userId == mEnvironment.getCurrentUserId()) {
                    if (mImpl.checkAndSignalReady(userId)) {
                        Slog.i(TAG, "Initial theme application confirmed. System is ready.");
                        finish();
                    } else {
                        Slog.d(TAG,
                                "Overlay change for 'android' received, but checkAndSignalReady "
                                        + "returned false.");
                    }
                }
            }
        }
    };

    ThemeInitializationObserver(Context context, ThemeManagerImpl impl,
            ThemeWallpaperManager wallpaperManager, ThemeEnvironment environment) {
        mContext = context;
        mImpl = impl;
        mWallpaperManager = wallpaperManager;
        mEnvironment = environment;
    }

    /**
     * Called when the ThemeManagerService is starting.
     */
    void onStart() {
        WindowManagerInternal wm = LocalServices.getService(WindowManagerInternal.class);
        if (wm != null) {
            Slog.i(TAG, "Registering ThemeManager boot screen blocker.");
            wm.setThemeReady(false);
        }
    }

    /**
     * Registers listeners for initialization triggers.
     */
    @RequiresPermission(Manifest.permission.SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE)
    void registerListeners() {
        Slog.d(TAG, "Registering initialization listeners.");

        // Always listen for overlay changes to confirm completion.
        final IntentFilter filter = new IntentFilter(Intent.ACTION_OVERLAY_CHANGED);
        filter.addDataScheme("package");
        mContext.registerReceiver(mOverlayReceiver, filter, null, BackgroundThread.getHandler());

        int userId = mEnvironment.getCurrentUserId();
        boolean requiresWallpaper = mImpl.requiresWallpaperForInitialization(userId);
        boolean wallpaperManagerAvailable = mWallpaperManager.isWallpaperManagerAvailable();

        if (requiresWallpaper && wallpaperManagerAvailable) {
            Slog.i(TAG, "Waiting for initial wallpaper colors.");
            // Listen for the first wallpaper color change to kick off initialization.
            mWallpaperManager.addOnColorsChangedListener(mWallpaperListener,
                    BackgroundThread.getHandler());
        } else {
            if (!wallpaperManagerAvailable) {
                Slog.i(TAG, "Wallpaper service unavailable. Triggering immediate initialization.");
            } else {
                Slog.i(TAG, "Theme does not require wallpaper colors. Triggering immediate "
                        + "initialization.");
            }
            triggerInitialization(userId);
        }
    }

    /**
     * Called when a boot phase is reached.
     */
    @RequiresPermission(Manifest.permission.SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE)
    void onBootPhase(int phase) {
        if (phase == SystemService.PHASE_BOOT_COMPLETED) {
            if (mTriggered) return;
            Slog.i(TAG, "Wallpaper colors not received by BOOT_COMPLETE. Forcing initialization.");
            triggerInitialization(mEnvironment.getCurrentUserId());
        }
    }

    @RequiresPermission(Manifest.permission.SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE)
    private void onWallpaperColorsChanged(WallpaperColors colors, int which,
            int displayId, int userId, boolean fromForegroundApp) {
        if (displayId != Display.DEFAULT_DISPLAY || userId != mEnvironment.getCurrentUserId()) {
            Slog.d(TAG, "Ignoring wallpaper colors from display=" + displayId + " user=" + userId);
            return;
        }
        Slog.i(TAG, "First Wallpaper Colors Received: " + colors);
        triggerInitialization(userId);
    }

    @RequiresPermission(Manifest.permission.SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE)
    private void triggerInitialization(int userId) {
        // Only trigger if we are still in the booting phase and haven't triggered yet.
        if (mTriggered || !mEnvironment.isBooting()) {
            return;
        }
        mTriggered = true;

        Slog.i(TAG, "Triggering initialization for user " + userId);

        if (mImpl.initializeThemingSystem()) {
            // Initialization started an update. The overlay receiver is already
            // registered and will handle the transition once the update is applied.
            mWaitingForOverlay = true;

            // Check if we are already ready (e.g. if the update was synchronous and already
            // reflected)
            if (mImpl.checkAndSignalReady(userId)) {
                Slog.i(TAG, "System already ready after initialization. Finishing observer.");
                finish();
            }
        } else {
            // No update was needed (theme already matches), so we are ready now.
            mImpl.onThemingSystemReady();
            finish();
        }
    }

    private void finish() {
        mWaitingForOverlay = false;
        mWallpaperManager.removeOnColorsChangedListener(mWallpaperListener);
        try {
            mContext.unregisterReceiver(mOverlayReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver might not be registered or already unregistered.
        }

        WindowManagerInternal wm = LocalServices.getService(WindowManagerInternal.class);
        if (wm != null) {
            Slog.i(TAG, "Notifying WindowManager to remove ThemeManager boot blocker.");
            wm.setThemeReady(true);
        }
    }
}
