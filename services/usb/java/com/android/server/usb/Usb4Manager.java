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

import static android.hardware.usb.InternalPciTunnelControlDisableReason.PCI_TUNNEL_CONTROL_DISABLE_REASON_APM;
import static android.hardware.usb.InternalPciTunnelControlDisableReason.PCI_TUNNEL_CONTROL_DISABLE_REASON_ENTERPRISE;

import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.UserInfo;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArraySet;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.io.File;
import java.util.Set;

/**
 * Usb4Manager manages USB4 related functionalities such as setting mode preferences to prioritize
 * Thunderbolt and authorizing PCI tunnels.
 */
public class Usb4Manager {
    private static final String TAG = Usb4Manager.class.getSimpleName();
    private static final String USB4_PREFS_XML = "usb4_manager_prefs.xml";

    @VisibleForTesting public static final String ENABLE_PCI_TUNNELS_PREF = "enable-pci-tunnels";

    private final Context mContext;
    private final UserManager mUserManager;

    private SharedPreferences mSettings;

    @GuardedBy("mLock")
    private @UserIdInt int mCurrentUserId;

    @GuardedBy("mLock")
    private boolean mIsScreenLocked;

    @GuardedBy("mLock")
    private boolean mPciTunnelsEnabled;

    @GuardedBy("mLock")
    private int mPciTunnelControlAllowed;

    @GuardedBy("mLock")
    private ArraySet<Integer> mPciTunnelControlDisableRequesters = new ArraySet<>();

    private static final Set<Integer> sValidDisableReasons =
            Set.of(
                    PCI_TUNNEL_CONTROL_DISABLE_REASON_APM,
                    PCI_TUNNEL_CONTROL_DISABLE_REASON_ENTERPRISE);

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

        boolean checkPciTunnelsSupported() {
            return mUsb4Manager.checkPciTunnelsSupported();
        }
    }

    private Usb4ManagerNative mUsb4ManagerNative;

    @VisibleForTesting
    public Usb4Manager(
            Context context,
            UserManager userManager,
            Usb4ManagerNative usb4ManagerNative,
            SharedPreferences sharedPreferences) {
        mContext = context;
        mUserManager = userManager;
        mUsb4ManagerNative = usb4ManagerNative;
        mSettings = sharedPreferences;

        // Default to the system user.
        mCurrentUserId = UserHandle.USER_SYSTEM;
        // Default to tunnels disabled.
        mPciTunnelsEnabled = false;

        // Initialize the native methods.
        mUsb4ManagerNative.init(this);

        // When the flag is set, default to allowing pci tunnels.
        if (com.android.server.usb.flags.Flags.defaultAllowPciTunnels()) {
            mPciTunnelsEnabled = true;
        }

        // Update tunnel control configuration.
        synchronized (mLock) {
            updatePciTunnelControlAllowed();
            setPciTunnelingEnabledInternal(mPciTunnelsEnabled, /* storePreference= */ false);
        }

        // Load stored preference asynchronously and apply it.
        if (android.hardware.usb.flags.Flags.enablePciTunnelControl()) {
            AsyncTask.execute(() -> {
                synchronized (mLock) {
                    loadPciTunnelPreference();
                    setPciTunnelingEnabledInternal(mPciTunnelsEnabled, /* storePreference= */false);
                }
            });
        }
    }

    public Usb4Manager(Context context, UserManager userManager) {
        this(context, userManager, new Usb4ManagerNative(), getSharedPreferences(context));
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        final File prefsFile = new File(
                Environment.getDataSystemDeDirectory(UserHandle.USER_SYSTEM), USB4_PREFS_XML);
        return context.createDeviceProtectedStorageContext()
                .getSharedPreferences(prefsFile, Context.MODE_PRIVATE);
    }

    void loadPciTunnelPreference() {
        if (mSettings.contains(ENABLE_PCI_TUNNELS_PREF)) {
            try {
                mPciTunnelsEnabled =
                        mSettings.getBoolean(ENABLE_PCI_TUNNELS_PREF, mPciTunnelsEnabled);
            } catch (ClassCastException cce) {
                Slog.e(TAG, "PCI Tunnels pref is wrong type. Not loading stored value.");
            }
        }
    }

    void storePciTunnelPreference() {
        // Update the tunnel preference and `apply` asynchronously.
        mSettings.edit().putBoolean(ENABLE_PCI_TUNNELS_PREF, mPciTunnelsEnabled).apply();
    }

    @GuardedBy("mLock")
    private void setPciTunnelingEnabledInternal(boolean enable, boolean storePreference) {
        Slog.d(TAG, "setPciTunnelingEnabledInternal: " + enable + ", " + storePreference);

        mPciTunnelsEnabled = enable;
        mUsb4ManagerNative.enablePciTunnels(mPciTunnelsEnabled);

        if (storePreference) {
            storePciTunnelPreference();
        }
    }

    /**
     * Enable or disable PCI tunnels.
     *
     * @param enable true to enable PCI tunnels, false to disable PCI tunnels.
     * @throws IllegalStateException if enabling and PCI tunnels are anything but supported
     */
    @SuppressLint("AndroidFrameworkRequiresPermission") // TODO(b/440646300)
    public void setPciTunnelingEnabled(boolean enable) {
        Slog.d(TAG, "enablePciTunnels: " + enable);

        synchronized (mLock) {
            int controlAllowedStatus = getPciTunnelingControlAllowedStatus();
            if (enable && controlAllowedStatus != UsbManager.PCI_TUNNEL_CTRL_SUPPORTED) {
                Slog.e(TAG, "enablePciTunnels: PCI tunnel control is not allowed");
                throw new IllegalStateException("PCI tunnel control is not allowed");
            }

            setPciTunnelingEnabledInternal(enable, /* storePreference= */ true);
        }
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
                updatePciTunnelControlAllowed();
            }
        }

        if (userId == UserHandle.USER_SYSTEM) {
            Slog.d(TAG, "updateLoggedInState: User is system user. Do nothing.");
            return;
        }

        mUsb4ManagerNative.updateLoggedInState(loggedIn, userId);
    }

    /**
     * Are PCI tunnels enabled?
     *
     * @return Current enabled state of PCI tunnels.
     */
    public boolean isPciTunnelingEnabled() {
        return mPciTunnelsEnabled;
    }

    @GuardedBy("mLock")
    void updatePciTunnelControlAllowed() {
        UserInfo userInfo = mUserManager.getUserInfo(mCurrentUserId);
        boolean is_admin = (userInfo != null && userInfo.isFull() && userInfo.isAdmin());

        if (!mUsb4ManagerNative.checkPciTunnelsSupported()) {
            mPciTunnelControlAllowed = UsbManager.PCI_TUNNEL_CTRL_UNSUPPORTED;
        } else if (!mPciTunnelControlDisableRequesters.isEmpty()) {
            if (mPciTunnelControlDisableRequesters.contains(
                    PCI_TUNNEL_CONTROL_DISABLE_REASON_ENTERPRISE)) {
                mPciTunnelControlAllowed =
                        UsbManager.PCI_TUNNEL_CTRL_DISALLOWED_BY_ENTERPRISE_POLICY;
            } else {
                mPciTunnelControlAllowed = UsbManager.PCI_TUNNEL_CTRL_DISALLOWED_BY_APM;
            }
        } else if (!is_admin) {
            mPciTunnelControlAllowed = UsbManager.PCI_TUNNEL_CTRL_DISALLOWED_FOR_NONADMIN_USER;
        } else {
            mPciTunnelControlAllowed = UsbManager.PCI_TUNNEL_CTRL_SUPPORTED;
        }
    }

    /**
     * Checks whether PCI tunnel control is allowed.
     *
     * @return 0 is allowed, non-zero is disallowed. See {@link
     *     UsbManager#PciTunnelControlAllowedStatus}.
     */
    public int getPciTunnelingControlAllowedStatus() {
        if (!android.hardware.usb.flags.Flags.enablePciTunnelControl()) {
            return UsbManager.PCI_TUNNEL_CTRL_UNSUPPORTED;
        }

        return mPciTunnelControlAllowed;
    }

    /**
     * Set whether PCI tunnels control is allowed.
     *
     * <p>Note: Disabling tunnel control will also disable pci tunnels if currently enabled.
     *
     * @param allowed Allow or disallow tunnel control.
     * @param disableReason The reason for enabling/disabling tunnel control. Valid values are in
     *     {@link android.hardware.usb.InternalPciTunnelControlDisableReason}
     * @throws IllegalArgumentException if invalid disable reason is given.
     * @return True if tunnel control was successfully updated.
     */
    public boolean setPciTunnelingControlAllowed(boolean allowed, int disableReason) {
        if (!android.hardware.usb.flags.Flags.enablePciTunnelControl()) {
            return false;
        }

        if (!sValidDisableReasons.contains(disableReason)) {
            throw new IllegalArgumentException("Invalid disable reason: " + disableReason);
        }

        synchronized (mLock) {
            if (allowed) {
                mPciTunnelControlDisableRequesters.remove(disableReason);
            } else {
                mPciTunnelControlDisableRequesters.add(disableReason);
            }

            updatePciTunnelControlAllowed();
        }

        // Disable tunnels if control is disallowed. Store the preference so user
        // needs to explicitly re-enable this if disabled for another reason.
        if (!allowed && isPciTunnelingEnabled()) {
            synchronized (mLock) {
                setPciTunnelingEnabledInternal(/* enable= */ false, /* storePreference= */ true);
            }
        }

        return true;
    }

    /**
     * Native methods for the Usb4Manager. Called by Usb4ManagerNative for testing.
     */
    native void nativeInit();
    native void enablePciTunnels(boolean enable);
    native void updateLockState(boolean locked);
    native void updateLoggedInState(boolean loggedIn, long userId);

    native boolean checkPciTunnelsSupported();
}
