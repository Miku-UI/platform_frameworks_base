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

import android.annotation.RequiresNoPermission;
import android.annotation.UserIdInt;
import android.annotation.WorkerThread;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.UserInfo;
import android.database.ContentObserver;
import android.hardware.usb.IUsbAuthEventsListener;
import android.hardware.usb.IUsbAuthManager;
import android.hardware.usb.InternalAuthorizationPinModeReason;
import android.hardware.usb.UsbAuthDeviceInfo;
import android.hardware.usb.UsbAuthorizationStatus;
import android.hardware.usb.UsbAuthorizationSystemState;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.AtomicFile;
import android.util.Slog;
import android.util.SparseBooleanArray;
import android.util.Xml;

import com.android.internal.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.internal.os.BackgroundThread;
import com.android.internal.util.XmlUtils;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;
import com.android.server.FgThread;
import com.android.server.IoThread;
import com.android.server.SystemServerInitThreadPool;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class UsbAuthManager implements IBinder.DeathRecipient {
    private static final String TAG = "UsbAuthManager";
    private IUsbAuthManager mService;
    private NotificationManager mNotificationManager = null;
    private final Context mContext;
    private final UserManager mUserManager;
    private final UsbHostManager mHostManager;
    private final UsbAuthEventsListener mAuthEventsListener;
    private static final String USB_AUTH_SERVICE_NAME = "usb_auth";

    // Name of notification channel for Usb authorization notifications.
    private static final String USB_AUTHORIZATION_CHANNEL = "usb_authorization";

    // Extra field in Intent we send to UsbAuthorizationActivity to indicate
    // whether we are in Booted (untrusted) or LoggedIn (trusted) state.
    private static final String AUTH_ACTIVITY_EXTRA_UNTRUSTED = "untrusted";

    @VisibleForTesting
    // Broadcast sent when auth notifications are dismissed.
    static final String ACTION_NOTIFICATION_DISMISSED =
            "com.android.server.usb.ACTION_NOTIFICATION_DISMISSED";

    // Root XML tag for persisted USB device fingerprints.
    private static final String XML_ROOT_NAME = "persisted-fingerprints";

    @GuardedBy("mLock")
    private @UserIdInt int mCurrentUserId;

    @GuardedBy("mLock")
    private boolean mIsScreenLocked;

    @GuardedBy("mLock")
    private SparseBooleanArray mFullUsersLoggedIn = new SparseBooleanArray();

    @GuardedBy("mLock")
    private @UsbAuthorizationSystemState int mPinnedSystemState = -1;

    @GuardedBy("mLock")
    private @UsbAuthorizationSystemState int mCurrentState = -1;

    // Class keeping track of authorized devices and whether host manager
    // has been notified of an authorized device.
    private static class AuthorizationState {
        // Auth service provided device info.
        UsbAuthDeviceInfo mDeviceInfo;

        // Authorization status for this device.
        @UsbAuthorizationStatus int mAuthorizationStatus;

        // Host manager has seen device and is ready for notification.
        boolean mHostReady;

        // Currently communicated authorized state with host manager once ready.
        boolean mHostAuthorized;

        AuthorizationState(
                UsbAuthDeviceInfo deviceInfo,
                int authorizationStatus,
                boolean hostReady,
                boolean hostAuthorized) {
            mDeviceInfo = deviceInfo;
            mAuthorizationStatus = authorizationStatus;
            mHostReady = hostReady;
            mHostAuthorized = hostAuthorized;
        }
    }

    // Map of currently connected usb devices. Updated via usbDeviceAdded and
    // usbDeviceRemoved. Key is device address (UsbDevice.getDeviceName) and
    // value is whether we have called usbDeviceAuthorized in host manager.
    @GuardedBy("mLock")
    private ArrayMap<String, AuthorizationState> mConnectedDeviceForHostMgr = new ArrayMap<>();

    // Set of authorized devices that are persisted to disk.
    @GuardedBy("mLock")
    private ArraySet<UsbDeviceFingerprint> mPersistedAuthorizedDevices = new ArraySet<>();

    // For devices that were authorized before fully logged in, persist after
    // fully logged-in.
    @GuardedBy("mLock")
    private ArraySet<String> mPersistAfterLogin = new ArraySet<>();

    // For devices that were used during setup, persist them after a user is logged in.
    @GuardedBy("mLock")
    private ArraySet<String> mPersistFromSetup = new ArraySet<>();

    // For devices that were deferred at the lock screen, used for notifications.
    @GuardedBy("mLock")
    private ArraySet<String> mDeferredAtLockscreen = new ArraySet<>();

    // Is timer active for devices that need to persist after login?
    boolean mPersistAfterLoginTimerActive = false;

    // Set of devices that are waiting to ask for user input.
    @GuardedBy("mLock")
    private ArrayMap<String, UsbAuthDeviceInfo> mPendingAskDevices = new ArrayMap<>();

    // Set of devices that are waiting to check for persisted devices.
    @GuardedBy("mLock")
    private ArrayMap<String, UsbAuthDeviceInfo> mPendingAllowPersistedDevices = new ArrayMap<>();

    // File containing persisted device fingerprints.
    private final AtomicFile mPersistedDevicesFile;

    // Currently writing persisted devices to file?
    @GuardedBy("mLock")
    private boolean mIsWritePersistedScheduled = false;

    // Have we finished reading persisted file from disk?
    @GuardedBy("mLock")
    private boolean mIsReadPersistedDevicesComplete = false;

    private final Object mLock = new Object();

    /**
     * Internal listener for changes to DEVICE_PROVISIONED state.
     *
     * <p>This is provided as a separate class for ease of testing. Tests can provide an overridden
     * implementation for mocking purposes.
     */
    @VisibleForTesting
    static class ProvisioningListener extends ContentObserver {
        private UsbAuthManager mAuthManager;
        private Context mLocalContext;

        ProvisioningListener(Context context, UsbAuthManager authManager) {
            super(FgThread.getHandler());

            mLocalContext = context;
            mAuthManager = authManager;

            // Register a provisioning listener only if we are currently not provisioned.
            if (isDeviceInSetup()) {
                context.getContentResolver()
                        .registerContentObserver(
                                Settings.Global.getUriFor(Settings.Global.DEVICE_PROVISIONED),
                                /* notifyForDescendants= */ false,
                                this);
            }
        }

        /**
         * Check if device is in setup (not provisioned).
         *
         * @return True if not provisioned, false if provisioned.
         */
        public boolean isDeviceInSetup() {
            int provisioned =
                    Settings.Global.getInt(
                            mLocalContext.getContentResolver(),
                            Settings.Global.DEVICE_PROVISIONED,
                            1);

            // Setup means it is not provisioned.
            return provisioned == 0;
        }

        @VisibleForTesting
        void setAuthManager(UsbAuthManager authManager) {
            mAuthManager = authManager;
        }

        @Override
        public void onChange(boolean selfChanged) {
            mAuthManager.updateSystemStateInternal();

            // We no longer need to listen for provisioned if we're already out of setup.
            if (!isDeviceInSetup()) {
                mLocalContext.getContentResolver().unregisterContentObserver(this);
            }
        }
    }

    // Listener for device provisioning. Safe to unregister after first check.
    private ProvisioningListener mDeviceProvisionedListener;

    private final BroadcastReceiver mDismissNotificationReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (ACTION_NOTIFICATION_DISMISSED.equals(intent.getAction())) {
                        deauthorizeDevicesAwaitingPersistAfterLogin();
                    }
                }
            };

    @VisibleForTesting
    // Duration user has to complete login after trusting device at BOOTED state.
    static final long LOGIN_TIMEOUT_MS = 60_000;

    // Handler for scheduling timeout operations. Injected for ease of testing.
    private final Handler mTimerHandler;

    private final Runnable mLoginTimeoutRunnable =
            new Runnable() {
                @Override
                public void run() {
                    boolean shouldDeauthorize = false;
                    synchronized (mLock) {
                        // Check if timer is active.
                        if (!mPersistAfterLoginTimerActive) return;

                        Slog.d(TAG, "Timed out waiting for login to complete");
                        shouldDeauthorize = true;
                        mPersistAfterLoginTimerActive = false;
                    }

                    if (shouldDeauthorize) {
                        deauthorizeDevicesAwaitingPersistAfterLogin();
                    }
                }
            };

    public UsbAuthManager(Context context, UserManager userManager, UsbHostManager hostManager) {
        mHostManager = hostManager;
        mUserManager = userManager;
        mContext = context;
        mAuthEventsListener = new UsbAuthEventsListener();
        mDeviceProvisionedListener = new ProvisioningListener(context, this);
        mTimerHandler = BackgroundThread.getHandler();
        mPersistedDevicesFile = new AtomicFile(new File(
                Environment.getDataSystemDeDirectory(UserHandle.USER_SYSTEM),
                "usb_auth_persisted_devices.xml"), "usb-auth-persisted-devices");
        readPersisted();
        listenForService();
    }

    @VisibleForTesting
    public UsbAuthManager(
            Context context,
            UserManager userManager,
            UsbHostManager hostManager,
            IUsbAuthManager service,
            ProvisioningListener deviceProvisionedListener,
            Handler timerHandler,
            AtomicFile persistedDevicesFile) {
        mHostManager = hostManager;
        mUserManager = userManager;
        mContext = context;
        mAuthEventsListener = new UsbAuthEventsListener();
        mDeviceProvisionedListener = deviceProvisionedListener;
        mTimerHandler = timerHandler;
        mPersistedDevicesFile = persistedDevicesFile;
        readPersisted();
        setAndLinkService(service);
    }

    private void listenForService() {
        SystemServerInitThreadPool.submit(
                () -> {
                    IBinder serviceBinder = ServiceManager.waitForService(USB_AUTH_SERVICE_NAME);
                    FgThread.getExecutor()
                            .execute(
                                    () -> {
                                        Slog.d(TAG, "UsbAuthService arrived");
                                        setAndLinkService(
                                                IUsbAuthManager.Stub.asInterface(serviceBinder));
                                    });
                },
                "UsbAuthManager#listenForService");
    }

    private void setAndLinkService(IUsbAuthManager service) {
        synchronized (mLock) {
            if (mService != null) {
                mService.asBinder().unlinkToDeath(this, 0);
            }
            mService = service;
            if (mService != null) {
                try {
                    mService.asBinder().linkToDeath(this, 0);
                    mService.registerForUsbAuthorizationEvents(mAuthEventsListener);
                    updateSystemStateInternal(); // Update system state after linking to service
                } catch (RemoteException e) {
                    Slog.e(TAG, "linkToDeath failed", e);
                    mService = null;
                }
            }
        }
    }

    @Override
    public void binderDied() {
        mService = null;
        listenForService();
    }

    // Take actions after ActivityManager is ready.
    void systemReady() {
        if (mNotificationManager == null) {
            mNotificationManager = mContext.getSystemService(NotificationManager.class);

            // Authorization notifications should be high importance and persist until dismissed.
            NotificationChannel channel =
                    new NotificationChannel(
                            USB_AUTHORIZATION_CHANNEL,
                            mContext.getString(R.string.notification_channel_usb_authorization),
                            NotificationManager.IMPORTANCE_HIGH);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            mNotificationManager.createNotificationChannel(channel);

            // Register for notification dismissal broadcast.
            IntentFilter filter = new IntentFilter(ACTION_NOTIFICATION_DISMISSED);
            mContext.registerReceiver(
                    mDismissNotificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        }

        // We may have missed some Ask and AllowPersist callbacks during boot. Take care of them
        // here after the system is further along in boot.
        List<UsbAuthDeviceInfo> allowPersistDevices = getDevicesAwaitingPersistedAuthorization();
        for (UsbAuthDeviceInfo device : allowPersistDevices) {
            mAuthEventsListener.onDeviceCheckPersistedAuthorization(device);
        }

        List<UsbAuthDeviceInfo> askDevices = getDevicesAwaitingAuthorization();
        for (UsbAuthDeviceInfo device : askDevices) {
            mAuthEventsListener.onDeviceAskForAuthorization(device);
        }
    }

    boolean pinAuthorizationMode(@InternalAuthorizationPinModeReason int reason) {
        @UsbAuthorizationSystemState int state;

        // Check for a valid reason or return false right away.
        switch (reason) {
            case InternalAuthorizationPinModeReason.REPAIR_MODE:
            case InternalAuthorizationPinModeReason.FACTORY_MODE:
                state = UsbAuthorizationSystemState.SET_UP;
                break;
            default:
                return false;

        }

        synchronized (mLock) {
            mPinnedSystemState = state;
        }

        updateSystemStateInternal();
        return true;
    }

    private IUsbAuthManager getService() {
        synchronized (mLock) {
            if (mService == null) {
                IUsbAuthManager service =
                        IUsbAuthManager.Stub.asInterface(
                                ServiceManager.getService(USB_AUTH_SERVICE_NAME));
                setAndLinkService(service);
            }
            return mService;
        }
    }

    @VisibleForTesting
    int getPinnedState() {
        synchronized (mLock) {
            return mPinnedSystemState;
        }
    }

    @VisibleForTesting
    int getSystemState() {
        synchronized (mLock) {
            return mCurrentState;
        }
    }

    @VisibleForTesting
    UsbAuthEventsListener getEventsListenerForTest() {
        return mAuthEventsListener;
    }

    @VisibleForTesting
    ArraySet<UsbDeviceFingerprint> getPersistedFingerprintsCopyForTesting() {
        synchronized (mLock) {
            return new ArraySet<UsbDeviceFingerprint>(mPersistedAuthorizedDevices);
        }
    }

    @VisibleForTesting
    void addFingerprintToPersistedForTest(UsbDeviceFingerprint fingerprint) {
        mPersistedAuthorizedDevices.add(fingerprint);
    }

    public void setSystemState(int state) {
        try {
            IUsbAuthManager service = getService();
            if (service != null) {
                service.setSystemState(state);
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to set system state, binder died", e);
            binderDied(); // Explicitly call binderDied to clean up and re-listen for the service
        }
    }

    private void setAuthorizationStatusInternal(
            UsbAuthDeviceInfo deviceInfo, @UsbAuthorizationStatus int status) {
        Slog.d(
                TAG,
                TextUtils.formatSimple(
                        "Setting /dev/bus/usb/%03d/%03d authorized = %d",
                        deviceInfo.busNumber, deviceInfo.deviceNumber, status));

        try {
            IUsbAuthManager service = getService();
            if (service != null) {
                service.setAuthorizationStatus(deviceInfo, status);
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to set system state, binder died", e);
            binderDied();
        } catch (IllegalArgumentException e) {
            Slog.e(TAG, "Failed to set authorization: ", e);
        }
    }

    private List<UsbAuthDeviceInfo> getDevicesAwaitingAuthorization() {
        try {
            IUsbAuthManager service = getService();
            if (service != null) {
                return service.getDevicesAwaitingAuthorization();
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to get devices awaiting authorization", e);
            binderDied();
        }

        return List.of();
    }

    private List<UsbAuthDeviceInfo> getDevicesAwaitingPersistedAuthorization() {
        try {
            IUsbAuthManager service = getService();
            if (service != null) {
                return service.getDevicesAwaitingPersistedAuthorization();
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to get devices awaiting persisted authorization", e);
            binderDied();
        }

        return List.of();
    }

    public void onUpdateScreenLockedState(boolean locked) {
        Slog.d(TAG, "updateLockState: " + locked);

        synchronized (mLock) {
            mIsScreenLocked = locked;
        }

        updateSystemStateInternal();
    }

    /**
     * Update the logged in state of a user. This method updates the internal state related to user
     * logins and logouts. It specifically tracks full or admin users: If a full or admin user logs
     * in, their ID is added to an internal map of logged-in full users. If a full or admin user
     * logs out, their ID is removed from this map.
     *
     * <p>When any user logs in, their ID is also set as the current user ID.
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

            UserInfo userInfo = mUserManager.getUserInfo(userId);
            if (userInfo == null) {
                Slog.e(TAG, "onUpdateLoggedInState: user not found");
                return;
            }
            // Update mFullUsersLoggedIn map
            if (userInfo.isFull() && !userInfo.isGuest()) {
                // Update the logged-in status for any full user, excluding guests.
                if (loggedIn) {
                    mFullUsersLoggedIn.put(userId, true);
                } else {
                    mFullUsersLoggedIn.delete(userId);
                }
            }
        }
        updateSystemStateInternal();
    }

    private void updateSystemStateInternal() {
        @UsbAuthorizationSystemState int state;
        synchronized (mLock) {
            if (mPinnedSystemState != -1) {
                state = mPinnedSystemState;
            } else if (mDeviceProvisionedListener.isDeviceInSetup()) {
                // Set-up wizard has not completed.
                state = UsbAuthorizationSystemState.SET_UP;
            } else if (mFullUsersLoggedIn.size() == 0) {
                // No full users logged in
                state = UsbAuthorizationSystemState.BOOTED;
            } else if (!mIsScreenLocked && mFullUsersLoggedIn.get(mCurrentUserId, false)) {
                // Active user is a full user, and screen is unlocked
                state = UsbAuthorizationSystemState.LOGGED_IN;
            } else {
                // There are full users logged in, but either screen is locked
                // or the active user is not a full user (e.g., guest) (I.e. guest user logs in)
                state = UsbAuthorizationSystemState.SCREEN_LOCKED;
            }
            if (mCurrentState == state) {
                return;
            }
            mCurrentState = state;

            // On every state change, clear any pending ask or allow-persisted.
            mPendingAskDevices.clear();
            mPendingAllowPersistedDevices.clear();

            // On every screen locked, remove stale devices from persisted list. We do this on
            // locked state specifically because a) we already clear stale entries when we read from
            // disk (i.e. booted) and b) we don't want to trust stale devices in the lower trust
            // state.
            if (removeStaleFingerprintsFromPersistedLocked()) {
                // If we removed stale fingerprints, write that back to disk.
                scheduleWritePersistedLocked();
            }
        }

        // When logged in, move all devices that are pending persistence
        // to the persisted list and remove all notifications that we may have
        // previously enabled.
        if (state == UsbAuthorizationSystemState.LOGGED_IN) {
            persistDevicesAfterLogin();
            clearNotificationsOnLoggedIn();
        }

        setSystemState(state);
    }

    @GuardedBy("mLock")
    AuthorizationState getOrCreateConnectedDeviceLocked(String deviceAddress) {
        AuthorizationState state = mConnectedDeviceForHostMgr.get(deviceAddress);
        if (state == null) {
            state =
                    new AuthorizationState(
                            new UsbAuthDeviceInfo(), UsbAuthorizationStatus.DENIED, false, false);
            mConnectedDeviceForHostMgr.put(deviceAddress, state);
        }

        return state;
    }

    // Synchronizing with usb devices getting added via HostManager.
    //
    // If the usb device is already authorized, then we notify HostManager.
    // If not, we initialize the map with a default auth state.
    void usbDeviceAdded(String deviceAddress) {
        boolean notifyHostManager = false;
        Slog.d(TAG, TextUtils.formatSimple("Host added device %s", deviceAddress));

        UsbDeviceFingerprint fingerprint =
                mHostManager.getConnectedDeviceFingerprintForAddress(deviceAddress);

        synchronized (mLock) {
            AuthorizationState state = getOrCreateConnectedDeviceLocked(deviceAddress);
            state.mHostReady = true;

            if (state.mAuthorizationStatus == UsbAuthorizationStatus.AUTHORIZED
                    && !state.mHostAuthorized) {
                state.mHostAuthorized = true;
                notifyHostManager = true;
            }

            // If we are persisting the fingerprint for this device, update when it was last seen so
            // that we don't consider it stale.
            int deviceIndex = mPersistedAuthorizedDevices.indexOf(fingerprint);
            if (deviceIndex >= 0) {
                mPersistedAuthorizedDevices.valueAt(deviceIndex).updateLastSeenToNow();
                scheduleWritePersistedLocked();
            }
        }

        // Check any pending interactions as well.
        checkAllowPersistedDevice(deviceAddress);
        checkAskDevice(deviceAddress);

        if (notifyHostManager) {
            Slog.d(TAG, TextUtils.formatSimple("Notify host for authorized - %s", deviceAddress));
            mHostManager.usbDeviceAuthorized(deviceAddress);
        }
    }

    // Remove device from connected devices.
    void usbDeviceRemoved(String deviceAddress) {
        Slog.d(TAG, TextUtils.formatSimple("Host removed device %s", deviceAddress));

        boolean loginNotificationChange = false;
        boolean lockNotificationChange = false;

        synchronized (mLock) {
            mPendingAskDevices.remove(deviceAddress);
            mPendingAllowPersistedDevices.remove(deviceAddress);
            mConnectedDeviceForHostMgr.remove(deviceAddress);

            loginNotificationChange = mPersistAfterLogin.remove(deviceAddress);
            lockNotificationChange = mDeferredAtLockscreen.remove(deviceAddress);
        }

        if (loginNotificationChange) {
            cancelOrUpdatePersistenceDelayedNotification();
        }

        if (lockNotificationChange) {
            cancelOrUpdateScreenLockedNotification();
        }
    }

    // Check for the existence of a persisted device and send authorization
    // response for it.
    void checkAllowPersistedDevice(String deviceAddress) {
        boolean sendAuthorization = false;
        int status = UsbAuthorizationStatus.DENIED;
        UsbAuthDeviceInfo deviceInfo = null;

        UsbDeviceFingerprint fingerprint =
                mHostManager.getConnectedDeviceFingerprintForAddress(deviceAddress);

        // Wait for iothread to finish reading persisted devices data.
        waitForPersistedDeviceData();

        synchronized (mLock) {
            if (!mPendingAllowPersistedDevices.containsKey(deviceAddress)) {
                return;
            }

            if (fingerprint != null) {
                sendAuthorization = true;
                deviceInfo = mPendingAllowPersistedDevices.remove(deviceAddress);

                if (mPersistedAuthorizedDevices.contains(fingerprint)) {
                    status = UsbAuthorizationStatus.AUTHORIZED;
                }
            }
        }

        if (sendAuthorization) {
            setAuthorizationStatusInternal(deviceInfo, status);
        }
    }

    // Check if ask device can send immediate response (persisted) or queue up for
    // user interaction.
    void checkAskDevice(String deviceAddress) {
        boolean sendAuthorization = false;
        boolean sendAskRequest = false;
        int status = UsbAuthorizationStatus.DENIED;
        UsbAuthDeviceInfo deviceInfo = null;
        boolean isLoggedIn = false;

        UsbDeviceFingerprint fingerprint =
                mHostManager.getConnectedDeviceFingerprintForAddress(deviceAddress);
        UsbDevice device = mHostManager.getConnectedDeviceForAddress(deviceAddress);

        // Wait for iothread to finish reading persisted devices data.
        waitForPersistedDeviceData();

        synchronized (mLock) {
            if (!mPendingAskDevices.containsKey(deviceAddress)) {
                return;
            }

            isLoggedIn = mCurrentState == UsbAuthorizationSystemState.LOGGED_IN;
            // Send a persisted result directly or queue up a user dialog.
            if (fingerprint != null && mPersistedAuthorizedDevices.contains(fingerprint)) {
                sendAuthorization = true;
                deviceInfo = mPendingAskDevices.remove(deviceAddress);
                status = UsbAuthorizationStatus.AUTHORIZED;
            } else if (device != null) {
                sendAskRequest = true;
            }
        }

        if (sendAuthorization && deviceInfo != null) {
            setAuthorizationStatusInternal(deviceInfo, status);
        }

        if (sendAskRequest && device != null) {
            startAuthorizationAlertActivity(device, isLoggedIn);
        }
    }

    // Start a new authorization alert activity or update the existing one to display
    // a prompt to allow a new device.
    private void startAuthorizationAlertActivity(UsbDevice device, boolean isLoggedIn) {
        Slog.d(TAG, TextUtils.formatSimple("Starting alert for device %s", device.getDeviceName()));

        final long token = Binder.clearCallingIdentity();
        try {
            UserHandle userHandle;
            synchronized (mLock) {
                userHandle = UserHandle.of(mCurrentUserId);
            }

            Context userContext = mContext.createContextAsUser(userHandle, 0);
            Intent intent = new Intent();
            intent.putExtra(UsbManager.EXTRA_DEVICE, device);
            intent.putExtra(AUTH_ACTIVITY_EXTRA_UNTRUSTED, !isLoggedIn);
            intent.setComponent(
                    ComponentName.unflattenFromString(
                            userContext
                                    .getResources()
                                    .getString(
                                            com.android.internal.R.string
                                                    .config_usbAuthorizationActivity)));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            userContext.startActivityAsUser(intent, userHandle);
            Slog.d(TAG, "Started alert");
        } catch (ActivityNotFoundException e) {
            Slog.e(TAG, "unable to start UsbAuthorizationActivity");
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @GuardedBy("mLock")
    private void cancelLoginTimerLocked() {
        if (mPersistAfterLoginTimerActive) {
            mTimerHandler.removeCallbacks(mLoginTimeoutRunnable);
            mPersistAfterLoginTimerActive = false;
        }
    }

    @GuardedBy("mLock")
    private void startLoginTimerLocked() {
        if (mPersistAfterLoginTimerActive) {
            return;
        }

        mTimerHandler.postDelayed(mLoginTimeoutRunnable, LOGIN_TIMEOUT_MS);
        mPersistAfterLoginTimerActive = true;
    }

    private void startPersistenceDelayedNotification(boolean resetTimeout) {
        if (mNotificationManager == null) {
            return;
        }

        // Build notification while locked to make sure we have the most up-to-date info.
        Notification notification;
        synchronized (mLock) {
            if (mCurrentState == UsbAuthorizationSystemState.LOGGED_IN) {
                Slog.d(
                        TAG,
                        "AuthNotify: Starting ~Finish Logging In~ notification in invalid state");
                return;
            }

            if (resetTimeout) {
                // Remove any existing timers first.
                if (mPersistAfterLoginTimerActive) {
                    Slog.d(TAG, "AuthNotify: Login timer already exists. Resetting.");
                    cancelLoginTimerLocked();
                }
            }

            // Start a timer if one isn't already active.
            startLoginTimerLocked();

            boolean hasMultiple = mPersistAfterLogin.size() > 1;

            Intent intent = new Intent(ACTION_NOTIFICATION_DISMISSED);
            intent.setPackage(mContext.getPackageName()); // Internal only

            PendingIntent deletePendingIntent =
                    PendingIntent.getBroadcast(
                            mContext,
                            0,
                            intent,
                            PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

            int titleId, messageId;

            if (hasMultiple) {
                titleId = R.string.usb_authorization_notify_finish_logging_in_title_multiple;
                messageId = R.string.usb_authorization_notify_finish_logging_in_message_multiple;
            } else {
                titleId = R.string.usb_authorization_notify_finish_logging_in_title;
                messageId = R.string.usb_authorization_notify_finish_logging_in_message;
            }

            String title = mContext.getString(titleId);
            String message = mContext.getString(messageId, LOGIN_TIMEOUT_MS / 1000);

            Notification.Builder builder =
                    new Notification.Builder(mContext, USB_AUTHORIZATION_CHANNEL)
                            .setOngoing(true)
                            .setSmallIcon(com.android.internal.R.drawable.stat_sys_data_usb)
                            .setDeleteIntent(deletePendingIntent)
                            .setContentTitle(title)
                            .setContentText(message)
                            .setVisibility(Notification.VISIBILITY_PUBLIC)
                            .setStyle(new Notification.BigTextStyle().bigText(message));
            notification = builder.build();
        }

        Slog.d(TAG, TextUtils.formatSimple("AuthNotify: Start ~Finish Logging In~ notification"));
        final long token = Binder.clearCallingIdentity();
        try {
            mNotificationManager.notifyAsUser(
                    null,
                    SystemMessage.NOTE_USB_AUTH_FINISH_LOGGING_IN_REMINDER,
                    notification,
                    UserHandle.ALL);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    // Checks if we have any devices to persist after login and either updates notification or
    // cancels it. Updates from this call should not reset the timeout.
    private void cancelOrUpdatePersistenceDelayedNotification() {
        boolean shouldCancel;
        synchronized (mLock) {
            shouldCancel = mPersistAfterLogin.isEmpty();
        }

        if (shouldCancel) {
            deauthorizeDevicesAwaitingPersistAfterLogin();
        } else {
            startPersistenceDelayedNotification(/* resetTimeout= */false);
        }
    }

    private void startScreenLockedNotification() {
        if (mNotificationManager == null) {
            return;
        }

        // Build notification while locked so we have up-to-date info.
        Notification notification;
        synchronized (mLock) {
            boolean hasMultiple = mDeferredAtLockscreen.size() > 1;

            String title =
                    mContext.getString(R.string.usb_authorization_notify_on_screenlock_title);
            String message;

            if (hasMultiple) {
                message =
                        mContext.getString(
                                R.string.usb_authorization_notify_on_screenlock_message_multiple);
            } else {
                message =
                        mContext.getString(R.string.usb_authorization_notify_on_screenlock_message);
            }

            Notification.Builder builder =
                    new Notification.Builder(mContext, USB_AUTHORIZATION_CHANNEL)
                            .setOngoing(true)
                            .setSmallIcon(com.android.internal.R.drawable.stat_sys_data_usb)
                            .setContentTitle(title)
                            .setContentText(message)
                            .setVisibility(Notification.VISIBILITY_PUBLIC)
                            .setStyle(new Notification.BigTextStyle().bigText(message));
            notification = builder.build();
        }

        Slog.d(TAG, TextUtils.formatSimple("AuthNotify: Starting screen locked notification"));
        final long token = Binder.clearCallingIdentity();
        try {
            mNotificationManager.notifyAsUser(
                    null,
                    SystemMessage.NOTE_USB_AUTH_SCREEN_LOCKED_REMINDER,
                    notification,
                    UserHandle.ALL);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    // Checks if we have any deferred devices and either update the notification or cancel it.
    private void cancelOrUpdateScreenLockedNotification() {
        if (mNotificationManager == null) {
            return;
        }

        boolean shouldCancel;
        synchronized (mLock) {
            shouldCancel = mDeferredAtLockscreen.isEmpty();
        }

        if (shouldCancel) {
            mNotificationManager.cancelAsUser(
                    null, SystemMessage.NOTE_USB_AUTH_SCREEN_LOCKED_REMINDER, UserHandle.ALL);
        } else {
            startScreenLockedNotification();
        }
    }

    // Once the state transitions to logged in, check if there are any devices that

    // Check screen is locked and then start notification.
    private void checkScreenLockedThenNotify(String deviceAddress) {
        boolean showNotification = false;

        synchronized (mLock) {
            mDeferredAtLockscreen.add(deviceAddress);

            if (mCurrentState == UsbAuthorizationSystemState.SCREEN_LOCKED
                    && mPersistAfterLogin.isEmpty()) {
                Slog.d(
                        TAG,
                        TextUtils.formatSimple(
                                "AuthNotify: Screen is locked and not awaiting login."));
                showNotification = true;
            }
        }

        if (showNotification) {
            startScreenLockedNotification();
        }
    }

    // Clear all notifications that we could have sent out.
    private void clearNotificationsOnLoggedIn() {
        if (mNotificationManager == null) {
            return;
        }

        synchronized (mLock) {
            cancelLoginTimerLocked();

            // Also clear deferred devices list (used for notifications).
            mDeferredAtLockscreen.clear();
        }

        mNotificationManager.cancelAsUser(
                null, SystemMessage.NOTE_USB_AUTH_SCREEN_LOCKED_REMINDER, UserHandle.ALL);
        mNotificationManager.cancelAsUser(
                null, SystemMessage.NOTE_USB_AUTH_FINISH_LOGGING_IN_REMINDER, UserHandle.ALL);
    }

    // Devices in the mPersistAfterLogin list get deauthorized in one of two scenarios:
    //   * The notification informing the user to complete logging in is dismissed.
    //   * The timeout for logging in after trusting the device at the login screen elapses.
    private void deauthorizeDevicesAwaitingPersistAfterLogin() {
        ArraySet<UsbAuthDeviceInfo> devicesToDeauthorize = new ArraySet<>();

        synchronized (mLock) {
            for (String deviceAddr : mPersistAfterLogin) {
                AuthorizationState state = mConnectedDeviceForHostMgr.get(deviceAddr);
                if (state != null) {
                    devicesToDeauthorize.add(state.mDeviceInfo);
                }
            }

            mPersistAfterLogin.clear();

            // If a login timer is active, remove it.
            cancelLoginTimerLocked();
        }

        // Deny all these devices.
        for (UsbAuthDeviceInfo device : devicesToDeauthorize) {
            setAuthorizationStatusInternal(device, UsbAuthorizationStatus.DENIED);
        }

        if (mNotificationManager != null) {
            // Remove notification to complete logging in (if it exist).
            mNotificationManager.cancelAsUser(
                    null, SystemMessage.NOTE_USB_AUTH_FINISH_LOGGING_IN_REMINDER, UserHandle.ALL);
        }
    }

    // need to be persisted, look up their fingerprints and persist them.
    private void persistDevicesAfterLogin() {
        ArraySet<String> devicesToPersist = new ArraySet<>();
        ArraySet<UsbDeviceFingerprint> fingerprintsToPersist = new ArraySet<>();
        synchronized (mLock) {
            if (mCurrentState != UsbAuthorizationSystemState.LOGGED_IN
                    || (mPersistAfterLogin.isEmpty() && mPersistFromSetup.isEmpty())) {
                return;
            }

            devicesToPersist.addAll(mPersistAfterLogin);
            mPersistAfterLogin.clear();

            // We also persist devices that may have been added during set-up here.
            devicesToPersist.addAll(mPersistFromSetup);
            mPersistFromSetup.clear();
        }

        for (String deviceAddress : devicesToPersist) {
            UsbDeviceFingerprint fingerprint =
                    mHostManager.getConnectedDeviceFingerprintForAddress(deviceAddress);
            if (fingerprint != null) {
                fingerprintsToPersist.add(fingerprint);
            }
        }

        synchronized (mLock) {
            int prevSize = mPersistedAuthorizedDevices.size();
            mPersistedAuthorizedDevices.addAll(fingerprintsToPersist);

            if (mPersistedAuthorizedDevices.size() > prevSize) {
                scheduleWritePersistedLocked();
            }
        }
    }

    // Set authorization response for a specific device. Valid for any currently connected device.
    //
    // This should be sent when the UI responds to an "Ask" request for a USB device.
    void setAuthorizationResponse(
            UsbDevice device, @UsbAuthorizationStatus int response, boolean isPersistent) {
        String deviceAddress = device.getDeviceName();
        UsbDeviceFingerprint fingerprint =
                mHostManager.getConnectedDeviceFingerprintForAddress(deviceAddress);
        UsbAuthDeviceInfo deviceInfo = null;
        boolean notifyDelayedPersistence = false;

        if (mHostManager.getConnectedDeviceForAddress(deviceAddress) == null) {
            Slog.e(TAG, "Attempted to authorize device that's not connected: " + deviceAddress);
            return;
        }

        synchronized (mLock) {
            // Expect the device to be a pending Ask.
            deviceInfo = mPendingAskDevices.remove(deviceAddress);

            // If it's not a pending Ask, then also check connected devices list.
            if (deviceInfo == null) {
                AuthorizationState state = mConnectedDeviceForHostMgr.get(deviceAddress);
                if (state != null && state.mHostReady) {
                    deviceInfo = state.mDeviceInfo;
                }
            }

            if (deviceInfo != null && fingerprint != null) {
                if (isPersistent && response == UsbAuthorizationStatus.AUTHORIZED) {
                    // In booted and screen locked states, we delay persistence until
                    // after a successful login is completed.
                    if (mCurrentState == UsbAuthorizationSystemState.BOOTED
                            || mCurrentState == UsbAuthorizationSystemState.SCREEN_LOCKED) {
                        mPersistAfterLogin.add(deviceAddress);
                        notifyDelayedPersistence = true;
                    } else {
                        if (mPersistedAuthorizedDevices.add(fingerprint)) {
                            scheduleWritePersistedLocked();
                        }
                    }
                }
            }
        }

        if (deviceInfo != null) {
            setAuthorizationStatusInternal(deviceInfo, response);
        }

        if (notifyDelayedPersistence) {
            startPersistenceDelayedNotification(/* resetTimeout= */true);
        }
    }

    /** Handle events received from UsbAuthService.
     *
     * This listener is registered directly with the usb_auth native service and is not accessible
     * to apps or other services. As a result, there is no need to enforce permissions on these
     * callbacks.
     */
    class UsbAuthEventsListener extends IUsbAuthEventsListener.Stub {
        UsbAuthEventsListener() {}

        @Override
        @RequiresNoPermission
        public void onDeviceAskForAuthorization(UsbAuthDeviceInfo device) {
            String deviceAddress;
            boolean readyForCheck = false;

            Slog.d(
                    TAG,
                    TextUtils.formatSimple(
                            "AuthEvents: received ASK request for bus %03d dev %03d",
                            device.busNumber, device.deviceNumber));

            synchronized (mLock) {
                deviceAddress = UsbDevice.getDeviceName(device.busNumber, device.deviceNumber);
                mPendingAskDevices.put(deviceAddress, device);

                AuthorizationState state = mConnectedDeviceForHostMgr.get(deviceAddress);
                if (state != null) {
                    state.mDeviceInfo = device;
                    readyForCheck = state.mHostReady;
                }
            }

            if (readyForCheck) {
                checkAskDevice(deviceAddress);
            }
        }

        @Override
        @RequiresNoPermission
        public void onDeviceCheckPersistedAuthorization(UsbAuthDeviceInfo device) {
            String deviceAddress;
            boolean readyForCheck = false;

            Slog.d(
                    TAG,
                    TextUtils.formatSimple(
                            "AuthEvents: received ALLOW-PERSIST request for bus %03d dev %03d",
                            device.busNumber, device.deviceNumber));

            synchronized (mLock) {
                deviceAddress = UsbDevice.getDeviceName(device.busNumber, device.deviceNumber);
                mPendingAllowPersistedDevices.put(deviceAddress, device);

                AuthorizationState state = mConnectedDeviceForHostMgr.get(deviceAddress);
                if (state != null) {
                    state.mDeviceInfo = device;
                    readyForCheck = state.mHostReady;
                }
            }

            if (readyForCheck) {
                checkAllowPersistedDevice(deviceAddress);
            }
        }

        // Note: When USB devices disconnect, this callback will be called with
        // UsbAuthorizationStatus.DENIED if it was previously authorized. This is done in order to
        // avoid a race where authorization arrives after a device is removed and impacts the next
        // connection.
        //    device added (service starts processing), device removed,
        //    onDeviceAuthorizationStatusChanged(.., AUTHORIZED, ...)
        @Override
        @RequiresNoPermission
        public void onDeviceAuthorizationStatusChanged(
                UsbAuthDeviceInfo device,
                @UsbAuthorizationStatus int status,
                @UsbAuthorizationSystemState int systemState) {
            boolean notifyHostManager = false;
            boolean hostAuthorized = false;
            boolean handleDenyDefers = false;
            String deviceAddress = null;

            Slog.d(
                    TAG,
                    TextUtils.formatSimple(
                            "AuthEvents: received STATUS-CHANGE for bus %03d dev %03d to %d in"
                                    + " state %d",
                            device.busNumber, device.deviceNumber, status, systemState));

            synchronized (mLock) {
                deviceAddress = UsbDevice.getDeviceName(device.busNumber, device.deviceNumber);
                AuthorizationState state = getOrCreateConnectedDeviceLocked(deviceAddress);

                state.mDeviceInfo = device;
                state.mAuthorizationStatus = status;
                if (status == UsbAuthorizationStatus.AUTHORIZED
                        && state.mHostReady
                        && !state.mHostAuthorized) {
                    notifyHostManager = true;
                    state.mHostAuthorized = true;
                } else if (state.mHostReady
                        && state.mHostAuthorized
                        && status != UsbAuthorizationStatus.AUTHORIZED) {
                    notifyHostManager = true;
                    state.mHostAuthorized = false;
                } else if (status == UsbAuthorizationStatus.DENIED_AND_DEFERRED) {
                    handleDenyDefers = true;
                }

                // All devices that were allowed during setup will be persisted once the user is set
                // up and logged in.
                if (status == UsbAuthorizationStatus.AUTHORIZED
                        && mCurrentState == UsbAuthorizationSystemState.SET_UP) {
                    mPersistFromSetup.add(deviceAddress);
                }

                // Hold authorized state for what message to send to host manager.
                hostAuthorized = state.mHostAuthorized;
            }
            Slog.d(
                    TAG,
                    TextUtils.formatSimple(
                            "onDeviceAuthorizationStatusChange (%03d/%03d) for %s",
                            device.busNumber, device.deviceNumber, deviceAddress));

            if (notifyHostManager) {
                Slog.d(
                        TAG,
                        TextUtils.formatSimple(
                                "AuthEvents: Notify host for authorized(%b) - %s",
                                hostAuthorized, deviceAddress));

                if (hostAuthorized) {
                    mHostManager.usbDeviceAuthorized(deviceAddress);
                } else {
                    mHostManager.usbDeviceDeauthorized(deviceAddress);
                }
            }

            if (handleDenyDefers) {
                Slog.d(
                        TAG,
                        TextUtils.formatSimple(
                                "AuthEvents: Notify deny-defer notification if screen locked"));
                checkScreenLockedThenNotify(deviceAddress);
            }
        }
    }

    @WorkerThread
    void waitForPersistedDeviceData() {
        synchronized (mLock) {
            while (!mIsReadPersistedDevicesComplete) {
                try {
                    mLock.wait();
                } catch (InterruptedException unused) {
                }
            }
        }
    }

    // Remove stale fingerprints from the list of persisted devices. This does not write back the
    // resulting devices to disk intentionally as it is also called to clear stale fingerprints when
    // reading.
    //
    // Only call this method while locked.
    @GuardedBy("mLock")
    @VisibleForTesting
    boolean removeStaleFingerprintsFromPersistedLocked() {
        int originalSize = mPersistedAuthorizedDevices.size();
        boolean removed =
                mPersistedAuthorizedDevices.removeIf(
                        (f) -> {
                            return f.isStale();
                        });

        if (removed) {
            Slog.d(
                    TAG,
                    TextUtils.formatSimple(
                            "Removed %d stale fingerprints.",
                            (originalSize - mPersistedAuthorizedDevices.size())));
        }

        return removed;
    }

    // Read from given parser. This is not thread-safe so make sure the parser is protected if
    // calling from multiple threads.
    ArraySet<UsbDeviceFingerprint> readFromParser(TypedXmlPullParser parser)
            throws XmlPullParserException, IOException {
        // Skip the initial document tag
        XmlUtils.nextElement(parser);

        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new XmlPullParserException("No start tag found");
        }

        if (!XML_ROOT_NAME.equals(parser.getName())) {
            throw new XmlPullParserException("Unexpected root tag: " + parser.getName());
        }

        ArraySet<UsbDeviceFingerprint> fingerprints = new ArraySet<>();

        int outerDepth = parser.getDepth();
        XmlUtils.nextElementWithin(parser, outerDepth);

        while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                XmlUtils.nextElementWithin(parser, outerDepth);
                continue;
            }

            if (!parser.getName().equals(UsbDeviceFingerprint.XML_ROOT_NAME)) {
                XmlUtils.nextElementWithin(parser, outerDepth);
                continue;
            }

            UsbDeviceFingerprint fingerprint = UsbDeviceFingerprint.read(parser);
            if (fingerprint != null) {
                fingerprints.add(fingerprint);
            } else {
                Slog.e(TAG, "Error reading fingerprint on tag " + parser.getName());
            }
        }

        return fingerprints;
    }

    // Mark reading as incomplete and push reading of file + unblocking readers to iothread.
    void readPersisted() {
        synchronized (mLock) {
            mIsReadPersistedDevicesComplete = false;
        }

        IoThread.getHandler()
                .post(
                        () -> {
                            ArraySet<UsbDeviceFingerprint> readFingerprints = new ArraySet<>();
                            try {
                                synchronized (mPersistedDevicesFile) {
                                    try (FileInputStream in = mPersistedDevicesFile.openRead()) {
                                        TypedXmlPullParser parser = Xml.resolvePullParser(in);
                                        readFingerprints = readFromParser(parser);
                                    } catch (FileNotFoundException e) {
                                        Slog.d(TAG, "Persisted devices file not found");
                                    } catch (XmlPullParserException | IOException e) {
                                        Slog.e(
                                                TAG,
                                                "Error reading persisted device file. Deleting to"
                                                        + " start fresh.",
                                                e);
                                        mPersistedDevicesFile.delete();
                                    }
                                }
                            } finally {
                                // Store read fingerprints and unblock any waiting threads.
                                synchronized (mLock) {
                                    mPersistedAuthorizedDevices = readFingerprints;
                                    removeStaleFingerprintsFromPersistedLocked();
                                    mIsReadPersistedDevicesComplete = true;
                                    mLock.notifyAll();
                                }
                            }
                        });
    }

    // Write to given serializer. This is not thread-safe so make sure the serializer is protected
    // if calling from multiple threads.
    void writeToSerializer(TypedXmlSerializer serializer, UsbDeviceFingerprint[] fingerprints)
            throws IOException {
        serializer.startDocument(null, true);
        serializer.startTag(null, XML_ROOT_NAME);

        if (fingerprints != null) {
            for (UsbDeviceFingerprint fp : fingerprints) {
                fp.write(serializer);
            }
        }

        serializer.endTag(null, XML_ROOT_NAME);
        serializer.endDocument();
    }

    @GuardedBy("mLock")
    void scheduleWritePersistedLocked() {
        if (mIsWritePersistedScheduled) {
            return;
        }

        mIsWritePersistedScheduled = true;
        IoThread.getHandler()
                .post(
                        () -> {
                            UsbDeviceFingerprint[] fingerprints;

                            // Make a copy of fingerprints.
                            synchronized (mLock) {
                                fingerprints =
                                        mPersistedAuthorizedDevices.toArray(
                                                new UsbDeviceFingerprint[0]);
                                mIsWritePersistedScheduled = false;
                            }

                            // Create xml serializer and write to file.
                            synchronized (mPersistedDevicesFile) {
                                FileOutputStream out = null;
                                try {
                                    out = mPersistedDevicesFile.startWrite();
                                    TypedXmlSerializer serializer = Xml.resolveSerializer(out);
                                    writeToSerializer(serializer, fingerprints);
                                    mPersistedDevicesFile.finishWrite(out);
                                } catch (IOException e) {
                                    Slog.e(TAG, "Failed to write persisted devices", e);
                                    mPersistedDevicesFile.failWrite(out);
                                }
                            }
                        });
    }
}
