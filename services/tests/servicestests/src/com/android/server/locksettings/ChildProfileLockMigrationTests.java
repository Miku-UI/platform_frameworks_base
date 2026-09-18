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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.text.TextUtils;

import androidx.test.filters.SmallTest;

import com.android.internal.widget.LockscreenCredential;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import platform.test.runner.parameterized.Parameter;
import platform.test.runner.parameterized.ParameterizedAndroidJunit4;
import platform.test.runner.parameterized.Parameters;

import java.util.List;
import java.util.stream.Stream;

/** atest FrameworksServicesTests:ChildProfileLockMigrationTests */
@SmallTest
@Presubmit
@RunWith(ParameterizedAndroidJunit4.class)
public class ChildProfileLockMigrationTests extends BaseChildProfileLockMigrationTests {

    private record Scenario(
            boolean hasChildProfileLockBefore,
            boolean hasSpProtectorPasswordBefore,
            boolean expectedToHaveChildProfileLockAfter,
            boolean expectedToHaveSpProtectorPasswordAfter) {
        @Override
        public String toString() {
            return TextUtils.formatSimple(
                    "Scenario: hasChildProfileLock: %b->%b, " + "hasSpProtectorPassword: %b->%b",
                    hasChildProfileLockBefore,
                    expectedToHaveChildProfileLockAfter,
                    hasSpProtectorPasswordBefore,
                    expectedToHaveSpProtectorPasswordAfter);
        }
    }

    /** Provides the list of scenarios to test. */
    @Parameters(name = "scenario={0}")
    public static List<Scenario[]> data() {
        return Stream.of(
                        new Scenario(
                                /* hasChildProfileLockBefore= */ true,
                                /* hasSpProtectorPasswordBefore= */ false,
                                /* hasChildProfileLockAfter= */ false,
                                /* hasSpProtectorPasswordAfter= */ true),
                        // existing child profile lock won't be cleaned up.
                        new Scenario(
                                /* hasChildProfileLockBefore= */ true,
                                /* hasSpProtectorPasswordBefore= */ true,
                                /* hasChildProfileLockAfter= */ true,
                                /* hasSpProtectorPasswordAfter= */ true))
                .map(scenario -> new Scenario[] {scenario})
                .toList();
    }

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Parameter public Scenario scenario;

    @Test
    public void testMigrateChildProfileLock() throws Exception {
        boolean hasChildProfileLockBefore = scenario.hasChildProfileLockBefore;
        boolean hasSpProtectorPasswordBefore = scenario.hasSpProtectorPasswordBefore;
        boolean expectedToHaveChildProfileLockAfter = scenario.expectedToHaveChildProfileLockAfter;
        boolean expectedToHaveSpProtectorPasswordAfter =
                scenario.expectedToHaveSpProtectorPasswordAfter;

        LockscreenCredential unifiedProfilePassword =
                LockscreenCredential.createUnifiedProfilePassword(new byte[] {1, 2, 3});
        mService.setLockCredential(UNIFIED_PASSWORD, nonePassword(), PRIMARY_USER_ID);
        setUpChildProfileLockFileIfNeeded(hasChildProfileLockBefore, unifiedProfilePassword);
        setUpSpProtectorPasswordIfNeeded(hasSpProtectorPasswordBefore, unifiedProfilePassword);

        // Double-check that the before state is as expected.
        assertFalse(
                TextUtils.formatSimple(
                        "%s, separateProfileChallenge should be false before", scenario),
                mService.getSeparateProfileChallengeEnabledInternal(MANAGED_PROFILE_USER_ID));
        assertEquals(
                TextUtils.formatSimple(
                        "%s, failed assertion on hasChildProfileLockBefore", scenario),
                hasChildProfileLockBefore,
                mStorage.hasChildProfileLock(MANAGED_PROFILE_USER_ID));
        assertEquals(
                TextUtils.formatSimple(
                        "%s, failed assertion on hasSpProtectorPasswordBefore", scenario),
                hasSpProtectorPasswordBefore,
                hasSpProtectorPassword(MANAGED_PROFILE_USER_ID));

        mService.getDecryptedPasswordForUnifiedProfile(MANAGED_PROFILE_USER_ID);

        assertFalse(
                TextUtils.formatSimple(
                        "%s, separateProfileChallenge should be false after", scenario),
                mService.getSeparateProfileChallengeEnabledInternal(MANAGED_PROFILE_USER_ID));
        assertEquals(
                TextUtils.formatSimple(
                        "%s, failed assertion on expectedToHaveChildProfileLockAfter", scenario),
                expectedToHaveChildProfileLockAfter,
                mStorage.hasChildProfileLock(MANAGED_PROFILE_USER_ID));
        assertEquals(
                TextUtils.formatSimple(
                        "%s, failed assertion on expectedToHaveSpProtectorPasswordAfter", scenario),
                expectedToHaveSpProtectorPasswordAfter,
                hasSpProtectorPassword(MANAGED_PROFILE_USER_ID));
    }
}
