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

package com.android.server.appop;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.AppOpsManager;
import android.companion.virtual.VirtualDeviceManager;
import android.content.Context;
import android.os.Process;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Set;


@RunWith(AndroidJUnit4.class)
public class AppOpHistoryHelperTest {
    private static final String APP_OPS_DB_NAME = "test_app_ops.db";
    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    // history helper setup with 1-minute time window interval
    private AppOpHistoryHelper mShortIntervalHelper;
    // history helper setup with 15-minute time window interval
    private AppOpHistoryHelper mLongIntervalHelper;

    @Before
    public void setUp() {
        mShortIntervalHelper = new AppOpHistoryHelper(mContext,
                mContext.getDatabasePath(APP_OPS_DB_NAME),
                HistoricalRegistry.AggregationTimeWindow.SHORT, 1);
        mShortIntervalHelper.systemReady(Duration.ofMinutes(1).toMillis(),
                Duration.ofDays(7).toMillis());
        mLongIntervalHelper = new AppOpHistoryHelper(mContext,
                mContext.getDatabasePath(APP_OPS_DB_NAME),
                HistoricalRegistry.AggregationTimeWindow.LONG, 1);
        mLongIntervalHelper.systemReady(Duration.ofMinutes(15).toMillis(),
                Duration.ofDays(7).toMillis());
    }

    @After
    public void cleanUp() {
        mShortIntervalHelper.clearHistory();
        mLongIntervalHelper.clearHistory();
    }

    @Test
    public void discretizedAccessTimestamp() {
        long currentTime = System.currentTimeMillis();
        // round down the time to start of the 1-minute time window
        long expectedTimestamp = currentTime / Duration.ofMinutes(1).toMillis()
                * Duration.ofMinutes(1).toMillis();
        assertThat(mShortIntervalHelper.discretizeTimestamp(currentTime)).isEqualTo(
                expectedTimestamp);
        // round down the time to start of the 15-minute time window
        expectedTimestamp = currentTime / Duration.ofMinutes(15).toMillis() * Duration.ofMinutes(
                15).toMillis();
        assertThat(mLongIntervalHelper.discretizeTimestamp(currentTime)).isEqualTo(
                expectedTimestamp);
    }

    @Test
    public void discretizedAccessDuration() {
        AppOpHistoryHelper appOpHistoryHelper = mShortIntervalHelper;
        assertThat(appOpHistoryHelper.discretizeDuration(Duration.ofSeconds(20).toMillis()))
                .isEqualTo(Duration.ofMinutes(1).toMillis());
        assertThat(appOpHistoryHelper.discretizeDuration(Duration.ofSeconds(1).toMillis()))
                .isEqualTo(Duration.ofMinutes(1).toMillis());
        assertThat(appOpHistoryHelper.discretizeDuration(Duration.ofSeconds(59).toMillis()))
                .isEqualTo(Duration.ofMinutes(1).toMillis());
        assertThat(appOpHistoryHelper.discretizeDuration(Duration.ofSeconds(60).toMillis()))
                .isEqualTo(Duration.ofMinutes(1).toMillis());
        assertThat(appOpHistoryHelper.discretizeDuration(Duration.ofSeconds(61).toMillis()))
                .isEqualTo(Duration.ofMinutes(2).toMillis());
    }

    @Test
    public void incrementOpAccessRejectCount() {
        AppOpHistoryHelper appOpHistoryHelper = mShortIntervalHelper;
        long accessTime = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            appOpHistoryHelper.incrementOpAccessedCount(AppOpsManager.OP_FINE_LOCATION,
                    Process.myUid(),
                    mContext.getPackageName(),
                    VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT, null,
                    AppOpsManager.UID_STATE_FOREGROUND, AppOpsManager.OP_FLAG_SELF, accessTime,
                    AppOpsManager.ATTRIBUTION_FLAGS_NONE, AppOpsManager.ATTRIBUTION_CHAIN_ID_NONE,
                    1,
                    false);
        }
        for (int i = 0; i < 5; i++) {
            appOpHistoryHelper.incrementOpRejectedCount(AppOpsManager.OP_FINE_LOCATION,
                    Process.myUid(),
                    mContext.getPackageName(),
                    VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT, null,
                    AppOpsManager.UID_STATE_FOREGROUND, AppOpsManager.OP_FLAG_SELF, accessTime,
                    AppOpsManager.ATTRIBUTION_FLAGS_NONE, AppOpsManager.ATTRIBUTION_CHAIN_ID_NONE,
                    1);
        }
        List<AggregatedAppOpAccessEvent> aggregatedAccessEvents =
                appOpHistoryHelper.getAppOpHistory();
        assertThat(aggregatedAccessEvents).isNotEmpty();
        assertThat(aggregatedAccessEvents.size()).isEqualTo(1);
        AggregatedAppOpAccessEvent appOpAccessEvent = aggregatedAccessEvents.getFirst();
        assertThat(appOpAccessEvent.totalAccessCount()).isEqualTo(5);
        assertThat(appOpAccessEvent.totalRejectCount()).isEqualTo(5);
    }

    @Test
    public void aggregationKeyConsiderAllParameters() {
        AppOpHistoryHelper appOpHistoryHelper = mShortIntervalHelper;
        long accessTime = System.currentTimeMillis();
        appOpHistoryHelper.incrementOpAccessedCount(AppOpsManager.OP_FINE_LOCATION,
                Process.myUid(),
                mContext.getPackageName(),
                VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT, null,
                AppOpsManager.UID_STATE_FOREGROUND, AppOpsManager.OP_FLAG_SELF, accessTime,
                AppOpsManager.ATTRIBUTION_FLAGS_NONE, AppOpsManager.ATTRIBUTION_CHAIN_ID_NONE,
                1,
                false);
        // different tag
        appOpHistoryHelper.incrementOpAccessedCount(AppOpsManager.OP_FINE_LOCATION,
                Process.myUid(),
                mContext.getPackageName(),
                VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT, "tag1",
                AppOpsManager.UID_STATE_FOREGROUND, AppOpsManager.OP_FLAG_SELF, accessTime,
                AppOpsManager.ATTRIBUTION_FLAGS_NONE, AppOpsManager.ATTRIBUTION_CHAIN_ID_NONE,
                1,
                false);
        // different uid state
        appOpHistoryHelper.incrementOpAccessedCount(AppOpsManager.OP_FINE_LOCATION,
                Process.myUid(),
                mContext.getPackageName(),
                VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT, null,
                AppOpsManager.UID_STATE_CACHED, AppOpsManager.OP_FLAG_SELF, accessTime,
                AppOpsManager.ATTRIBUTION_FLAGS_NONE, AppOpsManager.ATTRIBUTION_CHAIN_ID_NONE,
                1,
                false);
        // different op flag
        appOpHistoryHelper.incrementOpAccessedCount(AppOpsManager.OP_FINE_LOCATION,
                Process.myUid(),
                mContext.getPackageName(),
                VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT, null,
                AppOpsManager.UID_STATE_FOREGROUND, AppOpsManager.OP_FLAG_TRUSTED_PROXY, accessTime,
                AppOpsManager.ATTRIBUTION_FLAGS_NONE, AppOpsManager.ATTRIBUTION_CHAIN_ID_NONE,
                1,
                false);
        // different attribution flag
        appOpHistoryHelper.incrementOpAccessedCount(AppOpsManager.OP_FINE_LOCATION,
                Process.myUid(),
                mContext.getPackageName(),
                VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT, null,
                AppOpsManager.UID_STATE_FOREGROUND, AppOpsManager.OP_FLAG_SELF, accessTime,
                AppOpsManager.ATTRIBUTION_FLAG_ACCESSOR, AppOpsManager.ATTRIBUTION_CHAIN_ID_NONE,
                1,
                false);
        // different attribution flag
        appOpHistoryHelper.incrementOpAccessedCount(AppOpsManager.OP_FINE_LOCATION,
                Process.myUid(),
                mContext.getPackageName(),
                VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT, null,
                AppOpsManager.UID_STATE_FOREGROUND, AppOpsManager.OP_FLAG_SELF, accessTime,
                AppOpsManager.ATTRIBUTION_FLAGS_NONE, 123456,
                1,
                false);
        List<AggregatedAppOpAccessEvent> aggregatedAccessEvents =
                appOpHistoryHelper.getAppOpHistory();
        assertThat(aggregatedAccessEvents).isNotEmpty();
        assertThat(aggregatedAccessEvents.size()).isEqualTo(6);
    }


    @Test
    public void recordOpAccessDurationWithSameDiscretizedDuration() {
        AppOpHistoryHelper appOpHistoryHelper = mShortIntervalHelper;
        long accessTime = appOpHistoryHelper.discretizeTimestamp(System.currentTimeMillis());
        // start op
        appOpHistoryHelper.incrementOpAccessedCount(AppOpsManager.OP_CAMERA,
                Process.myUid(),
                mContext.getPackageName(),
                VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT, null,
                AppOpsManager.UID_STATE_FOREGROUND, AppOpsManager.OP_FLAG_SELF, accessTime,
                AppOpsManager.ATTRIBUTION_FLAGS_NONE, AppOpsManager.ATTRIBUTION_CHAIN_ID_NONE,
                1,
                true);
        // Pause op
        appOpHistoryHelper.recordOpAccessDuration(AppOpsManager.OP_CAMERA,
                Process.myUid(),
                mContext.getPackageName(),
                VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT, null,
                AppOpsManager.UID_STATE_FOREGROUND, AppOpsManager.OP_FLAG_SELF, accessTime,
                AppOpsManager.ATTRIBUTION_FLAGS_NONE, AppOpsManager.ATTRIBUTION_CHAIN_ID_NONE,
                Duration.ofSeconds(15).toMillis());
        // Resume op
        long resumeAccessTime = accessTime + Duration.ofSeconds(15).toMillis();
        appOpHistoryHelper.incrementOpAccessedCount(AppOpsManager.OP_CAMERA,
                Process.myUid(),
                mContext.getPackageName(),
                VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT, null,
                AppOpsManager.UID_STATE_FOREGROUND, AppOpsManager.OP_FLAG_SELF, resumeAccessTime,
                AppOpsManager.ATTRIBUTION_FLAGS_NONE, AppOpsManager.ATTRIBUTION_CHAIN_ID_NONE,
                1,
                true);
        // Finish op
        appOpHistoryHelper.recordOpAccessDuration(AppOpsManager.OP_CAMERA,
                Process.myUid(),
                mContext.getPackageName(),
                VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT, null,
                AppOpsManager.UID_STATE_FOREGROUND, AppOpsManager.OP_FLAG_SELF, accessTime,
                AppOpsManager.ATTRIBUTION_FLAGS_NONE, AppOpsManager.ATTRIBUTION_CHAIN_ID_NONE,
                Duration.ofSeconds(30).toMillis());

        List<AggregatedAppOpAccessEvent> aggregatedAccessEvents =
                appOpHistoryHelper.getAppOpHistory();
        assertThat(aggregatedAccessEvents).isNotEmpty();
        assertWithMessage(aggregatedAccessEvents.toString())
                .that(aggregatedAccessEvents.size()).isEqualTo(2);
        List<Long> expectedDurations = new ArrayList<>();
        // start/resume entry would have 0 duration
        expectedDurations.add(Duration.ofSeconds(0).toMillis());
        expectedDurations.add(Duration.ofSeconds(15).toMillis());

        int totalAccessCounts = 0;
        long totalDurationSum = 0;
        for (AggregatedAppOpAccessEvent accessEvent : aggregatedAccessEvents) {
            assertThat(accessEvent.durationMillis()).isIn(expectedDurations);
            assertThat(accessEvent.accessTimeMillis()).isEqualTo(accessTime);
            totalAccessCounts += accessEvent.totalAccessCount();
            totalDurationSum += accessEvent.totalDurationMillis();
        }
        assertThat(totalAccessCounts).isEqualTo(2);
        assertThat(totalDurationSum).isEqualTo(45000);
    }

    @Test
    public void filterAppOpsWithUidPackageNameFilter() {
        long accessTime = System.currentTimeMillis();
        mShortIntervalHelper.incrementOpAccessedCount(AppOpsManager.OP_FINE_LOCATION,
                Process.myUid(),
                mContext.getPackageName(),
                VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT, null,
                AppOpsManager.UID_STATE_FOREGROUND, AppOpsManager.OP_FLAG_SELF, accessTime,
                AppOpsManager.ATTRIBUTION_FLAGS_NONE, AppOpsManager.ATTRIBUTION_CHAIN_ID_NONE,
                1,
                false);
        mShortIntervalHelper.incrementOpAccessedCount(AppOpsManager.OP_COARSE_LOCATION,
                Process.myUid(),
                mContext.getPackageName(),
                VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT, null,
                AppOpsManager.UID_STATE_FOREGROUND, AppOpsManager.OP_FLAG_SELF, accessTime,
                AppOpsManager.ATTRIBUTION_FLAGS_NONE, AppOpsManager.ATTRIBUTION_CHAIN_ID_NONE,
                1,
                false);
        int filterFlag = AppOpsManager.FILTER_BY_UID | AppOpsManager.FILTER_BY_PACKAGE_NAME;
        long beginTimeMillis = accessTime;
        long endTimeMillis = System.currentTimeMillis();

        List<AggregatedAppOpAccessEvent> events = mShortIntervalHelper.getAppOpHistory(
                beginTimeMillis, endTimeMillis, filterFlag, Process.myUid(),
                mContext.getPackageName(), null, null, 0);
        assertThat(events).isNotEmpty();
        assertThat(events.size()).isEqualTo(2);
        for (AggregatedAppOpAccessEvent event : events) {
            assertThat(event.opCode()).isAnyOf(AppOpsManager.OP_COARSE_LOCATION,
                    AppOpsManager.OP_FINE_LOCATION);
            assertThat(event.totalAccessCount()).isEqualTo(1);
            assertThat(event.durationMillis()).isEqualTo(-1);
            assertThat(event.totalDurationMillis()).isEqualTo(0);
            assertThat(event.totalRejectCount()).isEqualTo(0);
        }
    }

    @Test
    public void filterAppOpsWithAttributionTagFilter() {
        long accessTime = System.currentTimeMillis();
        mShortIntervalHelper.incrementOpAccessedCount(AppOpsManager.OP_FINE_LOCATION,
                Process.myUid(),
                mContext.getPackageName(),
                VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT, "tag1",
                AppOpsManager.UID_STATE_FOREGROUND, AppOpsManager.OP_FLAG_SELF, accessTime,
                AppOpsManager.ATTRIBUTION_FLAGS_NONE, AppOpsManager.ATTRIBUTION_CHAIN_ID_NONE,
                1,
                false);
        mShortIntervalHelper.incrementOpAccessedCount(AppOpsManager.OP_COARSE_LOCATION,
                Process.myUid(),
                mContext.getPackageName(),
                VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT, "tag2",
                AppOpsManager.UID_STATE_FOREGROUND, AppOpsManager.OP_FLAG_SELF, accessTime,
                AppOpsManager.ATTRIBUTION_FLAGS_NONE, AppOpsManager.ATTRIBUTION_CHAIN_ID_NONE,
                1,
                false);
        int filterFlag = AppOpsManager.FILTER_BY_UID | AppOpsManager.FILTER_BY_ATTRIBUTION_TAG;
        long beginTimeMillis = accessTime;
        long endTimeMillis = System.currentTimeMillis();
        // assert total records in database
        assertThat(mShortIntervalHelper.getAppOpHistory().size()).isEqualTo(2);
        List<AggregatedAppOpAccessEvent> events = mShortIntervalHelper.getAppOpHistory(
                beginTimeMillis, endTimeMillis, filterFlag, Process.myUid(),
                mContext.getPackageName(), null, "tag1", 0);
        assertThat(events).isNotEmpty();
        assertThat(events.size()).isEqualTo(1);
        assertThat(events.getFirst().opCode()).isEqualTo(AppOpsManager.OP_FINE_LOCATION);
    }

    @Test
    public void filterAppOpsWithOpCodeFilter() {
        long accessTime = System.currentTimeMillis();
        mShortIntervalHelper.incrementOpAccessedCount(AppOpsManager.OP_FINE_LOCATION,
                Process.myUid(),
                mContext.getPackageName(),
                VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT, null,
                AppOpsManager.UID_STATE_FOREGROUND, AppOpsManager.OP_FLAG_SELF, accessTime,
                AppOpsManager.ATTRIBUTION_FLAGS_NONE, AppOpsManager.ATTRIBUTION_CHAIN_ID_NONE,
                1,
                false);
        mShortIntervalHelper.incrementOpAccessedCount(AppOpsManager.OP_COARSE_LOCATION,
                Process.myUid(),
                mContext.getPackageName(),
                VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT, null,
                AppOpsManager.UID_STATE_FOREGROUND, AppOpsManager.OP_FLAG_SELF, accessTime,
                AppOpsManager.ATTRIBUTION_FLAGS_NONE, AppOpsManager.ATTRIBUTION_CHAIN_ID_NONE,
                1,
                false);
        int filterFlag = AppOpsManager.FILTER_BY_UID | AppOpsManager.FILTER_BY_OP_NAMES;
        long beginTimeMillis = accessTime;
        long endTimeMillis = System.currentTimeMillis();
        // assert total records in database
        assertThat(mShortIntervalHelper.getAppOpHistory().size()).isEqualTo(2);
        List<AggregatedAppOpAccessEvent> events = mShortIntervalHelper.getAppOpHistory(
                beginTimeMillis, endTimeMillis, filterFlag, Process.myUid(),
                mContext.getPackageName(), new String[]{AppOpsManager.OPSTR_FINE_LOCATION},
                null, AppOpsManager.OP_FLAG_SELF);
        assertThat(events).isNotEmpty();
        assertThat(events.size()).isEqualTo(1);
        assertThat(events.getFirst().opCode()).isEqualTo(AppOpsManager.OP_FINE_LOCATION);

        // Use incorrect op flag
        events = mShortIntervalHelper.getAppOpHistory(
                beginTimeMillis, endTimeMillis, filterFlag, Process.myUid(),
                mContext.getPackageName(), new String[]{AppOpsManager.OPSTR_FINE_LOCATION},
                null, AppOpsManager.OP_FLAG_TRUSTED_PROXIED);
        assertThat(events).isEmpty();
    }

    @Test
    public void filterAppOpsWithBeginTime() {
        long accessTime = getAccessTimeMillis(8, 10, 15);
        // start op
        mLongIntervalHelper.incrementOpAccessedCount(AppOpsManager.OP_CAMERA,
                Process.myUid(),
                mContext.getPackageName(),
                VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT, null,
                AppOpsManager.UID_STATE_FOREGROUND, AppOpsManager.OP_FLAG_SELF, accessTime,
                AppOpsManager.ATTRIBUTION_FLAGS_NONE, AppOpsManager.ATTRIBUTION_CHAIN_ID_NONE,
                1,
                true);
        // finish op
        mLongIntervalHelper.recordOpAccessDuration(AppOpsManager.OP_CAMERA,
                Process.myUid(),
                mContext.getPackageName(),
                VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT, null,
                AppOpsManager.UID_STATE_FOREGROUND, AppOpsManager.OP_FLAG_SELF, accessTime,
                AppOpsManager.ATTRIBUTION_FLAGS_NONE, AppOpsManager.ATTRIBUTION_CHAIN_ID_NONE,
                Duration.ofMinutes(10).toMillis());

        long beginTimeMillis = getAccessTimeMillis(8, 16, 15);
        long endTimeMillis = beginTimeMillis + Duration.ofMinutes(60).toMillis();
        List<AggregatedAppOpAccessEvent> events = mLongIntervalHelper.getAppOpHistory(
                beginTimeMillis, endTimeMillis, 0, Process.myUid(),
                mContext.getPackageName(), null, null, 0);
        assertThat(events).isNotEmpty();
        assertThat(events.size()).isEqualTo(1);
        AggregatedAppOpAccessEvent appOpAccessEvent = events.getFirst();
        assertThat(appOpAccessEvent.durationMillis()).isEqualTo(Duration.ofMinutes(10).toMillis());
        assertThat(appOpAccessEvent.accessTimeMillis()).isEqualTo(accessTime);
        assertThat(appOpAccessEvent.totalDurationMillis()).isEqualTo(
                Duration.ofMinutes(10).toMillis());
    }

    @Test
    public void recentlyUsedDistinctPackageNames() {
        long accessTime = System.currentTimeMillis();
        long beforeAccessTime = accessTime - Duration.ofMinutes(5).toMillis();
        long afterAccessTime = accessTime + Duration.ofMinutes(5).toMillis();

        // Record 1: should be returned
        mShortIntervalHelper.incrementOpAccessedCount(AppOpsManager.OP_FINE_LOCATION,
                Process.myUid(), mContext.getPackageName(),
                VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT, null,
                AppOpsManager.UID_STATE_FOREGROUND, AppOpsManager.OP_FLAG_SELF, accessTime,
                AppOpsManager.ATTRIBUTION_FLAGS_NONE, AppOpsManager.ATTRIBUTION_CHAIN_ID_NONE, 1,
                false);

        // Record 2: should be returned
        mShortIntervalHelper.incrementOpAccessedCount(AppOpsManager.OP_COARSE_LOCATION,
                Process.myUid(), "com.example.app1",
                VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT, null,
                AppOpsManager.UID_STATE_FOREGROUND, AppOpsManager.OP_FLAG_SELF, accessTime,
                AppOpsManager.ATTRIBUTION_FLAGS_NONE, AppOpsManager.ATTRIBUTION_CHAIN_ID_NONE, 1,
                false);

        // Record 3: should be filtered out by op name
        mShortIntervalHelper.incrementOpAccessedCount(AppOpsManager.OP_CAMERA,
                Process.myUid(), "com.example.app2",
                VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT, null,
                AppOpsManager.UID_STATE_FOREGROUND, AppOpsManager.OP_FLAG_SELF, accessTime,
                AppOpsManager.ATTRIBUTION_FLAGS_NONE, AppOpsManager.ATTRIBUTION_CHAIN_ID_NONE, 1,
                false);

        // Record 4: duplicate package, should not appear twice in result
        mShortIntervalHelper.incrementOpAccessedCount(AppOpsManager.OP_FINE_LOCATION,
                Process.myUid(), mContext.getPackageName(),
                VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT, null,
                AppOpsManager.UID_STATE_FOREGROUND, AppOpsManager.OP_FLAG_SELF, accessTime,
                AppOpsManager.ATTRIBUTION_FLAGS_NONE, AppOpsManager.ATTRIBUTION_CHAIN_ID_NONE, 1,
                false);

        // Record 5: should be filtered out by op flag
        mShortIntervalHelper.incrementOpAccessedCount(AppOpsManager.OP_FINE_LOCATION,
                Process.myUid(), "com.example.app3",
                VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT, null,
                AppOpsManager.UID_STATE_FOREGROUND, AppOpsManager.OP_FLAG_TRUSTED_PROXIED,
                accessTime, AppOpsManager.ATTRIBUTION_FLAGS_NONE,
                AppOpsManager.ATTRIBUTION_CHAIN_ID_NONE, 1, false);

        // Record 6: should be filtered out by time
        long muchBeforeAccessTime = beforeAccessTime - Duration.ofMinutes(1).toMillis();
        mShortIntervalHelper.incrementOpAccessedCount(AppOpsManager.OP_FINE_LOCATION,
                Process.myUid(), "com.example.app4",
                VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT, null,
                AppOpsManager.UID_STATE_FOREGROUND, AppOpsManager.OP_FLAG_SELF,
                muchBeforeAccessTime, AppOpsManager.ATTRIBUTION_FLAGS_NONE,
                AppOpsManager.ATTRIBUTION_CHAIN_ID_NONE, 1, false);
        mShortIntervalHelper.persistPendingHistory();

        Set<String> recentPackages = mShortIntervalHelper.getRecentlyUsedPackageNames(
                new String[]{AppOpsManager.OPSTR_FINE_LOCATION,
                        AppOpsManager.OPSTR_COARSE_LOCATION},
                AppOpsManager.FILTER_BY_OP_NAMES, beforeAccessTime, afterAccessTime,
                AppOpsManager.OP_FLAG_SELF);
        assertThat(recentPackages.size()).isEqualTo(2);
        assertThat(recentPackages).containsAtLeast(mContext.getPackageName(),
                "com.example.app1");
    }

    private long getAccessTimeMillis(int hours, int minutes, int seconds) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hours);
        calendar.set(Calendar.MINUTE, minutes);
        calendar.set(Calendar.SECOND, seconds);
        return calendar.getTimeInMillis();
    }
}
