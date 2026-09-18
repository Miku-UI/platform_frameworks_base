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
import android.annotation.UserIdInt;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.UserHandle;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.BackgroundThread;
import com.android.server.SystemService;

/**
 * Handles user lifecycle events for the ThemeManagerService.
 * <p>
 * This class listens for when users start, switch, or complete actions.
 * It then tells the ThemeStateManager to update the theme if needed.
 *
 * @hide
 */
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
public class ThemeUserLifecycle {
    private static final String TAG = "ThemeUserLifecycle";

    private final Context mContext;
    private final ThemeEnvironment mEnvironment;
    private ThemeEventDispatcher mDispatcher;

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    ThemeUserLifecycle(@NonNull Context context, @NonNull ThemeEnvironment environment) {
        mContext = context;
        mEnvironment = environment;
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    void setDispatcher(ThemeEventDispatcher delegate) {
        mDispatcher = delegate;
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    final BroadcastReceiver mUserLifecycleReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_PROFILE_ADDED.equals(intent.getAction())) {
                handleProfileAdded(intent);
            }
        }
    };

    /**
     * Registers for user-related system broadcasts.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void registerListeners() {
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PROFILE_ADDED);
        filter.addDataScheme("package");

        mContext.registerReceiver(mUserLifecycleReceiver, filter, null,
                BackgroundThread.getHandler());
    }

    /**
     * Called when a user starts.
     *
     * @param user The user that is starting.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void onUserStarting(@NonNull SystemService.TargetUser user) {
        Slog.d(TAG, "User: " + user.getUserIdentifier() + " starting");
        mDispatcher.onUserStart(user.getUserHandle().getIdentifier());
    }

    /**
     * Called when users switch.
     *
     * @param from The user being switched from.
     * @param to   The user being switched to.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void onUserSwitching(@Nullable SystemService.TargetUser from,
            @NonNull SystemService.TargetUser to) {
        Slog.d(TAG, "User switch from:" + (from != null ? from.getUserIdentifier() : "-") + " to: "
                + to.getUserIdentifier());
        mDispatcher.onUserSwitching(from != null ? from.getUserIdentifier() : 0,
                to.getUserIdentifier());
    }

    /**
     * Called during initialization to load all existing users' theme states.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void loadCurrentUser() {
        int userId = mEnvironment.getCurrentUserId();
        loadUserStateAndNotifyStateManager(userId);
    }

    /**
     * Loads a user's theme state and notifies the state manager.
     *
     * @return true if the state manager has the state for the user.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public boolean loadUserStateAndNotifyStateManager(@UserIdInt int userId) {
        mDispatcher.onUserStart(userId);
        return true; // Simplified return, actual check in Impl/StateManager
    }

    private void handleProfileAdded(Intent intent) {
        UserHandle newUserHandle = intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle.class);
        if (newUserHandle == null) return;
        int newUserOrProfileId = newUserHandle.getIdentifier();

        Integer parentId = mEnvironment.parentOf(newUserOrProfileId);
        if (parentId == null) {
            return;
        }

        Slog.d(TAG, "User: " + newUserOrProfileId + " added to parent: " + parentId);
        mDispatcher.onProfileAdded(parentId, newUserOrProfileId);
    }
}