/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.hardware.SensorPrivacyManager.Sensors;
import android.hardware.SensorPrivacyManagerInternal;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Environment;
import android.os.Process;
import android.os.UserHandle;
import android.service.usb.UsbAccessoryPermissionProto;
import android.service.usb.UsbDevicePermissionProto;
import android.service.usb.UsbUserPermissionsManagerProto;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.AtomicFile;
import android.util.EventLog;
import android.util.Slog;
import android.util.SparseBooleanArray;
import android.util.Xml;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.content.PackageMonitor;
import com.android.internal.util.XmlUtils;
import com.android.internal.util.dump.DualDumpOutputStream;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;
import com.android.server.LocalServices;
import com.android.server.usb.flags.Flags;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * UsbUserPermissionManager manages usb device or accessory access permissions.
 *
 * @hide
 */
class UsbUserPermissionManager {
    private static final String TAG = UsbUserPermissionManager.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final int SNET_EVENT_LOG_ID = 0x534e4554;

    /** Record class for combining packageName + uid used for tracking permissions. */
    public record PackageAndUid(String packageName, int uid) {}

    // Maps UsbDevice address to set of temporary permissions (for Package+UID).
    // Entries are removed when the device is disconnected.
    @GuardedBy("mLock")
    private final ArrayMap<String, ArraySet<PackageAndUid>> mTemporaryDevicePermissionMap =
            new ArrayMap<>();

    // Maps UsbAccessory to set of temporary permissions (for Package+UID).
    // Entries are removed when the accessory is disconnected.
    @GuardedBy("mLock")
    private final ArrayMap<UsbAccessory, ArraySet<PackageAndUid>> mTemporaryAccessoryPermissionMap =
            new ArrayMap<>();

    // Maps UsbDeviceFingerprint to set of persistent permissions (for Package+UID).
    @GuardedBy("mLock")
    private final ArrayMap<UsbDeviceFingerprint, ArraySet<PackageAndUid>>
            mPersistentDevicePermissionMap = new ArrayMap<>();

    @GuardedBy("mLock")
    /*
     * Temporary mapping of USB device name to list of UIDs with permissions for
     * the device. Each entry lasts until device is disconnected.
     */
    private final ArrayMap<String, SparseBooleanArray> mDevicePermissionMap = new ArrayMap<>();

    @GuardedBy("mLock")
    /*
     * Temporary mapping of UsbAccessory to list of UIDs with permissions for the
     * accessory. Each entry lasts until accessory is disconnected.
     */
    private final ArrayMap<UsbAccessory, SparseBooleanArray> mAccessoryPermissionMap =
            new ArrayMap<>();

    private final Context mContext;
    private final UserHandle mUser;
    private final UsbUserSettingsManager mUsbUserSettingsManager;
    private final DevicePolicyManager mDevicePolicyManager;
    private final boolean mDisablePermissionDialogs;

    private final @NonNull AtomicFile mPermissionsFile;

    private final Object mLock = new Object();

    private class LocalPackageMonitor extends PackageMonitor {
        @Override
        public void onPackageRemoved(String packageName, int uid) {
            removePackagePermissions(packageName, uid);
        }
    }

    private LocalPackageMonitor mPackageMonitor = new LocalPackageMonitor();

    // XML serialization constant strings.
    private static final String XML_ROOT_NAME = "permissions";
    private static final String PERMISSION_TAG_NAME = "permission";
    private static final String PACKAGE_NAME_ATTR = "packageName";
    private static final String UID_ATTR = "uid";

    /**
     * If a async task to persist the mPersistentDevicePermissionMap is currently scheduled.
     */
    @GuardedBy("mLock")
    private boolean mIsCopyPermissionsScheduled;
    private final SensorPrivacyManagerInternal mSensorPrivacyMgrInternal;

    UsbUserPermissionManager(
            @NonNull Context context,
            @NonNull UsbUserSettingsManager usbUserSettingsManager,
            String storageDirPath) {
        mContext = context;
        mUser = context.getUser();
        mUsbUserSettingsManager = usbUserSettingsManager;
        mDevicePolicyManager = LocalServices.getService(DevicePolicyManager.class);
        mSensorPrivacyMgrInternal = LocalServices.getService(SensorPrivacyManagerInternal.class);
        mDisablePermissionDialogs = context.getResources().getBoolean(
                com.android.internal.R.bool.config_disableUsbPermissionDialogs);

        if (android.hardware.usb.flags.Flags.enablePersistentUsbDevicePermissions()) {
            mPackageMonitor.register(context, null, UserHandle.ALL, true);
        }

        File parentDir;
        if (storageDirPath != null) {
            parentDir = new File(storageDirPath);
        } else {
            parentDir = Environment.getUserSystemDirectory(mUser.getIdentifier());
        }

        mPermissionsFile =
                new AtomicFile(new File(parentDir, "usb_permissions.xml"), "usb-permissions");
        synchronized (mLock) {
            readPermissionsLocked();
        }
    }

    void unregisterReceivers() {
        if (android.hardware.usb.flags.Flags.enablePersistentUsbDevicePermissions()) {
            mPackageMonitor.unregister();
        }
    }

    /**
     * Removes access permissions of all packages for the USB accessory.
     *
     * @param accessory to remove permissions for
     */
    void removeAccessoryPermissions(@NonNull UsbAccessory accessory) {
        synchronized (mLock) {
            if (android.hardware.usb.flags.Flags.enablePersistentUsbDevicePermissions()) {
                mTemporaryAccessoryPermissionMap.remove(accessory);
            } else {
                mAccessoryPermissionMap.remove(accessory);
            }
        }
    }

    /**
     * Removes access permissions of all packages for the USB device.
     *
     * @param device to remove permissions for
     */
    void removeDevicePermissions(@NonNull UsbDevice device) {
        synchronized (mLock) {
            if (android.hardware.usb.flags.Flags.enablePersistentUsbDevicePermissions()) {
                mTemporaryDevicePermissionMap.remove(device.getDeviceName());
            } else {
                mDevicePermissionMap.remove(device.getDeviceName());
            }
        }
    }

    void removePackagePermissions(String packageName, int uid) {
        synchronized (mLock) {
            // Remove this package + uid pair from all permission maps
            PackageAndUid identifier = new PackageAndUid(packageName, uid);

            mTemporaryDevicePermissionMap.forEach(
                    (k, v) -> {
                        v.remove(identifier);
                    });
            mTemporaryAccessoryPermissionMap.forEach(
                    (k, v) -> {
                        v.remove(identifier);
                    });
            mPersistentDevicePermissionMap.forEach(
                    (k, v) -> {
                        v.remove(identifier);
                    });
        }
    }

    // Check if a package/uid pair is valid by getting all packages for that uid and
    // making sure the given package is part of that list.
    private boolean isValidPackageUidPair(PackageAndUid packageAndUid) {
        String[] packages = mContext.getPackageManager().getPackagesForUid(packageAndUid.uid);
        return packages != null && Arrays.asList(packages).contains(packageAndUid.packageName);
    }

    // Send broadcast intent for permission changes on a device or accessory.
    //
    // The broadcast is informational to make sure stale data about permissions
    // isn't used by system applications and will only be sent to registered
    // listeners.
    private void permissionChangedBroadcastIntent(UsbDevice device, UsbAccessory accessory) {
        Intent intent = new Intent(UsbManager.ACTION_USB_PERMISSION_CHANGED);
        if (device != null) {
            intent.putExtra(UsbManager.EXTRA_DEVICE, device);
        } else {
            intent.putExtra(UsbManager.EXTRA_ACCESSORY, accessory);
        }

        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        mContext.sendBroadcastAsUser(intent, UserHandle.ALL, Manifest.permission.MANAGE_USB);
    }

    @VisibleForTesting
    void grantDevicePermissionInternal(
            @NonNull UsbDevice device,
            UsbDeviceFingerprint fingerprint,
            String packageName,
            int uid,
            boolean isPersistent) {
        boolean wasAdded = false;

        synchronized (mLock) {
            PackageAndUid identifier = new PackageAndUid(packageName, uid);
            String deviceAddr = device.getDeviceName();
            boolean needsWrite = false;

            if (!isValidPackageUidPair(identifier)) {
                throw new IllegalArgumentException(
                        "Provided packageName and uid pair are not valid");
            }

            ArraySet<PackageAndUid> permissions;

            // Any connected device should have a fingerprint. If we can't find
            // the fingerprint, we can't persist the device permission so just
            // fall back to using a temporary permission.
            //
            // We use device address for temporary permissions because we will
            // remove it via usbDeviceRemoved if necessary.
            if (isPersistent && fingerprint != null) {
                needsWrite = true;
                permissions = mPersistentDevicePermissionMap.get(fingerprint);
                if (permissions == null) {
                    permissions = new ArraySet<>();
                    mPersistentDevicePermissionMap.put(fingerprint, permissions);
                }
            } else {
                permissions = mTemporaryDevicePermissionMap.get(deviceAddr);
                if (permissions == null) {
                    permissions = new ArraySet<>();
                    mTemporaryDevicePermissionMap.put(deviceAddr, permissions);
                }
            }

            // Only send broadcast for it didn't already exist
            wasAdded = permissions.add(identifier);

            if (wasAdded && needsWrite) {
                scheduleWritePermissionsLocked();
            }
        }

        if (wasAdded) {
            permissionChangedBroadcastIntent(device, null);
        }
    }

    /**
     * Grants permission for USB device without showing system dialog for package with uid.
     *
     * @param device to grant permission for
     * @param fingerprint to use if we need to persist this permission
     * @param packageName to grant permission for
     * @param uid to grant permission for
     * @param isPersistent to set whether permission is temporary or persisted
     */
    void grantDevicePermission(@NonNull UsbDevice device, UsbDeviceFingerprint fingerprint,
            String packageName, int uid, boolean isPersistent) {
        if (android.hardware.usb.flags.Flags.enablePersistentUsbDevicePermissions()) {
            grantDevicePermissionInternal(device, fingerprint, packageName, uid, isPersistent);
        } else {
            synchronized (mLock) {
                String deviceName = device.getDeviceName();
                SparseBooleanArray uidList = mDevicePermissionMap.get(deviceName);
                if (uidList == null) {
                    uidList = new SparseBooleanArray(1);
                    mDevicePermissionMap.put(deviceName, uidList);
                }
                uidList.put(uid, true);
            }
        }
    }

    @VisibleForTesting
    void grantAccessoryPermissionInternal(
            @NonNull UsbAccessory accessory, String packageName, int uid) {
        boolean wasAdded = false;

        synchronized (mLock) {
            PackageAndUid identifier = new PackageAndUid(packageName, uid);
            if (!isValidPackageUidPair(identifier)) {
                throw new IllegalArgumentException(
                        "Provided packageName and uid pair are not valid");
            }

            ArraySet<PackageAndUid> permissions = mTemporaryAccessoryPermissionMap.get(accessory);
            if (permissions == null) {
                permissions = new ArraySet<>();
                mTemporaryAccessoryPermissionMap.put(accessory, permissions);
            }

            wasAdded = permissions.add(identifier);
        }

        if (wasAdded) {
            permissionChangedBroadcastIntent(null, accessory);
        }
    }

    /**
     * Grants permission for USB accessory without showing system dialog for package with uid.
     *
     * @param accessory to grant permission for
     * @param packageName to grant permission for
     * @param uid to grant permission for
     */
    void grantAccessoryPermission(@NonNull UsbAccessory accessory, String packageName, int uid) {
        if (android.hardware.usb.flags.Flags.enablePersistentUsbDevicePermissions()) {
            grantAccessoryPermissionInternal(accessory, packageName, uid);
        } else {
            synchronized (mLock) {
                SparseBooleanArray uidList = mAccessoryPermissionMap.get(accessory);
                if (uidList == null) {
                    uidList = new SparseBooleanArray(1);
                    mAccessoryPermissionMap.put(accessory, uidList);
                }
                uidList.put(uid, true);
            }
        }
    }

    @VisibleForTesting
    boolean hasPermissionInternal(
            UsbDevice device, UsbDeviceFingerprint fingerprint, String packageName, int uid) {
        synchronized (mLock) {
            if (uid == Process.SYSTEM_UID || mDisablePermissionDialogs) {
                return true;
            }

            PackageAndUid identifier = new PackageAndUid(packageName, uid);
            String deviceAddr = device.getDeviceName();

            // Check for persistent permissions first and fall back to
            // temporary permissions if not found.
            if (fingerprint != null) {
                ArraySet<PackageAndUid> permissionsForDevice =
                        mPersistentDevicePermissionMap.get(fingerprint);
                if (permissionsForDevice != null && permissionsForDevice.contains(identifier)) {
                    return true;
                }
            }

            ArraySet<PackageAndUid> tmpPermissionsForDevice =
                    mTemporaryDevicePermissionMap.get(deviceAddr);
            if (tmpPermissionsForDevice != null) {
                return tmpPermissionsForDevice.contains(identifier);
            }

            return false;
        }
    }

    /**
     * Returns true if package with uid has permission to access the device.
     *
     * @param device to check permission for
     * @param fingerprint to use for checking persistent permissions
     * @param pid to check permission for
     * @param uid to check permission for
     * @return {@code true} if package with uid has permission
     */
    @SuppressLint("AndroidFrameworkRequiresPermission")
    boolean hasPermission(@NonNull UsbDevice device, UsbDeviceFingerprint fingerprint,
            @NonNull String packageName, int pid, int uid) {
        if (device.getHasVideoCapture()) {
            boolean isCameraPrivacyEnabled = mSensorPrivacyMgrInternal.isSensorPrivacyEnabled(
                    UserHandle.getUserId(uid), Sensors.CAMERA);

            boolean isCameraDisabled = false;
            if (Flags.enableCameraPolicyCheck()) {
                try {
                    isCameraDisabled = mDevicePolicyManager.getCameraDisabled(null);
                } catch (Exception e) {
                    Slog.e(TAG, "Failed to check camera disabled policy", e);
                }
            }

            if (DEBUG) {
                Slog.d(TAG, "isCameraPrivacyEnabled: " + isCameraPrivacyEnabled);
                Slog.d(TAG, "isCameraDisabled: " + isCameraDisabled);
            }

            if (isCameraPrivacyEnabled || isCameraDisabled
                    || !isCameraPermissionGranted(packageName, pid, uid)) {
                return false;
            }
        }
        // Only check for microphone privacy and not RECORD_AUDIO permission, because access to usb
        // camera device with audio recording capabilities may still be granted with a warning
        if (device.getHasAudioCapture() && mSensorPrivacyMgrInternal.isSensorPrivacyEnabled(
                UserHandle.getUserId(uid), Sensors.MICROPHONE)) {
            if (DEBUG) {
                Slog.d(TAG,
                        "Access to device with audio recording capabilities denied because "
                                + "microphone privacy is enabled.");
            }
            return false;
        }

        if (android.hardware.usb.flags.Flags.enablePersistentUsbDevicePermissions()) {
            return hasPermissionInternal(device, fingerprint, packageName, uid);
        } else {
            synchronized (mLock) {
                if (uid == Process.SYSTEM_UID || mDisablePermissionDialogs) {
                    return true;
                }
                SparseBooleanArray uidList = mDevicePermissionMap.get(device.getDeviceName());
                if (uidList == null) {
                    return false;
                }
                return uidList.get(uid);
            }
        }
    }

    @VisibleForTesting
    boolean hasPermissionInternal(UsbAccessory accessory, String packageName, int pid, int uid) {
        synchronized (mLock) {
            if (uid == Process.SYSTEM_UID
                    || mDisablePermissionDialogs
                    || mContext.checkPermission(android.Manifest.permission.MANAGE_USB, pid, uid)
                            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return true;
            }

            PackageAndUid identifier = new PackageAndUid(packageName, uid);
            ArraySet<PackageAndUid> permissions = mTemporaryAccessoryPermissionMap.get(accessory);
            if (permissions == null) {
                return false;
            }

            return permissions.contains(identifier);
        }
    }

    /**
     * Returns true if caller has permission to access the accessory.
     *
     * @param accessory to check permission for
     * @param packageName to check permission for
     * @param uid to check permission for
     * @return {@code true} if caller has permssion
     */
    boolean hasPermission(@NonNull UsbAccessory accessory, String packageName, int pid, int uid) {
        if (android.hardware.usb.flags.Flags.enablePersistentUsbDevicePermissions()) {
            return hasPermissionInternal(accessory, packageName, pid, uid);
        } else {
            synchronized (mLock) {
                if (uid == Process.SYSTEM_UID
                        || mDisablePermissionDialogs
                        || mContext.checkPermission(
                                android.Manifest.permission.MANAGE_USB, pid, uid)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    return true;
                }
                SparseBooleanArray uidList = mAccessoryPermissionMap.get(accessory);
                if (uidList == null) {
                    return false;
                }
                return uidList.get(uid);
            }
        }
    }

    private void readPermission(
            @NonNull TypedXmlPullParser parser,
            ArrayMap<UsbDeviceFingerprint, ArraySet<PackageAndUid>> allPermissions)
            throws XmlPullParserException, IOException {
        String packageName;
        int uid;
        int outerDepth = parser.getDepth();

        try {
            uid = parser.getAttributeInt(null, UID_ATTR);
        } catch (NumberFormatException e) {
            Slog.e(TAG, "error reading usb permission uid", e);
            XmlUtils.skipCurrentTag(parser);
            return;
        }

        packageName = parser.getAttributeValue(null, PACKAGE_NAME_ATTR);
        if (packageName == null) {
            Slog.e(TAG, "error reading usb permission package");
            XmlUtils.skipCurrentTag(parser);
            return;
        }

        PackageAndUid entry = new PackageAndUid(packageName, uid);
        if (!isValidPackageUidPair(entry)) {
            Slog.e(
                    TAG,
                    TextUtils.formatSimple(
                            "Skipping invalid package + uid pair: %s:%d", packageName, uid));
            XmlUtils.skipCurrentTag(parser);
            return;
        }

        XmlUtils.nextElementWithin(parser, outerDepth);

        UsbDeviceFingerprint fingerprint = null;
        if (UsbDeviceFingerprint.XML_ROOT_NAME.equals(parser.getName())
                && (fingerprint = UsbDeviceFingerprint.read(parser)) != null) {
            int idx = allPermissions.indexOfKey(fingerprint);
            if (idx >= 0) {
                ArraySet<PackageAndUid> permissionsForDevice = allPermissions.valueAt(idx);
                permissionsForDevice.add(entry);
            } else {
                ArraySet<PackageAndUid> permissionsForDevice = new ArraySet<>();
                allPermissions.put(fingerprint, permissionsForDevice);
                permissionsForDevice.add(entry);
            }
        }

        while (parser.getDepth() > outerDepth) {
            parser.nextTag(); // ignore unknown tags
        }
    }

    @VisibleForTesting
    void readPermissions(
            @NonNull TypedXmlPullParser parser,
            ArrayMap<UsbDeviceFingerprint, ArraySet<PackageAndUid>> allPermissions)
            throws XmlPullParserException, IOException {
        XmlUtils.nextElement(parser);

        while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                XmlUtils.nextElement(parser);
                continue;
            }

            String tagName = parser.getName();
            if (PERMISSION_TAG_NAME.equals(tagName)) {
                readPermission(parser, allPermissions);
            } else {
                XmlUtils.nextElement(parser);
            }
        }
    }

    @GuardedBy("mLock")
    private void readPermissionsLocked() {
        mPersistentDevicePermissionMap.clear();

        try (FileInputStream in = mPermissionsFile.openRead()) {
            TypedXmlPullParser parser = Xml.resolvePullParser(in);
            readPermissions(parser, mPersistentDevicePermissionMap);
        } catch (FileNotFoundException e) {
            if (DEBUG) Slog.d(TAG, "usb permissions file not found");
        } catch (Exception e) {
            Slog.e(TAG, "error reading usb permissions file, deleting to start fresh", e);
            mPermissionsFile.delete();
        }
    }

    void writeToSerializer(TypedXmlSerializer serializer, UsbDeviceFingerprint[] devices,
            PackageAndUid[][] packageAndUidsForDevices) throws IOException {
        int numDevices = devices.length;

        serializer.startDocument(null, true);
        serializer.startTag(null, XML_ROOT_NAME);

        for (int i = 0; i < numDevices; i++) {
            int numPermissions = packageAndUidsForDevices[i].length;
            for (int j = 0; j < numPermissions; j++) {
                serializer.startTag(null, PERMISSION_TAG_NAME);
                PackageAndUid entry = packageAndUidsForDevices[i][j];
                serializer.attributeInt(null, UID_ATTR, entry.uid);
                serializer.attribute(null, PACKAGE_NAME_ATTR, entry.packageName);
                devices[i].write(serializer);
                serializer.endTag(null, PERMISSION_TAG_NAME);
            }
        }

        serializer.endTag(null, XML_ROOT_NAME);
        serializer.endDocument();
    }

    @GuardedBy("mLock")
    private void scheduleWritePermissionsLocked() {
        if (mIsCopyPermissionsScheduled) {
            return;
        }
        mIsCopyPermissionsScheduled = true;

        AsyncTask.execute(
                () -> {
                    int numDevices;
                    UsbDeviceFingerprint[] devices;
                    PackageAndUid[][] packageAndUidsForDevices;

                    synchronized (mLock) {
                        // Copy the permission state so we can write outside of lock
                        numDevices = mPersistentDevicePermissionMap.size();
                        devices = new UsbDeviceFingerprint[numDevices];
                        packageAndUidsForDevices = new PackageAndUid[numDevices][];

                        for (int deviceIdx = 0; deviceIdx < numDevices; deviceIdx++) {
                            devices[deviceIdx] =
                                    new UsbDeviceFingerprint(
                                            mPersistentDevicePermissionMap.keyAt(deviceIdx));
                            ArraySet<PackageAndUid> permissions =
                                    mPersistentDevicePermissionMap.valueAt(deviceIdx);

                            int numPermissions = permissions.size();
                            int permissionIdx = 0;
                            packageAndUidsForDevices[deviceIdx] = new PackageAndUid[numPermissions];

                            for (PackageAndUid item : permissions) {
                                packageAndUidsForDevices[deviceIdx][permissionIdx] =
                                        new PackageAndUid(item.packageName, item.uid);
                                permissionIdx++;
                            }
                        }

                        mIsCopyPermissionsScheduled = false;
                    }

                    synchronized (mPermissionsFile) {
                        FileOutputStream out = null;
                        try {
                            out = mPermissionsFile.startWrite();
                            TypedXmlSerializer serializer = Xml.resolveSerializer(out);
                            writeToSerializer(serializer, devices, packageAndUidsForDevices);
                            mPermissionsFile.finishWrite(out);
                        } catch (IOException e) {
                            Slog.e(TAG, "Failed to write permissions", e);
                            if (out != null) {
                                mPermissionsFile.failWrite(out);
                            }
                        }
                    }
                });
    }

    /**
     * Creates UI dialog to request permission for the given package to access the device
     * or accessory.
     *
     * @param device       The USB device attached
     * @param accessory    The USB accessory attached
     * @param canBeDefault Whether the calling pacakge can set as default handler
     *                     of the USB device or accessory
     * @param packageName  The package name of the calling package
     * @param uid          The uid of the calling package
     * @param userContext  The context to start the UI dialog
     * @param pi           PendingIntent for returning result
     */
    void requestPermissionDialog(@Nullable UsbDevice device,
            @Nullable UsbAccessory accessory,
            boolean canBeDefault,
            @NonNull String packageName,
            int uid,
            @NonNull Context userContext,
            @NonNull PendingIntent pi) {
        final long identity = Binder.clearCallingIdentity();
        try {
            Intent intent = new Intent();
            if (device != null) {
                intent.putExtra(UsbManager.EXTRA_DEVICE, device);
            } else {
                intent.putExtra(UsbManager.EXTRA_ACCESSORY, accessory);
            }
            intent.putExtra(Intent.EXTRA_INTENT, pi);
            intent.putExtra(Intent.EXTRA_UID, uid);
            intent.putExtra(UsbManager.EXTRA_CAN_BE_DEFAULT, canBeDefault);
            intent.putExtra(UsbManager.EXTRA_PACKAGE, packageName);
            intent.setComponent(
                    ComponentName.unflattenFromString(userContext.getResources().getString(
                            com.android.internal.R.string.config_usbPermissionActivity)));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            userContext.startActivityAsUser(intent, mUser);
        } catch (ActivityNotFoundException e) {
            Slog.e(TAG, "unable to start UsbPermissionActivity");
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    void dump(@NonNull DualDumpOutputStream dump, String idName, long id) {
        long token = dump.start(idName, id);
        synchronized (mLock) {
            dump.write("user_id", UsbUserPermissionsManagerProto.USER_ID, mUser.getIdentifier());
            int numMappings = mDevicePermissionMap.size();
            for (int mappingsIdx = 0; mappingsIdx < numMappings; mappingsIdx++) {
                String deviceName = mDevicePermissionMap.keyAt(mappingsIdx);
                long devicePermissionToken = dump.start("device_permissions",
                        UsbUserPermissionsManagerProto.DEVICE_PERMISSIONS);

                dump.write("device_name", UsbDevicePermissionProto.DEVICE_NAME, deviceName);

                SparseBooleanArray uidList = mDevicePermissionMap.valueAt(mappingsIdx);
                int numUids = uidList.size();
                for (int uidsIdx = 0; uidsIdx < numUids; uidsIdx++) {
                    dump.write("uids", UsbDevicePermissionProto.UIDS, uidList.keyAt(uidsIdx));
                }

                dump.end(devicePermissionToken);
            }

            numMappings = mAccessoryPermissionMap.size();
            for (int mappingsIdx = 0; mappingsIdx < numMappings; ++mappingsIdx) {
                UsbAccessory accessory = mAccessoryPermissionMap.keyAt(mappingsIdx);
                long accessoryPermissionToken = dump.start("accessory_permissions",
                        UsbUserPermissionsManagerProto.ACCESSORY_PERMISSIONS);

                dump.write("accessory_description",
                        UsbAccessoryPermissionProto.ACCESSORY_DESCRIPTION,
                        accessory.getDescription());

                SparseBooleanArray uidList = mAccessoryPermissionMap.valueAt(mappingsIdx);
                int numUids = uidList.size();
                for (int uidsIdx = 0; uidsIdx < numUids; uidsIdx++) {
                    dump.write("uids", UsbAccessoryPermissionProto.UIDS, uidList.keyAt(uidsIdx));
                }

                dump.end(accessoryPermissionToken);
            }
        }
        dump.end(token);
    }

    /**
     * Check for camera permission of the calling process.
     *
     * @param packageName Package name of the caller.
     * @param pid         Linux pid of the calling process.
     * @param uid         Linux uid of the calling process.
     * @return True in case camera permission is available, False otherwise.
     */
    private boolean isCameraPermissionGranted(String packageName, int pid, int uid) {
        int targetSdkVersion = android.os.Build.VERSION_CODES.P;
        try {
            ApplicationInfo aInfo = mContext.getPackageManager().getApplicationInfo(packageName, 0);
            // compare uid with packageName to foil apps pretending to be someone else
            if (aInfo.uid != uid) {
                Slog.i(TAG, "Package " + packageName + " does not match caller's uid " + uid);
                return false;
            }
            targetSdkVersion = aInfo.targetSdkVersion;
        } catch (PackageManager.NameNotFoundException e) {
            Slog.i(TAG, "Package not found, likely due to invalid package name!");
            return false;
        }

        if (targetSdkVersion >= android.os.Build.VERSION_CODES.P) {
            int allowed = mContext.checkPermission(android.Manifest.permission.CAMERA, pid, uid);
            if (android.content.pm.PackageManager.PERMISSION_DENIED == allowed) {
                Slog.i(TAG, "Camera permission required for USB video class devices");
                return false;
            }
        }

        return true;
    }

    public void checkPermission(
            UsbDevice device, UsbDeviceFingerprint fingerprint,
            String packageName, int pid, int uid) {
        if (!hasPermission(device, fingerprint, packageName, pid, uid)) {
            throw new SecurityException("User has not given " + uid + "/" + packageName
                    + " permission to access device " + device.getDeviceName());
        }
    }

    public void checkPermission(UsbAccessory accessory, String packageName, int pid, int uid) {
        if (!hasPermission(accessory, packageName, pid, uid)) {
            throw new SecurityException("User has not given " + uid + " permission to accessory "
                    + accessory);
        }
    }

    private void requestPermissionDialog(@Nullable UsbDevice device,
            @Nullable UsbAccessory accessory,
            boolean canBeDefault,
            String packageName,
            PendingIntent pi,
            int uid) {
        boolean throwException = false;

        // compare uid with packageName to foil apps pretending to be someone else
        try {
            ApplicationInfo aInfo = mContext.getPackageManager().getApplicationInfo(packageName, 0);
            if (aInfo.uid != uid) {
                Slog.w(TAG, "package " + packageName
                        + " does not match caller's uid " + uid);
                EventLog.writeEvent(SNET_EVENT_LOG_ID, "180104273", -1, "");
                throwException = true;
            }
        } catch (PackageManager.NameNotFoundException e) {
            throwException = true;
        } finally {
            if (throwException)
                throw new IllegalArgumentException("package " + packageName + " not found");
        }

        requestPermissionDialog(device, accessory, canBeDefault, packageName, uid, mContext, pi);
    }

    public void requestPermission(UsbDevice device, UsbDeviceFingerprint fingerprint,
            String packageName, PendingIntent pi, int pid, int uid) {
        Intent intent = new Intent();

        // respond immediately if permission has already been granted
        if (hasPermission(device, fingerprint, packageName, pid, uid)) {
            intent.putExtra(UsbManager.EXTRA_DEVICE, device);
            intent.putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, true);
            try {
                pi.send(mContext, 0, intent);
            } catch (PendingIntent.CanceledException e) {
                if (DEBUG) Slog.d(TAG, "requestPermission PendingIntent was cancelled");
            }
            return;
        }
        // If the app doesn't have camera permission do not request permission to the USB device.
        // Note that if the USB camera also has a microphone, a warning will be shown to the user if
        // the app doesn't have RECORD_AUDIO permission.
        if (device.getHasVideoCapture()) {
            if (!isCameraPermissionGranted(packageName, pid, uid)) {
                intent.putExtra(UsbManager.EXTRA_DEVICE, device);
                intent.putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                try {
                    pi.send(mContext, 0, intent);
                } catch (PendingIntent.CanceledException e) {
                    if (DEBUG) Slog.d(TAG, "requestPermission PendingIntent was cancelled");
                }
                return;
            }
        }

        requestPermissionDialog(device, null,
                mUsbUserSettingsManager.canBeDefault(device, packageName), packageName, pi, uid);
    }

    public void requestPermission(UsbAccessory accessory, String packageName, PendingIntent pi,
            int pid, int uid) {
        // respond immediately if permission has already been granted
        if (hasPermission(accessory, packageName, pid, uid)) {
            Intent intent = new Intent();
            intent.putExtra(UsbManager.EXTRA_ACCESSORY, accessory);
            intent.putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, true);
            try {
                pi.send(mContext, 0, intent);
            } catch (PendingIntent.CanceledException e) {
                if (DEBUG) Slog.d(TAG, "requestPermission PendingIntent was cancelled");
            }
            return;
        }

        requestPermissionDialog(null, accessory,
                mUsbUserSettingsManager.canBeDefault(accessory, packageName), packageName, pi, uid);
    }

    /** Revoke device access permission for packageName + uid. */
    public void revokeDevicePermission(
            @NonNull UsbDevice device,
            UsbDeviceFingerprint fingerprint,
            String packageName,
            int uid) {
        if (!android.hardware.usb.flags.Flags.enablePersistentUsbDevicePermissions()) {
            return;
        }

        PackageAndUid identifier = new PackageAndUid(packageName, uid);
        String deviceAddr = device.getDeviceName();
        boolean removed = false;

        if (!isValidPackageUidPair(identifier)) {
            throw new IllegalArgumentException(
                    "Provided packageName and uid pair are not valid");
        }


        synchronized (mLock) {
            if (fingerprint != null) {
                ArraySet<PackageAndUid> permissionsForDevice =
                        mPersistentDevicePermissionMap.get(fingerprint);
                if (permissionsForDevice != null) {
                    if (permissionsForDevice.remove(identifier)) {
                        scheduleWritePermissionsLocked();
                        removed = true;
                    }
                }
            }

            ArraySet<PackageAndUid> temporaryPermissions =
                    mTemporaryDevicePermissionMap.get(deviceAddr);
            if (temporaryPermissions != null) {
                removed |= temporaryPermissions.remove(identifier);
            }
        }

        if (removed) {
            permissionChangedBroadcastIntent(device, null);
        }
    }

    /**
     * Get a list of packages that have both temporary and persistent permissions for given device.
     *
     * @param device - USB device to check
     * @return list of packages that have permissions to this UsbDevice.
     */
    public List<String> getPackagesWithDevicePermission(
            UsbDevice device, UsbDeviceFingerprint fingerprint) {
        ArraySet<String> packages = new ArraySet<>();

        if (android.hardware.usb.flags.Flags.enablePersistentUsbDevicePermissions()) {
            synchronized (mLock) {
                String deviceAddr = device.getDeviceName();
                if (fingerprint != null
                        && mPersistentDevicePermissionMap.get(fingerprint) != null) {
                    for (PackageAndUid entry : mPersistentDevicePermissionMap.get(fingerprint)) {
                        packages.add(entry.packageName);
                    }
                }

                ArraySet<PackageAndUid> tmpPermissions =
                        mTemporaryDevicePermissionMap.get(deviceAddr);
                if (tmpPermissions != null) {
                    for (PackageAndUid entry : tmpPermissions) {
                        packages.add(entry.packageName);
                    }
                }
            }
        }

        return new ArrayList<String>(packages);
    }
}
