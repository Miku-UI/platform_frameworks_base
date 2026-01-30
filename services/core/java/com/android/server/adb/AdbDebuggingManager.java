/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.server.adb;

import static android.os.InputConstants.DEFAULT_DISPATCHING_TIMEOUT_MILLIS;

import static com.android.internal.util.dump.DumpUtils.writeStringIfNotNull;
import static com.android.server.adb.AdbDebuggingManager.AdbDebuggingHandler.MSG_START_ADB_WIFI;
import static com.android.server.adb.AdbService.ADBD;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.debug.AdbManager;
import android.debug.AdbNotifications;
import android.debug.AdbProtoEnums;
import android.debug.AdbTransportType;
import android.debug.PairDevice;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileUtils;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemService;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.service.adb.AdbDebuggingManagerProto;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Slog;

import com.android.adbdauth.flags.Flags;
import com.android.internal.R;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.internal.util.FrameworkStatsLog;
import com.android.internal.util.dump.DualDumpOutputStream;
import com.android.server.FgThread;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeoutException;

/**
 * Manages communication with the Android Debug Bridge (ADB) daemon to allow, deny, or clear public
 * keys that are authorized to connect to the ADB service itself. The storage of authorized public
 * keys is done through {@link AdbKeyStore}.
 */
public class AdbDebuggingManager {
    private static final String TAG = AdbDebuggingManager.class.getSimpleName();

    private static final String ADBD_SOCKET = "adbd";
    private static final String ADB_DIRECTORY = "misc/adb";
    // This file contains keys that will always be allowed to connect to the device via adb.
    private static final String ADB_KEYS_FILE = "adb_keys";
    // This file contains keys that will be allowed to connect without user interaction as long
    // as a subsequent connection occurs within the allowed duration.
    private static final String ADB_TEMP_KEYS_FILE = "adb_temp_keys.xml";
    private static final int BUFFER_SIZE = 65536;
    private static final Ticker SYSTEM_TICKER = () -> System.currentTimeMillis();

    private final Context mContext;
    private final ContentResolver mContentResolver;
    @VisibleForTesting final AdbDebuggingHandler mHandler;
    private boolean mAdbUsbEnabled = false;
    private boolean mAdbWifiEnabled = false;
    private String mFingerprints;
    // A key can be used more than once (e.g. USB, wifi), so need to keep a refcount
    private final Map<String, Integer> mConnectedKeys = new HashMap<>();
    private final String mConfirmComponent;
    @Nullable private final File mUserKeyFile;
    @Nullable private final File mTempKeysFile;

    static final String WIFI_PERSISTENT_GUID = "persist.adb.wifi.guid";
    private static final int PAIRING_CODE_LENGTH = 6;

    /** The maximum time to wait for the adbd service to change state when toggling. */
    private static final long ADBD_STATE_CHANGE_TIMEOUT = DEFAULT_DISPATCHING_TIMEOUT_MILLIS;

    private AdbPairingThread mAdbPairingThread = null;

    /**
     * The set of public keys for devices currently connected over Wi-Fi ADB.
     *
     * <p>This collection is thread-safe for reads from any thread but MUST only be modified on the
     * {@link AdbDebuggingHandler} thread to avoid dead locks.
     *
     * <p>{@link CopyOnWriteArraySet} is used because reads (for updating the UI) are expected to be
     * much more frequent than writes (device connections and disconnections), making lock-free
     * reads highly efficient.
     */
    private final Set<String> mWifiConnectedKeys = new CopyOnWriteArraySet<>();

    // The current info of the adbwifi connection.
    private final AdbConnectionInfo mAdbConnectionInfo = new AdbConnectionInfo();

    // Polls for a tls port property when adb wifi is enabled
    private AdbConnectionPortPoller mConnectionPortPoller;

    private final Ticker mTicker;

    public AdbDebuggingManager(Context context) {
        this(
                context,
                /* confirmComponent= */ null,
                getAdbFile(ADB_KEYS_FILE),
                getAdbFile(ADB_TEMP_KEYS_FILE),
                /* adbDebuggingThread= */ null,
                SYSTEM_TICKER);
    }

    /**
     * Constructor that accepts the component to be invoked to confirm if the user wants to allow an
     * adb connection from the key.
     */
    @VisibleForTesting
    AdbDebuggingManager(
            Context context,
            String confirmComponent,
            File testUserKeyFile,
            File tempKeysFile,
            AdbDebuggingThread adbDebuggingThread,
            Ticker ticker) {
        mContext = context;
        mContentResolver = mContext.getContentResolver();
        mConfirmComponent = confirmComponent;
        mUserKeyFile = testUserKeyFile;
        mTempKeysFile = tempKeysFile;
        mTicker = ticker;
        mHandler = new AdbDebuggingHandler(FgThread.get().getLooper(), adbDebuggingThread);
    }

    static void sendBroadcastWithDebugPermission(
            @NonNull Context context, @NonNull Intent intent, @NonNull UserHandle userHandle) {
        context.sendBroadcastAsUser(
                intent, userHandle, android.Manifest.permission.MANAGE_DEBUGGING);
    }

    private void startTLSPortPoller() {
        if (wifiLifeCycleOverAdbdauthSupported()) {
            Slog.d(TAG, "Expecting tls port from adbdauth");
            return;
        }

        Slog.d(TAG, "Expecting tls port from ADB Wifi connection poller");
        mConnectionPortPoller =
                new AdbConnectionPortPoller(
                        port -> {
                            Slog.d(TAG, "Received tls port from poller =" + port);
                            Message msg =
                                    mHandler.obtainMessage(
                                            port > 0
                                                    ? AdbDebuggingHandler.MSG_SERVER_CONNECTED
                                                    : AdbDebuggingHandler.MSG_SERVER_DISCONNECTED);
                            msg.obj = port;
                            mHandler.sendMessage(msg);
                        });
        mConnectionPortPoller.start();
    }

    private void stopTLSPortPoller() {
        if (mConnectionPortPoller == null) {
            return;
        }

        mConnectionPortPoller.cancelAndWait();
        mConnectionPortPoller = null;
    }

    @VisibleForTesting
    static class AdbDebuggingThread extends Thread {
        private LocalSocket mSocket;
        private OutputStream mOutputStream;
        private InputStream mInputStream;
        private Handler mHandler;

        private boolean mConnected = false;

        @VisibleForTesting
        AdbDebuggingThread() {
            super(TAG);
        }

        @VisibleForTesting
        void setHandler(Handler handler) {
            mHandler = handler;
        }

        @Override
        public void run() {
            Slog.d(TAG, "Entering thread");
            while (true) {
                try {
                    synchronized (this) {
                        mConnected = false;
                        openSocketLocked();
                        mConnected = true;
                    }

                    listenToSocket();
                } catch (Exception e) {
                    /* Don't loop too fast if adbd dies, before init restarts it */
                    SystemClock.sleep(1000);
                }
            }
        }

        synchronized boolean isConnected() {
            return mConnected;
        }

        private void openSocketLocked() throws IOException {
            try {
                LocalSocketAddress address =
                        new LocalSocketAddress(ADBD_SOCKET, LocalSocketAddress.Namespace.RESERVED);
                mInputStream = null;

                Slog.d(TAG, "Creating socket");
                mSocket = new LocalSocket(LocalSocket.SOCKET_SEQPACKET);
                mSocket.connect(address);

                mOutputStream = mSocket.getOutputStream();
                mInputStream = mSocket.getInputStream();
                mHandler.sendEmptyMessage(AdbDebuggingHandler.MSG_ADBD_SOCKET_CONNECTED);
            } catch (IOException ioe) {
                Slog.e(TAG, "adbd_auth domain socket unavailable: " + ioe);
                closeSocketLocked();
                throw ioe;
            }
        }

        private void listenToSocket() throws IOException {
            try {
                byte[] buffer = new byte[BUFFER_SIZE];
                while (true) {
                    int count = mInputStream.read(buffer);
                    // if less than 2 bytes are read the if statements below will throw an
                    // IndexOutOfBoundsException.
                    if (count < 2) {
                        Slog.w(TAG, "Read failed with count " + count);
                        break;
                    }

                    Slog.d(TAG, "Recv packet: " + new String(Arrays.copyOfRange(buffer, 0, 2)));

                    // These messages are send from AdbdAuthContext::SendPacket
                    // in frameworks/native/libs/adbd_auth/adbd_auth.cpp

                    if (buffer[0] == 'P' && buffer[1] == 'K') {
                        // PK adbauth.AdbdAuthPacketRequestAuthorization
                        String key = new String(Arrays.copyOfRange(buffer, 2, count));
                        Slog.d(TAG, "Received public key: " + key);
                        Message msg =
                                mHandler.obtainMessage(AdbDebuggingHandler.MESSAGE_ADB_CONFIRM);
                        msg.obj = key;
                        mHandler.sendMessage(msg);
                    } else if (buffer[0] == 'D' && buffer[1] == 'C') {
                        // DC adbauth.AdbdAuthPacketDisconnected
                        String key = new String(Arrays.copyOfRange(buffer, 2, count));
                        Slog.d(TAG, "Received disconnected message: " + key);
                        Message msg =
                                mHandler.obtainMessage(AdbDebuggingHandler.MESSAGE_ADB_DISCONNECT);
                        msg.obj = key;
                        mHandler.sendMessage(msg);
                    } else if (buffer[0] == 'C' && buffer[1] == 'K') {
                        // CK adbauth.AdbdAuthPacketAuthenticated
                        String key = new String(Arrays.copyOfRange(buffer, 2, count));
                        Slog.d(TAG, "Received connected key message: " + key);
                        Message msg =
                                mHandler.obtainMessage(
                                        AdbDebuggingHandler.MESSAGE_ADB_CONNECTED_KEY);
                        msg.obj = key;
                        mHandler.sendMessage(msg);
                    } else if (buffer[0] == 'W' && buffer[1] == 'E') {
                        // WE adbauth.AdbdPacketTlsDeviceConnected
                        byte transportType = buffer[2];
                        String key = new String(Arrays.copyOfRange(buffer, 3, count));
                        switch (transportType) {
                            case AdbTransportType.USB -> {
                                Slog.d(TAG, "Received USB TLS connected key message: " + key);
                                Message msg =
                                        mHandler.obtainMessage(
                                                AdbDebuggingHandler.MESSAGE_ADB_CONNECTED_KEY);
                                msg.obj = key;
                                mHandler.sendMessage(msg);
                            }
                            case AdbTransportType.WIFI -> {
                                Slog.d(TAG, "Received WIFI TLS connected key message: " + key);
                                Message msg =
                                        mHandler.obtainMessage(
                                                AdbDebuggingHandler.MSG_WIFI_DEVICE_CONNECTED);
                                msg.obj = key;
                                mHandler.sendMessage(msg);
                            }
                            case AdbTransportType.VSOCK ->
                                    Slog.e(TAG, "AdbTransportType.VSOCK is not yet supported here");
                            default ->
                                    Slog.e(
                                            TAG,
                                            "Got unknown transport type from adbd ("
                                                    + transportType
                                                    + ")");
                        }
                    } else if (buffer[0] == 'W' && buffer[1] == 'F') {
                        // WF adbauth.AdbdPacketTlsDeviceDisconnected
                        byte transportType = buffer[2];
                        String key = new String(Arrays.copyOfRange(buffer, 3, count));
                        switch (transportType) {
                            case AdbTransportType.USB -> {
                                Slog.d(TAG, "Received USB TLS disconnect message: " + key);
                                Message msg =
                                        mHandler.obtainMessage(
                                                AdbDebuggingHandler.MESSAGE_ADB_DISCONNECT);
                                msg.obj = key;
                                mHandler.sendMessage(msg);
                            }
                            case AdbTransportType.WIFI -> {
                                Slog.d(TAG, "Received WIFI TLS disconnect key message: " + key);
                                Message msg =
                                        mHandler.obtainMessage(
                                                AdbDebuggingHandler.MSG_WIFI_DEVICE_DISCONNECTED);
                                msg.obj = key;
                                mHandler.sendMessage(msg);
                            }
                            case AdbTransportType.VSOCK ->
                                    Slog.e(TAG, "AdbTransportType.VSOCK is not yet supported here");
                            default ->
                                    Slog.e(
                                            TAG,
                                            "Got unknown transport type from adbd ("
                                                    + transportType
                                                    + ")");
                        }
                    } else if (buffer[0] == 'T' && buffer[1] == 'P') {
                        // TP adbauth.AdbdPacketTlsServerPort
                        if (count < 4) {
                            Slog.e(TAG, "Bad TP message length " + count);
                            break;
                        }
                        ByteBuffer bytes = ByteBuffer.wrap(buffer, 2, 2);
                        bytes.order(ByteOrder.LITTLE_ENDIAN);

                        int port = bytes.getShort() & 0xFFFF;
                        Slog.d(TAG, "Received tls port=" + port);
                        Message msg =
                                mHandler.obtainMessage(
                                        port > 0
                                                ? AdbDebuggingHandler.MSG_SERVER_CONNECTED
                                                : AdbDebuggingHandler.MSG_SERVER_DISCONNECTED);
                        msg.obj = port;
                        mHandler.sendMessage(msg);
                    } else {
                        Slog.e(
                                TAG,
                                "Wrong message: " + (new String(Arrays.copyOfRange(buffer, 0, 2))));
                        break;
                    }
                }
            } finally {
                synchronized (this) {
                    closeSocketLocked();
                }
            }
        }

        private void closeSocketLocked() {
            Slog.d(TAG, "Closing socket");
            try {
                if (mOutputStream != null) {
                    mOutputStream.close();
                    mOutputStream = null;
                }
            } catch (IOException e) {
                Slog.e(TAG, "Failed closing output stream: " + e);
            }

            try {
                if (mSocket != null) {
                    mSocket.close();
                    mSocket = null;
                }
            } catch (IOException ex) {
                Slog.e(TAG, "Failed closing socket: " + ex);
            }
            mHandler.sendEmptyMessage(AdbDebuggingHandler.MSG_ADBD_SOCKET_DISCONNECTED);
        }

        // TODO: Change the name of this method. This is not always a response. It should be called
        // sendMessage.
        void sendResponse(String msg) {
            synchronized (this) {
                Slog.d(TAG, "Send packet " + msg);
                if (mOutputStream != null) {
                    try {
                        mOutputStream.write(msg.getBytes());
                    } catch (IOException ex) {
                        Slog.e(TAG, "Failed to write response:", ex);
                    }
                }
            }
        }
    }

    // We need to know if ADBd will have access to the version of adbdauth which allows
    // to send ADB Wifi TSL port and ADBWifi lifecycle management over methods.
    private static boolean wifiLifeCycleOverAdbdauthSupported() {
        return Flags.useTlsLifecycle()
                && (Build.VERSION.SDK_INT >= 37
                        || (Build.VERSION.SDK_INT == 36 && isAtLeastPreReleaseCodename("Baklava")));
    }

    // This should only be used with NDK APIs because the NDK lacks flagging support.
    private static boolean isAtLeastPreReleaseCodename(@NonNull String codename) {
        // Special case "REL", which means the build is not a pre-release build.
        if ("REL".equals(Build.VERSION.CODENAME)) {
            return false;
        }

        // Otherwise lexically compare them. Return true if the build codename is equal to or
        // greater than the requested codename.
        return Build.VERSION.CODENAME.compareTo(codename) >= 0;
    }

    class AdbDebuggingHandler extends Handler {
        private NotificationManager mNotificationManager;
        private boolean mAdbNotificationShown;

        private final AdbNetworkMonitor mAdbNetworkMonitor;

        private static final String ADB_NOTIFICATION_CHANNEL_ID_TV = "usbdevicemanager.adb.tv";

        private boolean isTv() {
            return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        }

        private void setupNotifications() {
            if (mNotificationManager != null) {
                return;
            }
            mNotificationManager =
                    (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            if (mNotificationManager == null) {
                Slog.e(TAG, "Unable to setup notifications for wireless debugging");
                return;
            }

            // Ensure that the notification channels are set up
            if (isTv()) {
                // TV-specific notification channel
                mNotificationManager.createNotificationChannel(
                        new NotificationChannel(
                                ADB_NOTIFICATION_CHANNEL_ID_TV,
                                mContext.getString(
                                        com.android.internal.R.string
                                                .adb_debugging_notification_channel_tv),
                                NotificationManager.IMPORTANCE_HIGH));
            }
        }

        // The default time to schedule the job to keep the keystore updated with a currently
        // connected key as well as to removed expired keys.
        static final long UPDATE_KEYSTORE_JOB_INTERVAL = 86400000;
        // The minimum interval at which the job should run to update the keystore. This is intended
        // to prevent the job from running too often if the allowed connection time for adb grants
        // is set to an extremely small value.
        static final long UPDATE_KEYSTORE_MIN_JOB_INTERVAL = 60000;

        static final int MESSAGE_ADB_ENABLED = 1;
        static final int MESSAGE_ADB_DISABLED = 2;
        static final int MESSAGE_ADB_ALLOW = 3;
        static final int MESSAGE_ADB_DENY = 4;
        static final int MESSAGE_ADB_CONFIRM = 5;
        static final int MESSAGE_ADB_CLEAR = 6;
        static final int MESSAGE_ADB_DISCONNECT = 7;
        static final int MESSAGE_ADB_PERSIST_KEYSTORE = 8;
        static final int MESSAGE_ADB_UPDATE_KEYSTORE = 9;
        static final int MESSAGE_ADB_CONNECTED_KEY = 10;

        // === Messages from the UI ==============
        // UI asks adbd to enable adbdwifi
        static final int MSG_ADBDWIFI_ENABLE = 11;
        // UI asks adbd to disable adbdwifi
        static final int MSG_ADBDWIFI_DISABLE = 12;
        // Cancel pairing
        static final int MSG_PAIRING_CANCEL = 14;
        // Enable pairing by pairing code
        static final int MSG_PAIR_PAIRING_CODE = 15;
        // Enable pairing by QR code
        static final int MSG_PAIR_QR_CODE = 16;
        // UI asks to unpair (forget) a device.
        static final int MSG_REQ_UNPAIR = 17;
        // User allows debugging on the current network
        static final int MSG_ADBWIFI_ALLOW = 18;
        // User denies debugging on the current network
        static final int MSG_ADBWIFI_DENY = 19;

        // === Messages from the PairingThread ===========
        // Result of the pairing
        static final int MSG_RESPONSE_PAIRING_RESULT = 20;
        // The port opened for pairing
        static final int MSG_RESPONSE_PAIRING_PORT = 21;

        // === Messages from adbd ================
        // Notifies us a wifi device connected.
        static final int MSG_WIFI_DEVICE_CONNECTED = 22;
        // Notifies us a wifi device disconnected.
        static final int MSG_WIFI_DEVICE_DISCONNECTED = 23;
        // Notifies us the TLS server is connected and listening
        static final int MSG_SERVER_CONNECTED = 24;
        // Notifies us the TLS server is disconnected
        static final int MSG_SERVER_DISCONNECTED = 25;
        // Notification when adbd socket successfully connects.
        static final int MSG_ADBD_SOCKET_CONNECTED = 26;
        // Notification when adbd socket is disconnected.
        static final int MSG_ADBD_SOCKET_DISCONNECTED = 27;

        // === Messages from other parts of the system
        private static final int MESSAGE_KEY_FILES_UPDATED = 28;

        // === Messages we can send to adbd ===========
        static final String MSG_DISCONNECT_DEVICE = "DD";
        static final String MSG_START_ADB_WIFI = "W1";
        static final String MSG_STOP_ADB_WIFI = "W0";

        @NonNull @VisibleForTesting
        final AdbKeyStore mAdbKeyStore =
                new AdbKeyStore(mContext, mTempKeysFile, mUserKeyFile, mTicker);

        private final AdbDebuggingThread mThread;

        private ContentObserver mAuthTimeObserver =
                new ContentObserver(this) {
                    @Override
                    public void onChange(boolean selfChange, Uri uri) {
                        Slog.d(
                                TAG,
                                "Received notification that uri "
                                        + uri
                                        + " was modified; rescheduling keystore job");
                        scheduleJobToUpdateAdbKeyStore();
                    }
                };

        /** Constructor that accepts the AdbDebuggingThread to which responses should be sent. */
        @VisibleForTesting
        AdbDebuggingHandler(Looper looper, AdbDebuggingThread thread) {
            super(looper);
            if (thread == null) {
                thread = new AdbDebuggingThread();
                thread.setHandler(this);
            }
            mThread = thread;
            if (com.android.server.adb.Flags.allowAdbWifiReconnect()) {
                mAdbNetworkMonitor =
                        new AdbWifiNetworkMonitor(mContext, mAdbKeyStore::isTrustedNetwork);
            } else {
                mAdbNetworkMonitor = new AdbBroadcastReceiver(mContext, mAdbConnectionInfo);
            }
        }

        // Show when at least one device is connected.
        public void showAdbConnectedNotification(boolean show) {
            final int id = SystemMessage.NOTE_ADB_WIFI_ACTIVE;
            if (show == mAdbNotificationShown) {
                return;
            }
            setupNotifications();
            if (!mAdbNotificationShown) {
                Notification notification =
                        AdbNotifications.createNotification(mContext, AdbTransportType.WIFI);
                mAdbNotificationShown = true;
                mNotificationManager.notifyAsUser(null, id, notification, UserHandle.ALL);
            } else {
                mAdbNotificationShown = false;
                mNotificationManager.cancelAsUser(null, id, UserHandle.ALL);
            }
        }

        private void startAdbdWifi() {
            if (wifiLifeCycleOverAdbdauthSupported()) {
                mThread.sendResponse(MSG_START_ADB_WIFI);
            } else {
                AdbService.enableADBdWifi();
            }
        }

        private void stopAdbdWifi() {
            if (wifiLifeCycleOverAdbdauthSupported()) {
                mThread.sendResponse(MSG_STOP_ADB_WIFI);
            } else {
                AdbService.disableADBdWifi();
            }
        }

        // AdbService/AdbDebuggingManager are always created but we only start the connection
        // with adbd thread when it is actually needed.
        private void ensureAdbDebuggingThreadAlive() {
            if (!mThread.isAlive()) {
                mThread.start();

                registerForAuthTimeChanges();
                mAdbKeyStore.updateKeyStore();
                scheduleJobToUpdateAdbKeyStore();
            }
        }

        public void handleMessage(Message msg) {

            switch (msg.what) {
                case MESSAGE_ADB_ENABLED -> {
                    if (mAdbUsbEnabled) {
                        break;
                    }
                    ensureAdbDebuggingThreadAlive();
                    mAdbUsbEnabled = true;
                }
                case MESSAGE_ADB_DISABLED -> {
                    if (!mAdbUsbEnabled) {
                        break;
                    }
                    mAdbUsbEnabled = false;
                }
                case MESSAGE_ADB_ALLOW -> {
                    String key = (String) msg.obj;
                    String fingerprints = getFingerprints(key);
                    if (!fingerprints.equals(mFingerprints)) {
                        Slog.e(
                                TAG,
                                "Fingerprints do not match. Got "
                                        + fingerprints
                                        + ", expected "
                                        + mFingerprints);
                        break;
                    }

                    boolean alwaysAllow = msg.arg1 == 1;
                    mThread.sendResponse("OK");
                    if (alwaysAllow) {
                        if (!mConnectedKeys.containsKey(key)) {
                            mConnectedKeys.put(key, 1);
                        }
                        mAdbKeyStore.setLastConnectionTime(key, mTicker.currentTimeMillis());
                        sendPersistKeyStoreMessage();
                        scheduleJobToUpdateAdbKeyStore();
                    }
                    logAdbConnectionChanged(key, AdbProtoEnums.USER_ALLOWED, alwaysAllow);
                }
                case MESSAGE_ADB_DENY -> {
                    Slog.w(TAG, "Denying adb confirmation");
                    mThread.sendResponse("NO");
                    logAdbConnectionChanged(null, AdbProtoEnums.USER_DENIED, false);
                }
                case MESSAGE_ADB_CONFIRM -> {
                    String key = (String) msg.obj;
                    String fingerprints = getFingerprints(key);
                    if ("".equals(fingerprints)) {
                        mThread.sendResponse("NO");
                        logAdbConnectionChanged(key, AdbProtoEnums.DENIED_INVALID_KEY, false);
                        break;
                    }
                    logAdbConnectionChanged(key, AdbProtoEnums.AWAITING_USER_APPROVAL, false);
                    mFingerprints = fingerprints;
                    startConfirmationForKey(key, mFingerprints);
                }
                case MESSAGE_ADB_CLEAR -> {
                    Slog.d(TAG, "Received a request to clear the adb authorizations");
                    mConnectedKeys.clear();
                    mWifiConnectedKeys.clear();
                    mAdbKeyStore.deleteKeyStore();
                    cancelJobToUpdateAdbKeyStore();
                    // Disconnect all active sessions unless the user opted out through Settings.
                    if (Settings.Global.getInt(
                                    mContentResolver,
                                    Settings.Global.ADB_DISCONNECT_SESSIONS_ON_REVOKE,
                                    1)
                            == 1) {
                        // If adb is currently enabled, then toggle it off and back on to disconnect
                        // any existing sessions.
                        if (mAdbUsbEnabled) {
                            try {
                                SystemService.stop(ADBD);
                                SystemService.waitForState(
                                        ADBD,
                                        SystemService.State.STOPPED,
                                        ADBD_STATE_CHANGE_TIMEOUT);
                                SystemService.start(ADBD);
                                SystemService.waitForState(
                                        ADBD,
                                        SystemService.State.RUNNING,
                                        ADBD_STATE_CHANGE_TIMEOUT);
                            } catch (TimeoutException e) {
                                Slog.e(TAG, "Timeout occurred waiting for adbd to cycle: ", e);
                                // TODO(b/281758086): Display a dialog to the user to warn them
                                // of this state and direct them to manually toggle adb.
                                // If adbd fails to toggle within the timeout window, set adb to
                                // disabled to alert the user that further action is required if
                                // they want to continue using adb after revoking the grants.
                                Settings.Global.putInt(
                                        mContentResolver, Settings.Global.ADB_ENABLED, 0);
                            }
                        }
                    }
                }
                case MESSAGE_ADB_DISCONNECT -> {
                    String key = (String) msg.obj;
                    boolean alwaysAllow = false;
                    if (key != null && key.length() > 0) {
                        if (mConnectedKeys.containsKey(key)) {
                            alwaysAllow = true;
                            int refcount = mConnectedKeys.get(key) - 1;
                            if (refcount == 0) {
                                mAdbKeyStore.setLastConnectionTime(
                                        key, mTicker.currentTimeMillis());
                                sendPersistKeyStoreMessage();
                                scheduleJobToUpdateAdbKeyStore();
                                mConnectedKeys.remove(key);
                            } else {
                                mConnectedKeys.put(key, refcount);
                            }
                        }
                    } else {
                        Slog.w(TAG, "Received a disconnected key message with an empty key");
                    }
                    logAdbConnectionChanged(key, AdbProtoEnums.DISCONNECTED, alwaysAllow);
                }
                case MESSAGE_ADB_PERSIST_KEYSTORE -> {
                    if (mAdbKeyStore != null) {
                        mAdbKeyStore.persistKeyStore();
                    }
                }
                case MESSAGE_ADB_UPDATE_KEYSTORE -> {
                    if (!mConnectedKeys.isEmpty()) {
                        for (Map.Entry<String, Integer> entry : mConnectedKeys.entrySet()) {
                            mAdbKeyStore.setLastConnectionTime(
                                    entry.getKey(), mTicker.currentTimeMillis());
                        }
                        sendPersistKeyStoreMessage();
                        scheduleJobToUpdateAdbKeyStore();
                    } else if (!mAdbKeyStore.isEmpty()) {
                        mAdbKeyStore.updateKeyStore();
                        scheduleJobToUpdateAdbKeyStore();
                    }
                }
                case MESSAGE_ADB_CONNECTED_KEY -> {
                    String key = (String) msg.obj;
                    if (key == null || key.length() == 0) {
                        Slog.w(TAG, "Received a connected key message with an empty key");
                    } else {
                        if (!mConnectedKeys.containsKey(key)) {
                            mConnectedKeys.put(key, 1);
                        } else {
                            mConnectedKeys.put(key, mConnectedKeys.get(key) + 1);
                        }
                        mAdbKeyStore.setLastConnectionTime(key, mTicker.currentTimeMillis());
                        sendPersistKeyStoreMessage();
                        scheduleJobToUpdateAdbKeyStore();
                        logAdbConnectionChanged(key, AdbProtoEnums.AUTOMATICALLY_ALLOWED, true);
                    }
                }
                case MSG_ADBDWIFI_ENABLE -> {
                    if (mAdbWifiEnabled) {
                        break;
                    }

                    AdbConnectionInfo currentInfo = getCurrentWifiApInfo();
                    if (currentInfo == null) {
                        Settings.Global.putInt(
                                mContentResolver, Settings.Global.ADB_WIFI_ENABLED, 0);
                        break;
                    }

                    if (!verifyWifiNetwork(currentInfo.getBSSID(), currentInfo.getSSID())) {
                        // This means that the network is not in the list of trusted networks.
                        // We'll give user a prompt on whether to allow wireless debugging on
                        // the current wifi network.
                        Settings.Global.putInt(
                                mContentResolver, Settings.Global.ADB_WIFI_ENABLED, 0);
                        break;
                    }

                    mAdbConnectionInfo.copy(currentInfo);
                    mAdbNetworkMonitor.register();
                    ensureAdbDebuggingThreadAlive();
                    startTLSPortPoller();
                    startAdbdWifi();
                    mAdbWifiEnabled = true;

                    Slog.i(TAG, "adb start wireless adb");
                }
                case MSG_ADBDWIFI_DISABLE -> {
                    if (!mAdbWifiEnabled) {
                        break;
                    }
                    mAdbWifiEnabled = false;
                    mAdbConnectionInfo.clear();
                    mAdbNetworkMonitor.unregister();
                    stopAdbdWifi();
                    onAdbdWifiServerDisconnected(-1);
                }
                case MSG_ADBWIFI_ALLOW -> {
                    if (mAdbWifiEnabled) {
                        break;
                    }
                    String bssid = (String) msg.obj;
                    boolean alwaysAllow = msg.arg1 == 1;
                    if (alwaysAllow) {
                        mAdbKeyStore.addTrustedNetwork(bssid);
                    }

                    // Let's check again to make sure we didn't switch networks while verifying
                    // the wifi bssid.
                    AdbConnectionInfo newInfo = getCurrentWifiApInfo();
                    if (newInfo == null || !bssid.equals(newInfo.getBSSID())) {
                        break;
                    }
                    mAdbConnectionInfo.copy(newInfo);
                    Settings.Global.putInt(mContentResolver, Settings.Global.ADB_WIFI_ENABLED, 1);
                    mAdbNetworkMonitor.register();
                    ensureAdbDebuggingThreadAlive();
                    startTLSPortPoller();
                    startAdbdWifi();
                    mAdbWifiEnabled = true;
                    Slog.i(TAG, "adb start wireless adb");
                }
                case MSG_ADBWIFI_DENY -> {
                    Settings.Global.putInt(mContentResolver, Settings.Global.ADB_WIFI_ENABLED, 0);
                    sendServerConnectionState(false, -1);
                }
                case MSG_REQ_UNPAIR -> {
                    String fingerprint = (String) msg.obj;
                    // Tell adbd to disconnect the device if connected.
                    String publicKey = mAdbKeyStore.findKeyFromFingerprint(fingerprint);
                    if (publicKey == null || publicKey.isEmpty()) {
                        Slog.e(TAG, "Not a known fingerprint [" + fingerprint + "]");
                        break;
                    }
                    String cmdStr = MSG_DISCONNECT_DEVICE + publicKey;
                    mThread.sendResponse(cmdStr);
                    mAdbKeyStore.removeKey(publicKey);
                    // Send the updated paired devices list to the UI.
                    sendPairedDevicesToUI(getPairedDevicesForKeys(mAdbKeyStore.getKeys()));
                }
                case MSG_RESPONSE_PAIRING_RESULT -> {
                    String publicKey = (String) msg.obj;
                    onPairingResult(publicKey);
                    // Send the updated paired devices list to the UI.
                    sendPairedDevicesToUI(getPairedDevicesForKeys(mAdbKeyStore.getKeys()));
                }
                case MSG_RESPONSE_PAIRING_PORT -> {
                    int port = (int) msg.obj;
                    sendPairingPortToUI(port);
                }
                case MSG_PAIR_PAIRING_CODE -> {
                    String pairingCode = createPairingCode(PAIRING_CODE_LENGTH);
                    updateUIPairCode(pairingCode);
                    mAdbPairingThread = new AdbPairingThread(pairingCode, null, mContext, this);
                    mAdbPairingThread.start();
                }
                case MSG_PAIR_QR_CODE -> {
                    Bundle bundle = (Bundle) msg.obj;
                    String serviceName = bundle.getString("serviceName");
                    String password = bundle.getString("password");
                    mAdbPairingThread = new AdbPairingThread(password, serviceName, mContext, this);
                    mAdbPairingThread.start();
                }
                case MSG_PAIRING_CANCEL -> {
                    if (mAdbPairingThread != null) {
                        mAdbPairingThread.cancelPairing();
                        try {
                            mAdbPairingThread.join();
                        } catch (InterruptedException e) {
                            Slog.w(TAG, "Error while waiting for pairing thread to quit.");
                            e.printStackTrace();
                        }
                        mAdbPairingThread = null;
                    }
                }
                case MSG_WIFI_DEVICE_CONNECTED -> {
                    String key = (String) msg.obj;
                    if (mWifiConnectedKeys.add(key)) {
                        sendPairedDevicesToUI(getPairedDevicesForKeys(mAdbKeyStore.getKeys()));
                        showAdbConnectedNotification(true);
                    }
                }
                case MSG_WIFI_DEVICE_DISCONNECTED -> {
                    String key = (String) msg.obj;
                    if (mWifiConnectedKeys.remove(key)) {
                        sendPairedDevicesToUI(getPairedDevicesForKeys(mAdbKeyStore.getKeys()));
                        if (mWifiConnectedKeys.isEmpty()) {
                            showAdbConnectedNotification(false);
                        }
                    }
                }
                case MSG_SERVER_CONNECTED -> {
                    int port = (int) msg.obj;
                    onAdbdWifiServerConnected(port);
                    mAdbConnectionInfo.setPort(port);
                }
                case MSG_SERVER_DISCONNECTED -> {
                    if (!mAdbWifiEnabled) {
                        break;
                    }
                    int port = (int) msg.obj;
                    onAdbdWifiServerDisconnected(port);
                    stopTLSPortPoller();
                }
                case MSG_ADBD_SOCKET_CONNECTED -> {
                    Slog.d(TAG, "adbd socket connected");
                    if (mAdbWifiEnabled) {
                        // In scenarios where adbd is restarted, the tls port may change.
                        startTLSPortPoller();
                        if (wifiLifeCycleOverAdbdauthSupported()) {
                            mThread.sendResponse(MSG_START_ADB_WIFI);
                        }
                    }
                }
                case MSG_ADBD_SOCKET_DISCONNECTED -> {
                    Slog.d(TAG, "adbd socket disconnected");
                    stopTLSPortPoller();
                    if (mAdbWifiEnabled) {
                        // In scenarios where adbd is restarted, the tls port may change.
                        onAdbdWifiServerDisconnected(-1);
                    }
                }
                case MESSAGE_KEY_FILES_UPDATED -> {
                    mAdbKeyStore.reloadKeyMap();
                }
            }
        }

        void registerForAuthTimeChanges() {
            Uri uri = Settings.Global.getUriFor(Settings.Global.ADB_ALLOWED_CONNECTION_TIME);
            mContext.getContentResolver().registerContentObserver(uri, false, mAuthTimeObserver);
        }

        private void logAdbConnectionChanged(String key, int state, boolean alwaysAllow) {
            long lastConnectionTime = mAdbKeyStore.getLastConnectionTime(key);
            long authWindow = mAdbKeyStore.getAllowedConnectionTime();
            Slog.d(
                    TAG,
                    "Logging key "
                            + key
                            + ", state = "
                            + state
                            + ", alwaysAllow = "
                            + alwaysAllow
                            + ", lastConnectionTime = "
                            + lastConnectionTime
                            + ", authWindow = "
                            + authWindow);
            FrameworkStatsLog.write(
                    FrameworkStatsLog.ADB_CONNECTION_CHANGED,
                    lastConnectionTime,
                    authWindow,
                    state,
                    alwaysAllow);
        }

        /**
         * Schedules a job to update the connection time of the currently connected key and filter
         * out any keys that are beyond their expiration time.
         *
         * @return the time in ms when the next job will run or -1 if the job should not be
         *     scheduled to run.
         */
        @VisibleForTesting
        long scheduleJobToUpdateAdbKeyStore() {
            cancelJobToUpdateAdbKeyStore();
            long keyExpiration = mAdbKeyStore.getNextExpirationTime();
            // if the keyExpiration time is -1 then either the keys are set to never expire or
            // there are no keys in the keystore, just return for now as a new job will be
            // scheduled on the next connection or when the auth time changes.
            if (keyExpiration == -1) {
                return -1;
            }
            long delay;
            // if the keyExpiration is 0 this indicates a key has already expired; schedule the job
            // to run now to ensure the key is removed immediately from adb_keys.
            if (keyExpiration == 0) {
                delay = 0;
            } else {
                // else the next job should be run either daily or when the next key is set to
                // expire with a min job interval to ensure this job does not run too often if a
                // small value is set for the key expiration.
                delay =
                        Math.max(
                                Math.min(UPDATE_KEYSTORE_JOB_INTERVAL, keyExpiration),
                                UPDATE_KEYSTORE_MIN_JOB_INTERVAL);
            }
            Message message = obtainMessage(MESSAGE_ADB_UPDATE_KEYSTORE);
            sendMessageDelayed(message, delay);
            return delay;
        }

        /**
         * Cancels the scheduled job to update the connection time of the currently connected key
         * and to remove any expired keys.
         */
        private void cancelJobToUpdateAdbKeyStore() {
            removeMessages(AdbDebuggingHandler.MESSAGE_ADB_UPDATE_KEYSTORE);
        }

        // Generates a random string of digits with size |size|.
        private String createPairingCode(int size) {
            String res = "";
            SecureRandom rand = new SecureRandom();
            for (int i = 0; i < size; ++i) {
                res += rand.nextInt(10);
            }

            return res;
        }

        private void sendServerConnectionState(boolean connected, int port) {
            Intent intent = new Intent(AdbManager.WIRELESS_DEBUG_STATE_CHANGED_ACTION);
            intent.putExtra(
                    AdbManager.WIRELESS_STATUS_EXTRA,
                    connected
                            ? AdbManager.WIRELESS_STATUS_CONNECTED
                            : AdbManager.WIRELESS_STATUS_DISCONNECTED);
            intent.putExtra(AdbManager.WIRELESS_DEBUG_PORT_EXTRA, port);
            AdbDebuggingManager.sendBroadcastWithDebugPermission(mContext, intent, UserHandle.ALL);
        }

        private void onAdbdWifiServerConnected(int port) {
            // Send the paired devices list to the UI
            sendPairedDevicesToUI(getPairedDevicesForKeys(mAdbKeyStore.getKeys()));
            sendServerConnectionState(true, port);
        }

        private void onAdbdWifiServerDisconnected(int port) {
            // The TLS server disconnected while we had wireless debugging enabled.
            // Let's disable it.
            mWifiConnectedKeys.clear();
            showAdbConnectedNotification(false);
            sendServerConnectionState(false, port);
        }

        /** Returns the [bssid, ssid] of the current access point. */
        private AdbConnectionInfo getCurrentWifiApInfo() {
            WifiManager wifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo == null || wifiInfo.getNetworkId() == -1) {
                Slog.i(TAG, "Not connected to any wireless network. Not enabling adbwifi.");
                return null;
            }

            String ssid = null;
            if (wifiInfo.isPasspointAp() || wifiInfo.isOsuAp()) {
                ssid = wifiInfo.getPasspointProviderFriendlyName();
            } else {
                ssid = wifiInfo.getSSID();
                if (ssid == null || WifiManager.UNKNOWN_SSID.equals(ssid)) {
                    // OK, it's not in the connectionInfo; we have to go hunting for it
                    List<WifiConfiguration> networks = wifiManager.getConfiguredNetworks();
                    int length = networks.size();
                    for (int i = 0; i < length; i++) {
                        if (networks.get(i).networkId == wifiInfo.getNetworkId()) {
                            ssid = networks.get(i).SSID;
                        }
                    }
                    if (ssid == null) {
                        Slog.e(TAG, "Unable to get ssid of the wifi AP.");
                        return null;
                    }
                }
            }

            String bssid = wifiInfo.getBSSID();
            if (TextUtils.isEmpty(bssid)) {
                Slog.e(TAG, "Unable to get the wifi ap's BSSID.");
                return null;
            }
            return new AdbConnectionInfo(bssid, ssid);
        }

        private boolean verifyWifiNetwork(String bssid, String ssid) {
            // Check against a list of user-trusted networks.
            if (mAdbKeyStore.isTrustedNetwork(bssid)) {
                return true;
            }

            // Ask user to confirm using wireless debugging on this network.
            startConfirmationForNetwork(ssid, bssid);
            return false;
        }

        private void onPairingResult(String publicKey) {
            if (publicKey == null) {
                Intent intent = new Intent(AdbManager.WIRELESS_DEBUG_PAIRING_RESULT_ACTION);
                intent.putExtra(AdbManager.WIRELESS_STATUS_EXTRA, AdbManager.WIRELESS_STATUS_FAIL);
                AdbDebuggingManager.sendBroadcastWithDebugPermission(
                        mContext, intent, UserHandle.ALL);
            } else {
                Intent intent = new Intent(AdbManager.WIRELESS_DEBUG_PAIRING_RESULT_ACTION);
                intent.putExtra(
                        AdbManager.WIRELESS_STATUS_EXTRA, AdbManager.WIRELESS_STATUS_SUCCESS);
                String fingerprints = getFingerprints(publicKey);
                String hostname = "nouser@nohostname";
                String[] args = publicKey.split("\\s+");
                if (args.length > 1) {
                    hostname = args[1];
                }
                PairDevice device = new PairDevice();
                device.name = fingerprints;
                device.guid = hostname;
                device.connected = false;
                intent.putExtra(AdbManager.WIRELESS_PAIR_DEVICE_EXTRA, device);
                AdbDebuggingManager.sendBroadcastWithDebugPermission(
                        mContext, intent, UserHandle.ALL);
                // Add the key into the keystore
                mAdbKeyStore.setLastConnectionTime(publicKey, mTicker.currentTimeMillis());
                sendPersistKeyStoreMessage();
                scheduleJobToUpdateAdbKeyStore();
            }
        }

        private void sendPairingPortToUI(int port) {
            Intent intent = new Intent(AdbManager.WIRELESS_DEBUG_PAIRING_RESULT_ACTION);
            intent.putExtra(AdbManager.WIRELESS_STATUS_EXTRA, AdbManager.WIRELESS_STATUS_CONNECTED);
            intent.putExtra(AdbManager.WIRELESS_DEBUG_PORT_EXTRA, port);
            AdbDebuggingManager.sendBroadcastWithDebugPermission(mContext, intent, UserHandle.ALL);
        }

        private void sendPairedDevicesToUI(Map<String, PairDevice> devices) {
            Intent intent = new Intent(AdbManager.WIRELESS_DEBUG_PAIRED_DEVICES_ACTION);
            // Map is not serializable, so need to downcast
            intent.putExtra(AdbManager.WIRELESS_DEVICES_EXTRA, (HashMap) devices);
            AdbDebuggingManager.sendBroadcastWithDebugPermission(mContext, intent, UserHandle.ALL);
        }

        private void updateUIPairCode(String code) {
            Slog.i(TAG, "updateUIPairCode: " + code);

            Intent intent = new Intent(AdbManager.WIRELESS_DEBUG_PAIRING_RESULT_ACTION);
            intent.putExtra(AdbManager.WIRELESS_PAIRING_CODE_EXTRA, code);
            intent.putExtra(
                    AdbManager.WIRELESS_STATUS_EXTRA, AdbManager.WIRELESS_STATUS_PAIRING_CODE);
            AdbDebuggingManager.sendBroadcastWithDebugPermission(mContext, intent, UserHandle.ALL);
        }
    }

    /**
     * Calculates and returns the MD5 fingerprint of a given key string. The key string is expected
     * to be a Base64 encoded string, optionally followed by whitespace and other content. Only the
     * first part (before any whitespace) is used for the fingerprint calculation.
     *
     * <p>The MD5 fingerprint is returned as a colon-separated hexadecimal string. For example:
     * "A1:B2:C3:D4:E5:F6:A7:B8:C9:D0:E1:F2:A3:B4:C5:D6"
     *
     * @param key The key string from which to generate the fingerprint. Expected to contain a
     *     Base64 encoded string as its first part.
     * @return The MD5 fingerprint of the decoded Base64 key, or an empty string if the input key is
     *     null, if the MD5 algorithm is not available, or if there's an error during Base64
     *     decoding.
     */
    // TODO(b/420613813) move to AdbKey object.
    static String getFingerprints(String key) {
        String hex = "0123456789ABCDEF";
        StringBuilder sb = new StringBuilder();
        MessageDigest digester;

        if (key == null) {
            return "";
        }

        try {
            digester = MessageDigest.getInstance("MD5");
        } catch (Exception ex) {
            Slog.e(TAG, "Error getting digester", ex);
            return "";
        }

        byte[] base64_data = key.split("\\s+")[0].getBytes();
        byte[] digest;
        try {
            digest = digester.digest(Base64.decode(base64_data, Base64.DEFAULT));
        } catch (IllegalArgumentException e) {
            Slog.e(TAG, "error doing base64 decoding", e);
            return "";
        }
        for (int i = 0; i < digest.length; i++) {
            sb.append(hex.charAt((digest[i] >> 4) & 0xf));
            sb.append(hex.charAt(digest[i] & 0xf));
            if (i < digest.length - 1) {
                sb.append(":");
            }
        }
        return sb.toString();
    }

    private void startConfirmationForNetwork(String ssid, String bssid) {
        List<Map.Entry<String, String>> extras = new ArrayList<Map.Entry<String, String>>();
        extras.add(new AbstractMap.SimpleEntry<String, String>("ssid", ssid));
        extras.add(new AbstractMap.SimpleEntry<String, String>("bssid", bssid));
        int currentUserId = ActivityManager.getCurrentUser();
        String componentString =
                Resources.getSystem()
                        .getString(R.string.config_customAdbWifiNetworkConfirmationComponent);
        ComponentName componentName = ComponentName.unflattenFromString(componentString);
        UserInfo userInfo = UserManager.get(mContext).getUserInfo(currentUserId);
        if (startConfirmationActivity(componentName, userInfo.getUserHandle(), extras)
                || startConfirmationService(componentName, userInfo.getUserHandle(), extras)) {
            return;
        }
        Slog.e(
                TAG,
                "Unable to start customAdbWifiNetworkConfirmation[SecondaryUser]Component "
                        + componentString
                        + " as an Activity or a Service");
    }

    private void startConfirmationForKey(String key, String fingerprints) {
        List<Map.Entry<String, String>> extras = new ArrayList<Map.Entry<String, String>>();
        extras.add(new AbstractMap.SimpleEntry<String, String>("key", key));
        extras.add(new AbstractMap.SimpleEntry<String, String>("fingerprints", fingerprints));
        int currentUserId = ActivityManager.getCurrentUser();
        UserInfo userInfo = UserManager.get(mContext).getUserInfo(currentUserId);
        String componentString;
        if (userInfo.isAdmin()) {
            componentString =
                    mConfirmComponent != null
                            ? mConfirmComponent
                            : Resources.getSystem()
                                    .getString(
                                            com.android.internal.R.string
                                                    .config_customAdbPublicKeyConfirmationComponent);
        } else {
            // If the current foreground user is not the admin user we send a different
            // notification specific to secondary users.
            componentString =
                    Resources.getSystem()
                            .getString(
                                    R.string
                                            .config_customAdbPublicKeyConfirmationSecondaryUserComponent);
        }
        ComponentName componentName = ComponentName.unflattenFromString(componentString);
        if (startConfirmationActivity(componentName, userInfo.getUserHandle(), extras)
                || startConfirmationService(componentName, userInfo.getUserHandle(), extras)) {
            return;
        }
        Slog.e(
                TAG,
                "unable to start customAdbPublicKeyConfirmation[SecondaryUser]Component "
                        + componentString
                        + " as an Activity or a Service");
    }

    /**
     * @return true if the componentName led to an Activity that was started.
     */
    private boolean startConfirmationActivity(
            ComponentName componentName,
            UserHandle userHandle,
            List<Map.Entry<String, String>> extras) {
        PackageManager packageManager = mContext.getPackageManager();
        Intent intent = createConfirmationIntent(componentName, extras);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
            try {
                mContext.startActivityAsUser(intent, userHandle);
                return true;
            } catch (ActivityNotFoundException e) {
                Slog.e(TAG, "unable to start adb whitelist activity: " + componentName, e);
            }
        }
        return false;
    }

    /**
     * @return true if the componentName led to a Service that was started.
     */
    private boolean startConfirmationService(
            ComponentName componentName,
            UserHandle userHandle,
            List<Map.Entry<String, String>> extras) {
        Intent intent = createConfirmationIntent(componentName, extras);
        try {
            if (mContext.startServiceAsUser(intent, userHandle) != null) {
                return true;
            }
        } catch (SecurityException e) {
            Slog.e(TAG, "unable to start adb whitelist service: " + componentName, e);
        }
        return false;
    }

    private Intent createConfirmationIntent(
            ComponentName componentName, List<Map.Entry<String, String>> extras) {
        Intent intent = new Intent();
        intent.setClassName(componentName.getPackageName(), componentName.getClassName());
        for (Map.Entry<String, String> entry : extras) {
            intent.putExtra(entry.getKey(), entry.getValue());
        }
        return intent;
    }

    /** Returns a new File with the specified name in the adb directory. */
    private static File getAdbFile(String fileName) {
        File dataDir = Environment.getDataDirectory();
        File adbDir = new File(dataDir, ADB_DIRECTORY);

        if (!adbDir.exists()) {
            Slog.e(TAG, "ADB data directory does not exist");
            return null;
        }

        return new File(adbDir, fileName);
    }

    File getAdbTempKeysFile() {
        return mTempKeysFile;
    }

    File getUserKeyFile() {
        return mUserKeyFile;
    }

    /**
     * When {@code enabled} is {@code true}, this allows ADB debugging and starts the ADB handler
     * thread. When {@code enabled} is {@code false}, this disallows ADB debugging for the
     * given @{code transportType}. See {@link IAdbTransport} for all available transport types. If
     * all transport types are disabled, the ADB handler thread will shut down.
     */
    public void setAdbEnabled(boolean enabled, byte transportType) {
        switch (transportType) {
            case AdbTransportType.USB ->
                    mHandler.sendEmptyMessage(
                            enabled
                                    ? AdbDebuggingHandler.MESSAGE_ADB_ENABLED
                                    : AdbDebuggingHandler.MESSAGE_ADB_DISABLED);
            case AdbTransportType.WIFI ->
                    mHandler.sendEmptyMessage(
                            enabled
                                    ? AdbDebuggingHandler.MSG_ADBDWIFI_ENABLE
                                    : AdbDebuggingHandler.MSG_ADBDWIFI_DISABLE);
            case AdbTransportType.VSOCK ->
                    throw new IllegalArgumentException(
                            "AdbTransportType.VSOCK is not yet supported here");
            default ->
                    throw new IllegalArgumentException(
                            "setAdbEnabled called with unimplemented transport type="
                                    + transportType);
        }
    }

    /**
     * Allows the debugging from the endpoint identified by {@code publicKey} either once or always
     * if {@code alwaysAllow} is {@code true}.
     */
    public void allowDebugging(boolean alwaysAllow, String publicKey) {
        Message msg = mHandler.obtainMessage(AdbDebuggingHandler.MESSAGE_ADB_ALLOW);
        msg.arg1 = alwaysAllow ? 1 : 0;
        msg.obj = publicKey;
        mHandler.sendMessage(msg);
    }

    /** Denies debugging connection from the device that last requested to connect. */
    public void denyDebugging() {
        mHandler.sendEmptyMessage(AdbDebuggingHandler.MESSAGE_ADB_DENY);
    }

    /**
     * Clears all previously accepted ADB debugging public keys. Any subsequent request will need to
     * pass through {@link #allowUsbDebugging(boolean, String)} again.
     */
    public void clearDebuggingKeys() {
        mHandler.sendEmptyMessage(AdbDebuggingHandler.MESSAGE_ADB_CLEAR);
    }

    /**
     * Allows wireless debugging on the network identified by {@code bssid} either once or always if
     * {@code alwaysAllow} is {@code true}.
     */
    public void allowWirelessDebugging(boolean alwaysAllow, String bssid) {
        Message msg = mHandler.obtainMessage(AdbDebuggingHandler.MSG_ADBWIFI_ALLOW);
        msg.arg1 = alwaysAllow ? 1 : 0;
        msg.obj = bssid;
        mHandler.sendMessage(msg);
    }

    /** Denies wireless debugging connection on the last requested network. */
    public void denyWirelessDebugging() {
        mHandler.sendEmptyMessage(AdbDebuggingHandler.MSG_ADBWIFI_DENY);
    }

    /** Returns the port adbwifi is currently opened on. */
    public int getAdbWirelessPort() {
        return mAdbConnectionInfo.getPort();
    }

    /** Returns the list of paired devices. */
    public Map<String, PairDevice> getPairedDevices() {
        return getPairedDevicesForKeys(mHandler.mAdbKeyStore.getKeys());
    }

    private Map<String, PairDevice> getPairedDevicesForKeys(Set<String> keys) {
        Map<String, PairDevice> pairedDevices = new HashMap();
        for (String key : keys) {
            String fingerprints = getFingerprints(key);
            String hostname = "nouser@nohostname";
            String[] args = key.split("\\s+");
            if (args.length > 1) {
                hostname = args[1];
            }
            PairDevice pairDevice = new PairDevice();
            pairDevice.name = hostname;
            pairDevice.guid = fingerprints;
            pairDevice.connected = mWifiConnectedKeys.contains(key);
            pairedDevices.put(key, pairDevice);
        }
        return pairedDevices;
    }

    /** Unpair with device */
    public void unpairDevice(String fingerprint) {
        Message message = Message.obtain(mHandler, AdbDebuggingHandler.MSG_REQ_UNPAIR, fingerprint);
        mHandler.sendMessage(message);
    }

    /** Enable pairing by pairing code */
    public void enablePairingByPairingCode() {
        mHandler.sendEmptyMessage(AdbDebuggingHandler.MSG_PAIR_PAIRING_CODE);
    }

    /** Enable pairing by pairing code */
    public void enablePairingByQrCode(String serviceName, String password) {
        Bundle bundle = new Bundle();
        bundle.putString("serviceName", serviceName);
        bundle.putString("password", password);
        Message message = Message.obtain(mHandler, AdbDebuggingHandler.MSG_PAIR_QR_CODE, bundle);
        mHandler.sendMessage(message);
    }

    /** Disables pairing */
    public void disablePairing() {
        mHandler.sendEmptyMessage(AdbDebuggingHandler.MSG_PAIRING_CANCEL);
    }

    /** Status enabled/disabled check */
    public boolean isAdbWifiEnabled() {
        return mAdbWifiEnabled;
    }

    /** Notify that they key files were updated so the AdbKeyManager reloads the keys. */
    public void notifyKeyFilesUpdated() {
        mHandler.sendEmptyMessage(AdbDebuggingHandler.MESSAGE_KEY_FILES_UPDATED);
    }

    /** Sends a message to the handler to persist the keystore. */
    private void sendPersistKeyStoreMessage() {
        Message msg = mHandler.obtainMessage(AdbDebuggingHandler.MESSAGE_ADB_PERSIST_KEYSTORE);
        mHandler.sendMessage(msg);
    }

    /** Dump the USB debugging state. */
    public void dump(DualDumpOutputStream dump, String idName, long id) {
        long token = dump.start(idName, id);

        dump.write(
                "connected_to_adb",
                AdbDebuggingManagerProto.CONNECTED_TO_ADB,
                mHandler.mThread.isConnected());
        writeStringIfNotNull(
                dump,
                "last_key_received",
                AdbDebuggingManagerProto.LAST_KEY_RECEVIED,
                mFingerprints);

        try {
            File userKeys = new File("/data/misc/adb/adb_keys");
            if (userKeys.exists()) {
                dump.write(
                        "user_keys",
                        AdbDebuggingManagerProto.USER_KEYS,
                        FileUtils.readTextFile(userKeys, 0, null));
            } else {
                Slog.i(TAG, "No user keys on this device");
            }
        } catch (IOException e) {
            Slog.i(TAG, "Cannot read user keys", e);
        }

        try {
            dump.write(
                    "system_keys",
                    AdbDebuggingManagerProto.SYSTEM_KEYS,
                    FileUtils.readTextFile(new File("/adb_keys"), 0, null));
        } catch (IOException e) {
            Slog.i(TAG, "Cannot read system keys", e);
        }

        try {
            dump.write(
                    "keystore",
                    AdbDebuggingManagerProto.KEYSTORE,
                    FileUtils.readTextFile(mTempKeysFile, 0, null));
        } catch (IOException e) {
            Slog.i(TAG, "Cannot read keystore: ", e);
        }

        dump.end(token);
    }

    /**
     * A Guava-like interface for getting the current system time.
     *
     * <p>This allows us to swap a fake ticker in for testing to reduce "Thread.sleep()" calls and
     * test for exact expected times instead of random ones.
     */
    @VisibleForTesting
    interface Ticker {
        long currentTimeMillis();
    }
}
