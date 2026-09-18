/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.hardware.power.stats.EnergyConsumer;
import android.hardware.power.stats.EnergyConsumerAttribution;
import android.hardware.power.stats.EnergyConsumerResult;
import android.hardware.power.stats.EnergyConsumerType;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PersistableBundle;
import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.power.PowerStatsInternal;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.os.PowerStats;
import com.android.server.power.optimization.Flags;
import com.android.server.power.stats.format.PowerStatsLayout;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class PowerStatsCollectorTest {
    private final MockClock mMockClock = new MockClock();
    private final HandlerThread mHandlerThread = new HandlerThread("test");
    private Handler mHandler;
    private PowerStatsCollector mCollector;
    private PowerStats mCollectedStats;
    private PowerStatsUidResolver mUidResolver;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setup() {
        mHandlerThread.start();
        mHandler = mHandlerThread.getThreadHandler();
        mUidResolver = mock(PowerStatsUidResolver.class);
        mCollector = new PowerStatsCollector(mHandler, 60000, mUidResolver,
                mMockClock) {
            @Override
            protected PowerStats collectStats(long elapsedRealtimeMs, long uptimeMs) {
                return new PowerStats(
                        new PowerStats.Descriptor(0, 0, null, 0, 0, new PersistableBundle()));
            }
        };
        mCollector.addConsumer((stats, elapsedRealtime, uptime) -> mCollectedStats = stats);
        mCollector.setEnabled(true);
    }

    @Test
    public void throttlePeriod() {
        mMockClock.uptime = 1000;
        mCollector.schedule();
        waitForIdle();

        assertThat(mCollectedStats).isNotNull();

        mMockClock.uptime += 1000;
        mCollectedStats = null;
        mCollector.schedule();      // Should be throttled
        waitForIdle();

        assertThat(mCollectedStats).isNull();

        // Should be allowed to run
        mMockClock.uptime += 100_000;
        mCollector.schedule();
        waitForIdle();

        assertThat(mCollectedStats).isNotNull();
    }

    private void waitForIdle() {
        ConditionVariable done = new ConditionVariable();
        mHandler.post(done::open);
        done.block();
    }

    @Test
    @DisabledOnRavenwood
    public void consumedEnergyRetriever() throws Exception {
        PowerStatsInternal powerStatsInternal = mock(PowerStatsInternal.class);
        mockEnergyConsumers(powerStatsInternal);

        PowerStatsCollector.ConsumedEnergyRetrieverImpl retriever =
                new PowerStatsCollector.ConsumedEnergyRetrieverImpl(powerStatsInternal,
                        () -> 3500);
        int[] energyConsumerIds = retriever.getEnergyConsumerIds(EnergyConsumerType.CPU_CLUSTER);
        assertThat(energyConsumerIds).isEqualTo(new int[]{1, 2});
        EnergyConsumerResult[] energy = retriever.getConsumedEnergy(energyConsumerIds);
        assertThat(energy[0].energyUWs).isEqualTo(1000);
        assertThat(energy[1].energyUWs).isEqualTo(2000);
        energy = retriever.getConsumedEnergy(energyConsumerIds);
        assertThat(energy[0].energyUWs).isEqualTo(1500);
        assertThat(energy[1].energyUWs).isEqualTo(2700);
    }

    @Test
    public void checkUidStatsConsumedEnergy_singleChildUid() throws Exception {
        // Arrange block
        int voltageMv = 3500;
        int energyConsumerId = 1;
        int childUidEnergyUWs = 1000;
        int appUid = 10000;
        int childUid = 30000;
        // Establish the mapping from child UID to app UID
        when(mUidResolver.getOwnerUid(childUid)).thenReturn(appUid);
        // Mock Energy Consumers
        PowerStatsInternal powerStatsInternal = mock(PowerStatsInternal.class);
        when(powerStatsInternal.getEnergyConsumerInfo())
                .thenReturn(new EnergyConsumer[]{
                        new EnergyConsumer() {{
                            id = energyConsumerId;
                            type = EnergyConsumerType.CPU_CLUSTER;
                            ordinal = 0;
                            name = "CPU0";
                        }}
                });
        // Mock Energy Consumption with Attribution
        CompletableFuture<EnergyConsumerResult[]> future = mock(CompletableFuture.class);
        when(future.get(anyLong(), any(TimeUnit.class)))
                .thenReturn(new EnergyConsumerResult[]{
                        new EnergyConsumerResult() {{
                            id = energyConsumerId;
                            this.energyUWs = 0;
                            attribution = new EnergyConsumerAttribution[]{
                                    new EnergyConsumerAttribution() {{
                                        uid = childUid;
                                        this.energyUWs = 0;
                                    }}
                            };
                        }}
                })
                .thenReturn(new EnergyConsumerResult[]{
                        new EnergyConsumerResult() {{
                            id = energyConsumerId;
                            this.energyUWs = childUidEnergyUWs;
                            attribution = new EnergyConsumerAttribution[]{
                                    new EnergyConsumerAttribution() {{
                                        uid = childUid;
                                        this.energyUWs = childUidEnergyUWs;
                                    }}
                            };
                        }}
                });
        when(powerStatsInternal.getEnergyConsumedAsync(eq(new int[]{energyConsumerId})))
                .thenReturn(future);
        PowerStatsCollector.ConsumedEnergyRetrieverImpl retriever =
                new PowerStatsCollector.ConsumedEnergyRetrieverImpl(powerStatsInternal,
                        () -> voltageMv);
        TestPowerStatsLayout layout = new TestPowerStatsLayout();
        PowerStats.Descriptor descriptor = new PowerStats.Descriptor(
                0, 1, null, 0, 1, new PersistableBundle());
        layout.toExtras(descriptor.extras);
        PowerStats powerStats = new PowerStats(descriptor);
        PowerStatsCollector.ConsumedEnergyHelper helper = mCollector.new ConsumedEnergyHelper(
                retriever, energyConsumerId, true);

        // Act block
        helper.collectConsumedEnergy(powerStats, layout);
        helper.collectConsumedEnergy(powerStats, layout);

        // Assert block
        // Verify that the energy was attributed to the app UID, not the child UID
        long[] uidStats = powerStats.uidStats.get(appUid);
        assertThat(uidStats).isNotNull();
        assertThat(layout.getUidConsumedEnergy(uidStats, 0))
                .isEqualTo(PowerStatsCollector.uJtoUc(childUidEnergyUWs, voltageMv));
        assertThat(powerStats.uidStats.get(childUid)).isNull();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_UID_PARENT_ATTRIBUTION_PREVENT_OVERWRITE)
    public void checkUidStatsConsumedEnergy_multiChildUid() throws Exception {
        // Arrange block
        int voltageMv = 3500;
        int energyConsumerId = 1;
        int childUid1EnergyUWs = 1000;
        int childUid2EnergyUWs = 1500;
        int appUid = 10000;
        int childUid1 = 30000;
        int childUid2 = 35000;
        // Establish the mapping from child UID to app UID
        when(mUidResolver.getOwnerUid(childUid1)).thenReturn(appUid);
        when(mUidResolver.getOwnerUid(childUid2)).thenReturn(appUid);
        // Mock Energy Consumers
        PowerStatsInternal powerStatsInternal = mock(PowerStatsInternal.class);
        when(powerStatsInternal.getEnergyConsumerInfo())
                .thenReturn(new EnergyConsumer[]{
                        new EnergyConsumer() {{
                            id = energyConsumerId;
                            type = EnergyConsumerType.CPU_CLUSTER;
                            ordinal = 0;
                            name = "CPU0";
                        }}
                });
        // Mock Energy Consumption with Attribution
        CompletableFuture<EnergyConsumerResult[]> future = mock(CompletableFuture.class);
        when(future.get(anyLong(), any(TimeUnit.class)))
                .thenReturn(new EnergyConsumerResult[]{
                        new EnergyConsumerResult() {{
                            id = energyConsumerId;
                            this.energyUWs = 0;
                            attribution = new EnergyConsumerAttribution[]{
                                    new EnergyConsumerAttribution() {{
                                        uid = childUid1;
                                        this.energyUWs = 0;
                                    }},
                                    new EnergyConsumerAttribution() {{
                                        uid = childUid2;
                                        this.energyUWs = 0;
                                    }}
                            };
                        }}
                })
                .thenReturn(new EnergyConsumerResult[]{
                        new EnergyConsumerResult() {{
                            id = energyConsumerId;
                            this.energyUWs = childUid1EnergyUWs + childUid2EnergyUWs;
                            attribution = new EnergyConsumerAttribution[]{
                                    new EnergyConsumerAttribution() {{
                                        uid = childUid1;
                                        this.energyUWs = childUid1EnergyUWs;
                                    }},
                                    new EnergyConsumerAttribution() {{
                                        uid = childUid2;
                                        this.energyUWs = childUid2EnergyUWs;
                                    }}
                            };
                        }}
                });
        when(powerStatsInternal.getEnergyConsumedAsync(eq(new int[]{energyConsumerId})))
                .thenReturn(future);
        PowerStatsCollector.ConsumedEnergyRetrieverImpl retriever =
                new PowerStatsCollector.ConsumedEnergyRetrieverImpl(powerStatsInternal,
                        () -> voltageMv);
        TestPowerStatsLayout layout = new TestPowerStatsLayout();
        PowerStats.Descriptor descriptor = new PowerStats.Descriptor(
                0, 1, null, 0, 1, new PersistableBundle());
        layout.toExtras(descriptor.extras);
        PowerStats powerStats = new PowerStats(descriptor);
        PowerStatsCollector.ConsumedEnergyHelper helper = mCollector.new ConsumedEnergyHelper(
                retriever, energyConsumerId, true);

        // Act block
        helper.collectConsumedEnergy(powerStats, layout);
        helper.collectConsumedEnergy(powerStats, layout);

        // Assert block
        // Verify that the energy was attributed to the app UID, not the child UID
        long[] uidStats = powerStats.uidStats.get(appUid);
        assertThat(uidStats).isNotNull();
        assertThat(layout.getUidConsumedEnergy(uidStats, 0)).isEqualTo(
                PowerStatsCollector.uJtoUc(childUid1EnergyUWs, voltageMv)
                + PowerStatsCollector.uJtoUc(childUid2EnergyUWs, voltageMv));
        assertThat(powerStats.uidStats.get(childUid1)).isNull();
        assertThat(powerStats.uidStats.get(childUid2)).isNull();
    }


    @Test
    public void collectConsumedEnergy_emptyEnergyConsumerResult() throws Exception {
        // Arrange block
        int voltageMv = 3500;
        int energyConsumerId = 1;

        // Mock Energy Consumers
        PowerStatsInternal powerStatsInternal = mock(PowerStatsInternal.class);
        when(powerStatsInternal.getEnergyConsumerInfo())
                .thenReturn(new EnergyConsumer[]{
                        new EnergyConsumer() {{
                            id = energyConsumerId;
                            type = EnergyConsumerType.CPU_CLUSTER;
                            ordinal = 0;
                            name = "CPU0";
                        }}
                });

        // Mock Energy Consumption to return an empty array
        CompletableFuture<EnergyConsumerResult[]> future = mock(CompletableFuture.class);
        when(future.get(anyLong(), any(TimeUnit.class)))
                .thenReturn(new EnergyConsumerResult[0]);
        when(powerStatsInternal.getEnergyConsumedAsync(eq(new int[]{energyConsumerId})))
                .thenReturn(future);

        PowerStatsCollector.ConsumedEnergyRetrieverImpl retriever =
                new PowerStatsCollector.ConsumedEnergyRetrieverImpl(powerStatsInternal,
                        () -> voltageMv);
        TestPowerStatsLayout layout = new TestPowerStatsLayout();
        PowerStats.Descriptor descriptor = new PowerStats.Descriptor(
                0, 1, null, 0, 1, new PersistableBundle());
        layout.toExtras(descriptor.extras);
        PowerStats powerStats = new PowerStats(descriptor);
        PowerStatsCollector.ConsumedEnergyHelper helper =
                mCollector.new ConsumedEnergyHelper(retriever, energyConsumerId, true);

        // Act & Assert block
        assertThat(helper.collectConsumedEnergy(powerStats, layout)).isFalse();
    }

    @Test
    public void collectConsumedEnergy_manyEnergyConsumers() throws Exception {
        // Arrange block
        int voltageMv = 3500;

        // Mock Energy Consumers
        PowerStatsInternal powerStatsInternal = mock(PowerStatsInternal.class);
        when(powerStatsInternal.getEnergyConsumerInfo())
                .thenReturn(new EnergyConsumer[]{
                        new EnergyConsumer() {{
                            id = 1;
                            type = EnergyConsumerType.CPU_CLUSTER;
                            ordinal = 0;
                            name = "CPU0";
                        }},
                        new EnergyConsumer() {{
                            id = 2;
                            type = EnergyConsumerType.CPU_CLUSTER;
                            ordinal = 1;
                            name = "CPU1";
                        }}
                });

        // Mock Energy Consumption
        CompletableFuture<EnergyConsumerResult[]> future = mock(CompletableFuture.class);
        when(future.get(anyLong(), any(TimeUnit.class)))
                .thenReturn(new EnergyConsumerResult[]{
                        new EnergyConsumerResult() {{
                            id = 1;
                            this.energyUWs = 0;
                        }},
                        new EnergyConsumerResult() {{
                            id = 2;
                            this.energyUWs = 0;
                        }}
                })
                .thenReturn(new EnergyConsumerResult[]{
                        new EnergyConsumerResult() {{
                            id = 1;
                            this.energyUWs = 1000;
                        }},
                        new EnergyConsumerResult() {{
                            id = 2;
                            this.energyUWs = 1500;
                        }}
                });
        when(powerStatsInternal.getEnergyConsumedAsync(eq(new int[]{1, 2})))
                .thenReturn(future);
        PowerStatsCollector.ConsumedEnergyRetrieverImpl retriever =
                new PowerStatsCollector.ConsumedEnergyRetrieverImpl(powerStatsInternal,
                        () -> voltageMv);
        TestPowerStatsLayout layout = new TestPowerStatsLayout();
        PowerStats.Descriptor descriptor = new PowerStats.Descriptor(
                1, 2, null, 2, 1, new PersistableBundle());
        layout.toExtras(descriptor.extras);
        PowerStats powerStats = new PowerStats(descriptor);
        PowerStatsCollector.ConsumedEnergyHelper helper = mCollector.new ConsumedEnergyHelper(
                retriever, EnergyConsumerType.CPU_CLUSTER);

        // Act block
        helper.collectConsumedEnergy(powerStats, layout);
        helper.collectConsumedEnergy(powerStats, layout);

        // Assert block
        // Verify that the energy was set correctly
        assertThat(layout.getConsumedEnergy(powerStats.stats, 0))
                .isEqualTo(PowerStatsCollector.uJtoUc(1000, voltageMv));
        assertThat(layout.getConsumedEnergy(powerStats.stats, 1))
                .isEqualTo(PowerStatsCollector.uJtoUc(1500, voltageMv));
    }

    @Test
    public void collectConsumedEnergy_energyConsumersMoreThanResults() throws Exception {
        // Arrange block
        int voltageMv = 3500;

        // Mock Energy Consumers
        PowerStatsInternal powerStatsInternal = mock(PowerStatsInternal.class);
        when(powerStatsInternal.getEnergyConsumerInfo())
                .thenReturn(new EnergyConsumer[]{
                        new EnergyConsumer() {{
                            id = 1;
                            type = EnergyConsumerType.CPU_CLUSTER;
                            ordinal = 0;
                            name = "CPU0";
                        }},
                        new EnergyConsumer() {{
                            id = 2;
                            type = EnergyConsumerType.CPU_CLUSTER;
                            ordinal = 1;
                            name = "CPU1";
                        }}
                });

        // Mock Energy Consumption
        CompletableFuture<EnergyConsumerResult[]> future = mock(CompletableFuture.class);
        when(future.get(anyLong(), any(TimeUnit.class)))
                .thenReturn(new EnergyConsumerResult[]{
                        new EnergyConsumerResult() {{
                            id = 1;
                            this.energyUWs = 0;
                        }}
                })
                .thenReturn(new EnergyConsumerResult[]{
                        new EnergyConsumerResult() {{
                            id = 1;
                            this.energyUWs = 1000;
                        }}
                });
        when(powerStatsInternal.getEnergyConsumedAsync(eq(new int[]{1, 2})))
                .thenReturn(future);
        PowerStatsCollector.ConsumedEnergyRetrieverImpl retriever =
                new PowerStatsCollector.ConsumedEnergyRetrieverImpl(powerStatsInternal,
                        () -> voltageMv);
        TestPowerStatsLayout layout = new TestPowerStatsLayout();
        PowerStats.Descriptor descriptor = new PowerStats.Descriptor(
                0, 2, null, 0, 1, new PersistableBundle());
        layout.toExtras(descriptor.extras);
        PowerStats powerStats = new PowerStats(descriptor);
        PowerStatsCollector.ConsumedEnergyHelper helper = mCollector.new ConsumedEnergyHelper(
                retriever, EnergyConsumerType.CPU_CLUSTER);

        // Act block
        helper.collectConsumedEnergy(powerStats, layout);
        helper.collectConsumedEnergy(powerStats, layout);

        // Assert block
        // Verify that the energy was set correctly
        assertThat(layout.getConsumedEnergy(powerStats.stats, 0))
                .isEqualTo(PowerStatsCollector.uJtoUc(1000, voltageMv));
        assertThat(layout.getConsumedEnergy(powerStats.stats, 1))
                .isEqualTo(PowerStatsCollector.uJtoUc(0, voltageMv));
    }

    @Test
    public void collectConsumedEnergy_energyConsumersLessThanResults() throws Exception {
        // Arrange block
        int voltageMv = 3500;

        // Mock Energy Consumers
        PowerStatsInternal powerStatsInternal = mock(PowerStatsInternal.class);
        when(powerStatsInternal.getEnergyConsumerInfo())
                .thenReturn(new EnergyConsumer[]{
                        new EnergyConsumer() {{
                            id = 1;
                            type = EnergyConsumerType.CPU_CLUSTER;
                            ordinal = 0;
                            name = "CPU0";
                        }}
                });

        // Mock Energy Consumption
        CompletableFuture<EnergyConsumerResult[]> future = mock(CompletableFuture.class);
        when(future.get(anyLong(), any(TimeUnit.class)))
                .thenReturn(new EnergyConsumerResult[]{
                        new EnergyConsumerResult() {{
                            id = 1;
                            this.energyUWs = 0;
                        }},
                        new EnergyConsumerResult() {{
                            id = 2;
                            this.energyUWs = 0;
                        }}
                })
                .thenReturn(new EnergyConsumerResult[]{
                        new EnergyConsumerResult() {{
                            id = 1;
                            this.energyUWs = 1000;
                        }},
                        new EnergyConsumerResult() {{
                            id = 2;
                            this.energyUWs = 2000;
                        }}
                });
        when(powerStatsInternal.getEnergyConsumedAsync(eq(new int[]{1})))
                .thenReturn(future);
        PowerStatsCollector.ConsumedEnergyRetrieverImpl retriever =
                new PowerStatsCollector.ConsumedEnergyRetrieverImpl(powerStatsInternal,
                        () -> voltageMv);
        TestPowerStatsLayout layout = new TestPowerStatsLayout();
        PowerStats.Descriptor descriptor = new PowerStats.Descriptor(
                0, 2, null, 0, 1, new PersistableBundle());
        layout.toExtras(descriptor.extras);
        PowerStats powerStats = new PowerStats(descriptor);
        PowerStatsCollector.ConsumedEnergyHelper helper = mCollector.new ConsumedEnergyHelper(
                retriever, EnergyConsumerType.CPU_CLUSTER);

        // Act block
        helper.collectConsumedEnergy(powerStats, layout);
        helper.collectConsumedEnergy(powerStats, layout);

        // Assert block
        // Verify that the energy was set correctly
        assertThat(layout.getConsumedEnergy(powerStats.stats, 0))
                .isEqualTo(PowerStatsCollector.uJtoUc(1000, voltageMv));
        assertThat(layout.getConsumedEnergy(powerStats.stats, 1))
                .isEqualTo(PowerStatsCollector.uJtoUc(0, voltageMv));
    }

    @SuppressWarnings("unchecked")
    private void mockEnergyConsumers(PowerStatsInternal powerStatsInternal) throws Exception {

        when(powerStatsInternal.getEnergyConsumerInfo())
                .thenReturn(new EnergyConsumer[]{
                        new EnergyConsumer() {{
                            id = 1;
                            type = EnergyConsumerType.CPU_CLUSTER;
                            ordinal = 0;
                            name = "CPU0";
                        }},
                        new EnergyConsumer() {{
                            id = 2;
                            type = EnergyConsumerType.CPU_CLUSTER;
                            ordinal = 1;
                            name = "CPU4";
                        }},
                        new EnergyConsumer() {{
                            id = 3;
                            type = EnergyConsumerType.BLUETOOTH;
                            name = "BT";
                        }},
                });

        CompletableFuture<EnergyConsumerResult[]> future1 = mock(CompletableFuture.class);
        when(future1.get(anyLong(), any(TimeUnit.class)))
                .thenReturn(new EnergyConsumerResult[]{
                        new EnergyConsumerResult() {{
                            id = 1;
                            energyUWs = 1000;
                        }},
                        new EnergyConsumerResult() {{
                            id = 2;
                            energyUWs = 2000;
                        }}
                });

        CompletableFuture<EnergyConsumerResult[]> future2 = mock(CompletableFuture.class);
        when(future2.get(anyLong(), any(TimeUnit.class)))
                .thenReturn(new EnergyConsumerResult[]{
                        new EnergyConsumerResult() {{
                            id = 1;
                            energyUWs = 1500;
                        }},
                        new EnergyConsumerResult() {{
                            id = 2;
                            energyUWs = 2700;
                        }}
                });

        when(powerStatsInternal.getEnergyConsumedAsync(eq(new int[]{1, 2})))
                .thenReturn(future1)
                .thenReturn(future2);
    }

    private EnergyConsumerResult mockEnergyConsumerResult(long energyUWs) {
        EnergyConsumerResult ecr = new EnergyConsumerResult();
        ecr.energyUWs = energyUWs;
        return ecr;
    }

    private static class TestPowerStatsLayout extends PowerStatsLayout {
        TestPowerStatsLayout() {
            addDeviceSectionEnergyConsumers(1);
            addUidSectionEnergyConsumers(1);
        }
    }
}
