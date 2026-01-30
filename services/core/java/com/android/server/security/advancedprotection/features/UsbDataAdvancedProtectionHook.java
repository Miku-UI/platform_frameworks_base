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

package com.android.server.security.advancedprotection.features;

import static android.app.Notification.EXTRA_SUBSTITUTE_APP_NAME;
import static android.content.Intent.ACTION_LOCKED_BOOT_COMPLETED;
import static android.hardware.usb.UsbManager.ACTION_USB_PORT_CHANGED;
import static android.hardware.usb.UsbManager.ACTION_USB_ACCESSORY_ATTACHED;
import static android.hardware.usb.UsbManager.ACTION_USB_ACCESSORY_DETACHED;
import static android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED;
import static android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED;
import static android.security.advancedprotection.AdvancedProtectionManager.FEATURE_ID_DISALLOW_USB;
import static android.security.advancedprotection.AdvancedProtectionManager.SUPPORT_DIALOG_TYPE_BLOCKED_INTERACTION;
import static android.security.advancedprotection.AdvancedProtectionManager.SUPPORT_DIALOG_TYPE_UNKNOWN;
import static android.hardware.usb.UsbPortStatus.DATA_STATUS_DISABLED_FORCE;
import static android.hardware.usb.UsbPortStatus.DATA_STATUS_ENABLED;
import static android.hardware.usb.UsbPortStatus.POWER_ROLE_SINK;
import static android.hardware.usb.UsbPortStatus.POWER_BRICK_STATUS_CONNECTED;
import static android.hardware.usb.UsbPortStatus.POWER_BRICK_STATUS_DISCONNECTED;
import static android.hardware.usb.UsbPortStatus.POWER_BRICK_STATUS_UNKNOWN;
import static android.hardware.usb.InternalUsbDataSignalDisableReason.USB_DISABLE_REASON_APM;

import android.annotation.IntDef;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardLockedStateListener;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.UserInfo;
import android.content.pm.ResolveInfo;
import android.hardware.usb.ParcelableUsbPort;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.hardware.usb.IUsbManagerInternal;
import android.hardware.usb.UsbPort;
import android.hardware.usb.UsbPortStatus;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.SystemClock;

import android.security.Flags;
import android.util.Slog;
import android.content.pm.PackageManager;

import com.android.server.LocalServices;
import java.lang.Runnable;
import android.security.advancedprotection.AdvancedProtectionFeature;
import android.security.advancedprotection.AdvancedProtectionProtoEnums;

import com.android.internal.R;
import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.internal.util.FrameworkStatsLog;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.security.advancedprotection.AdvancedProtectionService;

import java.net.URISyntaxException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AAPM Feature for managing and protecting USB data signal from attacks.
 *
 * @hide
 */
public class UsbDataAdvancedProtectionHook extends AdvancedProtectionHook {
    private static final String TAG = "AdvancedProtectionUsb";

    private static final String APM_USB_FEATURE_NOTIF_CHANNEL = "APM_USB_SERVICE_NOTIF_CHANNEL";
    private static final String CHANNEL_NAME = "ApmUsbProtectionUiNotificationChannel";
    private static final String USB_DATA_PROTECTION_ENABLE_SYSTEM_PROPERTY =
            "ro.usb.data_protection.disable_when_locked.supported";
    private static final String USB_DATA_PROTECTION_REPLUG_REQUIRED_UPON_ENABLE_SYSTEM_PROPERTY =
            "ro.usb.data_protection.disable_when_locked.replug_required_upon_enable";
    private static final String
            USB_DATA_PROTECTION_POWER_BRICK_CONNECTION_CHECK_TIMEOUT_SYSTEM_PROPERTY =
                    "ro.usb.data_protection.disable_when_locked.power_brick_connection_check_timeout";
    private static final String USB_DATA_PROTECTION_PD_COMPLIANCE_CHECK_TIMEOUT_SYSTEM_PROPERTY =
            "ro.usb.data_protection.disable_when_locked.pd_compliance_check_timeout";
    private static final String
            USB_DATA_PROTECTION_DATA_REQUIRED_FOR_HIGH_POWER_CHARGE_SYSTEM_PROPERTY =
                    "ro.usb.data_protection.disable_when_locked.data_required_for_high_power_charge";
    private static final String ACTION_SILENCE_NOTIFICATION =
            "com.android.server.security.advancedprotection.features.silence";
    private static final String EXTRA_SILENCE_DATA_NOTIFICATION = "silence_data_notification";
    private static final String EXTRA_SILENCE_POWER_NOTIFICATION = "silence_power_notification";

    private static final int NOTIFICATION_CHARGE = 0;
    private static final int NOTIFICATION_CHARGE_DATA = 1;
    private static final int NOTIFICATION_DATA = 2;

    // For connection recovery, in case of Android Auto or unreliable cables
    private static final int DELAY_DISABLE_MILLIS = 15000;
    private static final int USB_DATA_CHANGE_MAX_RETRY_ATTEMPTS = 3;
    private static final long USB_PORT_POWER_BRICK_CONNECTION_CHECK_TIMEOUT_DEFAULT_MILLIS = 3000;
    private static final long USB_PD_COMPLIANCE_CHECK_TIMEOUT_DEFAULT_MILLIS = 1000;

    @IntDef({NOTIFICATION_CHARGE, NOTIFICATION_CHARGE_DATA, NOTIFICATION_DATA})
    private @interface NotificationType {}

    private static final Map<Integer, Integer> NOTIFICATION_TYPE_TO_TITLE =
            Map.of(
                    NOTIFICATION_CHARGE,
                    R.string.usb_apm_usb_plugged_in_when_locked_notification_title,
                    NOTIFICATION_CHARGE_DATA,
                    R.string.usb_apm_usb_plugged_in_when_locked_notification_title,
                    NOTIFICATION_DATA,
                    R.string.usb_apm_usb_plugged_in_when_locked_notification_title);
    private static final Map<Integer, Integer> NOTIFICATION_TYPE_TO_TITLE_WITH_REPLUG =
            Map.of(
                    NOTIFICATION_CHARGE,
                    R.string.usb_apm_usb_plugged_in_when_locked_replug_notification_title,
                    NOTIFICATION_CHARGE_DATA,
                    R.string.usb_apm_usb_plugged_in_when_locked_replug_notification_title,
                    NOTIFICATION_DATA,
                    R.string.usb_apm_usb_plugged_in_when_locked_replug_notification_title);

    private static final Map<Integer, Integer> NOTIFICATION_TYPE_TO_TEXT =
            Map.of(
                    NOTIFICATION_CHARGE,
                    R.string.usb_apm_usb_plugged_in_when_locked_charge_notification_text,
                    NOTIFICATION_CHARGE_DATA,
                    R.string.usb_apm_usb_plugged_in_when_locked_charge_data_notification_text,
                    NOTIFICATION_DATA,
                    R.string.usb_apm_usb_plugged_in_when_locked_data_notification_text);
    private static final Map<Integer, Integer> NOTIFICATION_TYPE_TO_TEXT_WITH_REPLUG =
            Map.of(
                    NOTIFICATION_CHARGE,
                    R.string.usb_apm_usb_plugged_in_when_locked_replug_notification_text,
                    NOTIFICATION_CHARGE_DATA,
                    R.string.usb_apm_usb_plugged_in_when_locked_charge_data_notification_text,
                    NOTIFICATION_DATA,
                    R.string.usb_apm_usb_plugged_in_when_locked_data_notification_text);

    private final ReentrantLock mDisableLock = new ReentrantLock();
    private final Context mContext;

    private AtomicBoolean mApmRequestedUsbDataStatus = new AtomicBoolean(false);

    // We use handlers for tasks that may need to be updated by broadcasts events.
    private Handler mDelayedDisableHandler = new Handler(Looper.getMainLooper());
    private Handler mDelayedNotificationHandler = new Handler(Looper.getMainLooper());

    private AdvancedProtectionFeature mFeature =
            new AdvancedProtectionFeature(FEATURE_ID_DISALLOW_USB);

    private UsbManager mUsbManager;
    private UserManager mUserManager;
    private IUsbManagerInternal mUsbManagerInternal;
    private BroadcastReceiver mUsbProtectionBroadcastReceiver;
    private KeyguardManager mKeyguardManager;
    private NotificationManager mNotificationManager;
    private NotificationChannel mNotificationChannel;
    private AdvancedProtectionService mAdvancedProtectionService;
    private ExecutorService mUsbDataSignalUpdateExecutor = Executors.newSingleThreadExecutor();
    private KeyguardLockedStateListener mKeyguardLockedStateListener;
    private UsbPortStatus mLastUsbPortStatus;

    // TODO(b/418846176):  Move these to a system property
    private long mUsbPortPowerBrickConnectionCheckTimeoutMillis;
    private long mUsbPortPdComplianceCheckTimeoutMillis;

    private boolean mCanSetUsbDataSignal = false;
    private boolean mDataRequiredForHighPowerCharge = false;
    private boolean mReplugRequiredUponEnable = false;
    private boolean mSilenceDataNotification = false;
    private boolean mSilencePowerNotification = false;
    private boolean mBroadcastReceiverIsRegistered = false;
    private boolean mIsAfterFirstUnlock = false;

    public UsbDataAdvancedProtectionHook(
            Context context, boolean enabled, AdvancedProtectionService advancedProtectionService) {
        super(context, enabled);
        mContext = context;
        if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST)
                && !mContext.getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_USB_ACCESSORY)) {
            return;
        }
        mUsbManager = Objects.requireNonNull(mContext.getSystemService(UsbManager.class));
        mNotificationManager =
                Objects.requireNonNull(mContext.getSystemService(NotificationManager.class));
        mAdvancedProtectionService = advancedProtectionService;
        mUsbManagerInternal =
                Objects.requireNonNull(LocalServices.getService(IUsbManagerInternal.class));
        mKeyguardManager = Objects.requireNonNull(mContext.getSystemService(KeyguardManager.class));
        mUserManager = Objects.requireNonNull(mContext.getSystemService(UserManager.class));
        mCanSetUsbDataSignal = canSetUsbDataSignal();
        onAdvancedProtectionChanged(enabled);
    }

    @VisibleForTesting
    public UsbDataAdvancedProtectionHook(
            Context context,
            AdvancedProtectionService advancedProtectionService,
            UsbManager usbManager,
            IUsbManagerInternal usbManagerInternal,
            KeyguardManager keyguardManager,
            NotificationManager notificationManager,
            UserManager userManager,
            Handler delayDisableHandler,
            Handler delayedNotificationHandler,
            AtomicBoolean apmRequestedUsbDataStatus,
            boolean canSetUsbDataSignal,
            boolean afterFirstUnlock) {
        super(context, false);
        mContext = context;
        mAdvancedProtectionService = advancedProtectionService;
        mUsbManager = usbManager;
        mUsbManagerInternal = usbManagerInternal;
        mKeyguardManager = keyguardManager;
        mNotificationManager = notificationManager;
        mDelayedNotificationHandler = delayedNotificationHandler;
        mDelayedDisableHandler = delayDisableHandler;
        mCanSetUsbDataSignal = canSetUsbDataSignal;
        mIsAfterFirstUnlock = afterFirstUnlock;
        mUserManager = userManager;
        mApmRequestedUsbDataStatus = apmRequestedUsbDataStatus;
    }

    @Override
    public AdvancedProtectionFeature getFeature() {
        return mFeature;
    }

    @Override
    public boolean isAvailable() {
        boolean usbDataProtectionEnabled =
                SystemProperties.getBoolean(USB_DATA_PROTECTION_ENABLE_SYSTEM_PROPERTY, false);
        if (!usbDataProtectionEnabled) {
            Slog.d(TAG, "USB data protection is disabled through system property");
        }
        return Flags.aapmFeatureUsbDataProtection()
                && (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST)
                        || mContext.getPackageManager()
                                .hasSystemFeature(PackageManager.FEATURE_USB_ACCESSORY))
                && mAdvancedProtectionService.isUsbDataProtectionEnabled()
                && mCanSetUsbDataSignal
                && usbDataProtectionEnabled;
    }

    @Override
    public void onAdvancedProtectionChanged(boolean enabled) {
        if (!isAvailable() && enabled) {
            Slog.w(TAG, "AAPM USB data protection feature is disabled");
            return;
        }
        Slog.i(TAG, "onAdvancedProtectionChanged: " + enabled);
        if (enabled) {
            if (mUsbProtectionBroadcastReceiver == null) {
                initialize();
            }
            if (!mBroadcastReceiverIsRegistered) {
                registerReceiver();
                registerKeyguardLockListener();
            }
            if (mKeyguardManager.isKeyguardLocked()) {
                setUsbDataSignalIfPossible(false);
            }
        } else {
            if (mBroadcastReceiverIsRegistered) {
                unregisterReceiver();
            }
            setUsbDataSignalIfPossible(true);
        }
    }

    private void initialize() {
        mDataRequiredForHighPowerCharge =
                SystemProperties.getBoolean(
                        USB_DATA_PROTECTION_DATA_REQUIRED_FOR_HIGH_POWER_CHARGE_SYSTEM_PROPERTY,
                        false);
        mReplugRequiredUponEnable =
                SystemProperties.getBoolean(
                        USB_DATA_PROTECTION_REPLUG_REQUIRED_UPON_ENABLE_SYSTEM_PROPERTY, false);
        mUsbPortPowerBrickConnectionCheckTimeoutMillis =
                SystemProperties.getLong(
                        USB_DATA_PROTECTION_POWER_BRICK_CONNECTION_CHECK_TIMEOUT_SYSTEM_PROPERTY,
                        USB_PORT_POWER_BRICK_CONNECTION_CHECK_TIMEOUT_DEFAULT_MILLIS);
        mUsbPortPdComplianceCheckTimeoutMillis =
                SystemProperties.getLong(
                        USB_DATA_PROTECTION_PD_COMPLIANCE_CHECK_TIMEOUT_SYSTEM_PROPERTY,
                        USB_PD_COMPLIANCE_CHECK_TIMEOUT_DEFAULT_MILLIS);
        initializeNotifications();
        mUsbProtectionBroadcastReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        try {
                            if (ACTION_USB_PORT_CHANGED.equals(intent.getAction())) {
                                UsbPortStatus portStatus =
                                        intent.getParcelableExtra(
                                                UsbManager.EXTRA_PORT_STATUS, UsbPortStatus.class);

                                // If we cannot retrieve the port status, then we skip this event.
                                if (portStatus == null) {
                                    Slog.w(
                                            TAG,
                                            "UsbPort Changed: USB Data protection failed to"
                                                    + " retrieve port status");
                                    return;
                                }
                                mLastUsbPortStatus = portStatus;

                                if (Build.IS_DEBUGGABLE) {
                                    dumpUsbDevices();
                                }

                                if (mKeyguardManager.isKeyguardLocked()) {
                                    updateDelayedDisableTask(portStatus);
                                }

                                if (!portStatus.isConnected()) {
                                    cleanUpNotificationHandlerTasks();
                                    clearExistingNotification();

                                    /*
                                     * Due to limitations of current APIs, we cannot cannot fully
                                     * rely on power brick and pd compliance check to be accurate
                                     * until it's passed the check timeouts or the value is
                                     * POWER_BRICK_STATUS_CONNECTED or isCompliant=true
                                     * respectively.
                                     */
                                } else if (portStatus.getCurrentPowerRole() == POWER_ROLE_SINK) {
                                    long pbCheckDuration =
                                            portStatus.getPowerBrickConnectionStatus()
                                                            != POWER_BRICK_STATUS_CONNECTED
                                                    ? mUsbPortPowerBrickConnectionCheckTimeoutMillis
                                                    : 0;
                                    long pdCheckDuration =
                                            !portStatus.isPdCompliant()
                                                    ? mUsbPortPdComplianceCheckTimeoutMillis
                                                    : 0;
                                    long delayTimeMillis = pbCheckDuration + pdCheckDuration;
                                    if (delayTimeMillis <= 0) {
                                        cleanUpNotificationHandlerTasks();
                                        determineUsbChargeStateAndSendNotification(portStatus);
                                    } else {
                                        updateDelayedNotificationTask(delayTimeMillis);
                                    }
                                } else {
                                    cleanUpNotificationHandlerTasks();
                                    createAndSendNotificationIfDeviceIsLocked(
                                            portStatus, NOTIFICATION_DATA);
                                }

                            }
                            // Any earlier call to USBService during bootup have a risk of
                            // having
                            // request dropped due to USB stack not being ready.
                            else if (ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction())) {
                                setUsbDataSignalIfPossible(false);
                            } else if (Set.of(
                                            ACTION_USB_ACCESSORY_ATTACHED,
                                            ACTION_USB_DEVICE_ATTACHED,
                                            ACTION_USB_ACCESSORY_DETACHED,
                                            ACTION_USB_DEVICE_DETACHED)
                                    .contains(intent.getAction())) {
                                if (!mApmRequestedUsbDataStatus.get()) {
                                    Slog.d(
                                            TAG,
                                            "Unexpected USB event when USB is disabled: "
                                                    + intent.getAction());
                                    logUnexpectedUsbEvent(intent.getAction());
                                    dumpUsbDevices();
                                }
                            }
                        } catch (Exception e) {
                            Slog.e(TAG, "USB Data protection failed with: " + e.getMessage());
                        }
                    }

                    private void logUnexpectedUsbEvent(String usbEvent) {
                        FrameworkStatsLog.write(
                                FrameworkStatsLog
                                        .ADVANCED_PROTECTION_USB_STATE_CHANGE_ERROR_REPORTED,
                                // populating with default values as StatsLog cannot skip fields
                                false,
                                -1,
                                getAtomUsbErrorType(usbEvent));
                    }

                    private int getAtomUsbErrorType(String usbEvent) {
                        switch (usbEvent) {
                            case ACTION_USB_ACCESSORY_ATTACHED:
                                return AdvancedProtectionProtoEnums
                                        .USB_ERROR_TYPE_UNEXPECTED_ACCESSORY_ATTACHED;
                            case ACTION_USB_DEVICE_ATTACHED:
                                return AdvancedProtectionProtoEnums
                                        .USB_ERROR_TYPE_UNEXPECTED_DEVICE_ATTACHED;
                            case ACTION_USB_ACCESSORY_DETACHED:
                                return AdvancedProtectionProtoEnums
                                        .USB_ERROR_TYPE_UNEXPECTED_ACCESSORY_DETACHED;
                            case ACTION_USB_DEVICE_DETACHED:
                                return AdvancedProtectionProtoEnums
                                        .USB_ERROR_TYPE_UNEXPECTED_DEVICE_DETACHED;
                            default:
                                return AdvancedProtectionProtoEnums.USB_ERROR_TYPE_UNKNOWN;
                        }
                    }

                    private void updateDelayedNotificationTask(long delayTimeMillis) {
                        if (!mDelayedNotificationHandler.hasMessagesOrCallbacks()
                                && delayTimeMillis > 0) {
                            boolean taskPosted =
                                    mDelayedNotificationHandler.postDelayed(
                                            () -> {
                                                determineUsbChargeStateAndSendNotification(
                                                        mLastUsbPortStatus);
                                            },
                                            delayTimeMillis);
                            if (!taskPosted) {
                                Slog.w(TAG, "Delayed Disable Task: Failed to post task");
                            }
                        }
                    }

                    private void updateDelayedDisableTask(UsbPortStatus portStatus) {
                        // For recovered intermittent/unreliable USB connections
                        if (usbPortIsConnected(portStatus)) {
                            mDelayedDisableHandler.removeCallbacksAndMessages(null);
                        } else if (!mDelayedDisableHandler.hasMessagesOrCallbacks()) {
                            boolean taskPosted =
                                    mDelayedDisableHandler.postDelayed(
                                            () -> {
                                                if (mKeyguardManager.isKeyguardLocked()) {
                                                    setUsbDataSignalIfPossible(false);
                                                }
                                            },
                                            DELAY_DISABLE_MILLIS);
                            if (!taskPosted) {
                                Slog.w(TAG, "Delayed Disable Task: Failed to post task");
                            }
                        }
                    }

                    private void determineUsbChargeStateAndSendNotification(
                            UsbPortStatus portStatus) {
                        clearExistingNotification();

                        if (portStatus.getPowerBrickConnectionStatus()
                                == POWER_BRICK_STATUS_CONNECTED) {
                            if (mDataRequiredForHighPowerCharge) {
                                createAndSendNotificationIfDeviceIsLocked(
                                        portStatus, NOTIFICATION_CHARGE);
                            }
                        } else {
                            if (portStatus.isPdCompliant() && !mDataRequiredForHighPowerCharge) {
                                createAndSendNotificationIfDeviceIsLocked(
                                        portStatus, NOTIFICATION_DATA);
                            } else {
                                createAndSendNotificationIfDeviceIsLocked(
                                        portStatus, NOTIFICATION_CHARGE_DATA);
                            }
                        }
                    }

                    private void dumpUsbDevices() {
                        Map<String, UsbDevice> portStatusMap = mUsbManager.getDeviceList();
                        for (UsbDevice device : portStatusMap.values()) {
                            Slog.d(TAG, "Device: " + device.getDeviceName());
                        }
                        UsbAccessory[] accessoryList = mUsbManager.getAccessoryList();
                        if (accessoryList != null) {
                            for (UsbAccessory accessory : accessoryList) {
                                Slog.d(TAG, "Accessory: " + accessory.toString());
                            }
                        }
                    }
                };
    }

    private void initializeNotifications() {
        if (mNotificationManager.getNotificationChannel(APM_USB_FEATURE_NOTIF_CHANNEL) == null) {
            mNotificationChannel =
                    new NotificationChannel(
                            APM_USB_FEATURE_NOTIF_CHANNEL,
                            CHANNEL_NAME,
                            NotificationManager.IMPORTANCE_HIGH);
            mNotificationManager.createNotificationChannel(mNotificationChannel);
        }
    }

    private void cleanUpNotificationHandlerTasks() {
        mDelayedNotificationHandler.removeCallbacksAndMessages(null);
    }

    private void createAndSendNotificationIfDeviceIsLocked(
            UsbPortStatus portStatus, @NotificationType int notificationType) {
        if ((notificationType == NOTIFICATION_CHARGE_DATA
                        && mSilenceDataNotification
                        && mSilencePowerNotification)
                || (notificationType == NOTIFICATION_CHARGE && mSilencePowerNotification)
                || (notificationType == NOTIFICATION_DATA && mSilenceDataNotification)) {
            // Log interactions that were suppressed due to silence flags
            mAdvancedProtectionService.logDialogShown(
                    FEATURE_ID_DISALLOW_USB,
                    // TODO: (b/446947637) - Update to correct dialog type (requires update in AdvancedProtectionManager API)
                    SUPPORT_DIALOG_TYPE_UNKNOWN,
                    false);
            return;
            // Last moment check to see if conditions are still met to show notification
        } else if (!mKeyguardManager.isKeyguardLocked()
                || !usbPortIsConnectedWithDataDisabled(portStatus)) {
            return;
        }

        String notificationTitle;
        String notificationBody;
        if (mReplugRequiredUponEnable) {
            notificationTitle =
                    mContext.getString(
                            NOTIFICATION_TYPE_TO_TITLE_WITH_REPLUG.get(notificationType));
            notificationBody =
                    mContext.getString(NOTIFICATION_TYPE_TO_TEXT_WITH_REPLUG.get(notificationType));
        } else {
            notificationTitle =
                    mContext.getString(NOTIFICATION_TYPE_TO_TITLE.get(notificationType));
            notificationBody = mContext.getString(NOTIFICATION_TYPE_TO_TEXT.get(notificationType));
        }

        Intent silenceIntent = new Intent(ACTION_SILENCE_NOTIFICATION);
        if(notificationType == NOTIFICATION_CHARGE_DATA) {
            silenceIntent.putExtra(EXTRA_SILENCE_DATA_NOTIFICATION, true);
            silenceIntent.putExtra(EXTRA_SILENCE_POWER_NOTIFICATION, true);
        } else if (notificationType == NOTIFICATION_CHARGE) {
            silenceIntent.putExtra(EXTRA_SILENCE_POWER_NOTIFICATION, true);
        } else if (notificationType == NOTIFICATION_DATA) {
            silenceIntent.putExtra(EXTRA_SILENCE_DATA_NOTIFICATION, true);
        }
        sendNotification(
                notificationTitle,
                notificationBody,
                PendingIntent.getBroadcast(
                        mContext, 0, silenceIntent, PendingIntent.FLAG_IMMUTABLE),
                getAtomUsbNotificationType(notificationType));

        mAdvancedProtectionService.logDialogShown(
                FEATURE_ID_DISALLOW_USB,
                SUPPORT_DIALOG_TYPE_BLOCKED_INTERACTION,
                false);
    }

    private int getAtomUsbNotificationType(@NotificationType int internalNotificationType) {
        switch (internalNotificationType) {
            case NOTIFICATION_CHARGE_DATA:
                return AdvancedProtectionProtoEnums.USB_NOTIFICATION_TYPE_CHARGE_DATA;
            case NOTIFICATION_CHARGE:
                return AdvancedProtectionProtoEnums.USB_NOTIFICATION_TYPE_CHARGE;
            case NOTIFICATION_DATA:
                return AdvancedProtectionProtoEnums.USB_NOTIFICATION_TYPE_DATA;
            default:
                return AdvancedProtectionProtoEnums.USB_NOTIFICATION_TYPE_UNKNOWN;
        }
    }

    private void sendNotification(
            String title,
            String message,
            PendingIntent silencePendingIntent,
            int notificationType) {
        Bundle notificationExtras = new Bundle();
        notificationExtras.putString(
                EXTRA_SUBSTITUTE_APP_NAME,
                mContext.getString(
                        R.string.usb_apm_usb_plugged_in_when_locked_notification_app_title));

        Notification.Builder notif =
                new Notification.Builder(mContext, APM_USB_FEATURE_NOTIF_CHANNEL)
                        .setSmallIcon(R.drawable.ic_security_privacy_notification_badge)
                        .setColor(
                                mContext.getColor(
                                        R.color.security_privacy_notification_tint_normal))
                        .setContentTitle(title)
                        .setStyle(new Notification.BigTextStyle().bigText(message))
                        .setAutoCancel(true)
                        .addExtras(notificationExtras)
                        .setVisibility(Notification.VISIBILITY_PUBLIC);
        if (silencePendingIntent != null) {
            notif.addAction(
                    0,
                    mContext.getString(
                            R.string
                                    .usb_apm_usb_plugged_in_when_locked_notification_silence_action_text),
                    silencePendingIntent);
        }

        // Intent may fail to initialize in BFU state, so we may need to initialize it lazily.
        PendingIntent helpPendingIntent = createHelpPendingIntent();
        if (helpPendingIntent != null) {
            notif.setContentIntent(helpPendingIntent);
        }
        UserHandle userHandle =
                mIsAfterFirstUnlock
                        ? UserHandle.of(ActivityManager.getCurrentUser())
                        : mContext.getUser();
        mNotificationManager.notifyAsUser(
                TAG, SystemMessage.NOTE_USB_DATA_PROTECTION_REMINDER, notif.build(), userHandle);
        FrameworkStatsLog.write(
                FrameworkStatsLog.ADVANCED_PROTECTION_USB_NOTIFICATION_DISPLAYED, notificationType);
    }

    private void clearExistingNotification() {
        mNotificationManager.cancel(TAG, SystemMessage.NOTE_USB_DATA_PROTECTION_REMINDER);
    }

    private boolean usbPortIsConnectedWithDataDisabled(UsbPortStatus portStatus) {
        if (portStatus != null && portStatus.isConnected()) {
            int usbDataStatus = portStatus.getUsbDataStatus();
            boolean isRequestedDisabled =
                    (portStatus.getUsbDataStatus() & UsbPortStatus.DATA_STATUS_DISABLED_FORCE) != 0;
            // Setting default to above value because HIDL implementation of DATA_STATUS_ENABLED
            // is DATA_STATUS_UNKNOWN as it is unable to feedback USB data state back
            // to framework. So we can only assume that disable request is honored.
            // The atomic boolean check is to make sure it requested by us and not by other reasons
            // ie. Enterprise policy.
            boolean isDataEnabled = isRequestedDisabled && mApmRequestedUsbDataStatus.get();
            int usbHalVersion = mUsbManager.getUsbHalVersion();
            // For AIDL implementation, DATA_STATUS_ENABLED is fed back to framework from the HAL
            if (usbHalVersion > UsbManager.USB_HAL_V1_3) {
                isDataEnabled = (usbDataStatus & UsbPortStatus.DATA_STATUS_ENABLED) != 0;
            }
            // We care about DATA_STATUS_DISABLED_FORCE for AIDL because not only do we need to
            // check if data is enabled, but also if data we had any data disable request that has
            // been acknowledged by the HAL. If not requested to be disabled by APM for any reason,
            // it is out-of-scope for APM to enforce.
            // Ie. if USB data is disabled not because of APM (ie. overheat),
            // APM needs to make sure not to assume that itself requested to disable USB and show
            // any notification or take any action.
            return !isDataEnabled && isRequestedDisabled;
        }
        return false;
    }

    private boolean setUsbDataSignalIfPossible(boolean status) {
        boolean successfullySetUsbDataSignal = false;
        mDisableLock.lock();
        try {
            /*
             * We check if there is already an existing USB connection and skip the USB
             * disablement if there is one unless it is in BFU state.
             */
            if (!status && deviceHaveUsbDataConnection() && mIsAfterFirstUnlock) {
                Slog.i(TAG, "USB Data protection toggle skipped due to existing USB connection");
                return false;
            }

            int usbChangeStateReattempts = 0;
            while (usbChangeStateReattempts < USB_DATA_CHANGE_MAX_RETRY_ATTEMPTS) {
                try {
                    Slog.d(TAG, "Setting USB data: " + status);
                    if (mUsbManagerInternal.enableUsbDataSignal(status, USB_DISABLE_REASON_APM)) {
                        mApmRequestedUsbDataStatus.set(status);
                        successfullySetUsbDataSignal = true;
                        Slog.d(TAG, "Successfully set USB data");
                        break;
                    } else {
                        Slog.e(TAG, "USB Data protection toggle to " + status + " attempt failed");
                    }
                } catch (RemoteException e) {
                    Slog.e(TAG, "RemoteException thrown when calling enableUsbDataSignal", e);
                }
                usbChangeStateReattempts += 1;
            }

            // Log the error if the USB change state failed at least once.
            if (usbChangeStateReattempts > 0) {
                FrameworkStatsLog.write(
                        FrameworkStatsLog.ADVANCED_PROTECTION_USB_STATE_CHANGE_ERROR_REPORTED,
                        /* desired_signal_state */ status,
                        /* retries_occurred */ usbChangeStateReattempts,
                        AdvancedProtectionProtoEnums.USB_ERROR_TYPE_CHANGE_DATA_STATUS_FAILED);
            }
            if (status) {
                clearExistingNotification();
            }
        } finally {
            mDisableLock.unlock();
        }
        return successfullySetUsbDataSignal;
    }

    private boolean deviceHaveUsbDataConnection() {
        for (UsbPort usbPort : mUsbManager.getPorts()) {
            if (Build.IS_DEBUGGABLE) {
                Slog.i(
                        TAG,
                        "setUsbDataSignal: false, Port status: " + usbPort.getStatus() == null
                                ? "null"
                                : usbPort.getStatus().toString());
            }
            if (usbPortIsConnected(usbPort.getStatus())) {
                return true;
            }
        }
        return false;
    }

    private boolean usbPortIsConnected(UsbPortStatus usbPortStatus) {
        return usbPortStatus != null && usbPortStatus.isConnected();
    }

    private void registerReceiver() {
        final IntentFilter filter = new IntentFilter();
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        filter.addAction(ACTION_LOCKED_BOOT_COMPLETED);
        filter.addAction(ACTION_USB_PORT_CHANGED);
        filter.addAction(ACTION_USB_ACCESSORY_ATTACHED);
        filter.addAction(ACTION_USB_ACCESSORY_DETACHED);
        filter.addAction(ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(ACTION_USB_DEVICE_DETACHED);

        mContext.registerReceiverAsUser(
                mUsbProtectionBroadcastReceiver, UserHandle.ALL, filter, null, null);

        mContext.registerReceiverAsUser(
                new NotificationSilenceReceiver(),
                UserHandle.ALL,
                new IntentFilter(ACTION_SILENCE_NOTIFICATION),
                null,
                null,
                Context.RECEIVER_NOT_EXPORTED);

        mBroadcastReceiverIsRegistered = true;
    }

    private void registerKeyguardLockListener() {
        KeyguardLockedStateListener keyguardListener =
                new KeyguardLockedStateListener() {
                    @Override
                    public void onKeyguardLockedStateChanged(boolean isKeyguardLocked) {
                        Slog.d(TAG, "onKeyguardLockedStateChanged: " + isKeyguardLocked);
                        if (!isKeyguardLocked) {
                            mDelayedDisableHandler.removeCallbacksAndMessages(null);
                            cleanUpNotificationHandlerTasks();
                            if (!currentUserIsGuest()) {
                                setUsbDataSignalIfPossible(true);
                                mIsAfterFirstUnlock = true;
                            }
                        } else {
                            setUsbDataSignalIfPossible(false);
                        }
                    }
                };
        mKeyguardManager.addKeyguardLockedStateListener(
                mUsbDataSignalUpdateExecutor, keyguardListener);
    }

    private void unregisterKeyguardLockListener() {
        mKeyguardManager.removeKeyguardLockedStateListener(mKeyguardLockedStateListener);
    }

    private void unregisterReceiver() {
        mContext.unregisterReceiver(mUsbProtectionBroadcastReceiver);
        mBroadcastReceiverIsRegistered = false;
    }

    // TODO:(b/428090717) Fix intent resolution during boot time
    private PendingIntent createHelpPendingIntent() {
        String helpIntentActivityUri =
                mContext.getString(R.string.config_help_url_action_disabled_by_advanced_protection);
        try {
            Intent helpIntent = Intent.parseUri(helpIntentActivityUri, Intent.URI_INTENT_SCHEME);
            if (helpIntent == null) {
                Slog.w(TAG, "Failed to parse help intent " + helpIntentActivityUri);
                return null;
            }
            helpIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ResolveInfo resolveInfo = mContext.getPackageManager().resolveActivity(helpIntent, 0);
            if (resolveInfo != null && resolveInfo.activityInfo != null) {
                return PendingIntent.getActivityAsUser(
                        mContext,
                        0,
                        helpIntent,
                        PendingIntent.FLAG_IMMUTABLE,
                        null,
                        UserHandle.of(ActivityManager.getCurrentUser()));
            } else {
                Slog.w(TAG, "Failed to resolve help intent " + resolveInfo);
            }
        } catch (URISyntaxException e) {
            Slog.e(TAG, "Failed to create help intent", e);
            return null;
        }

        return null;
    }

    private boolean currentUserIsGuest() {
        UserInfo currentUserInfo = mUserManager.getUserInfo(ActivityManager.getCurrentUser());
        return currentUserInfo != null && currentUserInfo.isGuest();
    }

    private boolean canSetUsbDataSignal() {
        if (Build.IS_DEBUGGABLE) {
            Slog.i(TAG, "USB_HAL_VERSION: " + mUsbManager.getUsbHalVersion());
        }
        return mUsbManager.getUsbHalVersion() >= UsbManager.USB_HAL_V1_3;
    }

    private class NotificationSilenceReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_SILENCE_NOTIFICATION.equals(intent.getAction())) {
                mSilenceDataNotification |=
                        intent.getBooleanExtra(EXTRA_SILENCE_DATA_NOTIFICATION, false);
                mSilencePowerNotification |=
                        intent.getBooleanExtra(EXTRA_SILENCE_POWER_NOTIFICATION, false);
                sendNotification(
                        mContext.getString(R.string.usb_apm_usb_notification_silenced_title),
                        mContext.getString(R.string.usb_apm_usb_notification_silenced_text),
                        null,
                        AdvancedProtectionProtoEnums.USB_NOTIFICATION_TYPE_SILENCE);
            }
        }
    }
}
