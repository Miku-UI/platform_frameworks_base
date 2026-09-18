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
package com.android.server.power.stats.format;

import android.annotation.NonNull;
import android.os.BatteryConsumer;
import android.os.PersistableBundle;

import com.android.internal.os.PowerStats;

/**
 * PowerStatsLayout for CPU cycle based power attribution.
 */
public final class CpuCyclePowerStatsLayout extends PowerStatsLayout {
    private static final String EXTRA_DEVICE_CPU_ENERGY_POSITION = "de";
    private static final String EXTRA_UID_CPU_ENERGY_POSITION = "ue";

    private int mDeviceCpuEnergyPosition;
    private int mUidCpuEnergyPosition;

    public CpuCyclePowerStatsLayout() {
        mDeviceCpuEnergyPosition = addDeviceSection(1, "energy");
        addDeviceSectionPowerEstimate();
        mUidCpuEnergyPosition = addUidSection(1, "energy");
        addUidSectionPowerEstimate();
    }

    public CpuCyclePowerStatsLayout(@NonNull PowerStats.Descriptor descriptor) {
        super(descriptor);
        PersistableBundle extras = descriptor.extras;
        mDeviceCpuEnergyPosition = extras.getInt(EXTRA_DEVICE_CPU_ENERGY_POSITION);
        mUidCpuEnergyPosition = extras.getInt(EXTRA_UID_CPU_ENERGY_POSITION);
    }

    /**
     * Creates a descriptor for the PowerStats object that will be produced by the
     * CpuCyclePerUidCollector.
     *
     * <p>This descriptor defines the structure of the PowerStats for CPU energy attribution
     * on x86 platforms, which is derived from eBPF and RAPL data.
     *
     * <ul>
     *   <li><b>Power Component:</b> {@link android.os.BatteryConsumer#POWER_COMPONENT_CPU}</li>
     *   <li><b>Device-level stats:</b> A single value (statsArrayLength = 1) representing the
     *       total CPU energy consumed by the entire device (in microcoulombs).</li>
     *   <li><b>UID-level stats:</b> A single value (uidStatsArrayLength = 1) representing the
     *       portion of CPU energy attributed to each specific UID (in microcoulombs).</li>
     * </ul>
     *
     * The names for these stats ("energy") are defined in the extras bundle.
     */
    public PowerStats.Descriptor createDescriptor() {
        PersistableBundle extras = new PersistableBundle();
        toExtras(extras);
        return new PowerStats.Descriptor(BatteryConsumer.POWER_COMPONENT_CPU,
                getDeviceStatsArrayLength(),
                /* stateLabels= */ null,
                /* stateStatsArrayLength= */ 0,
                getUidStatsArrayLength(),
                extras);
    }

    /**
     * Serializes the layout into the extras bundle.
     */
    public void toExtras(PersistableBundle extras) {
        super.toExtras(extras);
        extras.putInt(EXTRA_DEVICE_CPU_ENERGY_POSITION, mDeviceCpuEnergyPosition);
        extras.putInt(EXTRA_UID_CPU_ENERGY_POSITION, mUidCpuEnergyPosition);
    }

    /**
     * Sets the consumed energy for the entire device.
     */
    public void setDeviceCpuEnergy(long[] stats, long energyUc) {
        stats[mDeviceCpuEnergyPosition] = energyUc;
    }

    /**
     * Returns the consumed energy for the entire device.
     */
    public long getDeviceCpuEnergy(long[] stats) {
        return stats[mDeviceCpuEnergyPosition];
    }

    /**
     * Sets the consumed energy for a given UID.
     */
    public void setUidCpuEnergy(long[] uidStats, long energyUc) {
        uidStats[mUidCpuEnergyPosition] = energyUc;
    }

    /**
     * Returns the consumed energy for a given UID.
     */
    public long getUidCpuEnergy(long[] uidStats) {
        return uidStats[mUidCpuEnergyPosition];
    }
}
