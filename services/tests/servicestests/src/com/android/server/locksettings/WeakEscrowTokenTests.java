/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.locksettings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.admin.PasswordMetrics;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.widget.IWeakEscrowTokenActivatedListener;
import com.android.internal.widget.IWeakEscrowTokenRemovedListener;
import com.android.internal.widget.LockscreenCredential;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** atest FrameworksServicesTests:WeakEscrowTokenTests */
@SmallTest
@Presubmit
@RunWith(AndroidJUnit4.class)
public class WeakEscrowTokenTests extends BaseLockSettingsServiceTests{

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Before
    public void setUp() {
        mService.initializeSyntheticPassword(PRIMARY_USER_ID);
        mService.initializeSyntheticPassword(MANAGED_PROFILE_USER_ID);
    }

    @Test
    public void testWeakTokenActivatedImmediatelyIfNoUserPassword()
            throws RemoteException {
        mockAutoHardware();
        final byte[] token = "some-high-entropy-secure-token".getBytes();
        IWeakEscrowTokenActivatedListener mockListener =
                mock(IWeakEscrowTokenActivatedListener.Stub.class);
        long handle = mService.addWeakEscrowToken(token, PRIMARY_USER_ID, mockListener);
        assertTrue(mService.isWeakEscrowTokenActive(handle, PRIMARY_USER_ID));
        assertTrue(mService.isWeakEscrowTokenValid(handle, token, PRIMARY_USER_ID));
        verify(mockListener).onWeakEscrowTokenActivated(handle, PRIMARY_USER_ID);
    }

    @Test
    public void testWeakTokenActivatedLaterWithUserPassword()
            throws RemoteException {
        mockAutoHardware();
        byte[] token = "some-high-entropy-secure-token".getBytes();
        IWeakEscrowTokenActivatedListener mockListener =
                mock(IWeakEscrowTokenActivatedListener.Stub.class);
        LockscreenCredential password = newPassword("password");
        mService.setLockCredential(password, nonePassword(), PRIMARY_USER_ID);

        long handle = mService.addWeakEscrowToken(token, PRIMARY_USER_ID, mockListener);
        // Token not activated immediately since user password exists
        assertFalse(mService.isWeakEscrowTokenActive(handle, PRIMARY_USER_ID));
        // Activate token (password gets migrated to SP at the same time)
        assertTrue(mService.verifyCredential(password, PRIMARY_USER_ID, 0 /* flags */).isMatched());
        // Verify token is activated and valid
        assertTrue(mService.isWeakEscrowTokenActive(handle, PRIMARY_USER_ID));
        assertTrue(mService.isWeakEscrowTokenValid(handle, token, PRIMARY_USER_ID));
        verify(mockListener).onWeakEscrowTokenActivated(handle, PRIMARY_USER_ID);
    }

    @Test
    public void testWeakTokensRemovedIfCredentialChanged() throws Exception {
        mockAutoHardware();
        byte[] token = "some-high-entropy-secure-token".getBytes();
        IWeakEscrowTokenRemovedListener mockRemoveListener = mockAliveRemoveListener();
        IWeakEscrowTokenActivatedListener mockActivateListener =
                mock(IWeakEscrowTokenActivatedListener.Stub.class);
        LockscreenCredential password = newPassword("password");
        LockscreenCredential pattern = newPattern("123654");
        mService.setLockCredential(password, nonePassword(), PRIMARY_USER_ID);
        mService.registerWeakEscrowTokenRemovedListener(mockRemoveListener);

        long handle = mService.addWeakEscrowToken(token, PRIMARY_USER_ID, mockActivateListener);

        // Activate token
        assertTrue(mService.verifyCredential(password, PRIMARY_USER_ID, 0 /* flags */).isMatched());

        // Verify token removed
        assertTrue(mService.isWeakEscrowTokenActive(handle, PRIMARY_USER_ID));
        assertTrue(mLocalService.setLockCredentialWithToken(
                pattern, handle, token, PRIMARY_USER_ID));
        assertFalse(mLocalService.isEscrowTokenActive(handle, PRIMARY_USER_ID));
        verify(mockRemoveListener).onWeakEscrowTokenRemoved(handle, PRIMARY_USER_ID);
    }

    @Test
    public void testWeakTokenRemovedListenerRegistered() throws Exception {
        mockAutoHardware();
        IWeakEscrowTokenRemovedListener mockRemoveListener = mockAliveRemoveListener();
        IWeakEscrowTokenActivatedListener mockActivateListener =
                mock(IWeakEscrowTokenActivatedListener.Stub.class);
        byte[] token = "some-high-entropy-secure-token".getBytes();
        long handle = mService.addWeakEscrowToken(token, PRIMARY_USER_ID, mockActivateListener);

        mService.registerWeakEscrowTokenRemovedListener(mockRemoveListener);
        mService.removeWeakEscrowToken(handle, PRIMARY_USER_ID);

        verify(mockRemoveListener).onWeakEscrowTokenRemoved(handle, PRIMARY_USER_ID);
    }

    @Test
    public void testWeakTokenRemovedListenerUnregistered() throws Exception {
        mockAutoHardware();
        IWeakEscrowTokenRemovedListener mockRemoveListener = mockAliveRemoveListener();
        IWeakEscrowTokenActivatedListener mockActivateListener =
                mock(IWeakEscrowTokenActivatedListener.Stub.class);
        byte[] token0 = "some-high-entropy-secure-token-0".getBytes();
        byte[] token1 = "some-high-entropy-secure-token-1".getBytes();
        long handle0 = mService.addWeakEscrowToken(token0, PRIMARY_USER_ID, mockActivateListener);
        long handle1 = mService.addWeakEscrowToken(token1, PRIMARY_USER_ID, mockActivateListener);

        mService.registerWeakEscrowTokenRemovedListener(mockRemoveListener);
        mService.removeWeakEscrowToken(handle0, PRIMARY_USER_ID);
        verify(mockRemoveListener).onWeakEscrowTokenRemoved(handle0, PRIMARY_USER_ID);

        mService.unregisterWeakEscrowTokenRemovedListener(mockRemoveListener);
        mService.removeWeakEscrowToken(handle1, PRIMARY_USER_ID);
        verify(mockRemoveListener, never()).onWeakEscrowTokenRemoved(handle1, PRIMARY_USER_ID);
    }

    private void testUnlockUserWithToken_weakEscrowToken_helper() throws Exception {
        mockAutoHardware();
        IWeakEscrowTokenActivatedListener mockActivateListener =
                mock(IWeakEscrowTokenActivatedListener.Stub.class);
        LockscreenCredential password = newPassword("password");
        byte[] token = "some-high-entropy-secure-token".getBytes();
        mService.setLockCredential(password, nonePassword(), PRIMARY_USER_ID);
        // Disregard any reportPasswordChanged() invocations as part of credential setup.
        flushHandlerTasks();
        reset(mDevicePolicyManager);

        long handle = mService.addWeakEscrowToken(token, PRIMARY_USER_ID, mockActivateListener);
        assertTrue(mService.verifyCredential(password, PRIMARY_USER_ID, 0 /* flags */).isMatched());
        assertTrue(mService.isWeakEscrowTokenActive(handle, PRIMARY_USER_ID));
        assertTrue(mService.isWeakEscrowTokenValid(handle, token, PRIMARY_USER_ID));

        if (android.security.Flags.resetAuthFlagsAndMetricsInLockUser()) {
            mLocalService.lockUser(PRIMARY_USER_ID);
        } else {
            mService.onUserStopped(PRIMARY_USER_ID);
        }
        assertNull(mLocalService.getUserPasswordMetrics(PRIMARY_USER_ID));

        assertTrue(mLocalService.unlockUserWithToken(handle, token, PRIMARY_USER_ID));
        assertEquals(PasswordMetrics.computeForCredential(password),
                mLocalService.getUserPasswordMetrics(PRIMARY_USER_ID));
    }

    @Test
    @DisableFlags(android.security.Flags.FLAG_RESET_AUTH_FLAGS_AND_METRICS_IN_LOCK_USER)
    public void testUnlockUserWithToken_weakEscrowToken_orig() throws Exception {
        testUnlockUserWithToken_weakEscrowToken_helper();
    }

    @Test
    @EnableFlags(android.security.Flags.FLAG_RESET_AUTH_FLAGS_AND_METRICS_IN_LOCK_USER)
    public void testUnlockUserWithToken_weakEscrowToken() throws Exception {
        testUnlockUserWithToken_weakEscrowToken_helper();
    }

    private void mockAutoHardware() {
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)).thenReturn(true);
    }

    private IWeakEscrowTokenRemovedListener mockAliveRemoveListener() {
        IWeakEscrowTokenRemovedListener mockListener =
                mock(IWeakEscrowTokenRemovedListener.Stub.class);
        IBinder mockIBinder = mock(IBinder.class);
        when(mockIBinder.isBinderAlive()).thenReturn(true);
        when(mockListener.asBinder()).thenReturn(mockIBinder);
        return mockListener;
    }
}
