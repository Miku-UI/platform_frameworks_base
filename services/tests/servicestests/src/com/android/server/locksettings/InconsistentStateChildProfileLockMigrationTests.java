/*
 * Copyright 2025 The Android Open Source Project
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

import static com.android.server.locksettings.UnifiedProfilePasswordCrypto.removeKeystoreProfileKey;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.widget.LockscreenCredential;

import org.junit.Test;
import org.junit.runner.RunWith;

/** atest FrameworksServicesTests:InconsistentChildProfileLockMigrationTests */
@SmallTest
@Presubmit
@RunWith(AndroidJUnit4.class)
public class InconsistentStateChildProfileLockMigrationTests
        extends BaseChildProfileLockMigrationTests {
    @Test
    public void testMigrateChildProfileLock_profilePwdAlreadyExists() throws Exception {
        LockscreenCredential unifiedProfilePassword =
                LockscreenCredential.createUnifiedProfilePassword(new byte[] {1, 2, 3});
        mService.setLockCredential(UNIFIED_PASSWORD, nonePassword(), PRIMARY_USER_ID);
        setUpChildProfileLockFileIfNeeded(true, unifiedProfilePassword);
        setUpSpProtectorPasswordIfNeeded(true, unifiedProfilePassword);

        // Double-check that the before state is as expected.
        assertFalse(mService.getSeparateProfileChallengeEnabledInternal(MANAGED_PROFILE_USER_ID));
        assertTrue(mStorage.hasChildProfileLock(MANAGED_PROFILE_USER_ID));
        assertTrue(hasSpProtectorPassword(MANAGED_PROFILE_USER_ID));

        long parentSid = mGateKeeperService.getSecureUserId(PRIMARY_USER_ID);
        long protectorId = mService.getCurrentLskfBasedProtectorId(MANAGED_PROFILE_USER_ID);
        LockscreenCredential internalProfilePassword =
                mService.getDecryptedPasswordForUnifiedProfile(MANAGED_PROFILE_USER_ID);

        boolean migrated =
                mService.migrateChildProfileLockPasswordToProfileProtectorPwd(
                        internalProfilePassword, parentSid, MANAGED_PROFILE_USER_ID, protectorId);

        assertFalse(mService.getSeparateProfileChallengeEnabledInternal(MANAGED_PROFILE_USER_ID));
        // Migration would remove this file, so expect it to not be removed.
        assertTrue(mStorage.hasChildProfileLock(MANAGED_PROFILE_USER_ID));
        assertTrue(hasSpProtectorPassword(MANAGED_PROFILE_USER_ID));
        assertFalse(migrated);
    }

    @Test
    public void testMigrateChildProfileLock_undecryptableExistingProfilePwd() throws Exception {
        LockscreenCredential unifiedProfilePassword =
                LockscreenCredential.createUnifiedProfilePassword(new byte[] {1, 2, 3});
        mService.setLockCredential(UNIFIED_PASSWORD, nonePassword(), PRIMARY_USER_ID);
        setUpChildProfileLockFileIfNeeded(true, unifiedProfilePassword);
        setUpSpProtectorPasswordIfNeeded(true, unifiedProfilePassword);

        // Double-check that the before state is as expected.
        assertFalse(mService.getSeparateProfileChallengeEnabledInternal(MANAGED_PROFILE_USER_ID));
        assertTrue(mStorage.hasChildProfileLock(MANAGED_PROFILE_USER_ID));
        assertTrue(hasSpProtectorPassword(MANAGED_PROFILE_USER_ID));

        long parentSid = mGateKeeperService.getSecureUserId(PRIMARY_USER_ID);
        long protectorId = mService.getCurrentLskfBasedProtectorId(MANAGED_PROFILE_USER_ID);
        LockscreenCredential internalProfilePassword =
                mService.getDecryptedPasswordForUnifiedProfile(MANAGED_PROFILE_USER_ID);
        removeKeystoreProfileKey(mKeyStoreRule.getKeyStore(), MANAGED_PROFILE_USER_ID, protectorId);

        boolean migrated =
                mService.migrateChildProfileLockPasswordToProfileProtectorPwd(
                        internalProfilePassword, parentSid, MANAGED_PROFILE_USER_ID, protectorId);

        assertFalse(mService.getSeparateProfileChallengeEnabledInternal(MANAGED_PROFILE_USER_ID));
        // Migration would remove this file, so expect it to be removed.
        assertFalse(mStorage.hasChildProfileLock(MANAGED_PROFILE_USER_ID));
        assertTrue(hasSpProtectorPassword(MANAGED_PROFILE_USER_ID));
        assertTrue(migrated);
    }

    @Test
    public void testMigrateChildProfileLock_mismatchingExistingProfilePwd() throws Exception {
        LockscreenCredential unifiedProfilePassword =
                LockscreenCredential.createUnifiedProfilePassword(new byte[] {1, 2, 3});
        LockscreenCredential otherUnifiedProfilePassword =
                LockscreenCredential.createUnifiedProfilePassword(new byte[] {1, 2, 4});
        mService.setLockCredential(UNIFIED_PASSWORD, nonePassword(), PRIMARY_USER_ID);
        setUpChildProfileLockFileIfNeeded(
                /* hasChildProfileLockBefore= */ true,
                /* removeExisting= */ false,
                unifiedProfilePassword);
        setUpSpProtectorPasswordIfNeeded(
                /* hasSpProtectorPasswordBefore= */ true,
                /* removeExisting= */ false,
                otherUnifiedProfilePassword);

        // Double-check that the before state is as expected.
        assertFalse(mService.getSeparateProfileChallengeEnabledInternal(MANAGED_PROFILE_USER_ID));
        assertTrue(mStorage.hasChildProfileLock(MANAGED_PROFILE_USER_ID));
        assertTrue(hasSpProtectorPassword(MANAGED_PROFILE_USER_ID));

        long parentSid = mGateKeeperService.getSecureUserId(PRIMARY_USER_ID);
        long protectorId = mService.getCurrentLskfBasedProtectorId(MANAGED_PROFILE_USER_ID);

        assertThrows(
                IllegalStateException.class,
                () ->
                        mService.migrateChildProfileLockPasswordToProfileProtectorPwd(
                                unifiedProfilePassword,
                                parentSid,
                                MANAGED_PROFILE_USER_ID,
                                protectorId));
    }
}
