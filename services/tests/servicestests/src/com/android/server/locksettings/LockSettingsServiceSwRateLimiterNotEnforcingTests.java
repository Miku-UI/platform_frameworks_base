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

package com.android.server.locksettings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.widget.LockscreenCredential;
import com.android.internal.widget.VerifyCredentialResponse;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** atest FrameworksServicesTests:LockSettingsServiceSwRateLimiterNotEnforcingTests */
@SmallTest
@Presubmit
@RunWith(AndroidJUnit4.class)
public class LockSettingsServiceSwRateLimiterNotEnforcingTests
        extends BaseLockSettingsServiceTests {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Before
    public void setUp() {
        mService.initializeSyntheticPassword(PRIMARY_USER_ID);
    }

    @Override
    protected boolean isSoftwareLskfRateLimiterEnforcing() {
        return false;
    }

    @Test
    @EnableFlags(android.security.Flags.FLAG_SOFTWARE_RATELIMITER)
    public void testDuplicateWrongGuesses() throws Exception {
        final int userId = PRIMARY_USER_ID;
        final LockscreenCredential pin = newPin("1234");
        final LockscreenCredential wrongPin = newPin("1111");
        final int numGuesses = 100;

        mSpManager.enableWeaver();
        assertTrue(mService.setLockCredential(pin, nonePassword(), userId));
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
        }
        // The software counter should now be 1, since there was one unique guess. The hardware
        // counter should now be numGuesses, since the software rate-limiter was in non-enforcing
        // mode, i.e. the hardware rate-limiter should still have been called for every guess.
        assertEquals(1, mSpManager.readFailureCounter(lskfId));
        assertEquals(numGuesses, mSpManager.getSumOfWeaverFailureCounters());
    }
}
