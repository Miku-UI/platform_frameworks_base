/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.security.authenticationpolicy.agent;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.KeyguardManager;
import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager;
import android.companion.DeviceId;
import android.companion.DevicePresenceEvent;
import android.companion.ICompanionDeviceManager;
import android.companion.IOnAssociationsChangedListener;
import android.companion.IOnDevicePresenceEventListener;
import android.companion.virtual.IVirtualDevice;
import android.companion.virtual.IVirtualDeviceManager;
import android.companion.virtual.VirtualDevice;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.VirtualDeviceListener;
import android.content.Context;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricRequestConstants;
import android.hardware.biometrics.BiometricSourceType;
import android.hardware.biometrics.Flags;
import android.hardware.biometrics.events.AuthenticationSucceededInfo;
import android.os.Handler;
import android.os.RemoteException;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;
import android.test.mock.MockContentResolver;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.util.test.FakeSettingsProvider;
import com.android.server.locksettings.LockSettingsInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Presubmit
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class AgentAuthServiceTest {

    private static final int USER_ID = 10;
    private static final long AUTH_INTERVAL = TimeUnit.MINUTES.toMillis(30);

    @Rule
    public final MockitoRule mockito = MockitoJUnit.rule();

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock
    private Context mMockContext;
    @Mock
    private ICompanionDeviceManager mMockCDMService;
    @Mock
    private BiometricManager mBiometricManager;
    @Mock
    private KeyguardManager mKeyguardManager;
    @Mock
    private LockSettingsInternal mLockSettings;
    @Mock
    private IVirtualDeviceManager mMockVDMService;
    @Mock
    private IVirtualDevice mMockIVirtualDevice;
    private VirtualDeviceManager mVirtualDeviceManager;
    private CompanionDeviceManager mCompanionDeviceManager;
    private Handler mHandler;
    private AgentAuthService mService;
    private FakeClock mClock;
    private MockContentResolver mContentResolver;

    private IOnAssociationsChangedListener mAssocListener;
    private VirtualDeviceListener mVirtualDeviceListener;

    @Before
    public void setUp() throws Exception {
        mContentResolver = new MockContentResolver();
        mContentResolver.addProvider(Settings.AUTHORITY, new FakeSettingsProvider());
        when(mMockContext.getContentResolver()).thenReturn(mContentResolver);
        when(mMockContext.getUserId()).thenReturn(USER_ID);
        when(mMockContext.getSystemServiceName(CompanionDeviceManager.class))
                .thenReturn(Context.COMPANION_DEVICE_SERVICE);
        when(mMockContext.getSystemService(Context.COMPANION_DEVICE_SERVICE))
                .thenReturn(new CompanionDeviceManager(mMockCDMService, mMockContext));
        mCompanionDeviceManager = mMockContext.getSystemService(CompanionDeviceManager.class);

        mVirtualDeviceManager = new VirtualDeviceManager(mMockVDMService, mMockContext);

        mHandler = new Handler(TestableLooper.get(this).getLooper());
        mClock = new FakeClock();

        mService = new AgentAuthService(mMockContext, mHandler, mClock, AUTH_INTERVAL);

        mService.start(mLockSettings, mBiometricManager, mKeyguardManager, mCompanionDeviceManager,
                mVirtualDeviceManager, USER_ID);
        TestableLooper.get(this).processAllMessages();

        ArgumentCaptor<android.companion.virtual.IVirtualDeviceListener> vdlCaptor =
                ArgumentCaptor.forClass(android.companion.virtual.IVirtualDeviceListener.class);
        verify(mMockVDMService).registerVirtualDeviceListener(vdlCaptor.capture());
        // We can't easily get the VirtualDeviceListener wrapper back,
        // but we can trigger callbacks on the IVirtualDeviceListener.
        android.companion.virtual.IVirtualDeviceListener iVdl = vdlCaptor.getValue();
        mVirtualDeviceListener = new VirtualDeviceListener() {
            @Override
            public void onVirtualDeviceCreated(int deviceId) {
                try {
                    iVdl.onVirtualDeviceCreated(deviceId);
                } catch (RemoteException e) { }
            }
            @Override
            public void onVirtualDeviceClosed(int deviceId) {
                try {
                    iVdl.onVirtualDeviceClosed(deviceId);
                } catch (RemoteException e) { }
            }
        };

        ArgumentCaptor<IOnAssociationsChangedListener> assocCaptor =
                ArgumentCaptor.forClass(IOnAssociationsChangedListener.class);
        // Verify exactly one call to addOnAssociationsChangedListener for USER_ID
        verify(mMockCDMService).addOnAssociationsChangedListener(assocCaptor.capture(), eq(USER_ID));
        mAssocListener = assocCaptor.getValue();
    }

    @After
    public void tearDown() {
        // Stop current user monitoring by switching to USER_NULL
        mService.initInBackgroundForUser(android.os.UserHandle.USER_NULL);
        TestableLooper.get(this).processAllMessages();

        FakeSettingsProvider.clearSettingsProvider();
    }

    @Test
    public void testStart_initializesMonitoringForInitialUser() throws Exception {
        // The service in setUp() already called start(..., USER_ID).
        // Verify that monitoring started for USER_ID.
        verify(mMockCDMService).addOnAssociationsChangedListener(any(), eq(USER_ID));
    }

    @Test
    public void testIsAgentAuthorized_noSession_returnsFalse() {
        DeviceId deviceId = createDeviceId("123");
        assertThat(mService.isAgentAuthorized(USER_ID, Context.DEVICE_ID_DEFAULT, deviceId))
                .isFalse();
    }

    @Test
    public void testIsAgentAuthorized_wrongUser_returnsFalse() throws RemoteException {
        DeviceId deviceId = createDeviceId("123");
        triggerAgentConnected(123, deviceId);
        assertThat(mService.isAgentAuthorized(USER_ID + 1, Context.DEVICE_ID_DEFAULT, deviceId))
                .isFalse();
    }

    @Test
    public void testIsAgentAuthorized_staleAuth_returnsFalse() throws RemoteException {
        DeviceId deviceId = createDeviceId("123");

        // auth time is before the allowed interval
        mClock.setNow(AUTH_INTERVAL + 1000);
        when(mBiometricManager.getLastAuthenticationTime(eq(USER_ID), anyInt()))
                .thenReturn(500L);
        when(mKeyguardManager.isDeviceLocked(USER_ID)).thenReturn(true);
        when(mKeyguardManager.isDeviceLocked(USER_ID, Context.DEVICE_ID_DEFAULT)).thenReturn(true);

        triggerAgentConnected(123, deviceId);

        assertThat(mService.isAgentAuthorized(USER_ID, Context.DEVICE_ID_DEFAULT, deviceId))
                .isFalse();
    }

    @Test
    public void testIsAgentAuthorized_authorizedSession_returnsTrue() throws RemoteException {
        DeviceId deviceId = createDeviceId("123");

        // auth time is within the allowed interval
        mClock.setNow(AUTH_INTERVAL);
        when(mBiometricManager.getLastAuthenticationTime(eq(USER_ID), anyInt()))
                .thenReturn(AUTH_INTERVAL / 2);
        when(mKeyguardManager.isDeviceLocked(USER_ID)).thenReturn(true);
        when(mKeyguardManager.isDeviceLocked(USER_ID, Context.DEVICE_ID_DEFAULT)).thenReturn(true);

        triggerAgentConnected(123, deviceId);

        assertThat(mService.isAgentAuthorized(USER_ID, Context.DEVICE_ID_DEFAULT, deviceId))
                .isTrue();
    }

    @Test
    public void testIsAgentAuthorized_unlockedConnection_returnsTrue() throws RemoteException {
        DeviceId deviceId = createDeviceId("123");

        // mock device as unlocked
        when(mKeyguardManager.isDeviceLocked(USER_ID)).thenReturn(false);
        when(mKeyguardManager.isDeviceLocked(USER_ID, Context.DEVICE_ID_DEFAULT)).thenReturn(false);

        triggerAgentConnected(123, deviceId);

        // agent should be authorized even without recent biometric auth
        when(mBiometricManager.getLastAuthenticationTime(eq(USER_ID), anyInt())).thenReturn(-1L);

        assertThat(mService.isAgentAuthorized(USER_ID, Context.DEVICE_ID_DEFAULT, deviceId))
                .isTrue();
    }

    @Test
    public void testStrongAuth_promotesSession() throws RemoteException {
        DeviceId deviceId = createDeviceId("123");
        when(mKeyguardManager.isDeviceLocked(USER_ID)).thenReturn(true);
        when(mKeyguardManager.isDeviceLocked(USER_ID, Context.DEVICE_ID_DEFAULT)).thenReturn(true);
        when(mBiometricManager.getLastAuthenticationTime(eq(USER_ID), anyInt())).thenReturn(0L);

        triggerAgentConnected(123, deviceId);
        assertThat(mService.isAgentAuthorized(USER_ID, Context.DEVICE_ID_DEFAULT, deviceId))
                .isFalse();

        ArgumentCaptor<android.hardware.biometrics.AuthenticationStateListener> captor =
                ArgumentCaptor.forClass(
                        android.hardware.biometrics.AuthenticationStateListener.class);
        verify(mBiometricManager).registerAuthenticationStateListener(captor.capture());
        var listener = captor.getValue();

        mClock.setNow(TimeUnit.HOURS.toMillis(1));
        when(mBiometricManager.getLastAuthenticationTime(eq(USER_ID), anyInt()))
                .thenReturn(TimeUnit.HOURS.toMillis(1));

        AuthenticationSucceededInfo info = new AuthenticationSucceededInfo.Builder(
                BiometricSourceType.FINGERPRINT,
                BiometricRequestConstants.REASON_AUTH_BP,
                true,
                USER_ID).build();

        listener.onAuthenticationSucceeded(info);
        TestableLooper.get(this).processAllMessages();

        assertThat(mService.isAgentAuthorized(USER_ID, Context.DEVICE_ID_DEFAULT, deviceId))
                .isTrue();
    }

    @Test
    public void testUserSwitch_clearsSessions() throws RemoteException {
        DeviceId deviceId = createDeviceId("123");
        triggerAgentConnected(123, deviceId);

        mService.initInBackgroundForUser(USER_ID + 1);
        TestableLooper.get(this).processAllMessages();

        assertThat(mService.isAgentAuthorized(USER_ID, Context.DEVICE_ID_DEFAULT, deviceId))
                .isFalse();
    }

    @Test
    public void testSetOverride_ForAssociationId_updatesSession() throws RemoteException {
        DeviceId deviceId = createDeviceId("123");
        // Initial state: locked and no recent auth
        when(mKeyguardManager.isDeviceLocked(USER_ID)).thenReturn(true);
        when(mKeyguardManager.isDeviceLocked(USER_ID, Context.DEVICE_ID_DEFAULT)).thenReturn(true);
        when(mBiometricManager.getLastAuthenticationTime(eq(USER_ID), anyInt())).thenReturn(0L);

        triggerAgentConnected(123, deviceId);
        assertThat(mService.isAgentAuthorized(USER_ID, Context.DEVICE_ID_DEFAULT, deviceId))
                .isFalse();

        // Override to true
        boolean result = mService.setOverrideForAssociationId(USER_ID, 123, true);
        assertThat(result).isTrue();
        assertThat(mService.isAgentAuthorized(USER_ID, Context.DEVICE_ID_DEFAULT, deviceId))
                .isTrue();

        // Override to false
        result = mService.setOverrideForAssociationId(USER_ID, 123, false);
        assertThat(result).isFalse();
        assertThat(mService.isAgentAuthorized(USER_ID, Context.DEVICE_ID_DEFAULT, deviceId))
                .isFalse();
    }

    @Test
    public void testSetOverride_ForAssociationId_nonExistentSession_returnsFalse() {
        boolean result = mService.setOverrideForAssociationId(USER_ID, 999, true);
        assertThat(result).isFalse();
    }

    @Test
    public void testIsAgentAuthorized_localAgent_unlocked_returnsTrue() {
        // Simulate device is UNLOCKED
        when(mKeyguardManager.isDeviceLocked(USER_ID))
                .thenReturn(false);
        when(mKeyguardManager.isDeviceLocked(USER_ID, Context.DEVICE_ID_DEFAULT))
                .thenReturn(false);

        assertThat(mService.isAgentAuthorized(USER_ID, Context.DEVICE_ID_DEFAULT, null))
                .isTrue();
    }

    @Test
    public void testIsAgentAuthorized_localAgent_locked_returnsFalse() {
        // Simulate device is LOCKED
        when(mKeyguardManager.isDeviceLocked(USER_ID))
                .thenReturn(true);
        when(mKeyguardManager.isDeviceLocked(USER_ID, Context.DEVICE_ID_DEFAULT))
                .thenReturn(true);

        assertThat(mService.isAgentAuthorized(USER_ID, Context.DEVICE_ID_DEFAULT, null))
                .isFalse();
    }

    @Test
    public void testIsAgentAuthorized_localAgent_wrongUser_returnsFalse() {
        // Simulate device is UNLOCKED for current user
        when(mKeyguardManager.isDeviceLocked(USER_ID))
                .thenReturn(false);
        when(mKeyguardManager.isDeviceLocked(USER_ID, Context.DEVICE_ID_DEFAULT))
                .thenReturn(false);

        when(mKeyguardManager.isDeviceLocked(USER_ID + 1))
                .thenReturn(true);
        when(mKeyguardManager.isDeviceLocked(USER_ID + 1, Context.DEVICE_ID_DEFAULT))
                .thenReturn(true);
        assertThat(mService.isAgentAuthorized(USER_ID + 1, Context.DEVICE_ID_DEFAULT, null))
                .isFalse();

        when(mKeyguardManager.isDeviceLocked(USER_ID + 1))
                .thenReturn(false);
        when(mKeyguardManager.isDeviceLocked(USER_ID + 1, Context.DEVICE_ID_DEFAULT))
                .thenReturn(false);
        assertThat(mService.isAgentAuthorized(USER_ID + 1, Context.DEVICE_ID_DEFAULT, null))
                .isTrue();
    }

    @Test
    @EnableFlags(Flags.FLAG_AGENT_AUTH_ALLOW_AUTO_PROJECTED)
    public void testIsAgentAuthorizedByDeviceId_localAgent_returnsStatus() throws RemoteException {
        int deviceId = 456;
        mockVirtualDevice(deviceId, VirtualDevice.DEVICE_PROFILE_AUTOMOTIVE_PROJECTION);
        when(mKeyguardManager.isDeviceLocked(USER_ID)).thenReturn(false);
        when(mKeyguardManager.isDeviceLocked(USER_ID, Context.DEVICE_ID_DEFAULT)).thenReturn(false);

        mVirtualDeviceListener.onVirtualDeviceCreated(deviceId);
        TestableLooper.get(this).processAllMessages();

        // Initially authorized because device was unlocked
        assertThat(mService.isAgentAuthorizedByDeviceId(USER_ID, deviceId)).isTrue();

        // Revoke via override
        mService.setOverrideForDeviceId(USER_ID, deviceId, false);
        assertThat(mService.isAgentAuthorizedByDeviceId(USER_ID, deviceId)).isFalse();

        // Authorize via override
        mService.setOverrideForDeviceId(USER_ID, deviceId, true);
        assertThat(mService.isAgentAuthorizedByDeviceId(USER_ID, deviceId)).isTrue();
    }

    @Test
    @EnableFlags(Flags.FLAG_AGENT_AUTH_ALLOW_AUTO_PROJECTED)
    public void testSetOverride_ForDeviceId_updatesSession() throws RemoteException {
        int deviceId = 456;
        mockVirtualDevice(deviceId, VirtualDevice.DEVICE_PROFILE_AUTOMOTIVE_PROJECTION);
        when(mKeyguardManager.isDeviceLocked(USER_ID)).thenReturn(true);
        when(mKeyguardManager.isDeviceLocked(USER_ID, Context.DEVICE_ID_DEFAULT)).thenReturn(true);

        mVirtualDeviceListener.onVirtualDeviceCreated(deviceId);
        TestableLooper.get(this).processAllMessages();
        assertThat(mService.isAgentAuthorizedByDeviceId(USER_ID, deviceId)).isFalse();

        // Override to true
        boolean result = mService.setOverrideForDeviceId(USER_ID, deviceId, true);
        assertThat(result).isTrue();
        assertThat(mService.isAgentAuthorizedByDeviceId(USER_ID, deviceId)).isTrue();

        // Override to false
        result = mService.setOverrideForDeviceId(USER_ID, deviceId, false);
        assertThat(result).isFalse();
        assertThat(mService.isAgentAuthorizedByDeviceId(USER_ID, deviceId)).isFalse();
    }

    private void triggerAgentConnected(int associationId, DeviceId deviceId) throws RemoteException {
        assertThat(mAssocListener).isNotNull();

        AssociationInfo association = new AssociationInfo.Builder(associationId, USER_ID, "pkg")
                .setDisplayName("Device")
                .setRemoteAiAgentSupported(true)
                .setDeviceId(deviceId)
                .build();

        when(mMockCDMService.getAssociationByDeviceId(USER_ID, deviceId)).thenReturn(association);
        when(mMockCDMService.getAllAssociationsForUser(USER_ID)).thenReturn(List.of(association));

        mAssocListener.onAssociationsChanged(List.of(association));
        TestableLooper.get(this).processAllMessages();

        ArgumentCaptor<IOnDevicePresenceEventListener> presenceCaptor =
                ArgumentCaptor.forClass(IOnDevicePresenceEventListener.class);
        verify(mMockCDMService).setOnDevicePresenceEventListener(any(int[].class),
                any(String.class), presenceCaptor.capture(), eq(USER_ID));
        var presenceListener = presenceCaptor.getValue();

        assertThat(presenceListener).isNotNull();

        DevicePresenceEvent event = new DevicePresenceEvent.Builder()
                .setAssociationId(associationId)
                .setEvent(DevicePresenceEvent.EVENT_BT_CONNECTED)
                .build();

        presenceListener.onDevicePresence(event);
        TestableLooper.get(this).processAllMessages();
    }

    @Test
    public void testVirtualDeviceListener_isRegistered() throws RemoteException {
        verify(mMockVDMService).registerVirtualDeviceListener(
                any(android.companion.virtual.IVirtualDeviceListener.class));
    }

    @Test
    public void testVirtualDeviceListener_callbacksDoNotCrash() throws RemoteException {
        // These currently only log, so we just verify they can be called.
        mVirtualDeviceListener.onVirtualDeviceCreated(1);
        mVirtualDeviceListener.onVirtualDeviceClosed(1);
    }

    @Test
    public void testOnVirtualDeviceCreated_otherProfile_virtualDeviceUnlocked_isAuthorized() throws RemoteException {
        int deviceId = 789;
        mockVirtualDevice(deviceId, VirtualDevice.DEVICE_PROFILE_APP_STREAMING);
        // Host lock state shouldn't matter, but let's test with host locked to prove it's ignored
        when(mKeyguardManager.isDeviceLocked(USER_ID)).thenReturn(true);
        when(mKeyguardManager.isDeviceLocked(USER_ID, Context.DEVICE_ID_DEFAULT)).thenReturn(true);

        mVirtualDeviceListener.onVirtualDeviceCreated(deviceId);
        TestableLooper.get(this).processAllMessages();

        // Simulate virtual device unlocked at check time
        when(mKeyguardManager.isDeviceLocked(USER_ID, deviceId)).thenReturn(false);

        assertThat(mService.isAgentAuthorizedByDeviceId(USER_ID, deviceId)).isTrue();
    }

    @Test
    public void testOnVirtualDeviceCreated_otherProfile_virtualDeviceLocked_isUnauthorized() throws RemoteException {
        int deviceId = 789;
        mockVirtualDevice(deviceId, VirtualDevice.DEVICE_PROFILE_APP_STREAMING);
        // Host lock state shouldn't matter
        when(mKeyguardManager.isDeviceLocked(USER_ID)).thenReturn(false);
        when(mKeyguardManager.isDeviceLocked(USER_ID, Context.DEVICE_ID_DEFAULT)).thenReturn(false);

        mVirtualDeviceListener.onVirtualDeviceCreated(deviceId);
        TestableLooper.get(this).processAllMessages();

        // Simulate virtual device locked at check time
        when(mKeyguardManager.isDeviceLocked(USER_ID, deviceId)).thenReturn(true);

        assertThat(mService.isAgentAuthorizedByDeviceId(USER_ID, deviceId)).isFalse();
    }

    @Test
    @EnableFlags(Flags.FLAG_AGENT_AUTH_ALLOW_AUTO_PROJECTED)
    public void testOnVirtualDeviceCreated_AutoProjected_flagEnabled_hostLocked_noRecentAuth_isUnauthorized() throws RemoteException {
        int deviceId = 789;
        mockVirtualDevice(deviceId, VirtualDevice.DEVICE_PROFILE_AUTOMOTIVE_PROJECTION);

        // Host locked, no recent auth
        when(mKeyguardManager.isDeviceLocked(USER_ID)).thenReturn(true);
        when(mKeyguardManager.isDeviceLocked(USER_ID, Context.DEVICE_ID_DEFAULT)).thenReturn(true);
        when(mBiometricManager.getLastAuthenticationTime(eq(USER_ID), anyInt())).thenReturn(0L);

        mVirtualDeviceListener.onVirtualDeviceCreated(deviceId);
        TestableLooper.get(this).processAllMessages();

        // Even if virtual device is unlocked, the session wasn't authorized at connection
        when(mKeyguardManager.isDeviceLocked(USER_ID, deviceId)).thenReturn(false);

        assertThat(mService.isAgentAuthorized(USER_ID, deviceId, null)).isFalse();
    }

    @Test
    @EnableFlags(Flags.FLAG_AGENT_AUTH_ALLOW_AUTO_PROJECTED)
    public void testOnVirtualDeviceCreated_AutoProjected_flagEnabled_hostLocked_withRecentAuth_isAuthorized() throws RemoteException {
        int deviceId = 789;
        mockVirtualDevice(deviceId, VirtualDevice.DEVICE_PROFILE_AUTOMOTIVE_PROJECTION);

        // Host locked, but WITH recent auth
        when(mKeyguardManager.isDeviceLocked(USER_ID)).thenReturn(true);
        when(mKeyguardManager.isDeviceLocked(USER_ID, Context.DEVICE_ID_DEFAULT)).thenReturn(true);
        mClock.setNow(AUTH_INTERVAL);
        when(mBiometricManager.getLastAuthenticationTime(eq(USER_ID), anyInt()))
                .thenReturn(AUTH_INTERVAL / 2);

        mVirtualDeviceListener.onVirtualDeviceCreated(deviceId);
        TestableLooper.get(this).processAllMessages();

        // Session authorized at connection. Check depends on VD lock state.
        when(mKeyguardManager.isDeviceLocked(USER_ID, deviceId)).thenReturn(false);
        assertThat(mService.isAgentAuthorized(USER_ID, deviceId, null)).isTrue();
    }

    @Test
    @EnableFlags(Flags.FLAG_AGENT_AUTH_ALLOW_AUTO_PROJECTED)
    public void testOnVirtualDeviceCreated_AutoProjected_flagEnabled_hostUnlocked_isAuthorized() throws RemoteException {
        int deviceId = 789;
        mockVirtualDevice(deviceId, VirtualDevice.DEVICE_PROFILE_AUTOMOTIVE_PROJECTION);

        // Host unlocked at connection
        when(mKeyguardManager.isDeviceLocked(USER_ID)).thenReturn(false);
        when(mKeyguardManager.isDeviceLocked(USER_ID, Context.DEVICE_ID_DEFAULT)).thenReturn(false);

        mVirtualDeviceListener.onVirtualDeviceCreated(deviceId);
        TestableLooper.get(this).processAllMessages();

        // Session authorized at connection. Check depends on VD lock state.
        when(mKeyguardManager.isDeviceLocked(USER_ID, deviceId)).thenReturn(false);
        assertThat(mService.isAgentAuthorized(USER_ID, deviceId, null)).isTrue();
    }

    @Test
    @EnableFlags(Flags.FLAG_AGENT_AUTH_ALLOW_AUTO_PROJECTED)
    public void testOnVirtualDeviceCreated_AutoProjected_flagEnabled_virtualDeviceLocked_isUnauthorized() throws RemoteException {
        int deviceId = 89;
        mockVirtualDevice(deviceId, VirtualDevice.DEVICE_PROFILE_AUTOMOTIVE_PROJECTION);

        // Host unlocked at connection (authorizes the session)
        when(mKeyguardManager.isDeviceLocked(USER_ID)).thenReturn(false);
        when(mKeyguardManager.isDeviceLocked(USER_ID, Context.DEVICE_ID_DEFAULT)).thenReturn(false);

        mVirtualDeviceListener.onVirtualDeviceCreated(deviceId);
        TestableLooper.get(this).processAllMessages();

        // But virtual device is locked at check time -> not authorized
        when(mKeyguardManager.isDeviceLocked(USER_ID, deviceId)).thenReturn(true);

        assertThat(mService.isAgentAuthorized(USER_ID, deviceId, null)).isFalse();
    }

    @Test
    @DisableFlags(Flags.FLAG_AGENT_AUTH_ALLOW_AUTO_PROJECTED)
    public void testOnVirtualDeviceCreated_AutoProjected_flagDisabled_usesHostLockState_isAuthorized() throws RemoteException {
        int deviceId = 789;
        mockVirtualDevice(deviceId, VirtualDevice.DEVICE_PROFILE_AUTOMOTIVE_PROJECTION);

        // Host locked at connection - doesn't matter for session creation because flag is disabled
        when(mKeyguardManager.isDeviceLocked(USER_ID)).thenReturn(true);
        when(mKeyguardManager.isDeviceLocked(USER_ID, Context.DEVICE_ID_DEFAULT)).thenReturn(true);

        mVirtualDeviceListener.onVirtualDeviceCreated(deviceId);
        TestableLooper.get(this).processAllMessages();

        // Later, check happens. Host is now unlocked.
        when(mKeyguardManager.isDeviceLocked(USER_ID)).thenReturn(false);
        when(mKeyguardManager.isDeviceLocked(USER_ID, Context.DEVICE_ID_DEFAULT)).thenReturn(false);

        // VD lock state shouldn't matter
        when(mKeyguardManager.isDeviceLocked(USER_ID, deviceId)).thenReturn(true); 

        // The session is checked against the host, so since host is unlocked, it's authorized.
        assertThat(mService.isAgentAuthorized(USER_ID, deviceId, null)).isTrue();
    }

    @Test
    @DisableFlags(Flags.FLAG_AGENT_AUTH_ALLOW_AUTO_PROJECTED)
    public void testOnVirtualDeviceCreated_AutoProjected_flagDisabled_usesHostLockState_isUnauthorized() throws RemoteException {
        int deviceId = 78;
        mockVirtualDevice(deviceId, VirtualDevice.DEVICE_PROFILE_AUTOMOTIVE_PROJECTION);

        mVirtualDeviceListener.onVirtualDeviceCreated(deviceId);
        TestableLooper.get(this).processAllMessages();

        // Later, check happens. Host is locked.
        when(mKeyguardManager.isDeviceLocked(USER_ID)).thenReturn(true);
        when(mKeyguardManager.isDeviceLocked(USER_ID, Context.DEVICE_ID_DEFAULT)).thenReturn(true);

        // VD is unlocked, but shouldn't matter
        when(mKeyguardManager.isDeviceLocked(USER_ID, deviceId)).thenReturn(false); 

        // unauthorized since host device is locked
        assertThat(mService.isAgentAuthorized(USER_ID, deviceId, null)).isFalse();
    }

    private void mockVirtualDevice(int deviceId, int profile) throws RemoteException {
        when(mMockVDMService.isValidVirtualDeviceId(deviceId)).thenReturn(true);

        VirtualDevice vd = new VirtualDevice(mMockIVirtualDevice, deviceId, profile,
                "companion:" + deviceId, "device_name", "display name");
        when(mMockVDMService.getVirtualDevice(deviceId)).thenReturn(vd);
    }

    private DeviceId createDeviceId(String customId) {
        return new DeviceId.Builder().setCustomId(customId).build();
    }

    private static class FakeClock extends Clock {
        private long mNowMillis;

        void setNow(long millis) {
            mNowMillis = millis;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.systemDefault();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(mNowMillis);
        }

        @Override
        public long millis() {
            return mNowMillis;
        }
    }
}
