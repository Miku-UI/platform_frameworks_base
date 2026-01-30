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

import static android.hardware.usb.InternalUsbDataSignalDisableReason.USB_DISABLE_REASON_APM;
import static android.hardware.usb.UsbPortStatus.DATA_ROLE_HOST;
import static android.hardware.usb.UsbPortStatus.DATA_ROLE_NONE;
import static android.hardware.usb.UsbPortStatus.DATA_STATUS_DISABLED_FORCE;
import static android.hardware.usb.UsbPortStatus.DATA_STATUS_ENABLED;
import static android.hardware.usb.UsbPortStatus.MODE_DFP;
import static android.hardware.usb.UsbPortStatus.MODE_NONE;
import static android.hardware.usb.UsbPortStatus.POWER_BRICK_STATUS_CONNECTED;
import static android.hardware.usb.UsbPortStatus.POWER_BRICK_STATUS_DISCONNECTED;
import static android.hardware.usb.UsbPortStatus.POWER_BRICK_STATUS_UNKNOWN;
import static android.hardware.usb.UsbPortStatus.POWER_ROLE_SINK;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardLockedStateListener;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.UserInfo;
import android.hardware.usb.IUsbManager;
import android.hardware.usb.IUsbManagerInternal;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbPort;
import android.hardware.usb.UsbPortStatus;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;
import android.security.Flags;
import android.security.advancedprotection.AdvancedProtectionProtoEnums;
import android.security.advancedprotection.AdvancedProtectionManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.android.internal.R;
import com.android.internal.util.FrameworkStatsLog;
import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.security.advancedprotection.AdvancedProtectionService;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;

import java.util.Map;

/**
 * Unit tests for {@link UsbDataAdvancedProtectionHook}.
 *
 * <p>atest FrameworksMockingServicesTests_advanced_protection_mode
 */
@SuppressLint("VisibleForTests")
@RunWith(AndroidJUnit4.class)
public class UsbDataAdvancedProtectionHookTest {

    private static final String TAG = "AdvancedProtectionUsb";
    private static final String HELP_URL_ACTION = "android.settings.TEST_HELP";
    private static final String ACTION_SILENCE_NOTIFICATION =
            "com.android.server.security.advancedprotection.features.silence";
    private static final long TEST_TIMEOUT_MS = 2000;
    private static final int PD_COMPLIANT_ROLE_COMBINATIONS = 433;
    private static final int PD_NON_COMPLIANT_ROLE_COMBINATIONS = 0;

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .mockStatic(PendingIntent.class)
                    .mockStatic(ActivityManager.class)
                    .mockStatic(SystemProperties.class)
                    .mockStatic(Settings.Secure.class)
                    .mockStatic(FrameworkStatsLog.class)
                    .mockStatic(Intent.class)
                    .build();

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Context mContext;

    @Mock private AdvancedProtectionService mAdvancedProtectionService;
    @Mock private IUsbManagerInternal mUsbManagerInternal;
    @Mock private KeyguardManager mKeyguardManager;
    @Mock private NotificationManager mNotificationManager;
    @Mock private Handler mDelayDisableHandler;
    @Mock private Handler mDelayedNotificationHandler;
    @Mock private UsbManager mUsbManager;
    @Mock private UserManager mUserManager;

    @Captor private ArgumentCaptor<BroadcastReceiver> mBroadcastReceiverCaptor;
    @Captor private ArgumentCaptor<Runnable> mRunnableCaptor;
    @Captor private ArgumentCaptor<Notification> mNotificationCaptor;
    @Captor private ArgumentCaptor<Integer> mNotificationIdCaptor;
    @Captor private ArgumentCaptor<String> mNotificationTagCaptor;
    @Captor private ArgumentCaptor<UserHandle> mUserHandleCaptor;
    @Captor private ArgumentCaptor<IntentFilter> mIntentFilterCaptor;
    @Captor private ArgumentCaptor<KeyguardLockedStateListener> mKeyguardLockedStateListenerCaptor;

    private AtomicBoolean mApmRequestedUsbDataStatusBoolean = new AtomicBoolean(false);
    private UsbDataAdvancedProtectionHook mUsbDataHook;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    public void setupAndEnableFeature(
            boolean replugRequired, boolean dataForHighPower, boolean afterFirstUnlock)
            throws RemoteException {
        mUsbDataHook =
                new UsbDataAdvancedProtectionHook(
                        mContext,
                        mAdvancedProtectionService,
                        mUsbManager,
                        mUsbManagerInternal,
                        mKeyguardManager,
                        mNotificationManager,
                        mUserManager,
                        mDelayDisableHandler,
                        mDelayedNotificationHandler,
                        mApmRequestedUsbDataStatusBoolean,
                        true, // canSetUsbDataSignal
                        afterFirstUnlock); // afterFirstUnlock
        when(mUsbManager.getUsbHalVersion()).thenReturn(UsbManager.USB_HAL_V2_0);
        doReturn(replugRequired)
                .when(
                        () ->
                                SystemProperties.getBoolean(
                                        eq(
                                                "ro.usb.data_protection.disable_when_locked.replug_required_upon_enable"),
                                        anyBoolean()));
        doReturn(dataForHighPower)
                .when(
                        () ->
                                SystemProperties.getBoolean(
                                        eq(
                                                "ro.usb.data_protection.disable_when_locked.data_required_for_high_power_charge"),
                                        anyBoolean()));
        doReturn(true)
                .when(
                        () ->
                                SystemProperties.getBoolean(
                                        eq("ro.usb.data_protection.disable_when_locked.supported"),
                                        anyBoolean()));
        doReturn(TEST_TIMEOUT_MS)
                .when(
                        () ->
                                SystemProperties.getLong(
                                        eq(
                                                "ro.usb.data_protection.disable_when_locked.power_brick_connection_check_timeout"),
                                        anyLong()));
        doReturn(TEST_TIMEOUT_MS)
                .when(
                        () ->
                                SystemProperties.getLong(
                                        eq(
                                                "ro.usb.data_protection.disable_when_locked.pd_compliance_check_timeout"),
                                        anyLong()));
        doReturn(TEST_TIMEOUT_MS).when(() -> SystemProperties.getLong(anyString(), anyLong()));

        // Used for notification builder
        doReturn(1)
                .when(
                        () ->
                                Settings.Secure.getIntForUser(
                                        any(ContentResolver.class),
                                        anyString(),
                                        anyInt(),
                                        anyInt()));
        when(mContext.getString(anyInt())).thenReturn("Test String");
        when(mContext.getString(
                        eq(R.string.config_help_url_action_disabled_by_advanced_protection)))
                .thenReturn(HELP_URL_ACTION);
        when(mAdvancedProtectionService.isUsbDataProtectionEnabled()).thenReturn(true);

        when(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST))
                .thenReturn(true);
        when(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_ACCESSORY))
                .thenReturn(true);
        when(mUsbManagerInternal.enableUsbDataSignal(anyBoolean(), anyInt())).thenReturn(true);
        setupMocksForSilenceIntent();
        mUsbDataHook.onAdvancedProtectionChanged(true);
    }

    private void setupMocksForSilenceIntent() throws RemoteException {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = new ActivityInfo();
        Intent mockHelpIntent = mock(Intent.class);
        doReturn(mockHelpIntent)
                .when(() -> Intent.parseUri(eq(HELP_URL_ACTION), eq(Intent.URI_INTENT_SCHEME)));
        when(mContext.getPackageManager().resolveActivity(eq(mockHelpIntent), eq(0)))
                .thenReturn(resolveInfo);
        PendingIntent mockHelpPendingIntent = mock(PendingIntent.class);
        doReturn(mockHelpPendingIntent)
                .when(
                        () ->
                                PendingIntent.getActivityAsUser(
                                        eq(mContext),
                                        eq(0),
                                        eq(mockHelpIntent),
                                        eq(PendingIntent.FLAG_IMMUTABLE),
                                        isNull(),
                                        any(UserHandle.class)));
    }

    @Test
    @DisableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void isAvailable_whenFeatureFlagDisabled_doesNothing() throws RemoteException {
        setupAndEnableFeature(false, false, true);

        verifyNoInteractions(mUsbManagerInternal);
        verifyNoInteractions(mUsbManager);
        verifyNoInteractions(mNotificationManager);
    }

    @Test
    @DisableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void isAvailable_whenFeatureFlagDisabled_returnsFalse() throws RemoteException {
        setupAndEnableFeature(false, false, true);

        assertFalse(mUsbDataHook.isAvailable());
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void isAvailable_whenFeatureFlagEnabledAndDeviceSupportsFeature_returnsTrue()
            throws RemoteException {
        setupAndEnableFeature(false, false, true);

        assertTrue(mUsbDataHook.isAvailable());
    }

    // For bootup of Advanced Protection mode and enablement of Advanced Protection mode through ADB
    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void onAdvancedProtectionChanged_whenEnabled_registersReceiverAndDisablesUsb()
            throws RemoteException {
        clearAllUsbConnections();
        when(mKeyguardManager.isKeyguardLocked()).thenReturn(true);
        setupAndEnableFeature(false, false, true);

        verifyAdvancedProtectionChanged_registersReceiverRegisterReceiverBehavior();
        verify(mKeyguardManager)
                .addKeyguardLockedStateListener(
                        any(ExecutorService.class), any(KeyguardLockedStateListener.class));
        verify(mUsbManagerInternal).enableUsbDataSignal(eq(false), eq(USB_DISABLE_REASON_APM));
    }

    // For enablement of Advanced Protection mode through Settings page.
    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void
            onAdvancedProtectionChanged_whenEnabledInUnlockedState_registersReceiverAndNotDisableUsb()
                    throws RemoteException {
        clearAllUsbConnections();
        when(mKeyguardManager.isKeyguardLocked()).thenReturn(false);
        setupAndEnableFeature(false, false, true);

        verifyAdvancedProtectionChanged_registersReceiverRegisterReceiverBehavior();
        verify(mUsbManagerInternal, never()).enableUsbDataSignal(anyBoolean(), anyInt());
    }

    private void verifyAdvancedProtectionChanged_registersReceiverRegisterReceiverBehavior()
            throws RemoteException {
        // Verify receiver is registered for the correct user and with the correct intent filters
        verify(mContext, times(1))
                .registerReceiverAsUser(
                        any(BroadcastReceiver.class),
                        mUserHandleCaptor.capture(),
                        mIntentFilterCaptor.capture(),
                        isNull(),
                        isNull());

        // Silence receiver
        verify(mContext, times(1))
                .registerReceiverAsUser(
                        any(BroadcastReceiver.class),
                        mUserHandleCaptor.capture(),
                        mIntentFilterCaptor.capture(),
                        isNull(),
                        isNull(),
                        anyInt());

        IntentFilter mainFilter = mIntentFilterCaptor.getAllValues().get(0);
        assertEquals(UserHandle.ALL, mUserHandleCaptor.getAllValues().get(0));

        assertEquals(6, mainFilter.countActions());
        assertTrue(mainFilter.hasAction(Intent.ACTION_LOCKED_BOOT_COMPLETED));
        assertTrue(mainFilter.hasAction(UsbManager.ACTION_USB_PORT_CHANGED));

        IntentFilter silenceFilter = mIntentFilterCaptor.getAllValues().get(1);
        // Verify it's registered for all users.
        assertEquals(UserHandle.ALL, mUserHandleCaptor.getAllValues().get(1));
        // Verify it listens for the specific silence action.
        assertEquals(1, silenceFilter.countActions());
        assertTrue(silenceFilter.hasAction(ACTION_SILENCE_NOTIFICATION));
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void onAdvancedProtectionChanged_whenDisabled_unregistersReceiverAndEnablesUsb()
            throws RemoteException {
        setupAndEnableFeature(false, false, true);

        mUsbDataHook.onAdvancedProtectionChanged(false);

        verify(mContext).unregisterReceiver(any(BroadcastReceiver.class));
        verify(mUsbManagerInternal).enableUsbDataSignal(eq(true), eq(USB_DISABLE_REASON_APM));
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void userPresentAndUnlocked_enablesUsbAndClearsTasks() throws RemoteException {
        setupAndEnableFeature(false, false, true);
        UserInfo mockUserInfo = mock(UserInfo.class);
        when(mockUserInfo.isGuest()).thenReturn(false);
        when(mUserManager.getUserInfo(anyInt())).thenReturn(mockUserInfo);

        getKeyguardLockedStateListener().onKeyguardLockedStateChanged(false);

        verify(mDelayDisableHandler).removeCallbacksAndMessages(isNull());
        verify(mDelayedNotificationHandler).removeCallbacksAndMessages(isNull());
        verify(mUsbManagerInternal).enableUsbDataSignal(eq(true), eq(USB_DISABLE_REASON_APM));
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void onFirstBoot_disablesUsbRegardlessOfConnectionState() throws RemoteException {
        setupAndEnableFeature(false, false, false);
        when(mUsbManagerInternal.enableUsbDataSignal(anyBoolean(), anyInt())).thenReturn(true);
        BroadcastReceiver receiver = getAndCaptureReceiver();
        addUsbConnection(
                UsbPortStatus.POWER_ROLE_SINK, UsbPortStatus.POWER_BRICK_STATUS_DISCONNECTED);

        receiver.onReceive(mContext, new Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED));

        verify(mUsbManagerInternal).enableUsbDataSignal(eq(false), eq(USB_DISABLE_REASON_APM));
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void userPresentAndUnlocked_butUserIsGuest_keepsUsbDisabled() throws RemoteException {
        setupAndEnableFeature(false, false, true);
        UserInfo mockUserInfo = mock(UserInfo.class);
        when(mockUserInfo.isGuest()).thenReturn(true);
        when(mUserManager.getUserInfo(anyInt())).thenReturn(mockUserInfo);

        getKeyguardLockedStateListener().onKeyguardLockedStateChanged(false);

        verify(mDelayDisableHandler).removeCallbacksAndMessages(isNull());
        verify(mDelayedNotificationHandler).removeCallbacksAndMessages(isNull());
        verify(mUsbManagerInternal, never())
                .enableUsbDataSignal(eq(true), eq(USB_DISABLE_REASON_APM));
    }

    private void clearAllUsbConnections() {
        UsbPort mockUsbPort = new UsbPort(mUsbManager, "temp", 0, 0, true, true, true, 0);
        UsbPortStatus mockUsbPortStatus =
                new UsbPortStatus(
                        MODE_NONE, 0, DATA_ROLE_NONE, 0, 0, 0, DATA_STATUS_ENABLED, false, 0);
        mApmRequestedUsbDataStatusBoolean.set(false);
        when(mUsbManager.getPorts()).thenReturn(List.of(mockUsbPort));
        when(mockUsbPort.getStatus()).thenReturn(mockUsbPortStatus);
    }

    private void addUsbConnection(int powerRole, int powerBrickStatus) {
        UsbPort mockUsbPort = new UsbPort(mUsbManager, "temp", 0, 0, true, true, true, 0);
        UsbPortStatus mockUsbPortStatus =
                new UsbPortStatus(
                        MODE_DFP, // currently connected
                        powerRole,
                        DATA_ROLE_HOST,
                        0,
                        0,
                        0,
                        DATA_STATUS_ENABLED,
                        false,
                        powerBrickStatus);
        mApmRequestedUsbDataStatusBoolean.set(false);
        when(mockUsbPort.getStatus()).thenReturn(mockUsbPortStatus);
        when(mUsbManager.getPorts()).thenReturn(List.of(mockUsbPort));
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void screenOffAndLocked_withNoConnectedDevice_disablesUsb() throws RemoteException {
        setupAndEnableFeature(false, false, true);
        clearAllUsbConnections();

        getKeyguardLockedStateListener().onKeyguardLockedStateChanged(true);

        verify(mUsbManagerInternal, times(1))
                .enableUsbDataSignal(eq(false), eq(USB_DISABLE_REASON_APM));
    }

    private KeyguardLockedStateListener getKeyguardLockedStateListener() {
        verify(mKeyguardManager)
                .addKeyguardLockedStateListener(
                        any(ExecutorService.class), mKeyguardLockedStateListenerCaptor.capture());
        return mKeyguardLockedStateListenerCaptor.getValue();
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void screenOffAndLocked_withConnectedDevice_doesNothing() throws RemoteException {
        setupAndEnableFeature(false, false, true);
        when(mKeyguardManager.isKeyguardLocked()).thenReturn(true);
        BroadcastReceiver receiver = getAndCaptureReceiver();
        addUsbConnection(
                UsbPortStatus.POWER_ROLE_SINK, UsbPortStatus.POWER_BRICK_STATUS_DISCONNECTED);

        getKeyguardLockedStateListener().onKeyguardLockedStateChanged(true);

        verify(mUsbManagerInternal, never()).enableUsbDataSignal(anyBoolean(), anyInt());
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void usbPortChanged_disconnected_clearsNotifications() throws RemoteException {
        setupAndEnableFeature(false, false, true);
        BroadcastReceiver receiver = getAndCaptureReceiver();
        when(mKeyguardManager.isKeyguardLocked()).thenReturn(true);
        Intent intent = new Intent(UsbManager.ACTION_USB_PORT_CHANGED);
        UsbPortStatus mockUsbPortStatus = new UsbPortStatus(0, 0, 0, 0, 0, 0, 0, false, 0);
        intent.putExtra(UsbManager.EXTRA_PORT_STATUS, mockUsbPortStatus);

        receiver.onReceive(mContext, intent);

        verify(mDelayedNotificationHandler).removeCallbacksAndMessages(isNull());
        verify(mNotificationManager).cancel(TAG, SystemMessage.NOTE_USB_DATA_PROTECTION_REMINDER);
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void usbPortChanged_lockedAndDisconnected_delaysDisableUsb() throws RemoteException {
        setupAndEnableFeature(false, false, true);
        when(mKeyguardManager.isKeyguardLocked()).thenReturn(true);
        UsbPortStatus mockUsbPortStatus =
                new UsbPortStatus(0, 0, 0, 0, 0, 0, DATA_STATUS_DISABLED_FORCE, false, 0);
        mApmRequestedUsbDataStatusBoolean.set(true);
        clearAllUsbConnections();
        BroadcastReceiver receiver = getAndCaptureReceiver();
        Intent intent = new Intent(UsbManager.ACTION_USB_PORT_CHANGED);
        intent.putExtra(UsbManager.EXTRA_PORT_STATUS, mockUsbPortStatus);

        receiver.onReceive(mContext, intent);
        verify(mDelayDisableHandler).postDelayed(mRunnableCaptor.capture(), anyLong());
        mRunnableCaptor.getValue().run();

        verify(mUsbManagerInternal).enableUsbDataSignal(eq(false), eq(USB_DISABLE_REASON_APM));
        verify(mDelayedNotificationHandler).removeCallbacksAndMessages(isNull());
        verify(mNotificationManager).cancel(TAG, SystemMessage.NOTE_USB_DATA_PROTECTION_REMINDER);
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void
            usbPortChanged_lockedAndPowerBrickConnectedAndPdCompliant_dataRequiredForHighPowerCharge_sendsChargeNotification()
                    throws RemoteException {
        setupAndEnableFeature(false, true, true); // Data required for high power charge
        String expectedTitle = "Charge Title";
        String expectedText = "Charge Text";
        when(mContext.getString(R.string.usb_apm_usb_plugged_in_when_locked_notification_title))
                .thenReturn(expectedTitle);
        when(mContext.getString(
                        R.string.usb_apm_usb_plugged_in_when_locked_charge_notification_text))
                .thenReturn(expectedText);
        when(mKeyguardManager.isKeyguardLocked()).thenReturn(true);
        UsbPortStatus mockUsbPortStatus =
                new UsbPortStatus(
                        MODE_DFP,
                        POWER_ROLE_SINK,
                        DATA_ROLE_HOST,
                        PD_COMPLIANT_ROLE_COMBINATIONS,
                        0,
                        0,
                        DATA_STATUS_DISABLED_FORCE,
                        false,
                        UsbPortStatus.POWER_BRICK_STATUS_CONNECTED);
        mApmRequestedUsbDataStatusBoolean.set(true);

        BroadcastReceiver receiver = getAndCaptureReceiver();
        Intent intent = new Intent(UsbManager.ACTION_USB_PORT_CHANGED);
        intent.putExtra(UsbManager.EXTRA_PORT_STATUS, mockUsbPortStatus);

        receiver.onReceive(mContext, intent);

        verify(mDelayedNotificationHandler, never()).postDelayed(any(), anyInt());
        verify(mNotificationManager)
                .notifyAsUser(
                        mNotificationTagCaptor.capture(),
                        mNotificationIdCaptor.capture(),
                        mNotificationCaptor.capture(),
                        any(UserHandle.class));
        assertEquals(TAG, mNotificationTagCaptor.getValue());

        verify(mAdvancedProtectionService)
                .logDialogShown(
                        eq(AdvancedProtectionManager.FEATURE_ID_DISALLOW_USB),
                        eq(AdvancedProtectionManager.SUPPORT_DIALOG_TYPE_BLOCKED_INTERACTION),
                        eq(false));
        checkNotificationIntents(mNotificationCaptor.getValue());
        Notification notification = mNotificationCaptor.getValue();
        assertEquals(Notification.VISIBILITY_PUBLIC, notification.visibility);
        assertEquals(expectedTitle, notification.extras.getString(Notification.EXTRA_TITLE));
        assertEquals(expectedText, notification.extras.getString(Notification.EXTRA_BIG_TEXT));
    }

    private void checkNotificationIntents(Notification notification) {
        assertNotNull(notification);
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void usbPortChanged_lockedAndPdCompliant_sendsDataNotification() throws RemoteException {
        setupAndEnableFeature(false, false, true); // Data NOT required for high power charge
        when(mContext.getString(R.string.usb_apm_usb_plugged_in_when_locked_notification_title))
                .thenReturn("Data Title");
        when(mContext.getString(R.string.usb_apm_usb_plugged_in_when_locked_data_notification_text))
                .thenReturn("Data Text");
        when(mKeyguardManager.isKeyguardLocked()).thenReturn(true);
        UsbPortStatus mockUsbPortStatus =
                new UsbPortStatus(
                        1,
                        POWER_ROLE_SINK,
                        DATA_ROLE_HOST,
                        PD_COMPLIANT_ROLE_COMBINATIONS,
                        0,
                        0,
                        DATA_STATUS_DISABLED_FORCE,
                        false,
                        POWER_BRICK_STATUS_DISCONNECTED);
        mApmRequestedUsbDataStatusBoolean.set(true);

        BroadcastReceiver receiver = getAndCaptureReceiver();
        Intent intent = new Intent(UsbManager.ACTION_USB_PORT_CHANGED);
        intent.putExtra(UsbManager.EXTRA_PORT_STATUS, mockUsbPortStatus);

        receiver.onReceive(mContext, intent);
        verify(mDelayedNotificationHandler).postDelayed(mRunnableCaptor.capture(), anyLong());
        mRunnableCaptor.getValue().run();

        verify(mNotificationManager)
                .notifyAsUser(
                        anyString(),
                        anyInt(),
                        mNotificationCaptor.capture(),
                        any(UserHandle.class));

        assertNotNull(mNotificationCaptor.getValue());
        checkNotificationIntents(mNotificationCaptor.getValue());
        Notification notification = mNotificationCaptor.getValue();
        assertEquals(Notification.VISIBILITY_PUBLIC, notification.visibility);
        assertEquals("Data Title", notification.extras.getString(Notification.EXTRA_TITLE));
        assertEquals("Data Text", notification.extras.getString(Notification.EXTRA_BIG_TEXT));
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void usbPortChanged_notPowerBrickConnectedOrPdCompliant_sendsChargeDataNotification()
            throws RemoteException {
        setupAndEnableFeature(false, false, true);
        String expectedTitle = "Charge Data Title";
        String expectedText = "Charge Data Text";
        when(mContext.getString(R.string.usb_apm_usb_plugged_in_when_locked_notification_title))
                .thenReturn(expectedTitle);
        when(mContext.getString(
                        R.string.usb_apm_usb_plugged_in_when_locked_charge_data_notification_text))
                .thenReturn(expectedText);
        when(mKeyguardManager.isKeyguardLocked()).thenReturn(true);
        UsbPortStatus mockUsbPortStatus =
                new UsbPortStatus(
                        1,
                        POWER_ROLE_SINK,
                        DATA_ROLE_HOST,
                        0,
                        0,
                        0,
                        DATA_STATUS_DISABLED_FORCE,
                        false,
                        POWER_BRICK_STATUS_DISCONNECTED);
        mApmRequestedUsbDataStatusBoolean.set(true);

        BroadcastReceiver receiver = getAndCaptureReceiver();
        Intent intent = new Intent(UsbManager.ACTION_USB_PORT_CHANGED);
        intent.putExtra(UsbManager.EXTRA_PORT_STATUS, mockUsbPortStatus);

        receiver.onReceive(mContext, intent);

        verify(mDelayedNotificationHandler).postDelayed(mRunnableCaptor.capture(), anyLong());
        mRunnableCaptor.getValue().run();
        verify(mNotificationManager)
                .notifyAsUser(
                        anyString(), anyInt(), any(Notification.class), any(UserHandle.class));
        verify(mAdvancedProtectionService)
                .logDialogShown(
                        eq(AdvancedProtectionManager.FEATURE_ID_DISALLOW_USB),
                        eq(AdvancedProtectionManager.SUPPORT_DIALOG_TYPE_BLOCKED_INTERACTION),
                        eq(false));
        verify(mNotificationManager)
                .notifyAsUser(
                        anyString(),
                        anyInt(),
                        mNotificationCaptor.capture(),
                        any(UserHandle.class));

        checkNotificationIntents(mNotificationCaptor.getValue());
        Notification notification = mNotificationCaptor.getValue();
        assertEquals(Notification.VISIBILITY_PUBLIC, notification.visibility);
        assertEquals(expectedTitle, notification.extras.getString(Notification.EXTRA_TITLE));
        assertEquals(
                expectedText, notification.extras.getString(Notification.EXTRA_BIG_TEXT));
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void usbPortChanged_pendingChecks_postsDelayedNotification() throws RemoteException {
        setupAndEnableFeature(false, false, true);
        doReturn(TEST_TIMEOUT_MS)
                .when(
                        () ->
                                SystemProperties.getLong(
                                        eq(
                                                "ro.usb.data_protection.disable_when_locked.power_brick_connection_check_timeout"),
                                        anyLong()));
        doReturn(TEST_TIMEOUT_MS)
                .when(
                        () ->
                                SystemProperties.getLong(
                                        eq(
                                                "ro.usb.data_protection.disable_when_locked.pd_compliance_check_timeout"),
                                        anyLong()));

        mUsbDataHook.onAdvancedProtectionChanged(true);
        when(mKeyguardManager.isKeyguardLocked()).thenReturn(true);
        UsbPortStatus mockUsbPortStatus =
                new UsbPortStatus(
                        1,
                        POWER_ROLE_SINK,
                        DATA_ROLE_HOST,
                        0, // status is pending
                        0,
                        0,
                        DATA_STATUS_DISABLED_FORCE,
                        false,
                        POWER_BRICK_STATUS_UNKNOWN); // status is pending
        mApmRequestedUsbDataStatusBoolean.set(true);
        BroadcastReceiver receiver = getAndCaptureReceiver();
        Intent intent = new Intent(UsbManager.ACTION_USB_PORT_CHANGED);
        intent.putExtra(UsbManager.EXTRA_PORT_STATUS, mockUsbPortStatus);
        when(mContext.getUser()).thenReturn(UserHandle.ALL);

        receiver.onReceive(mContext, intent);

        verify(mDelayedNotificationHandler)
                .postDelayed(mRunnableCaptor.capture(), eq(TEST_TIMEOUT_MS + TEST_TIMEOUT_MS));
        mRunnableCaptor.getValue().run();
        verify(mNotificationManager)
                .notifyAsUser(
                        anyString(), anyInt(), any(Notification.class), any(UserHandle.class));
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void setUsbDataSignal_retriesOnFailure() throws Exception {
        setupAndEnableFeature(false, false, true);
        when(mKeyguardManager.isKeyguardLocked()).thenReturn(true);
        when(mUsbManagerInternal.enableUsbDataSignal(anyBoolean(), anyInt()))
                .thenReturn(false) // Fail first
                .thenReturn(false) // Fail second
                .thenReturn(true); // Succeed third

        mUsbDataHook.onAdvancedProtectionChanged(false);

        verify(mUsbManagerInternal, times(3)).enableUsbDataSignal(eq(true), eq(1));
        verify(
                () ->
                        FrameworkStatsLog.write(
                                FrameworkStatsLog
                                        .ADVANCED_PROTECTION_USB_STATE_CHANGE_ERROR_REPORTED,
                                true,
                                2,
                                AdvancedProtectionProtoEnums
                                        .USB_ERROR_TYPE_CHANGE_DATA_STATUS_FAILED));
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void notification_replugRequired_showsCorrectText() throws RemoteException {
        setupAndEnableFeature(true, true, true); // Replug required
        String expectedTitle = "Replug Title";
        String expectedText = "Replug Text";
        when(mContext.getString(
                        R.string.usb_apm_usb_plugged_in_when_locked_replug_notification_title))
                .thenReturn(expectedTitle);
        when(mContext.getString(
                        R.string.usb_apm_usb_plugged_in_when_locked_replug_notification_text))
                .thenReturn(expectedText);

        when(mKeyguardManager.isKeyguardLocked()).thenReturn(true);
        UsbPortStatus mockUsbPortStatus =
                new UsbPortStatus(
                        1,
                        POWER_ROLE_SINK,
                        DATA_ROLE_HOST,
                        PD_COMPLIANT_ROLE_COMBINATIONS,
                        0,
                        0,
                        DATA_STATUS_DISABLED_FORCE,
                        false,
                        POWER_BRICK_STATUS_CONNECTED);
        mApmRequestedUsbDataStatusBoolean.set(true);
        BroadcastReceiver receiver = getAndCaptureReceiver();
        Intent intent = new Intent(UsbManager.ACTION_USB_PORT_CHANGED);
        intent.putExtra(UsbManager.EXTRA_PORT_STATUS, mockUsbPortStatus);

        receiver.onReceive(mContext, intent);

        verify(mDelayedNotificationHandler).removeCallbacksAndMessages(isNull());
        verify(mNotificationManager)
                .notifyAsUser(
                        anyString(),
                        anyInt(),
                        mNotificationCaptor.capture(),
                        any(UserHandle.class));
        Notification notification = mNotificationCaptor.getValue();
        assertEquals(Notification.VISIBILITY_PUBLIC, notification.visibility);
        assertEquals(expectedTitle, notification.extras.getString(Notification.EXTRA_TITLE));
        assertEquals(
                expectedText, notification.extras.getString(Notification.EXTRA_BIG_TEXT));
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void notificationSilenceReceiver_silencesNotifications() throws RemoteException {
        setupAndEnableFeature(false, false, true);
        String expectedTitle = "Silenced Title";
        when(mContext.getString(R.string.usb_apm_usb_notification_silenced_title))
                .thenReturn(expectedTitle);
        BroadcastReceiver mainReceiver = getAndCaptureReceiver();
        // Get the second registered receiver, which is the NotificationSilenceReceiver
        BroadcastReceiver silenceReceiver = getAndCaptureSilenceReceiver();

        Intent silenceIntent = new Intent(ACTION_SILENCE_NOTIFICATION);
        silenceIntent.putExtra("silence_power_notification", true);
        silenceIntent.putExtra("silence_data_notification", true);

        silenceReceiver.onReceive(mContext, silenceIntent);

        // Verify silenced notification is shown
        verify(mNotificationManager)
                .notifyAsUser(
                        anyString(),
                        anyInt(),
                        mNotificationCaptor.capture(),
                        any(UserHandle.class));
        Notification notification = mNotificationCaptor.getValue();
        assertEquals(expectedTitle, notification.extras.getString(Notification.EXTRA_TITLE));

        // Check that a subsequent power notification would be suppressed
        // Simulate a power brick connection event again
        when(mKeyguardManager.isKeyguardLocked()).thenReturn(true);
        UsbPortStatus usbPortStatus =
                new UsbPortStatus(
                        1,
                        POWER_ROLE_SINK,
                        DATA_ROLE_HOST,
                        PD_NON_COMPLIANT_ROLE_COMBINATIONS,
                        0,
                        0,
                        DATA_STATUS_DISABLED_FORCE,
                        false,
                        POWER_BRICK_STATUS_DISCONNECTED);
        Intent powerDataIntent = new Intent(UsbManager.ACTION_USB_PORT_CHANGED);
        powerDataIntent.putExtra(UsbManager.EXTRA_PORT_STATUS, usbPortStatus);
        mainReceiver.onReceive(mContext, powerDataIntent);
        verify(mDelayedNotificationHandler).postDelayed(mRunnableCaptor.capture(), anyLong());
        mRunnableCaptor.getValue().run();

        // We expect only one notification (the "notification is silenced" one). If another was
        // sent, this would
        // fail.
        verify(mNotificationManager, times(1))
                .notifyAsUser(
                        anyString(), anyInt(), any(Notification.class), any(UserHandle.class));

        // Verify the silent notification was logged
        verify(mAdvancedProtectionService)
                .logDialogShown(
                        AdvancedProtectionManager.FEATURE_ID_DISALLOW_USB,
                        AdvancedProtectionManager.SUPPORT_DIALOG_TYPE_UNKNOWN,
                        false);
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void helpIntent_isCreatedAndAddedToNotification() throws RemoteException {
        setupAndEnableFeature(false, true, true);

        when(mKeyguardManager.isKeyguardLocked()).thenReturn(true);
        UsbPortStatus mockUsbPortStatus =
                new UsbPortStatus(
                        1,
                        POWER_ROLE_SINK,
                        DATA_ROLE_HOST,
                        PD_COMPLIANT_ROLE_COMBINATIONS,
                        0,
                        0,
                        DATA_STATUS_DISABLED_FORCE,
                        false,
                        POWER_BRICK_STATUS_CONNECTED);
        mApmRequestedUsbDataStatusBoolean.set(true);
        BroadcastReceiver receiver = getAndCaptureReceiver();
        Intent intent = new Intent(UsbManager.ACTION_USB_PORT_CHANGED);
        intent.putExtra(UsbManager.EXTRA_PORT_STATUS, mockUsbPortStatus);

        receiver.onReceive(mContext, intent);

        verify(mNotificationManager)
                .notifyAsUser(
                        anyString(),
                        anyInt(),
                        mNotificationCaptor.capture(),
                        any(UserHandle.class));
        Notification notification = mNotificationCaptor.getValue();
        // Verify help intent is attached
        assertNotNull(notification.contentIntent);
    }

    /** Helper to capture the main broadcast receiver. */
    public BroadcastReceiver getAndCaptureReceiver() {
        verify(mContext, times(1))
                .registerReceiverAsUser(
                        mBroadcastReceiverCaptor.capture(),
                        any(UserHandle.class),
                        any(IntentFilter.class),
                        isNull(),
                        isNull());
        return mBroadcastReceiverCaptor.getAllValues().get(0);
    }

    /** Helper to capture the silence broadcast receiver. */
    public BroadcastReceiver getAndCaptureSilenceReceiver() {
        verify(mContext, times(1))
                .registerReceiverAsUser(
                        mBroadcastReceiverCaptor.capture(),
                        any(UserHandle.class),
                        any(IntentFilter.class),
                        isNull(),
                        isNull(),
                        eq(Context.RECEIVER_NOT_EXPORTED));
        return mBroadcastReceiverCaptor.getAllValues().get(1);
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void unexpectedUsbEvent_logsError() throws RemoteException {
        setupAndEnableFeature(false, true, true);
        mApmRequestedUsbDataStatusBoolean.set(false);
        BroadcastReceiver receiver = getAndCaptureReceiver();

        for (Map.Entry<String, Integer> event :
                Map.of(
                                UsbManager.ACTION_USB_ACCESSORY_ATTACHED,
                                AdvancedProtectionProtoEnums
                                        .USB_ERROR_TYPE_UNEXPECTED_ACCESSORY_ATTACHED,
                                UsbManager.ACTION_USB_DEVICE_ATTACHED,
                                AdvancedProtectionProtoEnums
                                        .USB_ERROR_TYPE_UNEXPECTED_DEVICE_ATTACHED,
                                UsbManager.ACTION_USB_ACCESSORY_DETACHED,
                                AdvancedProtectionProtoEnums
                                        .USB_ERROR_TYPE_UNEXPECTED_ACCESSORY_DETACHED,
                                UsbManager.ACTION_USB_DEVICE_DETACHED,
                                AdvancedProtectionProtoEnums
                                        .USB_ERROR_TYPE_UNEXPECTED_DEVICE_DETACHED)
                        .entrySet()) {
            Intent intent = new Intent(event.getKey());

            receiver.onReceive(mContext, intent);

            verify(
                    () ->
                            FrameworkStatsLog.write(
                                    FrameworkStatsLog
                                            .ADVANCED_PROTECTION_USB_STATE_CHANGE_ERROR_REPORTED,
                                    false,
                                    -1,
                                    event.getValue()));
        }
    }
}
