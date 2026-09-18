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

package com.android.server.power.stats;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.os.Clock;
import com.android.internal.os.PowerStats;
import com.android.server.power.stats.format.CpuCyclePowerStatsLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class CpuCyclePerUidCollectorTest {
    private static final int UID_1 = 10899;
    private static final int UID_2 = 10988;

    private final MockClock mMockClock = new MockClock();
    private final HandlerThread mHandlerThread = new HandlerThread("test");
    private final PowerStatsUidResolver mUidResolver = new PowerStatsUidResolver();
    private Handler mHandler;
    private PowerStats mCollectedStats;

    @Mock
    private CpuCyclePerUidCollector.KernelCpuCycleReader mMockKernelCpuCycleReader;

    @Mock
    private PowerStatsCollector.ConsumedEnergyRetriever mMockConsumedEnergyRetriever;

    private class TestInjector implements CpuCyclePerUidCollector.Injector {
        @Override
        public Handler getHandler() {
            return mHandler;
        }

        @Override
        public Clock getClock() {
            return mMockClock;
        }

        @Override
        public PowerStatsUidResolver getUidResolver() {
            return mUidResolver;
        }

        @Override
        public CpuCyclePerUidCollector.KernelCpuCycleReader getKernelCpuCycleReader() {
            return mMockKernelCpuCycleReader;
        }

        @Override
        public PowerStatsCollector.ConsumedEnergyRetriever getConsumedEnergyRetriever() {
            return mMockConsumedEnergyRetriever;
        }
    }

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mHandlerThread.start();
        mHandler = mHandlerThread.getThreadHandler();
        when(mMockKernelCpuCycleReader.isSupported()).thenReturn(true);
        when(mMockKernelCpuCycleReader.startTracking()).thenReturn(true);
        when(mMockConsumedEnergyRetriever.getVoltageMv()).thenReturn(10000);
    }

    @Test
    public void testInitialCollectionEstablishesBaseline() {
        CpuCyclePerUidCollector collector = createCollector();
        collector.setEnabled(true);

        initBaseline(collector);

        assertThat(mCollectedStats).isNotNull();
        assertThat(mCollectedStats.uidStats.size()).isEqualTo(0);
        CpuCyclePowerStatsLayout layout =
                new CpuCyclePowerStatsLayout(collector.getPowerStatsDescriptor());
        assertThat(layout.getDeviceCpuEnergy(mCollectedStats.stats)).isEqualTo(0);
    }

    @Test
    public void testSuccessfulCollection() {
        CpuCyclePerUidCollector collector = createCollector();
        collector.setEnabled(true);

        initBaseline(collector);

        // Second collection should produce stats
        when(mMockKernelCpuCycleReader.readUidPowerDelta())
                .thenReturn(new long[]{UID_1, 1500, UID_2, 2500});
        mMockClock.realtime = 2000;
        collector.updateStats(mMockClock.realtime, mMockClock.uptime);
        waitForIdle();

        assertThat(mCollectedStats).isNotNull();
        assertThat(mCollectedStats.uidStats.size()).isEqualTo(2);
        CpuCyclePowerStatsLayout layout =
                new CpuCyclePowerStatsLayout(collector.getPowerStatsDescriptor());
        // Mock voltage is 10 Volt.
        assertThat(layout.getUidCpuEnergy(mCollectedStats.uidStats.get(UID_1))).isEqualTo(150);
        assertThat(layout.getUidCpuEnergy(mCollectedStats.uidStats.get(UID_2))).isEqualTo(250);
        assertThat(layout.getDeviceCpuEnergy(mCollectedStats.stats)).isEqualTo(400);
    }

    @Test
    public void testUnusualInterval() {
        CpuCyclePerUidCollector collector = createCollector();
        collector.setEnabled(true);

        initBaseline(collector);

        // Second collection with a very short interval
        when(mMockKernelCpuCycleReader.readUidPowerDelta())
                .thenReturn(new long[]{UID_1, 1500, UID_2, 2500});
        mMockClock.realtime = 1100;
        collector.updateStats(mMockClock.realtime, mMockClock.uptime);
        waitForIdle();

        assertThat(mCollectedStats).isNotNull();
        assertThat(mCollectedStats.uidStats.size()).isEqualTo(0);
        CpuCyclePowerStatsLayout layout =
                new CpuCyclePowerStatsLayout(collector.getPowerStatsDescriptor());
        assertThat(layout.getDeviceCpuEnergy(mCollectedStats.stats)).isEqualTo(0);

        // Third collection with a very long interval
        when(mMockKernelCpuCycleReader.readUidPowerDelta())
                .thenReturn(new long[]{UID_1, 2000, UID_2, 3000});
        mMockClock.realtime = 3000;
        collector.updateStats(mMockClock.realtime, mMockClock.uptime);
        waitForIdle();

        assertThat(mCollectedStats).isNotNull();
        assertThat(mCollectedStats.uidStats.size()).isEqualTo(0);
        assertThat(layout.getDeviceCpuEnergy(mCollectedStats.stats)).isEqualTo(0);
    }

    @Test
    public void testLayoutDescriptorConstructor() {
        CpuCyclePowerStatsLayout layout = new CpuCyclePowerStatsLayout();
        PowerStats.Descriptor descriptor = layout.createDescriptor();

        CpuCyclePowerStatsLayout restoredLayout = new CpuCyclePowerStatsLayout(descriptor);
        long[] stats = new long[restoredLayout.getDeviceStatsArrayLength()];
        restoredLayout.setDeviceCpuEnergy(stats, 1234);
        assertThat(restoredLayout.getDeviceCpuEnergy(stats)).isEqualTo(1234);

        long[] uidStats = new long[restoredLayout.getUidStatsArrayLength()];
        restoredLayout.setUidCpuEnergy(uidStats, 5678);
        assertThat(restoredLayout.getUidCpuEnergy(uidStats)).isEqualTo(5678);
    }

    @Test
    public void testNativeError() {
        CpuCyclePerUidCollector collector = createCollector();
        collector.setEnabled(true);

        initBaseline(collector);

        // Second collection returns null
        when(mMockKernelCpuCycleReader.readUidPowerDelta()).thenReturn(null);
        mMockClock.realtime = 2000;
        boolean success = collector.updateStats(mMockClock.realtime, mMockClock.uptime);
        waitForIdle();

        assertThat(success).isFalse();
    }

    @Test
    public void testOddNumberOfElements() {
        CpuCyclePerUidCollector collector = createCollector();
        collector.setEnabled(true);

        initBaseline(collector);

        // Second collection returns odd number of elements
        when(mMockKernelCpuCycleReader.readUidPowerDelta())
                .thenReturn(new long[]{UID_1, 1500, UID_2});
        mMockClock.realtime = 2000;
        boolean success = collector.updateStats(mMockClock.realtime, mMockClock.uptime);
        waitForIdle();

        assertThat(success).isFalse();
    }

    private void initBaseline(CpuCyclePerUidCollector collector) {
        when(mMockKernelCpuCycleReader.readUidPowerDelta())
                .thenReturn(new long[]{UID_1, 1000, UID_2, 2000});
        mMockClock.realtime = 1000;
        collector.updateStats(mMockClock.realtime, mMockClock.uptime);
        waitForIdle();
    }

    private CpuCyclePerUidCollector createCollector() {
        CpuCyclePerUidCollector collector = new CpuCyclePerUidCollector(new TestInjector());
        collector.addConsumer((stats, elapsedRealtime, uptime) -> mCollectedStats = stats);
        return collector;
    }

    private void waitForIdle() {
        ConditionVariable done = new ConditionVariable();
        mHandler.post(done::open);
        done.block();
    }
}
