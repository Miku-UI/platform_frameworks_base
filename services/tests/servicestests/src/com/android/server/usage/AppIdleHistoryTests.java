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
 * limitations under the License.
 */

package com.android.server.usage;

import static android.app.usage.UsageStatsManager.REASON_MAIN_FORCED_BY_SYSTEM;
import static android.app.usage.UsageStatsManager.REASON_MAIN_FORCED_BY_USER;
import static android.app.usage.UsageStatsManager.REASON_MAIN_TIMEOUT;
import static android.app.usage.UsageStatsManager.REASON_MAIN_USAGE;
import static android.app.usage.UsageStatsManager.REASON_SUB_FORCED_SYSTEM_FLAG_BACKGROUND_RESOURCE_USAGE;
import static android.app.usage.UsageStatsManager.REASON_SUB_USAGE_MOVE_TO_FOREGROUND;
import static android.app.usage.UsageStatsManager.REASON_SUB_USAGE_NOTIFICATION_SEEN;
import static android.app.usage.UsageStatsManager.REASON_SUB_USAGE_SLICE_PINNED;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_ACTIVE;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_FREQUENT;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_RARE;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_RESTRICTED;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_WORKING_SET;
import static android.app.usage.UsageStatsManager.standbyBucketToString;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.os.FileUtils;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public final class AppIdleHistoryTests {
    private static final String PACKAGE_1 = "com.android.testpackage1";
    private static final String PACKAGE_2 = "com.android.testpackage2";
    private static final String PACKAGE_3 = "com.android.testpackage3";
    private static final String PACKAGE_4 = "com.android.testpackage4";

    private static final int USER_ID = 0;

    private File mStorageDir;

    @Before
    public void setUp() {
        mStorageDir = new File(
                InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir(),
                "appidle");
        mStorageDir.mkdirs();
    }

    @After
    public void tearDown() {
        FileUtils.deleteContents(mStorageDir);
    }

    @Test
    public void testFilesCreation() {
        AppIdleHistory aih = new AppIdleHistory(mStorageDir, 0);

        aih.updateDisplay(/* screenOn= */ true, /* elapsedRealtime= */ 1000);
        aih.updateDisplay(/* screenOn= */ false, /* elapsedRealtime= */ 2000);
        // Screen On time file should be written right away
        assertThat(aih.getScreenOnTimeFile().exists()).isTrue();

        aih.writeAppIdleTimes(USER_ID, /* elapsedRealtime= */ 2000);
        // stats file should be written now
        assertThat(new File(new File(mStorageDir, "users/" + USER_ID),
                AppIdleHistory.APP_IDLE_FILENAME).exists()).isTrue();
    }

    @Test
    public void testScreenOnTime() {
        AppIdleHistory aih = new AppIdleHistory(mStorageDir, 1000);

        aih.updateDisplay(/* screenOn= */ false, /* elapsedRealtime= */ 2000);
        assertThat(aih.getScreenOnTime(2000)).isEqualTo(0);
        aih.updateDisplay(/* screenOn= */ true, /* elapsedRealtime= */ 3000);
        assertThat(aih.getScreenOnTime(4000)).isEqualTo(1000);
        assertThat(aih.getScreenOnTime(5000)).isEqualTo(2000);
        aih.updateDisplay(/* screenOn= */ false, /* elapsedRealtime= */ 6000);
        // Screen on time should not keep progressing with screen is off
        assertThat(aih.getScreenOnTime(7000)).isEqualTo(3000);
        assertThat(aih.getScreenOnTime(8000)).isEqualTo(3000);
        aih.writeAppIdleDurations();

        // Check if the screen on time is persisted across instantiations
        AppIdleHistory aih2 = new AppIdleHistory(mStorageDir, 0);
        assertThat(aih2.getScreenOnTime(11000)).isEqualTo(3000);
        aih2.updateDisplay(/* screenOn= */ true, /* elapsedRealtime= */ 4000);
        aih2.updateDisplay(/* screenOn= */ false, /* elapsedRealtime= */ 5000);
        assertThat(aih2.getScreenOnTime(13000)).isEqualTo(4000);
    }

    @Test
    public void testBuckets() {
        AppIdleHistory aih = new AppIdleHistory(mStorageDir, /* elapsedRealtime= */ 1000);

        aih.setAppStandbyBucket(PACKAGE_1, USER_ID, 1000, STANDBY_BUCKET_ACTIVE,
                REASON_MAIN_USAGE);
        // ACTIVE means not idle
        assertThat(aih.isIdle(PACKAGE_1, USER_ID, 2000)).isFalse();

        aih.setAppStandbyBucket(PACKAGE_2, USER_ID, 2000, STANDBY_BUCKET_ACTIVE,
                REASON_MAIN_USAGE);
        aih.setAppStandbyBucket(PACKAGE_3, USER_ID, 2500, STANDBY_BUCKET_RESTRICTED,
                REASON_MAIN_FORCED_BY_SYSTEM
                        | REASON_SUB_FORCED_SYSTEM_FLAG_BACKGROUND_RESOURCE_USAGE);
        aih.setAppStandbyBucket(PACKAGE_4, USER_ID, 2750, STANDBY_BUCKET_RESTRICTED,
                REASON_MAIN_FORCED_BY_USER);
        aih.setAppStandbyBucket(PACKAGE_1, USER_ID, 3000, STANDBY_BUCKET_RARE,
                REASON_MAIN_TIMEOUT);

        assertThat(aih.getAppStandbyBucket(PACKAGE_1, USER_ID, 3000))
                .isEqualTo(STANDBY_BUCKET_RARE);
        assertThat(aih.getAppStandbyBucket(PACKAGE_2, USER_ID, 3000))
                .isEqualTo(STANDBY_BUCKET_ACTIVE);
        assertThat(aih.getAppStandbyReason(PACKAGE_1, USER_ID, 3000))
                .isEqualTo(REASON_MAIN_TIMEOUT);
        assertThat(aih.getAppStandbyBucket(PACKAGE_3, USER_ID, 3000))
                .isEqualTo(STANDBY_BUCKET_RESTRICTED);
        assertThat(aih.getAppStandbyReason(PACKAGE_3, USER_ID, 3000))
                .isEqualTo(REASON_MAIN_FORCED_BY_SYSTEM
                        | REASON_SUB_FORCED_SYSTEM_FLAG_BACKGROUND_RESOURCE_USAGE);
        assertThat(aih.getAppStandbyReason(PACKAGE_4, USER_ID, 3000))
                .isEqualTo(REASON_MAIN_FORCED_BY_USER);

        // RARE and RESTRICTED are considered idle
        assertThat(aih.isIdle(PACKAGE_1, USER_ID, 3000)).isTrue();
        assertThat(aih.isIdle(PACKAGE_2, USER_ID, 3000)).isFalse();
        assertThat(aih.isIdle(PACKAGE_3, USER_ID, 3000)).isTrue();
        assertThat(aih.isIdle(PACKAGE_4, USER_ID, 3000)).isTrue();

        // Check persistence
        aih.writeAppIdleDurations();
        aih.writeAppIdleTimes(USER_ID, /* elapsedRealtime= */ 3000);
        aih = new AppIdleHistory(mStorageDir, /* elapsedRealtime= */ 4000);
        assertThat(aih.getAppStandbyBucket(PACKAGE_1, USER_ID, 5000))
                .isEqualTo(STANDBY_BUCKET_RARE);
        assertThat(aih.getAppStandbyBucket(PACKAGE_2, USER_ID, 5000))
                .isEqualTo(STANDBY_BUCKET_ACTIVE);
        assertThat(aih.getAppStandbyReason(PACKAGE_1, USER_ID, 5000))
                .isEqualTo(REASON_MAIN_TIMEOUT);
        assertThat(aih.getAppStandbyBucket(PACKAGE_3, USER_ID, 3000))
                .isEqualTo(STANDBY_BUCKET_RESTRICTED);
        assertThat(aih.getAppStandbyReason(PACKAGE_3, USER_ID, 3000))
                .isEqualTo(REASON_MAIN_FORCED_BY_SYSTEM
                        | REASON_SUB_FORCED_SYSTEM_FLAG_BACKGROUND_RESOURCE_USAGE);
        assertThat(aih.getAppStandbyReason(PACKAGE_4, USER_ID, 3000))
                .isEqualTo(REASON_MAIN_FORCED_BY_USER);

        assertThat(aih.shouldInformListeners(PACKAGE_1, USER_ID, 5000,
                STANDBY_BUCKET_RARE)).isFalse();
        assertThat(aih.shouldInformListeners(PACKAGE_1, USER_ID, 5000,
                STANDBY_BUCKET_FREQUENT)).isTrue();
    }

    @Test
    public void testJobRunTime() {
        AppIdleHistory aih = new AppIdleHistory(mStorageDir, /* elapsedRealtime= */ 1000);

        aih.setLastJobRunTime(PACKAGE_1, USER_ID, /* elapsedRealtime= */ 2000);
        assertThat(aih.getTimeSinceLastJobRun(PACKAGE_2, USER_ID, 0))
                .isEqualTo(Long.MAX_VALUE);
        assertThat(aih.getTimeSinceLastJobRun(PACKAGE_1, USER_ID, 6000))
                .isEqualTo(4000);

        aih.setLastJobRunTime(PACKAGE_2, USER_ID, /* elapsedRealtime= */ 6000);
        assertThat(aih.getTimeSinceLastJobRun(PACKAGE_2, USER_ID, 7000))
                .isEqualTo(1000);
        assertThat(aih.getTimeSinceLastJobRun(PACKAGE_1, USER_ID, 7000))
                .isEqualTo(5000);
    }

    @Test
    public void testReason() {
        AppIdleHistory aih = new AppIdleHistory(mStorageDir, /* elapsedRealtime= */ 1000);

        aih.reportUsage(PACKAGE_1, USER_ID, STANDBY_BUCKET_ACTIVE,
                REASON_SUB_USAGE_MOVE_TO_FOREGROUND, 2000, 0);
        assertThat(aih.getAppStandbyReason(PACKAGE_1, USER_ID, 3000))
                .isEqualTo(REASON_MAIN_USAGE | REASON_SUB_USAGE_MOVE_TO_FOREGROUND);
        aih.setAppStandbyBucket(PACKAGE_1, USER_ID, 4000, STANDBY_BUCKET_WORKING_SET,
                REASON_MAIN_TIMEOUT);
        aih.writeAppIdleTimes(USER_ID, /* elapsedRealtime= */ 4000);

        aih = new AppIdleHistory(mStorageDir, /* elapsedRealtime= */ 5000);
        assertThat(aih.getAppStandbyReason(PACKAGE_1, USER_ID, 5000))
                .isEqualTo(REASON_MAIN_TIMEOUT);
    }

    @Test
    public void testNullPackage() {
        AppIdleHistory aih = new AppIdleHistory(mStorageDir, /* elapsedRealtime= */ 1000);

        // Report usage of a package
        aih.reportUsage(PACKAGE_1, USER_ID, STANDBY_BUCKET_ACTIVE,
                REASON_SUB_USAGE_MOVE_TO_FOREGROUND, 2000, 0);
        // "Accidentally" report usage against a null named package
        aih.reportUsage(null, USER_ID, STANDBY_BUCKET_ACTIVE,
                REASON_SUB_USAGE_MOVE_TO_FOREGROUND, 2000, 0);
        // Persist data
        aih.writeAppIdleTimes(USER_ID, /* elapsedRealtime= */ 2000);
        // Recover data from disk
        aih = new AppIdleHistory(mStorageDir, /* elapsedRealtime= */ 5000);
        // Verify data is intact
        assertThat(aih.getAppStandbyReason(PACKAGE_1, USER_ID, 3000))
                .isEqualTo((REASON_MAIN_USAGE | REASON_SUB_USAGE_MOVE_TO_FOREGROUND));
    }

    @Test
    public void testBucketExpiryTimes() {
        AppIdleHistory aih = new AppIdleHistory(mStorageDir,  /* elapsedRealtime= */ 1000);

        aih.reportUsage(PACKAGE_1, USER_ID, STANDBY_BUCKET_WORKING_SET,
                REASON_SUB_USAGE_SLICE_PINNED,
                /* elapsedRealtime= */ 2000,  /* expiryRealtime= */ 6000);
        assertThat(aih.getBucketExpiryTimeMs(PACKAGE_1, USER_ID,
                STANDBY_BUCKET_WORKING_SET,  /* elapsedRealtime= */ 2000)).isEqualTo(5000);
        aih.reportUsage(PACKAGE_2, USER_ID, STANDBY_BUCKET_FREQUENT,
                REASON_SUB_USAGE_NOTIFICATION_SEEN,
                /* elapsedRealtime= */ 2000, /* expiryRealtime= */ 3000);
        assertThat(aih.getBucketExpiryTimeMs(PACKAGE_2, USER_ID,
                STANDBY_BUCKET_FREQUENT,  /* elapsedRealtime= */ 2000)).isEqualTo(2000);
        aih.writeAppIdleTimes(USER_ID,  /* elapsedRealtime= */ 4000);

        // Persist data
        aih = new AppIdleHistory(mStorageDir, /* elapsedRealtime= */ 5000);
        final Map<Integer, Long> expectedExpiryTimes1 = Map.of(
                STANDBY_BUCKET_ACTIVE, 0L,
                STANDBY_BUCKET_WORKING_SET, 5000L,
                STANDBY_BUCKET_FREQUENT, 0L,
                STANDBY_BUCKET_RARE, 0L,
                STANDBY_BUCKET_RESTRICTED, 0L
        );
        // For PACKAGE_1, only WORKING_SET bucket should have an expiry time.
        verifyBucketExpiryTimes(aih, PACKAGE_1, USER_ID, /* elapsedRealtime= */ 5000,
                expectedExpiryTimes1);
        final Map<Integer, Long> expectedExpiryTimes2 = Map.of(
                STANDBY_BUCKET_ACTIVE, 0L,
                STANDBY_BUCKET_WORKING_SET, 0L,
                STANDBY_BUCKET_FREQUENT, 0L,
                STANDBY_BUCKET_RARE, 0L,
                STANDBY_BUCKET_RESTRICTED, 0L
        );
        // For PACKAGE_2, there shouldn't be any expiry time since the one set earlier would have
        // elapsed by the time the data was persisted to disk
        verifyBucketExpiryTimes(aih, PACKAGE_2, USER_ID, /* elapsedRealtime= */ 5000,
                expectedExpiryTimes2);
    }

    private void verifyBucketExpiryTimes(AppIdleHistory aih, String packageName, int userId,
            long elapsedRealtimeMs, Map<Integer, Long> expectedExpiryTimesMs) {
        for (Map.Entry<Integer, Long> entry : expectedExpiryTimesMs.entrySet()) {
            final int bucket = entry.getKey();
            final long expectedExpiryTimeMs = entry.getValue();
            final long actualExpiryTimeMs = aih.getBucketExpiryTimeMs(packageName, userId, bucket,
                    elapsedRealtimeMs);
            assertWithMessage("Unexpected expiry time for pkg=" + packageName + ", userId=" + userId
                            + ", bucket=" + standbyBucketToString(bucket))
                    .that(actualExpiryTimeMs)
                    .isEqualTo(expectedExpiryTimeMs);
        }
    }
}
