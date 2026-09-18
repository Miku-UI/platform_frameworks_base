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

import static android.hardware.usb.UsbAuthorizationStatus.AUTHORIZED;
import static android.hardware.usb.UsbAuthorizationStatus.DENIED;
import static android.hardware.usb.UsbAuthorizationStatus.DENIED_AND_DEFERRED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.hardware.usb.DeviceFilter;
import android.hardware.usb.IUsbAuthManager;
import android.hardware.usb.IUsbSerialReader;
import android.hardware.usb.InternalAuthorizationPinModeReason;
import android.hardware.usb.UsbAuthDeviceInfo;
import android.hardware.usb.UsbAuthorizationSystemState;
import android.hardware.usb.UsbConfiguration;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.AtomicFile;
import android.util.Xml;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.R;
import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;
import com.android.server.LocalServices;
import com.android.server.usb.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/** Tests for {@link com.android.server.usb.UsbAuthManager} atest UsbTests:UsbAuthManagerTest */
@RunWith(AndroidJUnit4.class)
@EnableFlags({Flags.FLAG_ENABLE_USB_HOST_AUTHORIZATION})
public class UsbAuthManagerTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    @Rule public TemporaryFolder mTempFolder = new TemporaryFolder();

    private Context mContext;
    @Mock private UserManager mUserManager;
    @Mock private IUsbAuthManager mService;
    @Mock private UsbHostManager mHostManager;
    @Mock private IBinder mBinder;
    @Mock private Handler mHandler;
    @Mock private NotificationManager mNotificationManager;
    private UsbAuthManager.ProvisioningListener mDeviceProvisionedListener;
    private ArgumentCaptor<BroadcastReceiver> mDismissNotificationCaptor =
            ArgumentCaptor.forClass(BroadcastReceiver.class);
    private ArgumentCaptor<Runnable> mTimeoutCaptor = ArgumentCaptor.forClass(Runnable.class);
    private AtomicFile mAtomicFile;

    private UsbAuthManager mUsbAuthManager;

    private static final int TEST_USER_ID_FULL_ADMIN = 10;
    private static final int TEST_USER_ID_GUEST = 11;

    private static final UserInfo TEST_USER_INFO_FULL_ADMIN =
            new UserInfo(
                    TEST_USER_ID_FULL_ADMIN,
                    "full admin",
                    UserInfo.FLAG_FULL | UserInfo.FLAG_ADMIN);
    private static final UserInfo TEST_USER_INFO_GUEST =
            new UserInfo(TEST_USER_ID_GUEST, "guest", 0);

    private final ArrayMap<String, UsbDeviceFingerprint> mFingerprints = new ArrayMap<>();
    private final ArrayMap<String, UsbDevice> mDevices = new ArrayMap<>();

    static class LocalSerialReader extends IUsbSerialReader.Stub {
        String mSerialNumber;

        LocalSerialReader(String serialNumber) {
            mSerialNumber = serialNumber;
        }

        @Override
        public String getSerial(String packageName) {
            return mSerialNumber;
        }
    }

    // Create a fingerprint with a unique hash. Both device + hashcode must match for a fingerprint
    // to be considered the same so use this to create multiple fingerprints for a single device.
    UsbDeviceFingerprint createFingerprint(UsbDevice device, int hashCode, long lastSeen) {
        DeviceFilter df = new DeviceFilter(device);
        byte[] hashBytes =
                new byte[] {
                    (byte) (hashCode >> 24),
                    (byte) (hashCode >> 16),
                    (byte) (hashCode >> 8),
                    (byte) hashCode
                };
        UsbDeviceFingerprint.Hashcode descriptorHash =
                UsbDeviceFingerprint.Hashcode.fromString(
                        Base64.getEncoder().encodeToString(hashBytes));
        UsbDeviceFingerprint.Hashcode emptyHashcode =
                UsbDeviceFingerprint.Hashcode.createEmptyHashcode();
        return new UsbDeviceFingerprint(df, descriptorHash, emptyHashcode, 0, false, lastSeen);
    }

    private UsbDevice getDeviceCopy(String name, String serialNumber) {
        return new UsbDevice.Builder(
                        name, // name
                        0x1234, // vendorId
                        0x5678, // productId
                        0,
                        0,
                        0,
                        "Google",
                        "Fake Product",
                        "version",
                        new UsbConfiguration[0], // configurations
                        "",
                        false,
                        false,
                        false,
                        false,
                        false // unused fields
                        )
                .build(new LocalSerialReader(serialNumber));
    }

    // Helper record type for test data.
    public record TestData(
            String deviceAddress,
            UsbAuthDeviceInfo deviceInfo,
            UsbDevice device,
            UsbDeviceFingerprint fingerprint) {}

    private ArrayMap<Integer, TestData> mTestData = new ArrayMap<>();

    private static final Integer FAKE0 = 0;
    private static final Integer FAKE1 = 1;
    private static final Integer FAKE2 = 2;

    private AutoCloseable mMocks;

    @Before
    public void setUp() throws Exception {
        LocalServices.removeAllServicesForTest();
        mMocks = MockitoAnnotations.openMocks(this);
        mContext = spy(InstrumentationRegistry.getInstrumentation().getContext());

        doAnswer(
                        (invocation) -> {
                            String name = invocation.getArgument(0);
                            if (name == Context.NOTIFICATION_SERVICE) {
                                return mNotificationManager;
                            }

                            return invocation.callRealMethod();
                        })
                .when(mContext)
                .getSystemService(anyString());

        doReturn(mock(Intent.class))
                .when(mContext)
                .registerReceiver(mDismissNotificationCaptor.capture(), any(), anyInt());

        // If we started the authorization activity, just respond with authorized for the device.
        // Replace this before using if you want different behavior.
        doAnswer(
                        (invocation) -> {
                            Intent intent = invocation.getArgument(0);
                            UsbDevice device =
                                    intent.getParcelableExtra(
                                            UsbManager.EXTRA_DEVICE, UsbDevice.class);
                            if (device != null) {
                                mUsbAuthManager.setAuthorizationResponse(device, AUTHORIZED, true);
                            }

                            return null;
                        })
                .when(mContext)
                .startActivityAsUser(any(), any());
        doReturn(mContext).when(mContext).createContextAsUser(any(), anyInt());

        when(mService.asBinder()).thenReturn(mBinder);

        when(mUserManager.getUserInfo(TEST_USER_ID_FULL_ADMIN))
                .thenReturn(TEST_USER_INFO_FULL_ADMIN);
        when(mUserManager.getUserInfo(TEST_USER_ID_GUEST)).thenReturn(TEST_USER_INFO_GUEST);

        mFingerprints.clear();
        mDevices.clear();

        when(mHostManager.getConnectedDeviceFingerprintForAddress(anyString()))
                .thenAnswer(
                        (invocation) -> {
                            String deviceAddress = invocation.getArgument(0);
                            return mFingerprints.get(deviceAddress);
                        });
        when(mHostManager.getConnectedDeviceForAddress(anyString()))
                .thenAnswer(
                        (invocation) -> {
                            String deviceAddress = invocation.getArgument(0);
                            return mDevices.get(deviceAddress);
                        });

        createTestData();

        mDeviceProvisionedListener = spy(new UsbAuthManager.ProvisioningListener(mContext, null));
        doReturn(false).when(mDeviceProvisionedListener).isDeviceInSetup();

        mAtomicFile = new AtomicFile(mTempFolder.newFile("usb-auth-persisted-files.xml"));
        mUsbAuthManager =
                new UsbAuthManager(
                        mContext,
                        mUserManager,
                        mHostManager,
                        mService,
                        mDeviceProvisionedListener,
                        mHandler,
                        mAtomicFile);
        mDeviceProvisionedListener.setAuthManager(mUsbAuthManager);
        mUsbAuthManager.systemReady();
    }

    @After
    public void tearDown() throws Exception {
        mMocks.close();
    }

    private void createTestData() {
        long now = System.currentTimeMillis() - 1;
        long stale = now - UsbDeviceFingerprint.PERSIST_DURATION_WITH_SERIAL_MS;

        String address0 = UsbDevice.getDeviceName(1, 1);
        String address1 = UsbDevice.getDeviceName(1, 2);
        String address2 = UsbDevice.getDeviceName(1, 3);

        UsbAuthDeviceInfo deviceInfo0 = new UsbAuthDeviceInfo();
        deviceInfo0.busNumber = 1;
        deviceInfo0.deviceNumber = 1;

        UsbAuthDeviceInfo deviceInfo1 = new UsbAuthDeviceInfo();
        deviceInfo1.busNumber = 1;
        deviceInfo1.deviceNumber = 2;

        UsbAuthDeviceInfo deviceInfo2 = new UsbAuthDeviceInfo();
        deviceInfo2.busNumber = 1;
        deviceInfo2.deviceNumber = 3;

        UsbDevice device0 = getDeviceCopy(address0, "");
        UsbDevice device1 = getDeviceCopy(address1, "");
        UsbDevice device2 = getDeviceCopy(address2, "SERIAL003");

        UsbDeviceFingerprint fingerprint0 = createFingerprint(device0, 12345, now);
        UsbDeviceFingerprint fingerprint1 = createFingerprint(device1, 34567, now);
        UsbDeviceFingerprint fingerprint2 = createFingerprint(device2, 56789, stale);

        mTestData.clear();
        mTestData.put(FAKE0, new TestData(address0, deviceInfo0, device0, fingerprint0));
        mTestData.put(FAKE1, new TestData(address1, deviceInfo1, device1, fingerprint1));
        mTestData.put(FAKE2, new TestData(address2, deviceInfo2, device2, fingerprint2));
    }

    private void addConnectedDevice(TestData data) {
        mFingerprints.put(data.deviceAddress, data.fingerprint);
        mDevices.put(data.deviceAddress, data.device);
    }

    @Test
    public void testInitialState_isBooted() throws Exception {
        verify(mService).setSystemState(UsbAuthorizationSystemState.BOOTED);
    }

    @Test
    public void testSetup_withAndWithoutChanges() throws Exception {
        reset(mService);
        doReturn(true).when(mDeviceProvisionedListener).isDeviceInSetup();

        mUsbAuthManager.onUpdateScreenLockedState(true);
        verify(mService).setSystemState(UsbAuthorizationSystemState.SET_UP);
        mUsbAuthManager.onUpdateLoggedInState(true, TEST_USER_ID_FULL_ADMIN);
        verify(mService).setSystemState(UsbAuthorizationSystemState.SET_UP);

        doReturn(false).when(mDeviceProvisionedListener).isDeviceInSetup();
        mDeviceProvisionedListener.onChange(false);
        verify(mService).setSystemState(UsbAuthorizationSystemState.SCREEN_LOCKED);
    }

    @Test
    public void testSetup_authorizedDevicePersistsAfterLogin() throws Exception {
        // Put device into setup mode (but have a user ready to log in).
        doReturn(true).when(mDeviceProvisionedListener).isDeviceInSetup();
        mUsbAuthManager.onUpdateLoggedInState(true, TEST_USER_ID_FULL_ADMIN);
        mUsbAuthManager.onUpdateScreenLockedState(false);

        // Put all the fakes into the system
        TestData fake0 = mTestData.get(FAKE0);
        TestData fake1 = mTestData.get(FAKE1);
        addConnectedDevice(fake0);
        addConnectedDevice(fake1);
        mUsbAuthManager.usbDeviceAdded(fake0.deviceAddress);
        mUsbAuthManager.usbDeviceAdded(fake1.deviceAddress);

        // Send an authorized and denied result.
        mUsbAuthManager
                .getEventsListenerForTest()
                .onDeviceAuthorizationStatusChanged(
                        fake0.deviceInfo, AUTHORIZED, UsbAuthorizationSystemState.SET_UP);
        mUsbAuthManager
                .getEventsListenerForTest()
                .onDeviceAuthorizationStatusChanged(
                        fake1.deviceInfo, DENIED_AND_DEFERRED, UsbAuthorizationSystemState.SET_UP);

        // We should have 0 persisted so far (until we finish logging in).
        assertEquals(0, mUsbAuthManager.getPersistedFingerprintsCopyForTesting().size());

        // Now exit set-up
        doReturn(false).when(mDeviceProvisionedListener).isDeviceInSetup();
        mDeviceProvisionedListener.onChange(false);
        verify(mService).setSystemState(UsbAuthorizationSystemState.LOGGED_IN);

        // Now we should have only 1 (fake0 which was authorized)
        ArraySet<UsbDeviceFingerprint> fingerprints =
                mUsbAuthManager.getPersistedFingerprintsCopyForTesting();
        assertEquals(1, fingerprints.size());
        assertTrue(fingerprints.contains(fake0.fingerprint));
    }

    @Test
    public void testLoginFullUser_screenUnlocked_isLoggedIn() throws Exception {
        reset(mService);
        mUsbAuthManager.onUpdateLoggedInState(true, TEST_USER_ID_FULL_ADMIN);
        mUsbAuthManager.onUpdateScreenLockedState(false);
        verify(mService).setSystemState(UsbAuthorizationSystemState.LOGGED_IN);
    }


    @Test
    public void testLoginFullUser_screenLocked_isScreenLocked() throws Exception {
        mUsbAuthManager.onUpdateLoggedInState(true, TEST_USER_ID_FULL_ADMIN);
        mUsbAuthManager.onUpdateScreenLockedState(true);
        verify(mService).setSystemState(UsbAuthorizationSystemState.SCREEN_LOCKED);
    }

    @Test
    public void testLoginGuestUser_screenUnlocked_isScreenLocked() throws Exception {
        mUsbAuthManager.onUpdateLoggedInState(true, TEST_USER_ID_FULL_ADMIN);
        reset(mService);

        mUsbAuthManager.onUpdateLoggedInState(true, TEST_USER_ID_GUEST);
        mUsbAuthManager.onUpdateScreenLockedState(false);

        verify(mService).setSystemState(UsbAuthorizationSystemState.SCREEN_LOCKED);
    }

    @Test
    public void testLogoutFullUser_noOtherFullUsers_isBooted() throws Exception {
        mUsbAuthManager.onUpdateLoggedInState(true, TEST_USER_ID_FULL_ADMIN);
        reset(mService);

        mUsbAuthManager.onUpdateLoggedInState(false, TEST_USER_ID_FULL_ADMIN);
        verify(mService).setSystemState(UsbAuthorizationSystemState.BOOTED);
    }

    @Test
    public void testScreenLockAndUnlock_changesState() throws Exception {
        mUsbAuthManager.onUpdateLoggedInState(true, TEST_USER_ID_FULL_ADMIN);
        mUsbAuthManager.onUpdateScreenLockedState(false);
        verify(mService).setSystemState(UsbAuthorizationSystemState.LOGGED_IN);
        reset(mService);

        mUsbAuthManager.onUpdateScreenLockedState(true);
        verify(mService).setSystemState(UsbAuthorizationSystemState.SCREEN_LOCKED);
        reset(mService);

        mUsbAuthManager.onUpdateScreenLockedState(false);
        verify(mService).setSystemState(UsbAuthorizationSystemState.LOGGED_IN);
    }

    @Test
    public void testCallbacks_onDeviceAuthorizationStatusChanged() throws Exception {
        TestData fake0 = mTestData.get(FAKE0);
        TestData fake1 = mTestData.get(FAKE1);

        // Add the fake address. Should block here because that device is new and not authorized.
        addConnectedDevice(fake0);
        mUsbAuthManager.usbDeviceAdded(fake0.deviceAddress);
        verify(mHostManager, never()).usbDeviceAuthorized(fake0.deviceAddress);

        // Denial should result in no calls to host manager.
        mUsbAuthManager
                .getEventsListenerForTest()
                .onDeviceAuthorizationStatusChanged(
                        fake0.deviceInfo, DENIED, UsbAuthorizationSystemState.BOOTED);
        verify(mHostManager, never()).usbDeviceAuthorized(fake0.deviceAddress);
        verify(mHostManager, never()).usbDeviceDeauthorized(fake0.deviceAddress);

        // First authorization should result in authorized being called.
        mUsbAuthManager
                .getEventsListenerForTest()
                .onDeviceAuthorizationStatusChanged(
                        fake0.deviceInfo, AUTHORIZED, UsbAuthorizationSystemState.BOOTED);
        verify(mHostManager).usbDeviceAuthorized(fake0.deviceAddress);
        verify(mHostManager, never()).usbDeviceDeauthorized(fake0.deviceAddress);

        // Deauthorizing should result in dauthorize being called.
        mUsbAuthManager
                .getEventsListenerForTest()
                .onDeviceAuthorizationStatusChanged(
                        fake0.deviceInfo, DENIED, UsbAuthorizationSystemState.BOOTED);
        verify(mHostManager).usbDeviceDeauthorized(fake0.deviceAddress);

        // Next, check that we block until the device is seen from the host as well.
        mUsbAuthManager
                .getEventsListenerForTest()
                .onDeviceAuthorizationStatusChanged(
                        fake1.deviceInfo, AUTHORIZED, UsbAuthorizationSystemState.BOOTED);
        verify(mHostManager, never()).usbDeviceAuthorized(fake1.deviceAddress);

        addConnectedDevice(fake1);
        mUsbAuthManager.usbDeviceAdded(fake1.deviceAddress);
        verify(mHostManager).usbDeviceAuthorized(fake1.deviceAddress);
    }

    @Test
    public void testCallbacks_askDevice() throws Exception {
        TestData fake0 = mTestData.get(FAKE0);
        TestData fake1 = mTestData.get(FAKE1);

        // Intercept the start dialog activity and do nothing.
        doNothing().when(mContext).startActivityAsUser(any(), any());

        // First add the device and then send an Ask request.
        // This should result in the ask being handled right away on the event.
        addConnectedDevice(fake0);
        mUsbAuthManager.usbDeviceAdded(fake0.deviceAddress);

        // Let the first one be authorized.
        mUsbAuthManager.addFingerprintToPersistedForTest(fake0.fingerprint);
        verify(mService, never()).setAuthorizationStatus(fake0.deviceInfo, AUTHORIZED);
        mUsbAuthManager.getEventsListenerForTest().onDeviceAskForAuthorization(fake0.deviceInfo);
        verify(mService).setAuthorizationStatus(fake0.deviceInfo, AUTHORIZED);

        // Now try the other order. The Ask result won't occur until the device is added.
        mUsbAuthManager.getEventsListenerForTest().onDeviceAskForAuthorization(fake1.deviceInfo);
        verify(mService, never()).setAuthorizationStatus(fake1.deviceInfo, AUTHORIZED);
        addConnectedDevice(fake1);
        mUsbAuthManager.usbDeviceAdded(fake1.deviceAddress);

        // This device won't be authorized until after we set the authorization response.
        verify(mService, never()).setAuthorizationStatus(fake1.deviceInfo, AUTHORIZED);
        mUsbAuthManager.setAuthorizationResponse(fake1.device, AUTHORIZED, true);
        verify(mService).setAuthorizationStatus(fake1.deviceInfo, AUTHORIZED);
    }

    @Test
    public void testCallbacks_allowPersistDevice() throws Exception {
        TestData fake0 = mTestData.get(FAKE0);
        TestData fake1 = mTestData.get(FAKE1);

        // First add the device and then send a check persisted request.
        // This should result in the ask being handled right away on the event.
        addConnectedDevice(fake0);
        mUsbAuthManager.usbDeviceAdded(fake0.deviceAddress);

        verify(mService, never()).setAuthorizationStatus(fake0.deviceInfo, DENIED);
        mUsbAuthManager
                .getEventsListenerForTest()
                .onDeviceCheckPersistedAuthorization(fake0.deviceInfo);
        verify(mService).setAuthorizationStatus(fake0.deviceInfo, DENIED);

        // Push the fingerprint to persisted list. The same check should now pass.
        mUsbAuthManager.addFingerprintToPersistedForTest(fake0.fingerprint);
        mUsbAuthManager
                .getEventsListenerForTest()
                .onDeviceCheckPersistedAuthorization(fake0.deviceInfo);
        verify(mService).setAuthorizationStatus(fake0.deviceInfo, AUTHORIZED);

        // Now try the other order. The check persisted result won't occur until the device is
        // added.
        mUsbAuthManager
                .getEventsListenerForTest()
                .onDeviceCheckPersistedAuthorization(fake1.deviceInfo);
        verify(mService, never()).setAuthorizationStatus(fake1.deviceInfo, DENIED);

        addConnectedDevice(fake1);
        mUsbAuthManager.usbDeviceAdded(fake1.deviceAddress);
        verify(mService).setAuthorizationStatus(fake1.deviceInfo, DENIED);
    }

    @Test
    public void testScreenLocked_deniedDeferredSendsNotification() throws Exception {
        TestData fake0 = mTestData.get(FAKE0);
        TestData fake1 = mTestData.get(FAKE1);

        ArgumentCaptor<Notification> notificationCaptor =
                ArgumentCaptor.forClass(Notification.class);

        // Put system into screen locked state.
        mUsbAuthManager.onUpdateScreenLockedState(true);
        mUsbAuthManager.onUpdateLoggedInState(true, TEST_USER_ID_FULL_ADMIN);
        verify(mService).setSystemState(UsbAuthorizationSystemState.SCREEN_LOCKED);

        // Now send a DenyDefer notice and expect notification to be created
        mUsbAuthManager
                .getEventsListenerForTest()
                .onDeviceAuthorizationStatusChanged(
                        fake0.deviceInfo,
                        DENIED_AND_DEFERRED,
                        UsbAuthorizationSystemState.SCREEN_LOCKED);
        verify(mNotificationManager)
                .notifyAsUser(
                        any(),
                        eq(SystemMessage.NOTE_USB_AUTH_SCREEN_LOCKED_REMINDER),
                        notificationCaptor.capture(),
                        eq(UserHandle.ALL));

        // Assert singular variant.
        assertEquals(
                mContext.getString(R.string.usb_authorization_notify_on_screenlock_message),
                notificationCaptor.getValue().extras.getString(Notification.EXTRA_TEXT));

        Mockito.clearInvocations(mNotificationManager);

        // Now send a second DenyDefer notice. This should update the notification to a new message.
        mUsbAuthManager
                .getEventsListenerForTest()
                .onDeviceAuthorizationStatusChanged(
                        fake1.deviceInfo,
                        DENIED_AND_DEFERRED,
                        UsbAuthorizationSystemState.SCREEN_LOCKED);
        verify(mNotificationManager)
                .notifyAsUser(
                        any(),
                        eq(SystemMessage.NOTE_USB_AUTH_SCREEN_LOCKED_REMINDER),
                        notificationCaptor.capture(),
                        eq(UserHandle.ALL));

        // Assert multiple devices variant.
        assertEquals(
                mContext.getString(
                        R.string.usb_authorization_notify_on_screenlock_message_multiple),
                notificationCaptor.getValue().extras.getString(Notification.EXTRA_TEXT));

        Mockito.clearInvocations(mNotificationManager);
        mUsbAuthManager.onUpdateScreenLockedState(false);
        verify(mService).setSystemState(UsbAuthorizationSystemState.LOGGED_IN);

        // No notifications should be created when the screen isn't locked.
        mUsbAuthManager
                .getEventsListenerForTest()
                .onDeviceAuthorizationStatusChanged(
                        fake0.deviceInfo,
                        DENIED_AND_DEFERRED,
                        UsbAuthorizationSystemState.LOGGED_IN);
        verify(mNotificationManager, never())
                .notifyAsUser(
                        any(),
                        eq(SystemMessage.NOTE_USB_AUTH_SCREEN_LOCKED_REMINDER),
                        notificationCaptor.capture(),
                        eq(UserHandle.ALL));
    }

    @Test
    public void testScreenLocked_deviceRemoved() throws Exception {
        TestData fake0 = mTestData.get(FAKE0);
        TestData fake1 = mTestData.get(FAKE1);

        addConnectedDevice(fake0);
        addConnectedDevice(fake1);
        mUsbAuthManager.usbDeviceAdded(fake0.deviceAddress);
        mUsbAuthManager.usbDeviceAdded(fake1.deviceAddress);

        // Put system into screen locked state.
        mUsbAuthManager.onUpdateScreenLockedState(true);
        mUsbAuthManager.onUpdateLoggedInState(true, TEST_USER_ID_FULL_ADMIN);
        verify(mService).setSystemState(UsbAuthorizationSystemState.SCREEN_LOCKED);

        // Expect notification to be started when we get the deny + defer.
        mUsbAuthManager
                .getEventsListenerForTest()
                .onDeviceAuthorizationStatusChanged(
                        fake0.deviceInfo,
                        DENIED_AND_DEFERRED,
                        UsbAuthorizationSystemState.SCREEN_LOCKED);
        verify(mNotificationManager)
                .notifyAsUser(
                        any(),
                        eq(SystemMessage.NOTE_USB_AUTH_SCREEN_LOCKED_REMINDER),
                        any(),
                        eq(UserHandle.ALL));

        // Send another deny defer to update the notification.
        mUsbAuthManager
                .getEventsListenerForTest()
                .onDeviceAuthorizationStatusChanged(
                        fake1.deviceInfo,
                        DENIED_AND_DEFERRED,
                        UsbAuthorizationSystemState.SCREEN_LOCKED);

        Mockito.clearInvocations(mNotificationManager);

        // The first removal should just update the notification.
        mUsbAuthManager.usbDeviceRemoved(fake1.deviceAddress);
        verify(mNotificationManager)
                .notifyAsUser(
                        any(),
                        eq(SystemMessage.NOTE_USB_AUTH_SCREEN_LOCKED_REMINDER),
                        any(),
                        eq(UserHandle.ALL));

        Mockito.clearInvocations(mNotificationManager);

        // Remove the second device and expect the notification to be cancelled.
        mUsbAuthManager.usbDeviceRemoved(fake0.deviceAddress);
        verify(mNotificationManager)
                .cancelAsUser(
                        any(),
                        eq(SystemMessage.NOTE_USB_AUTH_SCREEN_LOCKED_REMINDER),
                        eq(UserHandle.ALL));
    }

    @Test
    public void testBooted_processAwaitingOnSystemReady() throws Exception {
        TestData fake0 = mTestData.get(FAKE0);
        TestData fake1 = mTestData.get(FAKE1);

        addConnectedDevice(fake0);
        addConnectedDevice(fake1);
        mUsbAuthManager.usbDeviceAdded(fake0.deviceAddress);
        mUsbAuthManager.usbDeviceAdded(fake1.deviceAddress);

        List<UsbAuthDeviceInfo> askList = List.of(fake0.deviceInfo);
        List<UsbAuthDeviceInfo> allowPersistList = List.of(fake1.deviceInfo);

        // Persist the fake used for ask (so we can just check authorization result).
        mUsbAuthManager.addFingerprintToPersistedForTest(fake0.fingerprint);

        when(mService.getDevicesAwaitingAuthorization()).thenReturn(askList);
        when(mService.getDevicesAwaitingPersistedAuthorization()).thenReturn(allowPersistList);

        // We should be starting in booted state.
        verify(mService).setSystemState(UsbAuthorizationSystemState.BOOTED);

        // No device should have been authorized at this point.
        verify(mService, never()).setAuthorizationStatus(any(), anyInt());

        // Trigger system ready which will get the pending devices and run callback handling.
        mUsbAuthManager.systemReady();

        // Ask device should be authorized and allowPersist device should be denied.
        verify(mService).setAuthorizationStatus(fake0.deviceInfo, AUTHORIZED);
        verify(mService).setAuthorizationStatus(fake1.deviceInfo, DENIED);
    }

    @Test
    public void testBooted_authorizePersistent_timeoutOrDismiss() throws Exception {
        TestData fake0 = mTestData.get(FAKE0);
        TestData fake1 = mTestData.get(FAKE1);

        addConnectedDevice(fake0);
        addConnectedDevice(fake1);
        mUsbAuthManager.usbDeviceAdded(fake0.deviceAddress);
        mUsbAuthManager.usbDeviceAdded(fake1.deviceAddress);

        // We should be starting in booted state.
        verify(mService).setSystemState(UsbAuthorizationSystemState.BOOTED);

        // First validate that the timeout works properly.
        mUsbAuthManager.getEventsListenerForTest().onDeviceAskForAuthorization(fake0.deviceInfo);
        verify(mService).setAuthorizationStatus(fake0.deviceInfo, AUTHORIZED);
        verify(mNotificationManager)
                .notifyAsUser(
                        any(),
                        eq(SystemMessage.NOTE_USB_AUTH_FINISH_LOGGING_IN_REMINDER),
                        any(),
                        eq(UserHandle.ALL));
        verify(mHandler).postDelayed(mTimeoutCaptor.capture(), eq(UsbAuthManager.LOGIN_TIMEOUT_MS));
        mTimeoutCaptor.getValue().run();
        verify(mService).setAuthorizationStatus(fake0.deviceInfo, DENIED);
        verify(mNotificationManager)
                .cancelAsUser(
                        any(),
                        eq(SystemMessage.NOTE_USB_AUTH_FINISH_LOGGING_IN_REMINDER),
                        eq(UserHandle.ALL));

        Mockito.clearInvocations(mService);
        Mockito.clearInvocations(mNotificationManager);
        Mockito.clearInvocations(mHandler);

        // Also check the notification dismissal is working.
        mUsbAuthManager.getEventsListenerForTest().onDeviceAskForAuthorization(fake1.deviceInfo);
        verify(mService).setAuthorizationStatus(fake1.deviceInfo, AUTHORIZED);
        verify(mNotificationManager)
                .notifyAsUser(
                        any(),
                        eq(SystemMessage.NOTE_USB_AUTH_FINISH_LOGGING_IN_REMINDER),
                        any(),
                        eq(UserHandle.ALL));
        verify(mHandler).postDelayed(any(), eq(UsbAuthManager.LOGIN_TIMEOUT_MS));

        // Send dismiss broadcast intent directly to the captured notification.
        Intent intent = new Intent(UsbAuthManager.ACTION_NOTIFICATION_DISMISSED);
        mDismissNotificationCaptor.getValue().onReceive(mContext, intent);
        verify(mService).setAuthorizationStatus(fake1.deviceInfo, DENIED);
        verify(mHandler).removeCallbacks(any());
        // We always cancel the notification (even on the dismiss notifier).
        verify(mNotificationManager)
                .cancelAsUser(
                        any(),
                        eq(SystemMessage.NOTE_USB_AUTH_FINISH_LOGGING_IN_REMINDER),
                        eq(UserHandle.ALL));
    }

    @Test
    public void testBooted_authorizePersistent_handleMultiple() throws Exception {
        TestData fake0 = mTestData.get(FAKE0);
        TestData fake1 = mTestData.get(FAKE1);

        ArgumentCaptor<Notification> notificationCaptor =
                ArgumentCaptor.forClass(Notification.class);

        addConnectedDevice(fake0);
        addConnectedDevice(fake1);
        mUsbAuthManager.usbDeviceAdded(fake0.deviceAddress);
        mUsbAuthManager.usbDeviceAdded(fake1.deviceAddress);

        mUsbAuthManager.getEventsListenerForTest().onDeviceAskForAuthorization(fake0.deviceInfo);
        verify(mService).setAuthorizationStatus(fake0.deviceInfo, AUTHORIZED);
        verify(mNotificationManager)
                .notifyAsUser(
                        any(),
                        eq(SystemMessage.NOTE_USB_AUTH_FINISH_LOGGING_IN_REMINDER),
                        notificationCaptor.capture(),
                        eq(UserHandle.ALL));

        // There should have been no timers running and we simply added a new one.
        verify(mHandler, never()).removeCallbacks(any());
        verify(mHandler).postDelayed(any(), eq(UsbAuthManager.LOGIN_TIMEOUT_MS));

        // First we expect a singular text in the notification.
        assertEquals(
                mContext.getString(
                        R.string.usb_authorization_notify_finish_logging_in_message,
                        UsbAuthManager.LOGIN_TIMEOUT_MS / 1000),
                notificationCaptor.getValue().extras.getString(Notification.EXTRA_TEXT));

        Mockito.clearInvocations(mNotificationManager);
        Mockito.clearInvocations(mHandler);
        Mockito.clearInvocations(mService);

        mUsbAuthManager.getEventsListenerForTest().onDeviceAskForAuthorization(fake0.deviceInfo);
        verify(mService).setAuthorizationStatus(fake0.deviceInfo, AUTHORIZED);
        verify(mNotificationManager)
                .notifyAsUser(
                        any(),
                        eq(SystemMessage.NOTE_USB_AUTH_FINISH_LOGGING_IN_REMINDER),
                        notificationCaptor.capture(),
                        eq(UserHandle.ALL));

        // We should have cancelled the previous timer and added a new one.
        verify(mHandler).removeCallbacks(any());
        verify(mHandler).postDelayed(any(), eq(UsbAuthManager.LOGIN_TIMEOUT_MS));

        // Expect that we have the multiple device message.
        assertEquals(
                mContext.getString(
                        R.string.usb_authorization_notify_finish_logging_in_message,
                        UsbAuthManager.LOGIN_TIMEOUT_MS / 1000),
                notificationCaptor.getValue().extras.getString(Notification.EXTRA_TEXT));
    }

    @Test
    public void testBooted_authorizePersistent_deviceRemoved() throws Exception {
        TestData fake0 = mTestData.get(FAKE0);
        TestData fake1 = mTestData.get(FAKE1);

        addConnectedDevice(fake0);
        addConnectedDevice(fake1);
        mUsbAuthManager.usbDeviceAdded(fake0.deviceAddress);
        mUsbAuthManager.usbDeviceAdded(fake1.deviceAddress);

        // First device sets up notification.
        mUsbAuthManager.getEventsListenerForTest().onDeviceAskForAuthorization(fake0.deviceInfo);
        verify(mService).setAuthorizationStatus(fake0.deviceInfo, AUTHORIZED);
        verify(mNotificationManager)
                .notifyAsUser(
                        any(),
                        eq(SystemMessage.NOTE_USB_AUTH_FINISH_LOGGING_IN_REMINDER),
                        any(),
                        eq(UserHandle.ALL));

        Mockito.clearInvocations(mNotificationManager);
        Mockito.clearInvocations(mHandler);
        Mockito.clearInvocations(mService);

        // Second device should update notification and timer.
        mUsbAuthManager.getEventsListenerForTest().onDeviceAskForAuthorization(fake1.deviceInfo);
        verify(mService).setAuthorizationStatus(fake1.deviceInfo, AUTHORIZED);
        verify(mNotificationManager)
                .notifyAsUser(
                        any(),
                        eq(SystemMessage.NOTE_USB_AUTH_FINISH_LOGGING_IN_REMINDER),
                        any(),
                        eq(UserHandle.ALL));
        verify(mHandler).removeCallbacks(any());
        verify(mHandler).postDelayed(any(), eq(UsbAuthManager.LOGIN_TIMEOUT_MS));

        Mockito.clearInvocations(mNotificationManager);
        Mockito.clearInvocations(mHandler);

        // Removing one device should update notification and not update timer.
        mUsbAuthManager.usbDeviceRemoved(fake1.deviceAddress);
        verify(mHandler, never()).removeCallbacks(any());
        verify(mHandler, never()).postDelayed(any(), eq(UsbAuthManager.LOGIN_TIMEOUT_MS));
        verify(mNotificationManager)
                .notifyAsUser(
                        any(),
                        eq(SystemMessage.NOTE_USB_AUTH_FINISH_LOGGING_IN_REMINDER),
                        any(),
                        eq(UserHandle.ALL));

        Mockito.clearInvocations(mNotificationManager);

        // Remove the device and expect the notification to be cancelled.
        mUsbAuthManager.usbDeviceRemoved(fake0.deviceAddress);
        verify(mNotificationManager)
                .cancelAsUser(
                        any(),
                        eq(SystemMessage.NOTE_USB_AUTH_FINISH_LOGGING_IN_REMINDER),
                        eq(UserHandle.ALL));

    }

    @Test
    public void testBooted_authorizePersistent_completesOnLogin() throws Exception {
        TestData fake0 = mTestData.get(FAKE0);

        addConnectedDevice(fake0);
        mUsbAuthManager.usbDeviceAdded(fake0.deviceAddress);

        // Now check that logging in fully works.
        mUsbAuthManager.getEventsListenerForTest().onDeviceAskForAuthorization(fake0.deviceInfo);
        verify(mService).setAuthorizationStatus(fake0.deviceInfo, AUTHORIZED);

        mUsbAuthManager.onUpdateLoggedInState(true, TEST_USER_ID_FULL_ADMIN);
        mUsbAuthManager.onUpdateScreenLockedState(false);
        verify(mService).setSystemState(UsbAuthorizationSystemState.LOGGED_IN);

        // We should cancel the timer and dismiss all notifications.
        assertTrue(
                mUsbAuthManager
                        .getPersistedFingerprintsCopyForTesting()
                        .contains(fake0.fingerprint));
        verify(mHandler).removeCallbacks(any());
        verify(mNotificationManager)
                .cancelAsUser(
                        any(),
                        eq(SystemMessage.NOTE_USB_AUTH_FINISH_LOGGING_IN_REMINDER),
                        eq(UserHandle.ALL));
        verify(mNotificationManager)
                .cancelAsUser(
                        any(),
                        eq(SystemMessage.NOTE_USB_AUTH_SCREEN_LOCKED_REMINDER),
                        eq(UserHandle.ALL));
    }

    @Test
    public void testSerialization_writeAndReadback() throws Exception {
        TestData fake0 = mTestData.get(FAKE0);
        TestData fake1 = mTestData.get(FAKE1);

        ArraySet<UsbDeviceFingerprint> fingerprints = new ArraySet<>();
        fingerprints.add(fake0.fingerprint);
        fingerprints.add(fake1.fingerprint);

        UsbDeviceFingerprint[] fpArray = fingerprints.toArray(new UsbDeviceFingerprint[0]);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        TypedXmlSerializer serializer = Xml.resolveSerializer(stream);
        mUsbAuthManager.writeToSerializer(serializer, fpArray);

        // Read back the stream.
        ByteArrayInputStream inputStream = new ByteArrayInputStream(stream.toByteArray());
        TypedXmlPullParser parser = Xml.resolvePullParser(inputStream);

        ArraySet<UsbDeviceFingerprint> readFingerprints = mUsbAuthManager.readFromParser(parser);

        assertEquals(fingerprints.size(), readFingerprints.size());
        assertTrue(readFingerprints.contains(fake0.fingerprint));
        assertTrue(readFingerprints.contains(fake1.fingerprint));
    }

    @Test
    public void testReadPersisted_badOrNoFile_clearsAndStartsEmpty() throws Exception {
        TestData fake0 = mTestData.get(FAKE0);
        TestData fake1 = mTestData.get(FAKE1);

        // Add some fingerprints so that we don't start empty.
        mUsbAuthManager.addFingerprintToPersistedForTest(fake0.fingerprint);
        mUsbAuthManager.addFingerprintToPersistedForTest(fake1.fingerprint);
        assertEquals(2, mUsbAuthManager.getPersistedFingerprintsCopyForTesting().size());

        // Force a read of the bad XML and confirm that there are no persisted devices.
        String badXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

        FileOutputStream out = mAtomicFile.startWrite();
        out.write(badXml.getBytes(StandardCharsets.UTF_8));
        mAtomicFile.finishWrite(out);

        // Read persisted data and confirm that the persisted devices list is cleared.
        mUsbAuthManager.readPersisted();
        mUsbAuthManager.waitForPersistedDeviceData();
        assertEquals(0, mUsbAuthManager.getPersistedFingerprintsCopyForTesting().size());

        // Reset the fingerprints again
        mUsbAuthManager.addFingerprintToPersistedForTest(fake0.fingerprint);
        mUsbAuthManager.addFingerprintToPersistedForTest(fake1.fingerprint);
        assertEquals(2, mUsbAuthManager.getPersistedFingerprintsCopyForTesting().size());

        // Delete the atomic file and reload.
        mAtomicFile.delete();
        mUsbAuthManager.readPersisted();
        mUsbAuthManager.waitForPersistedDeviceData();
        assertEquals(0, mUsbAuthManager.getPersistedFingerprintsCopyForTesting().size());
    }

    @Test
    public void testRemovesStale_updatesLastSeenOnAdded() throws Exception {
        TestData staleFake2 = mTestData.get(FAKE2);
        mUsbAuthManager.addFingerprintToPersistedForTest(staleFake2.fingerprint);

        assertTrue(staleFake2.fingerprint.isStale());

        addConnectedDevice(staleFake2);
        mUsbAuthManager.usbDeviceAdded(staleFake2.deviceAddress);

        ArraySet<UsbDeviceFingerprint> fingerprints =
                mUsbAuthManager.getPersistedFingerprintsCopyForTesting();
        assertEquals(1, fingerprints.size());

        // Fingerprint should no longer be stale.
        assertFalse(
                fingerprints.removeIf(
                        (f) -> {
                            return f.isStale();
                        }));
    }

    @Test
    public void testRemovesStale_onLoadAndLocked() throws Exception {
        TestData fake0 = mTestData.get(FAKE0);
        TestData fake1 = mTestData.get(FAKE1);
        TestData staleFake2 = mTestData.get(FAKE2);
        ArraySet<UsbDeviceFingerprint> fingerprints;

        // Throw these into the persisted list temporarily.
        mUsbAuthManager.addFingerprintToPersistedForTest(fake0.fingerprint);
        mUsbAuthManager.addFingerprintToPersistedForTest(staleFake2.fingerprint);
        fingerprints = mUsbAuthManager.getPersistedFingerprintsCopyForTesting();
        assertEquals(2, fingerprints.size());

        // Go into locked state
        mUsbAuthManager.onUpdateScreenLockedState(true);
        mUsbAuthManager.onUpdateLoggedInState(true, TEST_USER_ID_FULL_ADMIN);
        verify(mService).setSystemState(UsbAuthorizationSystemState.SCREEN_LOCKED);

        // On entering locked (from BOOTED), we should have removed the stale entry.
        fingerprints = mUsbAuthManager.getPersistedFingerprintsCopyForTesting();
        assertEquals(1, fingerprints.size());

        // Add the stale entry back in.
        mUsbAuthManager.addFingerprintToPersistedForTest(fake1.fingerprint);
        mUsbAuthManager.addFingerprintToPersistedForTest(staleFake2.fingerprint);
        fingerprints = mUsbAuthManager.getPersistedFingerprintsCopyForTesting();
        assertEquals(3, fingerprints.size());

        // Now write this to disk and read it back.
        FileOutputStream out = mAtomicFile.startWrite();
        TypedXmlSerializer serializer = Xml.resolveSerializer(out);

        UsbDeviceFingerprint[] fpArray = fingerprints.toArray(new UsbDeviceFingerprint[0]);
        mUsbAuthManager.writeToSerializer(serializer, fpArray);

        mAtomicFile.finishWrite(out);
        mUsbAuthManager.readPersisted();
        mUsbAuthManager.waitForPersistedDeviceData();

        // The fingerprint is gone again once loaded from disk.
        fingerprints = mUsbAuthManager.getPersistedFingerprintsCopyForTesting();
        assertEquals(2, fingerprints.size());
    }

    @Test
    public void testPinAuthorizationMode() throws Exception {
        // We start in booted state.
        assertEquals(UsbAuthorizationSystemState.BOOTED, mUsbAuthManager.getSystemState());

        // First try an invalid reason.
        assertFalse(mUsbAuthManager.pinAuthorizationMode(9999));
        assertEquals(UsbAuthorizationSystemState.BOOTED, mUsbAuthManager.getSystemState());

        // Valid reasons should pin the state.
        assertTrue(
                mUsbAuthManager.pinAuthorizationMode(
                        InternalAuthorizationPinModeReason.REPAIR_MODE));
        assertEquals(mUsbAuthManager.getPinnedState(), mUsbAuthManager.getSystemState());
        assertEquals(UsbAuthorizationSystemState.SET_UP, mUsbAuthManager.getSystemState());

        assertTrue(
                mUsbAuthManager.pinAuthorizationMode(
                        InternalAuthorizationPinModeReason.FACTORY_MODE));
        assertEquals(mUsbAuthManager.getPinnedState(), mUsbAuthManager.getSystemState());
    }
}
