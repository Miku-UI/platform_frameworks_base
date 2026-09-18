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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManagerInternal;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.UserHandle;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.LocalServices;
import com.android.server.pm.UserManagerInternal;

/**
 * Centralized configuration, system signals, and policy for the Theme Service.
 *
 * <p>This class encapsulates:
 * <ul>
 *   <li>Immutable system configuration determined at service start (e.g., platform, specs).</li>
 *   <li>Dynamic system state queries (e.g., boot status, device lock state).</li>
 *   <li>Shared policy decisions (e.g., whether to process user events).</li>
 * </ul>
 *
 * <p>It serves as a single source of truth for environmental factors affecting theming logic.
 *
 * @hide
 */
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
public class ThemeEnvironment {
    private static final String TAG = "ThemeEnvironment";

    private ThemeUserLifecycle mThemeUserLifecycle;

    private final UserManagerInternal mUserManager;
    private final ActivityManagerInternal mActivityManager;
    private KeyguardManager mKeyguardManager;

    private final ThemeConfig mConfig;

    // --- DYNAMIC GLOBAL STATE ---

    /**
     * Whether the system is currently in the boot phase.
     * Transitions from {@code true} to {@code false} exactly once during boot.
     */
    private volatile boolean mIsBooting = true;

    ThemeEnvironment(@NonNull Context context,
            @NonNull SystemPropertiesReader reader) {

        mUserManager = LocalServices.getService(UserManagerInternal.class);
        mActivityManager = LocalServices.getService(ActivityManagerInternal.class);

        mConfig = new ThemeConfig(context, reader);
    }

    ThemeConfig getConfig() {
        return mConfig;
    }

    /**
     * Returns whether the system is currently in the boot phase.
     */
    boolean isBooting() {
        return mIsBooting;
    }

    /**
     * Marks the boot phase as complete. This should be called when the boot animation dismisses.
     */
    void setBootingComplete(ThemeUserLifecycle userLifecycle) {
        if (!isBooting()) return;
        mThemeUserLifecycle = userLifecycle;
        mIsBooting = false;
        Slog.d(TAG, "Boot phase complete.");
    }

    void onServicesReady(KeyguardManager keyguardManager) {
        mKeyguardManager = keyguardManager;
    }

    int getCurrentUserId() {
        return mActivityManager.getCurrentUserId();
    }

    /**
     * Returns true if the user is a valid target for theme updates based on system policy.
     *
     * <p>Returns false for the system user in HSUM (except iHSUM), or for profiles (which are
     * handled via their parents).
     */
    boolean isManagedUser(int userId) {
        if (mUserManager.isHeadlessSystemUserMode()
                && userId == UserHandle.USER_SYSTEM
                && !mConfig.canSwitchToHeadlessSystemUser()) {
            return false;
        }

        int parentId = mUserManager.getProfileParentId(userId);
        if (parentId != userId) {
            return false;
        }

        return true;
    }

    /**
     * Determines if a theme-related event should be ignored for a specific user.
     *
     * @param userId     The ID of the user to check.
     * @param methodName The name of the calling method for logging purposes.
     * @return {@code true} if the event should be ignored because the user is not managed
     * or their state could not be loaded; {@code false} otherwise.
     */
    boolean shouldIgnoreEventForUser(int userId, String methodName) {
        return shouldIgnoreEventForUser(userId, methodName, false);
    }

    boolean shouldIgnoreEventForUser(int userId, String methodName, boolean skipLazyLoad) {
        if (isBooting() && !skipLazyLoad) {
            return true;
        }

        // Use unified environment policy
        if (!isManagedUser(userId)) {
            Slog.d(TAG,
                    "Bypassing '" + methodName + "' for user " + userId + " per system policy.");
            return true;
        }

        if (!skipLazyLoad && mThemeUserLifecycle != null) {
            // Lazy load the user state if it's missing. This handles race conditions where
            // settings/events for a user arrive before the user lifecycle "START" event.
            if (!mThemeUserLifecycle.loadUserStateAndNotifyStateManager(userId)) {
                Slog.d(TAG, "Ignoring '" + methodName + "' for user " + userId
                        + " (State load failed)");
                return true;
            }
        }

        return false;
    }

    @Nullable
    Integer parentOf(int userId) {
        int possibleParentID = mUserManager.getProfileParentId(userId);
        return possibleParentID == userId ? null : possibleParentID;
    }

    boolean isDeviceLocked() {
        return mKeyguardManager.isDeviceLocked();
    }
}
