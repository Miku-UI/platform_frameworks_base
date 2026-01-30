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

package com.android.server.usb;

import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

/**
 * Usb4Manager manages USB4 related functionalities such as setting mode preferences to prioritize
 * Thunderbolt and authorizing PCI tunnels.
 */
public class Usb4Manager {
    private static final String TAG = Usb4Manager.class.getSimpleName();

    private final Context mContext;
    private final UserManager mUserManager;

    @GuardedBy("mLock")
    private @UserIdInt int mCurrentUserId;

    @GuardedBy("mLock")
    private boolean mIsScreenLocked;

    private final Object mLock = new Object();

    /**
     * Native methods for the Usb4Manager.
     *
     * <p> This class exists to make testing easier. The native methods are loaded in the
     * init(), and can be mocked in tests.
     */
    public static class Usb4ManagerNative {
        private Usb4Manager mUsb4Manager;

        public Usb4ManagerNative() {
        }

        void init(Usb4Manager usb4Manager) {
            mUsb4Manager = usb4Manager;
            System.loadLibrary("usb4_policies_jni");
            mUsb4Manager.nativeInit();
        }

        void enablePciTunnels(boolean enable) {
            mUsb4Manager.enablePciTunnels(enable);
        }

        void updateLockState(boolean locked) {
            mUsb4Manager.updateLockState(locked);
        }

        void updateLoggedInState(boolean loggedIn, long userId) {
            mUsb4Manager.updateLoggedInState(loggedIn, userId);
        }
    }

    private Usb4ManagerNative mUsb4ManagerNative;

    @VisibleForTesting
    public Usb4Manager(
            Context context, UserManager userManager, Usb4ManagerNative usb4ManagerNative) {
        mContext = context;
        mUserManager = userManager;
        mUsb4ManagerNative = usb4ManagerNative;

        // Default to the system user.
        mCurrentUserId = UserHandle.USER_SYSTEM;

        // Initialize the native methods.
        mUsb4ManagerNative.init(this);

        // When the flag is set, default to allowing pci tunnels.
        // Call the native method directly (bypassing user checks).
        if (com.android.server.usb.flags.Flags.defaultAllowPciTunnels()) {
            mUsb4ManagerNative.enablePciTunnels(true);
        }
    }

    public Usb4Manager(Context context, UserManager userManager) {
        this(context, userManager, new Usb4ManagerNative());
    }

    /**
     * Enable or disable PCI tunnels.
     *
     * @param enable true to enable PCI tunnels, false to disable PCI tunnels.
     * @throws IllegalStateException if the currently logged in user is not full or admin.
     */
    @SuppressLint("AndroidFrameworkRequiresPermission") // TODO(b/440646300)
    public void onEnablePciTunnels(boolean enable) {
        Slog.d(TAG, "enablePciTunnels: " + enable);

        synchronized (mLock) {
            UserInfo userInfo = mUserManager.getUserInfo(mCurrentUserId);
            if (userInfo == null) {
                Slog.e(TAG, "enablePciTunnels: UserInfo is null");
                return;
            }

            if (!userInfo.isFull() || !userInfo.isAdmin()) {
                Slog.e(TAG, "enablePciTunnels: User is not full or admin");
                throw new IllegalStateException("User is not full or admin");
            }
        }

        mUsb4ManagerNative.enablePciTunnels(enable);
    }

    /**
     * Update the screen locked state.
     *
     * @param locked True when the screen is locked, false otherwise.
     */
    public void onUpdateScreenLockedState(boolean locked) {
        Slog.d(TAG, "updateLockState: " + locked);

        synchronized (mLock) {
            mIsScreenLocked = locked;
        }

        mUsb4ManagerNative.updateLockState(locked);
    }

    /**
     * Update the logged in state of a user.
     *
     * <p>Note: Changes to the logged in state of the system user are ignored.
     *
     * @param loggedIn True when a user logs in, False when a user is stopped.
     * @param userId User that is the target of this state change.
     */
    public void onUpdateLoggedInState(boolean loggedIn, int userId) {
        Slog.d(TAG, "updateLoggedInState: " + loggedIn + " " + userId);

        synchronized (mLock) {
            // Update the current user id to the logged in user.
            if (loggedIn) {
                mCurrentUserId = userId;
            }
        }

        if (userId == UserHandle.USER_SYSTEM) {
            Slog.d(TAG, "updateLoggedInState: User is system user. Do nothing.");
            return;
        }

        mUsb4ManagerNative.updateLoggedInState(loggedIn, userId);
    }

    /**
     * Native methods for the Usb4Manager. Called by Usb4ManagerNative for testing.
     */
    native void nativeInit();
    native void enablePciTunnels(boolean enable);
    native void updateLockState(boolean locked);
    native void updateLoggedInState(boolean loggedIn, long userId);
}
