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

package com.android.server.serial;

import static android.hardware.serial.SerialPort.INVALID_ID;
import static android.hardware.serial.SerialPort.OPEN_FLAG_DATA_SYNC;
import static android.hardware.serial.SerialPort.OPEN_FLAG_NONBLOCK;
import static android.hardware.serial.SerialPort.OPEN_FLAG_READ_ONLY;
import static android.hardware.serial.SerialPort.OPEN_FLAG_READ_WRITE;
import static android.hardware.serial.SerialPort.OPEN_FLAG_SYNC;
import static android.hardware.serial.SerialPort.OPEN_FLAG_WRITE_ONLY;
import static android.hardware.serial.flags.Flags.enableWiredSerialApi;
import static android.hardware.serial.flags.Flags.persistentAccess;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.UserIdInt;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.hardware.serial.ISerialManager;
import android.hardware.serial.ISerialPortListener;
import android.hardware.serial.ISerialPortResponseCallback;
import android.hardware.serial.ISerialPortResponseCallback.ErrorCode;
import android.hardware.serial.SerialPortInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ServiceManager;
import android.os.ShellCallback;
import android.os.Trace;
import android.os.UserHandle;
import android.system.OsConstants;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.BackgroundThread;
import com.android.internal.util.DumpUtils;
import com.android.server.LocalServices;
import com.android.server.SystemService;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * System service for managing wired serial ports.
 *
 * <p>This service acts as a facade for a native service that handles device enumeration and file
 * operations. This service manages user consent and proxies requests to the native service.
 */
public class SerialManagerService extends ISerialManager.Stub implements
        SerialDeviceFilter.FilteredSerialPortListener {
    private static final String TAG = "SerialManagerService";

    private static final int OPEN_MODE_BITS =
            OPEN_FLAG_READ_ONLY | OPEN_FLAG_WRITE_ONLY | OPEN_FLAG_READ_WRITE;
    private static final int FORBIDDEN_FLAG_BITS =
            ~(OPEN_FLAG_READ_ONLY | OPEN_FLAG_WRITE_ONLY | OPEN_FLAG_READ_WRITE | OPEN_FLAG_NONBLOCK
                    | OPEN_FLAG_DATA_SYNC | OPEN_FLAG_SYNC);

    // Name of the Native Serial Service that handles device enumeration and file operations.
    private static final String NATIVE_SERIAL_SERVICE_NAME = "native_serial";

    private static final String DEV_DIR_PREFIX = "/dev/";

    private final Context mContext;

    private final boolean mNativeServiceSupported;

    private final String[] mPortsInConfig;

    private final String[] mBlockedUsbIdsInConfig;

    private final String mDialogComponent;

    private final Supplier<android.hardware.serialservice.ISerialManager> mNativeServiceSupplier;

    private final SerialUserAccessManagerFactory mAccessManagerFactory;

    private final PortAccessSerializerInterface mPortAccessSerializer;

    private final Object mLock = new Object();

    // Binder proxy for the native serial service.
    @GuardedBy("mLock")
    private android.hardware.serialservice.ISerialManager mNativeService;

    // Manages the list of available serial ports. Lazily initialized in connectToNativeService().
    @GuardedBy("mLock")
    private SerialDeviceFilter mSerialDeviceFilter;

    @GuardedBy("mLock")
    private final SparseArray<SerialUserAccessManagerInterface> mAccessManagerPerUser =
            new SparseArray<>();

    @GuardedBy("mLock")
    private final RemoteCallbackList<ISerialPortListener> mListeners = new RemoteCallbackList<>();

    @GuardedBy("mLock")
    private boolean mIsConnectedToNativeService;

    private SerialManagerService(Context context) {
        this(context, context.getResources().getStringArray(R.array.config_serialPorts),
                context.getResources().getStringArray(R.array.config_blockedUsbSerialIds),
                context.getResources().getString(R.string.config_portAccessDialogComponent),
                () -> android.hardware.serialservice.ISerialManager.Stub.asInterface(
                        ServiceManager.waitForService(NATIVE_SERIAL_SERVICE_NAME)),
                SerialUserAccessManager::new,
                new PortAccessSerializer(BackgroundThread.getExecutor()),
                context.getResources().getBoolean(
                        com.android.internal.R.bool.config_supportNativeSerialService));
    }

    @VisibleForTesting
    SerialManagerService(Context context, String[] portsInConfig, String[] blockedUsbIdsInConfig,
            String dialogComponent,
            Supplier<android.hardware.serialservice.ISerialManager> nativeServiceSupplier,
            SerialUserAccessManagerFactory accessManagerFactory,
            PortAccessSerializerInterface portAccessSerializer,
            boolean nativeServiceSupported) {
        mContext = context;
        mDialogComponent = dialogComponent;
        mPortsInConfig = stripDevPrefix(portsInConfig);
        mBlockedUsbIdsInConfig = blockedUsbIdsInConfig;
        mNativeServiceSupplier = nativeServiceSupplier;
        mAccessManagerFactory = accessManagerFactory;
        mPortAccessSerializer = portAccessSerializer;
        mNativeServiceSupported = nativeServiceSupported;
    }

    private static String[] stripDevPrefix(String[] portPaths) {
        if (portPaths.length == 0) {
            return portPaths;
        }

        ArrayList<String> portNames = new ArrayList<>();
        for (int i = 0; i < portPaths.length; ++i) {
            String portPath = portPaths[i];
            if (portPath.startsWith(DEV_DIR_PREFIX)) {
                portNames.add(portPath.substring(DEV_DIR_PREFIX.length()));
            } else {
                Slog.w(TAG, "Skipping port path not under /dev: " + portPath);
            }
        }
        return portNames.toArray(new String[0]);
    }

    @Override
    public List<SerialPortInfo> getSerialPorts() {
        synchronized (mLock) {
            if (!connectToNativeService()) {
                return Collections.emptyList();
            }
            traceBegin("getSerialPorts", 0);
            final List<SerialPortInfo> ports = Collections.unmodifiableList(
                    new ArrayList<>(mSerialDeviceFilter.getAvailablePorts().values()));
            traceEnd(0);
            return ports;
        }
    }

    @Override
    @RequiresPermission(Manifest.permission.SERIAL_PORT)
    public String[] getSerialPortsInConfig() {
        mContext.enforceCallingPermission(
                Manifest.permission.SERIAL_PORT,
                "The caller doesn't have SERIAL_PORT permission.");
        return mPortsInConfig;
    }

    @Override
    public void registerSerialPortListener(@NonNull ISerialPortListener listener) {
        synchronized (mLock) {
            connectToNativeService();
            mListeners.register(listener);
        }
    }

    @Override
    public void unregisterSerialPortListener(@NonNull ISerialPortListener listener) {
        synchronized (mLock) {
            mListeners.unregister(listener);
        }
    }

    private void onUserUnlocking(int userId) {
        synchronized (mLock) {
            final SerialUserAccessManagerInterface accessManager = createAccessManager(userId);
            mAccessManagerPerUser.put(userId, accessManager);
        }
    }

    private void onUserStopping(int userId) {
        final SerialUserAccessManagerInterface accessManager;
        synchronized (mLock) {
            accessManager = mAccessManagerPerUser.removeReturnOld(userId);
        }
        accessManager.onUserStopping();
    }

    @Override
    @RequiresPermission(Manifest.permission.MANAGE_SERIAL_PORTS)
    public void grantSerialPortAccess(
            @NonNull String serialPort, int uid, boolean persistent, @Nullable IBinder token) {
        mContext.enforceCallingPermission(
                Manifest.permission.MANAGE_SERIAL_PORTS,
                "The caller doesn't have MANAGE_SERIAL_PORTS permission.");
        synchronized (mLock) {
            if (!connectToNativeService()) {
                Slog.w(TAG, "Not able to connect to native service. Not granting port access to "
                        + serialPort);
                return;
            }
            if (!mSerialDeviceFilter.getAvailablePorts().containsKey(serialPort)) {
                Slog.w(TAG, "Not granting access to missing port " + serialPort);
                return;
            }

            traceBegin("grantSerialPortAccess", 0);
            final @UserIdInt int userId = UserHandle.getUserId(uid);
            final SerialUserAccessManagerInterface accessManager = getOrCreateAccessManager(userId);
            accessManager.grantAccess(serialPort, uid, persistent, token);
            traceEnd(0);
        }
    }

    @Override
    @RequiresPermission(Manifest.permission.MANAGE_SERIAL_PORTS)
    public void revokeSerialPortAccess(
            @NonNull String serialPort, int uid, boolean persistent, @Nullable IBinder token) {
        mContext.enforceCallingPermission(
                Manifest.permission.MANAGE_SERIAL_PORTS,
                "The caller doesn't have MANAGE_SERIAL_PORTS permission.");
        synchronized (mLock) {
            traceBegin("revokeSerialPortAccess", 0);
            // We always allow to revoke access to a port, even if it is unplugged.
            final @UserIdInt int userId = UserHandle.getUserId(uid);
            final SerialUserAccessManagerInterface accessManager = getOrCreateAccessManager(userId);
            accessManager.revokeAccess(serialPort, uid, persistent, token);
            traceEnd(0);
        }
    }

    @Override
    public void requestOpen(@NonNull String portName, int flags, boolean exclusive,
            @NonNull String packageName, @NonNull ISerialPortResponseCallback callback) {
        final int callingPid = Binder.getCallingPid();
        final int callingUid = Binder.getCallingUid();
        final @UserIdInt int userId = UserHandle.getUserId(callingUid);
        final PackageManagerInternal pm = LocalServices.getService(PackageManagerInternal.class);
        if (!pm.isSameApp(packageName, callingUid, userId)) {
            // We can never allow an app to impersonate any other apps.
            Slog.w(TAG, "Package " + packageName + " doesn't match the calling UID " + callingUid);
            deliverErrorToCallback(callback, ErrorCode.ERROR_ACCESS_DENIED, portName);
            return;
        }

        synchronized (mLock) {
            if (!connectToNativeService()) {
                deliverErrorToCallback(callback, ErrorCode.ERROR_PORT_NOT_FOUND, portName);
                return;
            }
            traceBegin("obtainPortForOpen", 0);
            SerialPortInfo port = mSerialDeviceFilter.getAvailablePorts().get(portName);
            if (port == null && hasSerialPortPermission(mContext, callingPid, callingUid)) {
                // Allow privileged apps to open ports listed in the config, even if they are not
                // currently available (e.g. built-in UARTs that are normally hidden for security).
                // This is for legacy use cases.
                for (int i = 0; i < mPortsInConfig.length; ++i) {
                    if (portName.equals(mPortsInConfig[i])) {
                        port = new SerialPortInfo(portName, INVALID_ID, INVALID_ID);
                        break;
                    }
                }
            }
            traceEnd(0);
            if (port == null) {
                deliverErrorToCallback(callback, ErrorCode.ERROR_PORT_NOT_FOUND, portName);
                return;
            }
            traceBegin("requestAccessForOpen", 0);
            final SerialPortInfo portToOpen = port;
            final SerialUserAccessManagerInterface accessManager = getOrCreateAccessManager(userId);
            accessManager.requestAccess(portName, callingPid, callingUid, packageName,
                    (resultPort, pid, uid, granted) -> {
                        if (!granted) {
                            deliverErrorToCallback(callback, ErrorCode.ERROR_ACCESS_DENIED,
                                    "User denied " + packageName + " access to " + portName);
                            return;
                        }
                        nativeOpen(portToOpen, toOsConstants(flags), exclusive, callback);
                    });
            traceEnd(0);
        }
    }

    @GuardedBy("mLock")
    private SerialUserAccessManagerInterface getOrCreateAccessManager(int userId) {
        SerialUserAccessManagerInterface accessManager = mAccessManagerPerUser.get(userId);
        if (accessManager != null) {
            return accessManager;
        }
        return createAccessManager(userId);
    }

    @GuardedBy("mLock")
    private SerialUserAccessManagerInterface createAccessManager(int userId) {
        SerialUserAccessManagerInterface accessManager = mAccessManagerPerUser.get(userId);
        if (accessManager != null) {
            Slog.wtf(TAG, "There is an access manager for user " + userId);
            return accessManager;
        }
        accessManager = mAccessManagerFactory.create(
                mContext, mPortsInConfig, mDialogComponent, mPortAccessSerializer, userId);
        mAccessManagerPerUser.put(userId, accessManager);
        return accessManager;
    }

    /**
     * Opens the serial port by calling the native serial service.
     */
    private void nativeOpen(SerialPortInfo port, int flags, boolean exclusive,
            @NonNull ISerialPortResponseCallback callback) {
        traceBegin("nativeOpenPort", 0);
        try (ParcelFileDescriptor pfd = mNativeService.requestOpen(port.getName(), flags,
                exclusive)) {
            deliverResultToCallback(callback, port, pfd);
        } catch (RemoteException | RuntimeException e) {
            deliverErrorToCallback(callback, ErrorCode.ERROR_OPENING_PORT,
                    "Error opening serial port " + port.getName() + ": " + e.getMessage());
        } catch (IOException e) {
            Slog.w(TAG, "Error closing the file descriptor", e);
        } finally {
            traceEnd(0);
        }
    }

    private void deliverResultToCallback(@NonNull ISerialPortResponseCallback callback,
            SerialPortInfo port, ParcelFileDescriptor pfd) {
        try {
            callback.onResult(port, pfd);
        } catch (RemoteException | RuntimeException e) {
            Slog.e(TAG, "Error sending result to callback", e);
        }
    }

    private void deliverErrorToCallback(@NonNull ISerialPortResponseCallback callback,
            @ErrorCode int errorCode, String message) {
        try {
            callback.onError(errorCode, message);
        } catch (RemoteException | RuntimeException e) {
            Slog.e(TAG, "Error sending error to callback", e);
        }
    }

    /**
     * Converts the public API flags from {@link android.hardware.serial.SerialPort} to the
     * corresponding {@link OsConstants} flags used for the {@code open(2)} syscall.
     *
     * @param flags A combination of {@code SerialPort.OPEN_FLAG_*} constants.
     * @return The corresponding {@code OsConstants.O_*} flags.
     * @throws IllegalArgumentException if the flags are invalid.
     */
    private static int toOsConstants(int flags) {
        // Always open the device with O_NOCTTY flag, so that it will not become the process's
        // controlling terminal.
        int osFlags = OsConstants.O_NOCTTY;
        switch (flags & OPEN_MODE_BITS) {
            case OPEN_FLAG_READ_ONLY -> osFlags |= OsConstants.O_RDONLY;
            case OPEN_FLAG_WRITE_ONLY -> osFlags |= OsConstants.O_WRONLY;
            case OPEN_FLAG_READ_WRITE -> osFlags |= OsConstants.O_RDWR;
            default -> throw new IllegalArgumentException(
                    "Flags value " + flags + " must contain only one open mode flag");
        }
        if ((flags & OPEN_FLAG_NONBLOCK) != 0) {
            osFlags |= OsConstants.O_NONBLOCK;
        }
        if ((flags & OPEN_FLAG_DATA_SYNC) != 0) {
            osFlags |= OsConstants.O_DSYNC;
        }
        if ((flags & OPEN_FLAG_SYNC) != 0) {
            osFlags |= OsConstants.O_SYNC;
        }
        if ((flags & FORBIDDEN_FLAG_BITS) != 0) {
            throw new IllegalArgumentException(
                    "Flags value " + flags + " is not a combination of FLAG_* constants");
        }
        return osFlags;
    }

    /**
     * Connects to the native serial service if not already connected.
     *
     * <p>This method retrieves the native service binder, registers a listener for port
     * connect/disconnect events, and populates the initial list of serial ports.
     *
     * @return {@code true} if the service is connected to the native service and the serial port
     * list is successfully populated/updated, {@code false} otherwise.
     */
    @GuardedBy("mLock")
    private boolean connectToNativeService() {
        if (mIsConnectedToNativeService) {
            return true;
        }
        if (!mNativeServiceSupported) {
            return false;
        }
        traceBegin("obtainNativeService", 0);
        mNativeService = mNativeServiceSupplier.get();
        traceEnd(0);
        if (mNativeService == null) {
            Slog.e(TAG, "Native Serial Service not found");
            return false;
        }
        traceBegin("createDeviceFilter", 0);
        try {
            mSerialDeviceFilter = new SerialDeviceFilter(mContext, mBlockedUsbIdsInConfig,
                    mNativeService, mLock);
        } catch (RemoteException e) {
            Slog.e(TAG, "Error communicating with native service", e);
            return false;
        } finally {
            traceEnd(0);
        }
        mSerialDeviceFilter.setFilteredSerialPortListener(this);
        mIsConnectedToNativeService = true;
        return true;
    }

    @Override
    @GuardedBy("mLock")
    public void onSerialPortAdded(SerialPortInfo port) {
        Slog.d(TAG, "Added serial device " + port.getName());
        int n = mListeners.beginBroadcast();
        for (int i = 0; i < n; i++) {
            try {
                mListeners.getBroadcastItem(i).onSerialPortConnected(port);
            } catch (RemoteException e) {
                Slog.e(TAG, "Error notifying listener", e);
            }
        }
        mListeners.finishBroadcast();
    }

    @Override
    @GuardedBy("mLock")
    public void onSerialPortRemoved(SerialPortInfo port) {
        for (int i = mAccessManagerPerUser.size() - 1; i >= 0; --i) {
            mAccessManagerPerUser.valueAt(i).onPortRemoved(port.getName());
        }
        Slog.d(TAG, "Removed serial device " + port.getName());
        int n = mListeners.beginBroadcast();
        for (int i = 0; i < n; i++) {
            try {
                mListeners.getBroadcastItem(i).onSerialPortDisconnected(port);
            } catch (RemoteException e) {
                Slog.e(TAG, "Error notifying listener", e);
            }
        }
        mListeners.finishBroadcast();
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (!DumpUtils.checkDumpPermission(mContext, TAG, pw)) {
            return;
        }
        synchronized (mLock) {
            if (mBlockedUsbIdsInConfig.length > 0) {
                pw.println("Blocked USB IDs: " + Arrays.deepToString(mBlockedUsbIdsInConfig));
            }
            if (!mIsConnectedToNativeService) {
                pw.println("Not connected to native service");
                return;
            }
            var ports = mSerialDeviceFilter.getAvailablePorts();
            pw.println("Available ports (" + ports.size() + "):");
            for (SerialPortInfo port : ports.values()) {
                pw.println("Port " + port.getName() + ":");
                pw.println("   Vendor ID: " + Integer.toHexString(port.getVendorId()));
                pw.println("   Product ID: " + Integer.toHexString(port.getProductId()));
            }
        }
    }

    /**
     * Shell support (for CTS tests).
     */
    @Override
    @RequiresPermission(Manifest.permission.MANAGE_SERIAL_PORTS)
    public void onShellCommand(FileDescriptor in, FileDescriptor out, FileDescriptor err,
            String[] args, ShellCallback callback, ResultReceiver resultReceiver) {
        mContext.enforceCallingPermission(Manifest.permission.MANAGE_SERIAL_PORTS,
                "The caller doesn't have MANAGE_SERIAL_PORTS permission.");
        synchronized (mLock) {
            // Lazily connect to native service to initialize mSerialDeviceFilter.
            if (!connectToNativeService()) {
                PrintWriter errPw = new PrintWriter(new FileOutputStream(err));
                errPw.println("Error: Could not connect to native serial service.");
                errPw.flush();
                resultReceiver.send(-1, null);
                return;
            }
            var shellCommand = new SerialShellCommand(this, mSerialDeviceFilter);
            shellCommand.exec(this, in, out, err, args, callback, resultReceiver);
        }
    }

    /**
     * Clears all user-granted access permissions. This method is used in `SerialShellCommand` to
     * implement the "clear-user-access" shell command.
     */
    void clearUserAccess() {
        synchronized (mLock) {
            if (persistentAccess()) {
                for (int i = 0; i < mAccessManagerPerUser.size(); ++i) {
                    mAccessManagerPerUser.valueAt(i).clearUserAccess(
                            mAccessManagerPerUser.keyAt(i));
                }
            } else {
                mAccessManagerPerUser.clear();
            }
        }
    }

    static boolean hasSerialPortPermission(Context context, int pid, int uid) {
        return context.checkPermission(Manifest.permission.SERIAL_PORT, pid, uid)
                == PackageManager.PERMISSION_GRANTED;
    }

    static void traceBegin(String methodName, int cookie) {
        Trace.asyncTraceForTrackBegin(Trace.TRACE_TAG_SYSTEM_SERVER, TAG, methodName, cookie);
    }

    static void traceEnd(int cookie) {
        Trace.asyncTraceForTrackEnd(Trace.TRACE_TAG_SYSTEM_SERVER, TAG, cookie);
    }

    interface SerialUserAccessManagerFactory {
        SerialUserAccessManagerInterface create(Context context, String[] portsInConfig,
                String dialogComponent, PortAccessSerializerInterface serializer, int userId);
    }

    public static class Lifecycle extends SystemService {
        private final Context mContext;
        private SerialManagerService mService;

        public Lifecycle(@NonNull Context context) {
            super(context);
            mContext = context;
        }

        @Override
        public void onStart() {
            if (enableWiredSerialApi()) {
                traceBegin("createSerialManager", 0);
                mService = new SerialManagerService(mContext);
                publishBinderService(Context.SERIAL_SERVICE, mService);
                traceEnd(0);
            }
        }

        @Override
        public void onUserUnlocking(@NonNull TargetUser user) {
            if (!persistentAccess()) {
                return;
            }
            mService.onUserUnlocking(user.getUserIdentifier());
        }

        @Override
        public void onUserStopping(@NonNull TargetUser user) {
            if (!persistentAccess()) {
                return;
            }
            mService.onUserStopping(user.getUserIdentifier());
        }
    }
}
