/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.server.power.stats;

import static android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT;
import static android.os.BatteryConsumer.POWER_COMPONENT_WAKELOCK;
import static android.os.BatteryStats.WAKE_TYPE_PARTIAL;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.ravenwood.RavenwoodRule;

import com.android.internal.os.PowerStats;
import com.android.server.power.stats.format.WakelockPowerStatsLayout;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class WakelockPowerStatsCollectorTest {
    @Rule public final RavenwoodRule mRule = new RavenwoodRule.Builder().build();

    @Rule public final BatteryUsageStatsRule mStatsRule = new BatteryUsageStatsRule();

    private static final int APP_UID1 = Process.FIRST_APPLICATION_UID + 42;
    private static final int PCC_UID = Process.FIRST_PCC_UID + 42;
    private static final int APP_UID2 = Process.FIRST_APPLICATION_UID + 101;

    private MockBatteryStatsImpl mBatteryStats;

    private final MockClock mClock = mStatsRule.getMockClock();
    private PowerStats mPowerStats;
    private final WakelockPowerStatsLayout mStatsLayout = new WakelockPowerStatsLayout();
    private Context mContext;
    private PackageManager mPackageManager;

    @Before
    public void setup() throws Throwable {
        mBatteryStats = mStatsRule.getBatteryStats();
        mBatteryStats.setPowerStatsCollectorEnabled(POWER_COMPONENT_WAKELOCK, true);
        mBatteryStats.getPowerStatsCollector(POWER_COMPONENT_WAKELOCK)
                .addConsumer((stats, elapsedRealtime, uptime) -> mPowerStats = stats);

        mContext = mock(Context.class);
        mPackageManager = mock(PackageManager.class);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.getAppUidForPrivateComputeCoreUid(PCC_UID)).thenReturn(APP_UID1);
        mBatteryStats.onSystemReady(mContext);
        // onSystemReady schedules the initial power stats collection. Wait for it to finish
        mStatsRule.waitForBackgroundThread();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void collectStats_pccUidWakelockStats_attributedToParentUid() {
        PowerStatsCollector powerStatsCollector = mBatteryStats.getPowerStatsCollector(
                POWER_COMPONENT_WAKELOCK);

        mBatteryStats.forceRecordAllHistory();
        mBatteryStats.setNoAutoReset(true);

        mStatsRule.advanceSuspendedTime(1000);

        synchronized (mBatteryStats) {
            mBatteryStats.setOnBatteryLocked(mClock.realtime, mClock.uptime, true, 0, 90, 1000);
        }

        mStatsRule.advanceSuspendedTime(3000);
        synchronized (mBatteryStats) {
            mBatteryStats.noteStartWakeLocked(PCC_UID, 0, null, "app", null, WAKE_TYPE_PARTIAL,
                    false);
        }

        mStatsRule.advanceTime(1000);
        powerStatsCollector.collectAndDeliverStats(mClock.realtime, mClock.uptime);

        assertThat(mStatsLayout.getUsageDuration(mPowerStats.stats)).isEqualTo(1000);
        assertThat(mStatsLayout.getUidUsageDuration(mPowerStats.uidStats.get(APP_UID1)))
                .isEqualTo(1000);
        assertThat(mPowerStats.uidStats.get(PCC_UID)).isNull();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void collectStats_pccAndAppUidWakeLocksInterleaved() {
        PowerStatsCollector powerStatsCollector = mBatteryStats.getPowerStatsCollector(
                POWER_COMPONENT_WAKELOCK);

        mBatteryStats.forceRecordAllHistory();
        mBatteryStats.setNoAutoReset(true);

        mStatsRule.advanceSuspendedTime(1000);

        synchronized (mBatteryStats) {
            mBatteryStats.setOnBatteryLocked(mClock.realtime, mClock.uptime, true, 0, 90, 1000);
        }

        mStatsRule.advanceSuspendedTime(3000);
        synchronized (mBatteryStats) {
            mBatteryStats.noteStartWakeLocked(APP_UID1, 0, null, "app", null, WAKE_TYPE_PARTIAL,
                    false);
        }

        mStatsRule.advanceTime(1000);
        powerStatsCollector.collectAndDeliverStats(mClock.realtime, mClock.uptime);

        assertThat(mStatsLayout.getUsageDuration(mPowerStats.stats)).isEqualTo(1000);
        assertThat(mStatsLayout.getUidUsageDuration(mPowerStats.uidStats.get(APP_UID1)))
                .isEqualTo(1000);
        assertThat(mPowerStats.uidStats.get(PCC_UID)).isNull();

        mStatsRule.advanceTime(3000);
        synchronized (mBatteryStats) {
            mBatteryStats.noteStartWakeLocked(PCC_UID, 0, null, "pcc", null, WAKE_TYPE_PARTIAL,
                    false);
        }

        mStatsRule.advanceTime(2000);
        synchronized (mBatteryStats) {
            mBatteryStats.noteStopWakeLocked(APP_UID1, 0, null, "app", null, WAKE_TYPE_PARTIAL);
        }

        mStatsRule.advanceTime(5000);
        synchronized (mBatteryStats) {
            mBatteryStats.noteStopWakeLocked(PCC_UID, 0, null, "pcc", null, WAKE_TYPE_PARTIAL);
        }
        mStatsRule.advanceSuspendedTime(7000);

        // Plug in
        synchronized (mBatteryStats) {
            mBatteryStats.setOnBatteryLocked(mClock.realtime, mClock.uptime, false, 0, 90, 1000);
        }

        mStatsRule.advanceSuspendedTime(1000);
        powerStatsCollector.collectAndDeliverStats(mClock.realtime, mClock.uptime);

        // Based on the uptime, the device was awake for (3000+2000+5000) = 10000 ms
        assertThat(mStatsLayout.getUsageDuration(mPowerStats.stats)).isEqualTo(10000);
        // APP_UID1 stats = 3000 + (2000/2) +
        // pccUid stats = (2000/2) + 5000
        assertThat(mStatsLayout.getUidUsageDuration(mPowerStats.uidStats.get(APP_UID1)))
                .isEqualTo(10000);
        assertThat(mPowerStats.uidStats.get(PCC_UID)).isNull();
    }

    @Test
    public void collectStats() {
        PowerStatsCollector powerStatsCollector = mBatteryStats.getPowerStatsCollector(
                POWER_COMPONENT_WAKELOCK);

        mBatteryStats.forceRecordAllHistory();
        mBatteryStats.setNoAutoReset(true);

        mStatsRule.advanceSuspendedTime(1000);

        synchronized (mBatteryStats) {
            mBatteryStats.setOnBatteryLocked(mClock.realtime, mClock.uptime, true, 0, 90, 1000);
        }

        mStatsRule.advanceSuspendedTime(3000);
        synchronized (mBatteryStats) {
            mBatteryStats.noteStartWakeLocked(APP_UID1, 0, null, "one", null, WAKE_TYPE_PARTIAL,
                    false);
        }

        mStatsRule.advanceTime(1000);
        powerStatsCollector.collectAndDeliverStats(mClock.realtime, mClock.uptime);

        assertThat(mStatsLayout.getUsageDuration(mPowerStats.stats)).isEqualTo(1000);
        assertThat(mStatsLayout.getUidUsageDuration(mPowerStats.uidStats.get(APP_UID1)))
                .isEqualTo(1000);

        mStatsRule.advanceTime(3000);
        synchronized (mBatteryStats) {
            mBatteryStats.noteStartWakeLocked(APP_UID2, 0, null, "two", null, WAKE_TYPE_PARTIAL,
                    false);
        }

        mStatsRule.advanceTime(2000);
        synchronized (mBatteryStats) {
            mBatteryStats.noteStopWakeLocked(APP_UID1, 0, null, "one", null, WAKE_TYPE_PARTIAL);
        }

        mStatsRule.advanceTime(5000);
        synchronized (mBatteryStats) {
            mBatteryStats.noteStopWakeLocked(APP_UID2, 0, null, "two", null, WAKE_TYPE_PARTIAL);
        }
        mStatsRule.advanceSuspendedTime(7000);

        // Plug in
        synchronized (mBatteryStats) {
            mBatteryStats.setOnBatteryLocked(mClock.realtime, mClock.uptime, false, 0, 90, 1000);
        }

        mStatsRule.advanceSuspendedTime(1000);
        powerStatsCollector.collectAndDeliverStats(mClock.realtime, mClock.uptime);

        // Based on the uptime, the device was awake for (3000+2000+5000) = 10000 ms
        assertThat(mStatsLayout.getUsageDuration(mPowerStats.stats)).isEqualTo(10000);
        assertThat(mStatsLayout.getUidUsageDuration(mPowerStats.uidStats.get(APP_UID1)))
                .isEqualTo(4000);  // 3000 + (2000/2) -- the 2000 ms overlap is split two-way
        assertThat(mStatsLayout.getUidUsageDuration(mPowerStats.uidStats.get(APP_UID2)))
                .isEqualTo(6000);  // (2000/2) + 5000
    }
}
