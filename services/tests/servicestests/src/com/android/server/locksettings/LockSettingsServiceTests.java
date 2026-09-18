/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.locksettings;

import static android.Manifest.permission.CONFIGURE_FACTORY_RESET_PROTECTION;
import static android.security.Flags.FLAG_SECURE_LOCK_DEVICE;

import static androidx.test.ext.truth.os.ParcelableSubject.assertThat;

import static com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_NONE;
import static com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_PASSWORD;
import static com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_PATTERN;
import static com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_PIN;
import static com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_UNKNOWN;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Intent;
import android.hardware.weaver.WeaverReadStatus;
import android.os.RemoteException;
import android.os.UserHandle;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.platform.test.flag.junit.SetFlagsRule;
import android.text.TextUtils;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.widget.ICheckCredentialProgressCallback;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockscreenCredential;
import com.android.internal.widget.VerifyCredentialResponse;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

/** atest FrameworksServicesTests:LockSettingsServiceTests */
@SmallTest
@Presubmit
@RunWith(AndroidJUnit4.class)
public class LockSettingsServiceTests extends BaseLockSettingsServiceTests {
    private static final Duration TEN_YEARS = Duration.ofDays(10 * 365);
    private static final Duration MAX_LENGTH_DURATION =
            Duration.ofSeconds(Long.MAX_VALUE, 999999999L);

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        mService.initializeSyntheticPassword(PRIMARY_USER_ID);
        mService.initializeSyntheticPassword(MANAGED_PROFILE_USER_ID);
    }

    @Test
    @EnableFlags({
        android.security.Flags.FLAG_APP_LOCK_APIS,
        android.security.Flags.FLAG_MOVE_NOTIFY_LOCK_CREDENTIAL_CALLS,
    })
    public void testSetPasswordPrimaryUser() throws RemoteException {
        setAndVerifyCredential(PRIMARY_USER_ID, newPassword("password"));
    }

    @Test
    @DisableFlags({
        android.security.Flags.FLAG_APP_LOCK_APIS,
        android.security.Flags.FLAG_MOVE_NOTIFY_LOCK_CREDENTIAL_CALLS,
    })
    public void testSetPasswordPrimaryUser_flagsOff() throws RemoteException {
        setAndVerifyCredential(PRIMARY_USER_ID, newPassword("password"));
    }

    @Test
    public void testSetPasswordFailsWithoutLockScreen() throws RemoteException {
        testSetCredentialFailsWithoutLockScreen(PRIMARY_USER_ID, newPassword("password"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetTooShortPatternFails() throws RemoteException {
        mService.setLockCredential(newPattern("123"), nonePassword(), PRIMARY_USER_ID);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetTooShortPinFails() throws RemoteException {
        mService.setLockCredential(newPin("123"), nonePassword(), PRIMARY_USER_ID);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetTooShortPassword() throws RemoteException {
        mService.setLockCredential(newPassword("123"), nonePassword(), PRIMARY_USER_ID);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetPasswordWithInvalidChars() throws RemoteException {
        mService.setLockCredential(newPassword("§µ¿¶¥£"), nonePassword(), PRIMARY_USER_ID);
    }

    @Test
    public void testSetPatternPrimaryUser() throws RemoteException {
        setAndVerifyCredential(PRIMARY_USER_ID, newPattern("123456789"));
    }

    @Test
    public void testSetPatternFailsWithoutLockScreen() throws RemoteException {
        testSetCredentialFailsWithoutLockScreen(PRIMARY_USER_ID, newPattern("123456789"));
    }

    @Test
    public void testChangePasswordPrimaryUser() throws RemoteException {
        testChangeCredential(PRIMARY_USER_ID, newPattern("78963214"), newPassword("asdfghjk"));
    }

    @Test
    public void testChangePatternPrimaryUser() throws RemoteException {
        testChangeCredential(PRIMARY_USER_ID, newPassword("password"), newPattern("1596321"));
    }

    @Test
    public void testChangePasswordFailPrimaryUser() throws RemoteException {
        setCredential(PRIMARY_USER_ID, newPassword("password"));
        assertFalse(mService.setLockCredential(newPassword("newpwd"), newPassword("badpwd"),
                    PRIMARY_USER_ID));
        assertVerifyCredential(PRIMARY_USER_ID, newPassword("password"));
    }

    @Test
    public void testClearPasswordPrimaryUser() throws RemoteException {
        setCredential(PRIMARY_USER_ID, newPassword("password"));
        clearCredential(PRIMARY_USER_ID, newPassword("password"));
    }

    @Test
    public void testManagedProfileUnifiedChallenge() throws RemoteException {
        mService.initializeSyntheticPassword(TURNED_OFF_PROFILE_USER_ID);

        final LockscreenCredential firstUnifiedPassword = newPassword("pwd-1");
        final LockscreenCredential secondUnifiedPassword = newPassword("pwd-2");
        setUpUnifiedPassword(firstUnifiedPassword);
        final long primarySid = mGateKeeperService.getSecureUserId(PRIMARY_USER_ID);
        final long profileSid = mGateKeeperService.getSecureUserId(MANAGED_PROFILE_USER_ID);
        final long turnedOffProfileSid =
                mGateKeeperService.getSecureUserId(TURNED_OFF_PROFILE_USER_ID);
        assertTrue(primarySid != 0);
        assertTrue(profileSid != 0);
        assertTrue(profileSid != primarySid);
        assertTrue(turnedOffProfileSid != 0);
        assertTrue(turnedOffProfileSid != primarySid);
        assertTrue(turnedOffProfileSid != profileSid);

        // clear auth token and wait for verify challenge from primary user to re-generate it.
        mGateKeeperService.clearAuthToken(MANAGED_PROFILE_USER_ID);
        mGateKeeperService.clearAuthToken(TURNED_OFF_PROFILE_USER_ID);
        // verify credential
        assertTrue(
                mService.verifyCredential(firstUnifiedPassword, PRIMARY_USER_ID, 0 /* flags */)
                        .isMatched());

        // Verify that we have a new auth token for the profile
        assertNotNull(mGateKeeperService.getAuthToken(MANAGED_PROFILE_USER_ID));
        assertEquals(profileSid, mGateKeeperService.getSecureUserId(MANAGED_PROFILE_USER_ID));

        // Verify that profile which aren't running (e.g. turn off work) don't get unlocked
        assertNull(mGateKeeperService.getAuthToken(TURNED_OFF_PROFILE_USER_ID));

        // Change primary password and verify that profile SID remains
        setCredential(PRIMARY_USER_ID, secondUnifiedPassword, firstUnifiedPassword);
        assertEquals(profileSid, mGateKeeperService.getSecureUserId(MANAGED_PROFILE_USER_ID));
        assertNull(mGateKeeperService.getAuthToken(TURNED_OFF_PROFILE_USER_ID));

        // Clear unified challenge
        clearCredential(PRIMARY_USER_ID, secondUnifiedPassword);
        assertEquals(0, mGateKeeperService.getSecureUserId(PRIMARY_USER_ID));
        assertEquals(0, mGateKeeperService.getSecureUserId(MANAGED_PROFILE_USER_ID));
        assertEquals(0, mGateKeeperService.getSecureUserId(TURNED_OFF_PROFILE_USER_ID));
    }

    @Test
    public void testManagedProfileUnifiedChallenge_useSpProtectorPasswordAfterMigration()
            throws Exception {
        final LockscreenCredential unifiedPassword = newPassword("pwd-1");
        setUpUnifiedPassword(unifiedPassword, /* isLegacy= */ true);
        assertTrue(mStorage.hasChildProfileLock(MANAGED_PROFILE_USER_ID));
        assertFalse(hasSpProtectorPassword(MANAGED_PROFILE_USER_ID));

        VerifyCredentialResponse response =
                mService.verifyTiedProfileChallenge(
                        unifiedPassword, MANAGED_PROFILE_USER_ID, 0 /* flags */);

        assertFalse(mStorage.hasChildProfileLock(MANAGED_PROFILE_USER_ID));
        assertTrue(hasSpProtectorPassword(MANAGED_PROFILE_USER_ID));
        assertTrue(response.isMatched());
    }

    @Test
    public void testManagedProfileSeparateChallenge() throws RemoteException {
        final LockscreenCredential primaryPassword = newPassword("primary");
        final LockscreenCredential profilePassword = newPassword("profile");
        setCredential(PRIMARY_USER_ID, primaryPassword);
        setCredential(MANAGED_PROFILE_USER_ID, profilePassword);

        final long primarySid = mGateKeeperService.getSecureUserId(PRIMARY_USER_ID);
        final long profileSid = mGateKeeperService.getSecureUserId(MANAGED_PROFILE_USER_ID);
        assertTrue(primarySid != 0);
        assertTrue(profileSid != 0);
        assertTrue(profileSid != primarySid);

        // clear auth token and make sure verify challenge from primary user does not regenerate it.
        mGateKeeperService.clearAuthToken(MANAGED_PROFILE_USER_ID);
        // verify primary credential
        assertTrue(
                mService.verifyCredential(primaryPassword, PRIMARY_USER_ID, 0 /* flags */)
                        .isMatched());
        assertNull(mGateKeeperService.getAuthToken(MANAGED_PROFILE_USER_ID));

        // verify profile credential
        assertTrue(
                mService.verifyCredential(profilePassword, MANAGED_PROFILE_USER_ID, 0 /* flags */)
                        .isMatched());
        assertNotNull(mGateKeeperService.getAuthToken(MANAGED_PROFILE_USER_ID));
        assertEquals(profileSid, mGateKeeperService.getSecureUserId(MANAGED_PROFILE_USER_ID));

        setCredential(PRIMARY_USER_ID, newPassword("password"), primaryPassword);
        assertTrue(
                mService.verifyCredential(profilePassword, MANAGED_PROFILE_USER_ID, 0 /* flags */)
                        .isMatched());
        assertEquals(profileSid, mGateKeeperService.getSecureUserId(MANAGED_PROFILE_USER_ID));
    }

    @Test
    public void testManagedProfileChallengeUnification_parentUserNoPassword() throws Exception {
        // Start with a profile with unified challenge, parent user has not password
        mService.setSeparateProfileChallengeEnabled(MANAGED_PROFILE_USER_ID, false, null);
        assertEquals(0, mGateKeeperService.getSecureUserId(MANAGED_PROFILE_USER_ID));
        assertEquals(CREDENTIAL_TYPE_NONE, mService.getCredentialType(MANAGED_PROFILE_USER_ID));

        // Set a separate challenge on the profile
        setCredential(MANAGED_PROFILE_USER_ID, newPassword("12345678"));
        assertNotEquals(0, mGateKeeperService.getSecureUserId(MANAGED_PROFILE_USER_ID));
        assertEquals(CREDENTIAL_TYPE_PASSWORD, mService.getCredentialType(MANAGED_PROFILE_USER_ID));

        // Now unify again, profile should become passwordless again
        mService.setSeparateProfileChallengeEnabled(MANAGED_PROFILE_USER_ID, false,
                newPassword("12345678"));
        assertEquals(0, mGateKeeperService.getSecureUserId(MANAGED_PROFILE_USER_ID));
        assertEquals(CREDENTIAL_TYPE_NONE, mService.getCredentialType(MANAGED_PROFILE_USER_ID));
    }

    @Test
    public void testSetLockCredential_forPrimaryUser_sendsFrpNotification() throws Exception {
        setCredential(PRIMARY_USER_ID, newPassword("password"));
        checkRecordedFrpNotificationIntent();
    }

    @Test
    public void testSetLockCredential_forPrimaryUser_sendsCredentials() throws Exception {
        LockscreenCredential password = newPassword("password");
        setCredential(PRIMARY_USER_ID, password);
        verify(mRecoverableKeyStoreManager).lockScreenSecretChanged(password, PRIMARY_USER_ID);
    }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    public void testUnlockNotReportedToStrongAuth_onCredentialVerified_inSecureLockDevice()
            throws RemoteException {
        when(mSecureLockDeviceServiceInternal.isSecureLockDeviceEnabled()).thenReturn(true);
        LockscreenCredential password = newPassword("password");
        setCredential(PRIMARY_USER_ID, password);

        reset(mStrongAuth);
        mService.checkCredential(password, PRIMARY_USER_ID,
                mock(ICheckCredentialProgressCallback.class));

        verify(mStrongAuth, never()).reportUnlock(anyInt());
        verify(mStrongAuth, never()).reportSuccessfulStrongAuthUnlock(anyInt());
    }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    public void testStrongAuthNotified_onDisableSecureLockDevice() {
        when(mSecureLockDeviceServiceInternal.isSecureLockDeviceEnabled()).thenReturn(true);
        reset(mStrongAuth);
        mLocalService.disableSecureLockDevice(PRIMARY_USER_ID, /* authenticationComplete=*/ true);

        verify(mStrongAuth).disableSecureLockDevice(eq(PRIMARY_USER_ID), eq(true));
    }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    public void testStrongAuthNotified_afterSecureLockDeviceDisabledWithoutAuth()
            throws RemoteException {
        when(mSecureLockDeviceServiceInternal.isSecureLockDeviceEnabled()).thenReturn(true);
        mLocalService.disableSecureLockDevice(PRIMARY_USER_ID, /* authenticationComplete=*/ false);

        verify(mStrongAuth).disableSecureLockDevice(eq(PRIMARY_USER_ID), eq(false));
    }

    @Test
    public void setLockCredential_forPrimaryUser_clearsStrongAuth()
            throws Exception {
        setCredential(PRIMARY_USER_ID, newPassword("password"));

        verify(mStrongAuth).reportUnlock(PRIMARY_USER_ID);
    }

    @Test
    public void setLockCredential_forPrimaryUserWithCredential_leavesStrongAuth() throws Exception {
        setCredential(PRIMARY_USER_ID, newPassword("password"));
        reset(mStrongAuth);

        setCredential(PRIMARY_USER_ID, newPassword("password2"), newPassword("password"));

        verify(mStrongAuth, never()).reportUnlock(anyInt());
    }

    @Test
    public void testSetLockCredential_forProfileWithSeparateChallenge_sendsCredentials()
            throws Exception {
        LockscreenCredential pattern = newPattern("12345");
        setCredential(MANAGED_PROFILE_USER_ID, pattern);
        verify(mRecoverableKeyStoreManager)
                .lockScreenSecretChanged(pattern, MANAGED_PROFILE_USER_ID);
    }

    @Test
    public void testSetLockCredential_forProfileWithSeparateChallenge_updatesCredentials()
            throws Exception {
        LockscreenCredential cred1 = newPattern("12345");
        LockscreenCredential cred2 = newPassword("newPassword");
        mService.setSeparateProfileChallengeEnabled(MANAGED_PROFILE_USER_ID, true, null);
        setCredential(MANAGED_PROFILE_USER_ID, cred1);
        setCredential(MANAGED_PROFILE_USER_ID, cred2, cred1);
        verify(mRecoverableKeyStoreManager).lockScreenSecretChanged(cred2, MANAGED_PROFILE_USER_ID);
    }

    @Test
    public void setLockCredential_profileWithNewSeparateChallenge_clearsStrongAuth()
            throws Exception {
        mService.setSeparateProfileChallengeEnabled(MANAGED_PROFILE_USER_ID, true, null);

        setCredential(MANAGED_PROFILE_USER_ID, newPattern("12345"));

        verify(mStrongAuth).reportUnlock(MANAGED_PROFILE_USER_ID);
    }

    @Test
    public void testSetLockCredential_forProfileWithUnifiedChallenge_doesNotSendRandomCredential()
            throws Exception {
        LockscreenCredential pattern = newPattern("12345");
        mService.setSeparateProfileChallengeEnabled(MANAGED_PROFILE_USER_ID, false, null);
        setCredential(PRIMARY_USER_ID, pattern);
        verify(mRecoverableKeyStoreManager, never())
                .lockScreenSecretChanged(not(eq(pattern)), eq(MANAGED_PROFILE_USER_ID));
    }

    @Test
    public void
            testSetLockCredential_forPrimaryUserWithUnifiedChallengeProfile_updatesBothCredentials()
                    throws Exception {
        final LockscreenCredential oldCredential = newPassword("oldPassword");
        final LockscreenCredential newCredential = newPassword("newPassword");
        setUpUnifiedPassword(oldCredential);
        setCredential(PRIMARY_USER_ID, newCredential, oldCredential);

        verify(mRecoverableKeyStoreManager).lockScreenSecretChanged(newCredential, PRIMARY_USER_ID);
        verify(mRecoverableKeyStoreManager)
                .lockScreenSecretChanged(newCredential, MANAGED_PROFILE_USER_ID);
    }


    @Test
    public void setLockCredential_primaryWithUnifiedProfileAndCredential_leavesStrongAuthForBoth()
            throws Exception {
        final LockscreenCredential oldCredential = newPassword("oldPassword");
        final LockscreenCredential newCredential = newPassword("newPassword");
        setUpUnifiedPassword(oldCredential);
        reset(mStrongAuth);

        setCredential(PRIMARY_USER_ID, newCredential, oldCredential);

        verify(mStrongAuth, never()).reportUnlock(anyInt());
    }

    @Test
    public void setLockCredential_primaryWithUnifiedProfile_clearsStrongAuthForBoth()
            throws Exception {
        final LockscreenCredential credential = newPassword("oldPassword");
        setUpUnifiedPassword(credential);
        clearCredential(PRIMARY_USER_ID, credential);
        reset(mStrongAuth);

        setCredential(PRIMARY_USER_ID, credential);

        verify(mStrongAuth).reportUnlock(PRIMARY_USER_ID);
        verify(mStrongAuth).reportUnlock(MANAGED_PROFILE_USER_ID);
    }

    @Test
    public void setLockCredential_primaryWithUnifiedProfileWithCredential_leavesStrongAuthForBoth()
            throws Exception {
        final LockscreenCredential oldCredential = newPassword("oldPassword");
        final LockscreenCredential newCredential = newPassword("newPassword");
        setUpUnifiedPassword(oldCredential);
        reset(mStrongAuth);

        setCredential(PRIMARY_USER_ID, newCredential, oldCredential);

        verify(mStrongAuth, never()).reportUnlock(anyInt());
    }

    @Test
    public void
            testSetLockCredential_forPrimaryUserWithUnifiedChallengeProfile_removesBothCredentials()
                    throws Exception {
        LockscreenCredential noneCredential = nonePassword();
        setUpUnifiedPassword(newPassword("oldPassword"));
        clearCredential(PRIMARY_USER_ID, newPassword("oldPassword"));

        verify(mRecoverableKeyStoreManager)
                .lockScreenSecretChanged(noneCredential, PRIMARY_USER_ID);
        verify(mRecoverableKeyStoreManager)
                .lockScreenSecretChanged(noneCredential, MANAGED_PROFILE_USER_ID);
    }

    @Test
    public void testClearLockCredential_removesBiometrics() throws RemoteException {
        setUpUnifiedPassword(newPattern("123654"));
        clearCredential(PRIMARY_USER_ID, newPattern("123654"));

        // Verify fingerprint is removed
        verify(mFingerprintManager).removeAll(eq(PRIMARY_USER_ID), any());
        verify(mFaceManager).removeAll(eq(PRIMARY_USER_ID), any());

        verify(mFingerprintManager).removeAll(eq(MANAGED_PROFILE_USER_ID), any());
        verify(mFaceManager).removeAll(eq(MANAGED_PROFILE_USER_ID), any());
    }

    @Test
    public void testClearLockCredential_sendsFrpNotification() throws Exception {
        setCredential(PRIMARY_USER_ID, newPassword("password"));
        checkRecordedFrpNotificationIntent();
        mService.clearRecordedFrpNotificationData();
        clearCredential(PRIMARY_USER_ID, newPassword("password"));
        checkRecordedFrpNotificationIntent();
    }

    @Test
    public void clearLockCredential_primaryWithUnifiedProfile_leavesStrongAuthForBoth()
            throws Exception {
        setUpUnifiedPassword(newPassword("password"));
        reset(mStrongAuth);

        clearCredential(PRIMARY_USER_ID, newPassword("password"));

        verify(mStrongAuth, never()).reportUnlock(anyInt());
    }

    @Test
    public void testSetLockCredential_forUnifiedToSeparateChallengeProfile_sendsNewCredentials()
            throws Exception {
        final LockscreenCredential parentPassword = newPassword("parentPassword");
        final LockscreenCredential profilePassword = newPassword("profilePassword");
        setUpUnifiedPassword(parentPassword);
        setCredential(MANAGED_PROFILE_USER_ID, profilePassword);
        verify(mRecoverableKeyStoreManager)
                .lockScreenSecretChanged(profilePassword, MANAGED_PROFILE_USER_ID);
    }

    @Test
    public void
            testSetLockCredential_forSeparateToUnifiedChallengeProfile_doesNotSendRandomCredential()
                    throws Exception {
        final LockscreenCredential parentPassword = newPassword("parentPassword");
        final LockscreenCredential profilePassword = newPattern("12345");
        mService.setSeparateProfileChallengeEnabled(
                MANAGED_PROFILE_USER_ID, true, profilePassword);
        setCredential(PRIMARY_USER_ID, parentPassword);
        setAndVerifyCredential(MANAGED_PROFILE_USER_ID, profilePassword);

        mService.setSeparateProfileChallengeEnabled(
                MANAGED_PROFILE_USER_ID, false, profilePassword);

        // Called once for setting the initial separate profile credentials and not again during
        // unification.
        verify(mRecoverableKeyStoreManager)
                .lockScreenSecretChanged(any(), eq(MANAGED_PROFILE_USER_ID));
    }

    @Test
    public void testVerifyCredential_forPrimaryUser_sendsCredentials() throws Exception {
        final LockscreenCredential password = newPassword("password");
        setCredential(PRIMARY_USER_ID, password);
        reset(mRecoverableKeyStoreManager);

        mService.verifyCredential(password, PRIMARY_USER_ID, 0 /* flags */);

        verify(mRecoverableKeyStoreManager).lockScreenSecretAvailable(password, PRIMARY_USER_ID);
    }

    @Test
    public void testVerifyCredential_forProfileWithSeparateChallenge_sendsCredentials()
            throws Exception {
        final LockscreenCredential pattern = newPattern("12345");
        setCredential(MANAGED_PROFILE_USER_ID, pattern);
        reset(mRecoverableKeyStoreManager);

        mService.verifyCredential(pattern, MANAGED_PROFILE_USER_ID, 0 /* flags */);

        verify(mRecoverableKeyStoreManager)
                .lockScreenSecretAvailable(pattern, MANAGED_PROFILE_USER_ID);
    }

    @Test
    public void verifyCredential_forPrimaryUserWithUnifiedChallengeProfile_sendsCredentialsForBoth()
                    throws Exception {
        final LockscreenCredential pattern = newPattern("12345");
        setUpUnifiedPassword(pattern);
        reset(mRecoverableKeyStoreManager);

        mService.verifyCredential(pattern, PRIMARY_USER_ID, 0 /* flags */);

        // Parent sends its credentials for both the parent and profile.
        verify(mRecoverableKeyStoreManager).lockScreenSecretAvailable(pattern, PRIMARY_USER_ID);
        verify(mRecoverableKeyStoreManager)
                .lockScreenSecretAvailable(pattern, MANAGED_PROFILE_USER_ID);
        // Profile doesn't send its own random credentials.
        verify(mRecoverableKeyStoreManager, never())
                .lockScreenSecretAvailable(not(eq(pattern)), eq(MANAGED_PROFILE_USER_ID));
    }

    @Test
    public void testVerifyCredential_notifyLockSettingsStateListeners_whenGoodPassword()
            throws Exception {
        final LockscreenCredential password = newPassword("password");
        setCredential(PRIMARY_USER_ID, password);
        final LockSettingsStateListener listener = mock(LockSettingsStateListener.class);
        mLocalService.registerLockSettingsStateListener(listener);

        assertTrue(mService.verifyCredential(password, PRIMARY_USER_ID, 0 /* flags */).isMatched());

        verify(listener).onAuthenticationSucceeded(PRIMARY_USER_ID);
    }

    @Test
    public void testVerifyCredential_notifyLockSettingsStateListeners_whenBadPassword()
            throws Exception {
        final LockscreenCredential password = newPassword("password");
        setCredential(PRIMARY_USER_ID, password);
        final LockscreenCredential badPassword = newPassword("badPassword");
        final LockSettingsStateListener listener = mock(LockSettingsStateListener.class);
        mLocalService.registerLockSettingsStateListener(listener);

        assertTrue(
                mService.verifyCredential(badPassword, PRIMARY_USER_ID, 0 /* flags */)
                        .isOtherError());

        verify(listener).onAuthenticationFailed(PRIMARY_USER_ID);
    }

    @Test
    public void testLockSettingsStateListener_registeredThenUnregistered() throws Exception {
        final LockscreenCredential password = newPassword("password");
        setCredential(PRIMARY_USER_ID, password);
        final LockscreenCredential badPassword = newPassword("badPassword");
        final LockSettingsStateListener listener = mock(LockSettingsStateListener.class);

        mLocalService.registerLockSettingsStateListener(listener);
        assertTrue(mService.verifyCredential(password, PRIMARY_USER_ID, 0 /* flags */).isMatched());
        verify(listener).onAuthenticationSucceeded(PRIMARY_USER_ID);

        mLocalService.unregisterLockSettingsStateListener(listener);
        assertTrue(
                mService.verifyCredential(badPassword, PRIMARY_USER_ID, 0 /* flags */)
                        .isOtherError());
        verify(listener, never()).onAuthenticationFailed(PRIMARY_USER_ID);
    }

    @Test
    public void testSetCredentialNotPossibleInSecureFrpModeDuringSuw() {
        setUserSetupComplete(false);
        setSecureFrpMode(true);
        try {
            mService.setLockCredential(newPassword("1234"), nonePassword(), PRIMARY_USER_ID);
            fail("Password shouldn't be changeable while FRP is active");
        } catch (SecurityException e) { }
    }

    @Test
    public void testSetCredentialNotPossibleInSecureFrpModeAfterSuw_FlagOn()
            throws RemoteException {
        setUserSetupComplete(true);
        setSecureFrpMode(true);
        try {
            mService.setLockCredential(newPassword("1234"), nonePassword(), PRIMARY_USER_ID);
            fail("Password shouldn't be changeable after SUW while FRP is active");
        } catch (SecurityException e) { }
    }

    @Test
    public void testPasswordHistoryDisabledByDefault() throws Exception {
        final int userId = PRIMARY_USER_ID;
        checkPasswordHistoryLength(userId, 0);
        setCredential(userId, newPassword("1234"));
        checkPasswordHistoryLength(userId, 0);
    }

    @Test
    public void testPasswordHistoryLengthHonored() throws Exception {
        final int userId = PRIMARY_USER_ID;
        when(mDevicePolicyManager.getPasswordHistoryLength(any(), eq(userId))).thenReturn(3);
        checkPasswordHistoryLength(userId, 0);

        setCredential(userId, newPassword("pass1"));
        checkPasswordHistoryLength(userId, 1);

        setCredential(userId, newPassword("pass2"), newPassword("pass1"));
        checkPasswordHistoryLength(userId, 2);

        setCredential(userId, newPassword("pass3"), newPassword("pass2"));
        checkPasswordHistoryLength(userId, 3);

        // maximum length should have been reached
        setCredential(userId, newPassword("pass4"), newPassword("pass3"));
        checkPasswordHistoryLength(userId, 3);
    }

    @Test(expected=NullPointerException.class)
    public void testSetBooleanRejectsNullKey() {
        mService.setBoolean(null, false, 0);
    }

    @Test(expected=NullPointerException.class)
    public void testSetLongRejectsNullKey() {
        mService.setLong(null, 0, 0);
    }

    @Test(expected=NullPointerException.class)
    public void testSetStringRejectsNullKey() {
        mService.setString(null, "value", 0);
    }

    @Test
    public void testDuplicateWrongGuessesAreNotCounted() throws Exception {
        final int userId = PRIMARY_USER_ID;
        final LockscreenCredential pin = newPin("1234");
        final LockscreenCredential wrongPin = newPin("1111");
        final int numGuesses = 100;

        mSpManager.enableWeaver();
        setCredential(userId, pin);
        final long protectorId = mService.getCurrentLskfBasedProtectorId(userId);
        final LskfIdentifier lskfId = new LskfIdentifier(userId, protectorId);

        // The software and hardware counters start at 0.
        assertEquals(0, mSpManager.readFailureCounter(lskfId));
        assertEquals(0, mSpManager.getSumOfWeaverFailureCounters());

        // Try the same wrong PIN repeatedly.
        for (int i = 0; i < numGuesses; i++) {
            VerifyCredentialResponse response =
                    mService.verifyCredential(wrongPin, userId, 0 /* flags */);
            assertFalse(response.isMatched());
            assertTrue(response.isCredCertainlyIncorrect());
            assertEquals(i != 0, response.isCredAlreadyTried());
            assertTrue(response.getTimeoutAsDuration().isZero());
        }
        // The software and hardware counters should now be 1, for 1 unique guess.
        assertEquals(1, mSpManager.readFailureCounter(lskfId));
        assertEquals(1, mSpManager.getSumOfWeaverFailureCounters());
    }

    @Test
    public void testVerifyCredentialTooShort() throws Exception {
        final int userId = PRIMARY_USER_ID;
        setCredential(userId, newPassword("password"));
        VerifyCredentialResponse response =
                mService.verifyCredential(newPassword("a"), userId, /* flags= */ 0);
        assertTrue(response.isCredTooShort());
    }

    // Tests that if verifyCredential is passed a wrong guess and Weaver reports INCORRECT_KEY with
    // zero timeout (which indicates a certainly wrong guess), then LockSettingsService saves that
    // guess as a recent wrong guess and rejects a repeat of it as a duplicate.
    @Test
    public void testRepeatOfWrongGuessRejectedAsDuplicate_afterWeaverIncorrectKeyWithoutTimeout()
            throws Exception {
        final int userId = PRIMARY_USER_ID;
        final LockscreenCredential credential = newPassword("password");
        final LockscreenCredential wrongGuess = newPassword("wrong");

        mSpManager.enableWeaver();
        setCredential(userId, credential);

        mSpManager.injectWeaverReadResponse(WeaverReadStatus.INCORRECT_KEY, Duration.ZERO);
        VerifyCredentialResponse response =
                mService.verifyCredential(wrongGuess, userId, /* flags= */ 0);
        assertTrue(response.isCredCertainlyIncorrect());
        assertFalse(response.isCredAlreadyTried());
        assertFalse(response.hasTimeout());
        assertEquals(Duration.ZERO, response.getTimeoutAsDuration());

        response = mService.verifyCredential(wrongGuess, userId, /* flags= */ 0);
        assertTrue(response.isCredAlreadyTried());
        assertEquals(Duration.ZERO, response.getTimeoutAsDuration());
    }

    @Test
    @EnableFlags(com.android.server.flags.Flags.FLAG_FIX_KEYSTORE_MEMORY_CLEANUP)
    public void testLockUser_locksCeStorageAndKeystore() throws Exception {
        final int userId = PRIMARY_USER_ID;
        final LockscreenCredential password = newPassword("password");
        setCredential(PRIMARY_USER_ID, password);
        mLocalService.lockUser(userId);

        // Verify that lockCeStorage is called on the correct user ID.
        verify(mInjector.getStorageManager()).lockCeStorage(eq(userId));
    }

    // Same as preceding test case, but uses a nonzero timeout.
    @Test
    public void testRepeatOfWrongGuessRejectedAsDuplicate_afterWeaverIncorrectKeyWithTimeout()
            throws Exception {
        final int userId = PRIMARY_USER_ID;
        final LockscreenCredential credential = newPassword("password");
        final LockscreenCredential wrongGuess = newPassword("wrong");
        final Duration timeout = Duration.ofSeconds(60);
        final Duration start = Duration.ofSeconds(10);
        mInjector.setTimeSinceBoot(start);

        mSpManager.enableWeaver();
        setCredential(userId, credential);

        mSpManager.injectWeaverReadResponse(WeaverReadStatus.INCORRECT_KEY, timeout);
        VerifyCredentialResponse response =
                mService.verifyCredential(wrongGuess, userId, /* flags= */ 0);
        assertTrue(response.isCredCertainlyIncorrect());
        assertFalse(response.isCredAlreadyTried());
        assertTrue(response.hasTimeout());
        assertEquals(timeout, response.getTimeoutAsDuration());
        mInjector.setTimeSinceBoot(start.plus(timeout).plusSeconds(1));

        response = mService.verifyCredential(wrongGuess, userId, /* flags= */ 0);
        assertTrue(response.isCredAlreadyTried());
        assertEquals(Duration.ZERO, response.getTimeoutAsDuration());
    }

    // Both software and hardware timeouts should preempt duplicate detection.
    @Test
    public void testRepeatOfWrongGuessThrottled_afterWeaverIncorrectKeyWithTimeoutButWithinTimeout()
            throws Exception {
        final int userId = PRIMARY_USER_ID;
        final LockscreenCredential credential = newPassword("password");
        final LockscreenCredential wrongGuess = newPassword("wrong");
        final Duration timeout = Duration.ofSeconds(60);
        final Duration start = Duration.ofSeconds(10);
        mInjector.setTimeSinceBoot(start);
        final Duration timeoutRemaining = Duration.ofSeconds(1);

        mSpManager.enableWeaver();
        setCredential(userId, credential);

        mSpManager.injectWeaverReadResponse(WeaverReadStatus.INCORRECT_KEY, timeout);
        VerifyCredentialResponse response =
                mService.verifyCredential(wrongGuess, userId, /* flags= */ 0);
        assertTrue(response.isCredCertainlyIncorrect());
        assertFalse(response.isCredAlreadyTried());
        assertTrue(response.hasTimeout());
        assertEquals(timeout, response.getTimeoutAsDuration());
        mInjector.setTimeSinceBoot(start.plus(timeout).minus(timeoutRemaining));

        response = mService.verifyCredential(wrongGuess, userId, /* flags= */ 0);
        assertFalse(response.isCredCertainlyIncorrect());
        assertTrue(response.hasTimeout());
        assertEquals(timeoutRemaining, response.getTimeoutAsDuration());
    }

    // Tests that if verifyCredential is passed a correct guess but it fails due to Weaver reporting
    // a status of THROTTLE (which is the expected status when there is a remaining rate-limiting
    // timeout in Weaver), then LockSettingsService does not block the same guess from being
    // re-attempted and in particular does not reject it as a duplicate wrong guess.
    @Test
    public void testRepeatOfCorrectGuessAllowed_afterWeaverThrottle() throws Exception {
        final int userId = PRIMARY_USER_ID;
        final LockscreenCredential credential = newPassword("password");
        final Duration start = Duration.ofSeconds(10);
        mInjector.setTimeSinceBoot(start);
        final Duration timeout = Duration.ofSeconds(60);

        mSpManager.enableWeaver();
        setCredential(userId, credential);

        mSpManager.injectWeaverReadResponse(WeaverReadStatus.THROTTLE, timeout);
        VerifyCredentialResponse response =
                mService.verifyCredential(credential, userId, /* flags= */ 0);
        assertTrue(response.hasTimeout());
        assertEquals(timeout, response.getTimeoutAsDuration());

        mInjector.setTimeSinceBoot(start.plus(timeout));
        response = mService.verifyCredential(credential, userId, /* flags= */ 0);
        assertTrue(response.isMatched());
    }

    // Tests that if verifyCredential is passed a correct guess but it fails due to Weaver reporting
    // a status of FAILED (which is the expected status when there is a transient error unrelated to
    // the guess), then LockSettingsService does not block the same guess from being re-attempted
    // and in particular does not reject it as a duplicate wrong guess.
    @Test
    public void testRepeatOfCorrectGuessAllowed_afterWeaverFailed() throws Exception {
        final int userId = PRIMARY_USER_ID;
        final LockscreenCredential credential = newPassword("password");

        mSpManager.enableWeaver();
        setCredential(userId, credential);

        mSpManager.injectWeaverReadResponse(WeaverReadStatus.FAILED, Duration.ZERO);
        VerifyCredentialResponse response =
                mService.verifyCredential(credential, userId, /* flags= */ 0);
        assertTrue(response.isOtherError());
        assertEquals(Duration.ZERO, response.getTimeoutAsDuration());

        response = mService.verifyCredential(credential, userId, /* flags= */ 0);
        assertTrue(response.isMatched());
    }

    @Test
    public void test20UniqueGuessesAllowed() throws Exception {
        final int userId = PRIMARY_USER_ID;
        final LockscreenCredential credential = newPassword("password");
        VerifyCredentialResponse response;

        mInjector.setTimeSinceBoot(Duration.ZERO);
        setCredential(userId, credential);
        guessWrongCredential(userId, 19, TEN_YEARS);
        response = mService.verifyCredential(credential, userId, /* flags= */ 0);
        assertTrue(response.isMatched());
    }

    @Test
    public void testMoreThan20UniqueGuessesNotAllowed() throws Exception {
        final int userId = PRIMARY_USER_ID;
        final LockscreenCredential credential = newPassword("password");
        VerifyCredentialResponse response;

        mInjector.setTimeSinceBoot(Duration.ZERO);
        setCredential(userId, credential);
        guessWrongCredential(userId, 20, TEN_YEARS);
        response = mService.verifyCredential(credential, userId, /* flags= */ 0);
        assertFalse(response.isMatched());
    }

    @Test
    public void testVerifyCredentialResponseEqualsAndHashCode() {
        testEqualsAndHashCode(() -> new VerifyCredentialResponse.Builder().build());
        testEqualsAndHashCode(
                () ->
                        new VerifyCredentialResponse.Builder()
                                .setGatekeeperHAT(new byte[] {0, 1, 2, 3})
                                .setGatekeeperPasswordHandle(1L)
                                .build());
        Arrays.asList(Duration.ZERO, TEN_YEARS, MAX_LENGTH_DURATION).forEach(timeout -> {
            testEqualsAndHashCode(() -> VerifyCredentialResponse.credIncorrect(timeout));
            testEqualsAndHashCode(() -> VerifyCredentialResponse.fromTimeout(timeout));
        });
        testEqualsAndHashCode(VerifyCredentialResponse::credAlreadyTried);
        testEqualsAndHashCode(VerifyCredentialResponse::credTooShort);
        testEqualsAndHashCode(VerifyCredentialResponse::fromError);
    }

    private void testEqualsAndHashCode(Supplier<VerifyCredentialResponse> responseFactory) {
        VerifyCredentialResponse first = responseFactory.get();
        VerifyCredentialResponse second = responseFactory.get();
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    @Test
    public void testVerifyCredentialResponseNotEqual() {
        VerifyCredentialResponse okResponse = new VerifyCredentialResponse.Builder().build();
        VerifyCredentialResponse okResponseWithHat = new VerifyCredentialResponse.Builder()
                .setGatekeeperPasswordHandle(1234L)
                .setGatekeeperHAT(new byte[] { 1 })
                .build();
        VerifyCredentialResponse incorrectResponse = VerifyCredentialResponse.credIncorrect();
        VerifyCredentialResponse incorrectResponseWithTimeout =
                VerifyCredentialResponse.credIncorrect(Duration.ofMillis(1000));
        VerifyCredentialResponse incorrectResponseWithTimeout2 =
                VerifyCredentialResponse.credIncorrect(Duration.ofMillis(2000));
        VerifyCredentialResponse timeoutResponse =
                VerifyCredentialResponse.fromTimeout(Duration.ofMillis(1000));
        VerifyCredentialResponse timeoutResponse2 =
                VerifyCredentialResponse.fromTimeout(Duration.ofMillis(2000));
        VerifyCredentialResponse tooShortResponse =
                VerifyCredentialResponse.credTooShort();
        VerifyCredentialResponse alreadyTriedResponse = VerifyCredentialResponse.credAlreadyTried();
        VerifyCredentialResponse errorResponse = VerifyCredentialResponse.fromError();

        List<VerifyCredentialResponse> responses = Arrays.asList(okResponse, okResponseWithHat,
                incorrectResponse, incorrectResponseWithTimeout, incorrectResponseWithTimeout2,
                timeoutResponse, timeoutResponse2, tooShortResponse, alreadyTriedResponse,
                errorResponse);

        // All responses are distinct and should not be equal.
        for (int i = 0; i < responses.size() - 1; i++) {
            for (int j = i + 1; j < responses.size(); j++) {
                assertThat(responses.get(i)).isNotEqualTo(responses.get(j));
            }
        }
    }

    @Test
    public void testVerifyCredentialResponseRecreatesEqual() {
        testRecreatesEqual(new VerifyCredentialResponse.Builder().build());
        testRecreatesEqual(
                new VerifyCredentialResponse.Builder()
                        .setGatekeeperHAT(new byte[] {0, 1, 2, 3})
                        .setGatekeeperPasswordHandle(1L)
                        .build());
        Arrays.asList(Duration.ZERO, TEN_YEARS, MAX_LENGTH_DURATION).forEach(timeout -> {
            testRecreatesEqual(VerifyCredentialResponse.credIncorrect(timeout));
            testRecreatesEqual(VerifyCredentialResponse.fromTimeout(timeout));
        });
        testRecreatesEqual(VerifyCredentialResponse.credAlreadyTried());
        testRecreatesEqual(VerifyCredentialResponse.credTooShort());
        testRecreatesEqual(VerifyCredentialResponse.fromError());
    }

    private void testRecreatesEqual(VerifyCredentialResponse response) {
        assertThat(response).recreatesEqual(VerifyCredentialResponse.CREATOR);
    }

    @Test
    @DisableFlags(android.security.Flags.FLAG_ENABLE_UNCLAMPED_LOCKOUTS)
    public void testVerifyCredentialResponseTimeoutClamping() {
        testTimeoutClamping(Duration.ofMillis(Long.MIN_VALUE), Integer.MAX_VALUE);
        testTimeoutClamping(Duration.ofMillis(Integer.MIN_VALUE), Integer.MAX_VALUE);
        testTimeoutClamping(Duration.ofMillis(-100), Integer.MAX_VALUE);
        testTimeoutClamping(Duration.ofMillis(-1), Integer.MAX_VALUE);
        testTimeoutClamping(Duration.ofNanos(-1), Integer.MAX_VALUE);
        testTimeoutClamping(Duration.ZERO, 0);
        testTimeoutClamping(Duration.ofNanos(1), 0);
        testTimeoutClamping(Duration.ofMillis(1), 1);
        testTimeoutClamping(Duration.ofSeconds(1), 1000);
        testTimeoutClamping(Duration.ofSeconds(1000000), 1000000000);
        testTimeoutClamping(Duration.ofMillis(Integer.MAX_VALUE), Integer.MAX_VALUE);
        testTimeoutClamping(Duration.ofMillis((long) Integer.MAX_VALUE + 1), Integer.MAX_VALUE);
        testTimeoutClamping(Duration.ofMillis(Long.MAX_VALUE), Integer.MAX_VALUE);
    }

    @Test
    @EnableFlags(android.security.Flags.FLAG_ENABLE_UNCLAMPED_LOCKOUTS)
    public void testVerifyCredentialResponseNoTimeoutClamping() {
        testNoTimeoutClamping(Duration.ofMillis(Long.MIN_VALUE));
        testNoTimeoutClamping(Duration.ofMillis(Integer.MIN_VALUE));
        testNoTimeoutClamping(Duration.ofMillis(-100));
        testNoTimeoutClamping(Duration.ofMillis(-1));
        testNoTimeoutClamping(Duration.ofNanos(-1));
        testNoTimeoutClamping(Duration.ZERO);
        testNoTimeoutClamping(Duration.ofNanos(1));
        testNoTimeoutClamping(Duration.ofMillis(1));
        testNoTimeoutClamping(Duration.ofSeconds(1));
        testNoTimeoutClamping(Duration.ofSeconds(1000000));
        testNoTimeoutClamping(Duration.ofMillis(Integer.MAX_VALUE));
        testNoTimeoutClamping(Duration.ofMillis((long) Integer.MAX_VALUE + 1));
        testNoTimeoutClamping(Duration.ofMillis(Long.MAX_VALUE));
    }

    @Test
    public void testGetLockoutEndTime_initialState() {
        final int userId = PRIMARY_USER_ID;

        reset(mInvalidateLockoutEndTimeCacheMock);
        Duration lockoutEndTime = mService.getLockoutEndTime(userId).getDuration();

        assertEquals(Duration.ZERO, lockoutEndTime);
        verify(mInvalidateLockoutEndTimeCacheMock, never()).run();
    }

    @Test
    public void testGetLockoutEndTime_nonZeroAfterTimedOutAttempt() throws Exception {
        final int userId = PRIMARY_USER_ID;
        final LockscreenCredential credential = newPassword("password");
        final Duration now = Duration.ofSeconds(1);
        mInjector.setTimeSinceBoot(now);
        setCredential(userId, credential);
        reset(mInvalidateLockoutEndTimeCacheMock);
        guessWrongCredential(userId, /* times= */ 5);

        final Duration lockoutEndTime = mService.getLockoutEndTime(userId).getDuration();

        final Duration expectedEndTime = now.plusSeconds(60);
        assertEquals(expectedEndTime, lockoutEndTime);
        verify(mInvalidateLockoutEndTimeCacheMock).run();
    }

    @Test
    public void testGetLockoutEndTime_zeroAfterVerificationPostLockout() throws Exception {
        final int userId = PRIMARY_USER_ID;
        final LockscreenCredential credential = newPassword("password");
        final Duration now = Duration.ofSeconds(1);
        mInjector.setTimeSinceBoot(now);
        setCredential(userId, credential);
        reset(mInvalidateLockoutEndTimeCacheMock);
        guessWrongCredential(userId, /* times= */ 5);
        mInjector.setTimeSinceBoot(now.plusSeconds(61)); // Advance past lockout
        VerifyCredentialResponse response =
                mService.verifyCredential(credential, userId, /* flags= */ 0);
        assertTrue(response.isMatched());

        final Duration lockoutEndTime = mService.getLockoutEndTime(userId).getDuration();

        assertEquals(Duration.ZERO, lockoutEndTime);
        // invalidate 2 times:
        // * upon the timeout after 5th failed attempt
        // * upon verifying the credential for the primary user
        verify(mInvalidateLockoutEndTimeCacheMock, times(2)).run();
    }

    @Test
    public void testGetLockoutEndTime_zeroAfterEndTime() throws Exception {
        final int userId = PRIMARY_USER_ID;
        final LockscreenCredential credential = newPassword("password");
        final Duration now = Duration.ofSeconds(1);
        mInjector.setTimeSinceBoot(now);
        setCredential(userId, credential);
        reset(mInvalidateLockoutEndTimeCacheMock);
        guessWrongCredential(userId, /* times= */ 5);
        mInjector.setTimeSinceBoot(now.plusSeconds(61)); // Advance past lockout

        final Duration lockoutEndTime = mService.getLockoutEndTime(userId).getDuration();

        assertEquals(Duration.ZERO, lockoutEndTime);
        // invalidate 2 times:
        // * upon the timeout after 5th failed attempt
        // * upon getting the timeout after the boot clock has passed it
        verify(mInvalidateLockoutEndTimeCacheMock, times(2)).run();
    }

    @Test
    public void testGetLockoutEndTime_zeroAfterCredentialResetWithToken() throws Exception {
        byte[] token = "some-high-entropy-secure-token".getBytes();
        EscrowTokenStateChangeCallback mockActivateListener =
                mock(EscrowTokenStateChangeCallback.class);
        final int userId = PRIMARY_USER_ID;
        final LockscreenCredential credential = newPassword("password");
        final Duration now = Duration.ofSeconds(1);
        mInjector.setTimeSinceBoot(now);
        setCredential(userId, credential);
        long handle = mLocalService.addEscrowToken(token, userId, mockActivateListener);
        // Activate token
        assertTrue(mService.verifyCredential(credential, userId, /* flags= */ 0).isMatched());
        guessWrongCredential(userId, /* times= */ 5);
        final LockscreenCredential credential2 = newPassword("password2");
        reset(mInvalidateLockoutEndTimeCacheMock);
        mLocalService.setLockCredentialWithToken(credential2, handle, token, userId);

        final Duration lockoutEndTime = mService.getLockoutEndTime(userId).getDuration();

        assertEquals(Duration.ZERO, lockoutEndTime);
        // should invalidate upon setting the lock credential
        verify(mInvalidateLockoutEndTimeCacheMock).run();
    }

    @Test
    @DisableFlags(android.security.Flags.FLAG_ENABLE_WEAVER_GET_TIMEOUT)
    public void testGetLockoutEndTime_doesNotAccountForWeaverTimeoutWhenFlagDisabled()
            throws Exception {
        final int userId = PRIMARY_USER_ID;
        final LockscreenCredential credential = newPassword("password");
        final Duration now = Duration.ZERO;
        final Duration hwTimeout = Duration.ofMinutes(5);

        mInjector.setTimeSinceBoot(now);
        mSpManager.enableWeaver();
        mSpManager.injectWeaverTimeout(hwTimeout);
        setCredential(userId, credential);
        assertEquals(Duration.ZERO, mService.getLockoutEndTime(userId).getDuration());
    }

    @Test
    @EnableFlags(android.security.Flags.FLAG_ENABLE_WEAVER_GET_TIMEOUT)
    public void testGetLockoutEndTime_accountsForWeaverTimeout() throws Exception {
        final int userId = PRIMARY_USER_ID;
        final LockscreenCredential credential = newPassword("password");
        final Duration now = Duration.ZERO;
        final Duration hwTimeout = Duration.ofMinutes(5);

        mInjector.setTimeSinceBoot(now);
        mSpManager.enableWeaver();
        mSpManager.injectWeaverTimeout(hwTimeout);
        setCredential(userId, credential);
        assertEquals(hwTimeout, mService.getLockoutEndTime(userId).getDuration());
    }

    @Test
    @EnableFlags(android.security.Flags.FLAG_ENABLE_SERVICE_SIDE_CREDENTIAL_TYPE_CACHE)
    public void testUserCredentialCache() throws Exception {
        final int userId = PRIMARY_USER_ID;

        // The initial getCredentialType() should return NONE and set the cache entry.
        assertEquals(CREDENTIAL_TYPE_UNKNOWN, mService.queryServiceSideCredentialTypeCache(userId));
        assertEquals(CREDENTIAL_TYPE_NONE, mService.getCredentialType(userId));
        assertEquals(CREDENTIAL_TYPE_NONE, mService.queryServiceSideCredentialTypeCache(userId));

        // Set a PIN.
        assertTrue(mService.setLockCredential(newPin("1234"), nonePassword(), userId));

        // setLockCredential() should have updated the cache entry to PIN. (Actually it removes the
        // cache entry and then re-adds it, but the effect is the same.)
        assertEquals(CREDENTIAL_TYPE_PIN, mService.queryServiceSideCredentialTypeCache(userId));

        // getCredentialType() should return PIN, and the cache entry should remain set.
        assertEquals(CREDENTIAL_TYPE_PIN, mService.getCredentialType(userId));
        assertEquals(CREDENTIAL_TYPE_PIN, mService.queryServiceSideCredentialTypeCache(userId));
    }

    @Test
    @DisableFlags(android.security.Flags.FLAG_ENABLE_SERVICE_SIDE_CREDENTIAL_TYPE_CACHE)
    public void testUserCredentialCache_disabled() throws Exception {
        final int userId = PRIMARY_USER_ID;

        assertEquals(CREDENTIAL_TYPE_UNKNOWN, mService.queryServiceSideCredentialTypeCache(userId));
        assertEquals(CREDENTIAL_TYPE_NONE, mService.getCredentialType(userId));
        assertEquals(CREDENTIAL_TYPE_UNKNOWN, mService.queryServiceSideCredentialTypeCache(userId));
        assertTrue(mService.setLockCredential(newPin("1234"), nonePassword(), userId));
        assertEquals(CREDENTIAL_TYPE_UNKNOWN, mService.queryServiceSideCredentialTypeCache(userId));
        assertEquals(CREDENTIAL_TYPE_PIN, mService.getCredentialType(userId));
        assertEquals(CREDENTIAL_TYPE_UNKNOWN, mService.queryServiceSideCredentialTypeCache(userId));
    }

    private void guessWrongCredential(int userId, int times) {
        guessWrongCredential(userId, times, Duration.ZERO);
    }

    private void guessWrongCredential(int userId, int times, Duration timeBetweenGuesses) {
        for (int i = 0; i < times; i++) {
            VerifyCredentialResponse response =
                    mService.verifyCredential(newPassword("wrong" + i), userId, /* flags= */ 0);
            assertFalse(response.isMatched());
            mInjector.setTimeSinceBoot(mInjector.getTimeSinceBoot().plus(timeBetweenGuesses));
        }
    }

    private void testTimeoutClamping(Duration originalTimeout, int expectedClampedTimeout) {
        VerifyCredentialResponse response = VerifyCredentialResponse.fromTimeout(originalTimeout);
        assertEquals(Duration.ofMillis(expectedClampedTimeout), response.getTimeout());
    }

    private void testNoTimeoutClamping(Duration originalTimeout) {
        VerifyCredentialResponse response = VerifyCredentialResponse.fromTimeout(originalTimeout);
        assertEquals(originalTimeout, response.getTimeout());
    }

    private void checkRecordedFrpNotificationIntent() {
        Intent savedNotificationIntent = mService.getSavedFrpNotificationIntent();
        assertNotNull(savedNotificationIntent);
        UserHandle userHandle = mService.getSavedFrpNotificationUserHandle();
        assertEquals(userHandle, UserHandle.of(mInjector.getUserManagerInternal().getMainUserId()));

        String permission = mService.getSavedFrpNotificationPermission();
        assertEquals(CONFIGURE_FACTORY_RESET_PROTECTION, permission);
    }

    private void checkPasswordHistoryLength(int userId, int expectedLen) {
        String history = mService.getString(LockPatternUtils.PASSWORD_HISTORY_KEY, "", userId);
        String[] hashes = TextUtils.split(history, LockPatternUtils.PASSWORD_HISTORY_DELIMITER);
        assertEquals(expectedLen, hashes.length);
    }

    private void testSetCredentialFailsWithoutLockScreen(
            int userId, LockscreenCredential credential) throws RemoteException {
        mService.mHasSecureLockScreen = false;
        try {
            mService.setLockCredential(credential, nonePassword(), userId);
            fail("An exception should have been thrown.");
        } catch (UnsupportedOperationException e) {
            // Success - the exception was expected.
        }

        assertEquals(CREDENTIAL_TYPE_NONE, mService.getCredentialType(userId));
    }

    private void testChangeCredential(int userId, LockscreenCredential newCredential,
            LockscreenCredential oldCredential) throws RemoteException {
        setCredential(userId, oldCredential);
        setCredential(userId, newCredential, oldCredential);
        assertVerifyCredential(userId, newCredential);
    }

    private void assertVerifyCredential(int userId, LockscreenCredential credential)
            throws RemoteException{
        VerifyCredentialResponse response = mService.verifyCredential(credential, userId,
                0 /* flags */);

        assertTrue(response.isMatched());
        if (credential.isPassword()) {
            assertEquals(CREDENTIAL_TYPE_PASSWORD, mService.getCredentialType(userId));
        } else if (credential.isPin()) {
            assertEquals(CREDENTIAL_TYPE_PIN, mService.getCredentialType(userId));
        } else if (credential.isPattern()) {
            assertEquals(CREDENTIAL_TYPE_PATTERN, mService.getCredentialType(userId));
        } else {
            assertEquals(CREDENTIAL_TYPE_NONE, mService.getCredentialType(userId));
        }
        // check for bad credential
        final LockscreenCredential badCredential;
        if (!credential.isNone()) {
            badCredential = credential.duplicate();
            badCredential.getCredential()[0] ^= 1;
        } else {
            badCredential = LockscreenCredential.createPin("0");
        }
        assertTrue(mService.verifyCredential(badCredential, userId, 0 /* flags */).isOtherError());
    }

    private void setUpUnifiedPassword(LockscreenCredential unifiedPassword) throws RemoteException {
        setUpUnifiedPassword(unifiedPassword, /* isLegacy= */ false);
    }

    private void setUpUnifiedPassword(LockscreenCredential unifiedPassword, boolean isLegacy)
            throws RemoteException {
        setCredential(PRIMARY_USER_ID, unifiedPassword);
        mService.setSeparateProfileChallengeEnabled(MANAGED_PROFILE_USER_ID, false, null);
        LockscreenCredential unifiedProfilePassword = null;
        try {
            unifiedProfilePassword =
                    mService.getDecryptedPasswordForUnifiedProfile(MANAGED_PROFILE_USER_ID);
        } catch (KeyStoreException
                | UnrecoverableKeyException
                | NoSuchAlgorithmException
                | NoSuchPaddingException
                | InvalidKeyException
                | InvalidAlgorithmParameterException
                | IllegalBlockSizeException
                | BadPaddingException
                | CertificateException
                | IOException e) {
            throw new IllegalStateException("Unexpected failure to get profile password", e);
        }
        assertTrue(unifiedProfilePassword.isUnifiedProfilePassword());
        setUpChildProfileLockFileIfNeeded(
                /* hasChildProfileLockBefore= */ isLegacy, unifiedProfilePassword);
        setUpSpProtectorPasswordIfNeeded(
                /* hasSpProtectorPasswordBefore= */ !isLegacy, unifiedProfilePassword);
    }

    private void setAndVerifyCredential(int userId, LockscreenCredential newCredential)
            throws RemoteException {
        setCredential(userId, newCredential);
        assertVerifyCredential(userId, newCredential);
    }

    private void setCredential(int userId, LockscreenCredential newCredential)
            throws RemoteException {
        setCredential(userId, newCredential, nonePassword());
    }

    private void clearCredential(int userId, LockscreenCredential oldCredential)
            throws RemoteException {
        setCredential(userId, nonePassword(), oldCredential);
    }

    private void setCredential(int userId, LockscreenCredential newCredential,
            LockscreenCredential oldCredential) throws RemoteException {
        reset(mMockWindowManager, mMockPackageManagerInternal);
        assertTrue(mService.setLockCredential(newCredential, oldCredential, userId));
        flushHandlerTasks();
        verify(mMockWindowManager).reportPasswordChanged(userId);
        if (android.security.Flags.appLockApis()) {
            verify(mMockPackageManagerInternal).reportLockCredentialChanged(userId);
        }
        assertEquals(newCredential.getType(), mService.getCredentialType(userId));
        if (newCredential.isNone()) {
            assertEquals(0, mGateKeeperService.getSecureUserId(userId));
        } else {
            assertNotEquals(0, mGateKeeperService.getSecureUserId(userId));
        }
    }
}
