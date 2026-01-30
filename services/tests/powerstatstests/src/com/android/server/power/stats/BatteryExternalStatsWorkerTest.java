/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.connectivity.WifiActivityEnergyInfo;
import android.power.PowerStatsInternal;
import android.util.SparseArray;

import androidx.test.InstrumentationRegistry;

import com.android.internal.os.Clock;
import com.android.internal.os.CpuScalingPolicies;
import com.android.internal.os.MonotonicClock;
import com.android.internal.os.PowerProfile;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link BatteryExternalStatsWorker}.
 *
 * Build/Install/Run:
 * atest FrameworksServicesTests:BatteryExternalStatsWorkerTest
 */
@SuppressWarnings("GuardedBy")
@android.platform.test.annotations.DisabledOnRavenwood
public class BatteryExternalStatsWorkerTest {
    private BatteryExternalStatsWorker mBatteryExternalStatsWorker;
    private MockPowerStatsInternal mPowerStatsInternal;
    private Handler mHandler;
    private MockClock mClock = new MockClock();

    @Before
    public void setUp() {
        final Context context = InstrumentationRegistry.getContext();

        mHandler = new Handler(Looper.getMainLooper());
        BatteryStatsImpl batteryStats = new BatteryStatsImpl(
                new BatteryStatsImpl.BatteryStatsConfig.Builder().build(), Clock.SYSTEM_CLOCK,
                new MonotonicClock(0, Clock.SYSTEM_CLOCK), null,
                mHandler, null, null, null,
                new PowerProfile(context, true /* forTest */), buildScalingPolicies(),
                new PowerStatsUidResolver());
        mPowerStatsInternal = new MockPowerStatsInternal();
        mBatteryExternalStatsWorker =
                new BatteryExternalStatsWorker(new TestInjector(context), batteryStats, mHandler,
                        mClock);
    }

    @Test
    public void testUpdateWifiState() {
        WifiActivityEnergyInfo firstInfo = new WifiActivityEnergyInfo(1111,
                WifiActivityEnergyInfo.STACK_STATE_STATE_ACTIVE, 11, 22, 33, 44);

        WifiActivityEnergyInfo delta = mBatteryExternalStatsWorker.extractDeltaLocked(firstInfo);

        assertEquals(1111, delta.getTimeSinceBootMillis());
        assertEquals(WifiActivityEnergyInfo.STACK_STATE_STATE_ACTIVE, delta.getStackState());
        assertEquals(0, delta.getControllerTxDurationMillis());
        assertEquals(0, delta.getControllerRxDurationMillis());
        assertEquals(0, delta.getControllerScanDurationMillis());
        assertEquals(0, delta.getControllerIdleDurationMillis());

        WifiActivityEnergyInfo secondInfo = new WifiActivityEnergyInfo(91111,
                WifiActivityEnergyInfo.STACK_STATE_STATE_IDLE, 811, 722, 633, 544);

        delta = mBatteryExternalStatsWorker.extractDeltaLocked(secondInfo);

        assertEquals(91111, delta.getTimeSinceBootMillis());
        assertEquals(WifiActivityEnergyInfo.STACK_STATE_STATE_IDLE, delta.getStackState());
        assertEquals(800, delta.getControllerTxDurationMillis());
        assertEquals(700, delta.getControllerRxDurationMillis());
        assertEquals(600, delta.getControllerScanDurationMillis());
        assertEquals(500, delta.getControllerIdleDurationMillis());
    }

    public class TestInjector extends BatteryExternalStatsWorker.Injector {
        public TestInjector(Context context) {
            super(context);
        }

        public <T> T getSystemService(Class<T> serviceClass) {
            return null;
        }

        public <T> T getLocalService(Class<T> serviceClass) {
            if (serviceClass == PowerStatsInternal.class) {
                return (T) mPowerStatsInternal;
            }
            return null;
        }
    }

    private static CpuScalingPolicies buildScalingPolicies() {
        SparseArray<int[]> cpusByPolicy = new SparseArray<>();
        cpusByPolicy.put(0, new int[]{0, 1, 2, 3});
        cpusByPolicy.put(4, new int[]{4, 5, 6, 7});
        SparseArray<int[]> freqsByPolicy = new SparseArray<>();
        freqsByPolicy.put(0, new int[]{300000, 1000000, 2000000});
        freqsByPolicy.put(4, new int[]{300000, 1000000, 2500000, 3000000});
        return new CpuScalingPolicies(freqsByPolicy, freqsByPolicy);
    }
}
