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

import android.os.Handler;
import android.util.Slog;

import com.android.internal.os.Clock;
import com.android.internal.os.KernelCpuCyclePerUidBpf;
import com.android.internal.os.PowerStats;
import com.android.server.power.optimization.Flags;
import com.android.server.power.stats.format.CpuCyclePowerStatsLayout;

import java.io.PrintWriter;
import java.util.Arrays;

/**
 * Collects CPU energy attribution for x86 platforms. This collector periodically polls the
 * total package energy from the RAPL interface and the per-UID CPU cycle counts from an eBPF
 * map. It then calculates the energy consumed during the interval and attributes it to each UID
 * based on their proportional share of CPU cycles.
 */
public class CpuCyclePerUidCollector extends CpuPowerStatsCollector {
    private static final String TAG = "CpuCyclePerUidCollector";
    public static final int RAPL_POLL_INTERVAL_MS = 1000; // Poll every 1 second

    // The collector runs periodically. We accept a tolerance of 20% on the lower bound
    // (to avoid quantization errors from small deltas) and 50% on the upper bound
    // (to tolerate system load delays while rejecting suspend/resume artifacts).
    private static final double DURATION_TOLERANCE_LOWER_BOUND = 0.8;
    private static final double DURATION_TOLERANCE_UPPER_BOUND = 1.5;

    private CpuCyclePowerStatsLayout mLayout;
    private PowerStats.Descriptor mPowerStatsDescriptor;
    private PowerStats mPowerStats;
    private final KernelCpuCycleReader mKernelCpuCycleReader;
    private boolean mBpfProgramStarted;
    private boolean mFirstCollection = true;
    private long mLastCollectionRealtimeMs;
    private long mLastAutoPollRealtimeMs;
    private ConsumedEnergyRetriever mConsumedEnergyRetriever;
    private int mLastVoltageMv;
    private final Injector mInjector;

    public interface Injector {
        /** Returns a Handler. */
        Handler getHandler();
        /** Returns a Clock. */
        Clock getClock();
        /** Returns a PowerStatsUidResolver. */
        PowerStatsUidResolver getUidResolver();
        /** Returns a KernelCpuCycleReader. */
        KernelCpuCycleReader getKernelCpuCycleReader();
        /** Returns a ConsumedEnergyRetriever. */
        ConsumedEnergyRetriever getConsumedEnergyRetriever();
    }

    public static class KernelCpuCycleReader {
        /** Returns true if the feature is supported. */
        public boolean isSupported() {
            return KernelCpuCyclePerUidBpf.isSupported();
        }

        /** Starts tracking. */
        public boolean startTracking() {
            return KernelCpuCyclePerUidBpf.startTracking();
        }

        /** Stops tracking. */
        public void stopTracking() {
            KernelCpuCyclePerUidBpf.stopTracking();
        }

        /** Reads UID power delta. */
        public long[] readUidPowerDelta() {
            return KernelCpuCyclePerUidBpf.readUidPowerDelta();
        }

        /** Reads package power. */
        public long readPackagePower() {
            return KernelCpuCyclePerUidBpf.readPackagePower();
        }

        /** Reads last recorded cycle. */
        public long readLastRecordedCycle() {
            return KernelCpuCyclePerUidBpf.readLastRecordedCycle();
        }

        /** Reads desync count. */
        public long readDesyncCount() {
            return KernelCpuCyclePerUidBpf.readDesyncCount();
        }

        /** Reads UID CPU cycles. */
        public long[] readUidCpuCycles() {
            return KernelCpuCyclePerUidBpf.readUidCpuCycles();
        }
    }

    public CpuCyclePerUidCollector(Injector injector) {
        super(injector.getHandler(), RAPL_POLL_INTERVAL_MS, injector.getUidResolver(),
                injector.getClock());
        mInjector = injector;
        mKernelCpuCycleReader = injector.getKernelCpuCycleReader();
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (isEnabled() == enabled) {
            return;
        }

        super.setEnabled(enabled);

        if (!enabled) {
            mKernelCpuCycleReader.stopTracking();
            mBpfProgramStarted = false;
        }
    }

    @Override
    public boolean isEnabled() {
        return super.isEnabled() && mKernelCpuCycleReader.isSupported();
    }

    private boolean ensureInitialized() {
        if (!isEnabled()) {
            return false;
        }
        if (mBpfProgramStarted) {
            return true;
        }

        Slog.d(TAG, "Starting BPF tracking for CPU cycle attribution");
        mBpfProgramStarted = mKernelCpuCycleReader.startTracking();
        if (mBpfProgramStarted) {
            mConsumedEnergyRetriever = mInjector.getConsumedEnergyRetriever();
            mLayout = new CpuCyclePowerStatsLayout();
            mPowerStatsDescriptor = mLayout.createDescriptor();
            mPowerStats = new PowerStats(mPowerStatsDescriptor);
        }
        return mBpfProgramStarted;
    }

    @Override
    protected PowerStats collectStats(long elapsedRealtimeMs, long uptimeMillis) {
        if (!ensureInitialized()) {
            return null;
        }

        if (mFirstCollection) {
            // Establish the initial baseline without reporting data.
            return resetBaseline(elapsedRealtimeMs);
        }

        final long durationMs = elapsedRealtimeMs - mLastCollectionRealtimeMs;

        // Discard data if the collection interval is unusually long (e.g. after resume from
        // suspend) to avoid reporting misleading power spikes. A new baseline will be established
        // by the resetBaseline call below.
        if (durationMs > RAPL_POLL_INTERVAL_MS * DURATION_TOLERANCE_UPPER_BOUND) {
            Slog.i(TAG, "Discarding data due to unusually long collection interval: "
                    + durationMs + "ms");
            return resetBaseline(elapsedRealtimeMs);
        }

        final long[] attributedPower = mKernelCpuCycleReader.readUidPowerDelta();
        if (attributedPower == null || attributedPower.length == 0
                || attributedPower.length % 2 != 0) {
            // This indicates an error or an anomaly in the native layer (e.g., zero cycle
            // delta over a valid interval). Stop polling to prevent incorrect reporting.
            return null;
        }

        mLastCollectionRealtimeMs = elapsedRealtimeMs;

        int voltageMv = mConsumedEnergyRetriever.getVoltageMv();
        if (voltageMv <= 0) {
            Slog.wtf(TAG, "Unexpected battery voltage (" + voltageMv
                    + " mV) when querying energy consumers");
            return null;
        }

        int averageVoltage = mLastVoltageMv != 0 ? (mLastVoltageMv + voltageMv) / 2 : voltageMv;
        mLastVoltageMv = voltageMv;

        mPowerStats.durationMs = durationMs;
        mPowerStats.uidStats.clear();
        long totalPowerUc = 0;
        for (int i = 0; i < attributedPower.length; i += 2) {
            final int uid = (int) attributedPower[i];
            final long powerUj = attributedPower[i + 1];

            if (powerUj > 0) {
                long powerUc = uJtoUc(powerUj, averageVoltage);
                totalPowerUc += powerUc;
                long[] uidStats = mPowerStats.uidStats.get(uid);
                if (uidStats == null) {
                    uidStats = new long[mPowerStatsDescriptor.uidStatsArrayLength];
                    mPowerStats.uidStats.put(uid, uidStats);
                }
                mLayout.setUidCpuEnergy(uidStats,
                        mLayout.getUidCpuEnergy(uidStats) + powerUc);
            }
        }
        mLayout.setDeviceCpuEnergy(mPowerStats.stats, totalPowerUc);
        return mPowerStats;
    }

    private PowerStats resetBaseline(long elapsedRealtimeMs) {
        mKernelCpuCycleReader.readUidPowerDelta();
        mFirstCollection = false;
        mLastCollectionRealtimeMs = elapsedRealtimeMs;
        mPowerStats.uidStats.clear();
        mLayout.setDeviceCpuEnergy(mPowerStats.stats, 0);
        return mPowerStats;
    }

    /**
     * Returns the descriptor of PowerStats produced by this collector.
     * @hide
     */
    @com.android.internal.annotations.VisibleForTesting
    public PowerStats.Descriptor getPowerStatsDescriptor() {
        ensureInitialized();
        return mPowerStatsDescriptor;
    }

    /**
     * Collects and delivers stats, returning true if successful (stats were generated).
     */
    public boolean updateStats(long elapsedRealtimeMs, long uptimeMs) {
        if (!ensureInitialized()) {
            return false;
        }

        if (mLastAutoPollRealtimeMs == 0) {
            mLastAutoPollRealtimeMs = elapsedRealtimeMs;
        } else {
            final long timeSinceLastAutoPoll = elapsedRealtimeMs - mLastAutoPollRealtimeMs;
            if (timeSinceLastAutoPoll < RAPL_POLL_INTERVAL_MS * DURATION_TOLERANCE_LOWER_BOUND) {
                // The handler fired too early. Skip this poll to avoid quantization errors.
                return false;
            }
            mLastAutoPollRealtimeMs = elapsedRealtimeMs;
        }

        PowerStats stats = collectStats(elapsedRealtimeMs, uptimeMs);
        if (stats == null) {
            return false;
        }
        deliverStats(stats, elapsedRealtimeMs, uptimeMs);
        return true;
    }

    private Runnable mPollingTask;

    @Override
    public boolean schedule() {
        if (isEnabled()) {
            if (mPollingTask == null) {
                mPollingTask = new CpuCyclePerUidPollingTask();
                getHandler().post(mPollingTask);
            }
            return true;
        }
        return false;
    }

    private class CpuCyclePerUidPollingTask implements Runnable {
        private int mConsecutiveFailures = 0;

        @Override
        public void run() {
            if (isEnabled()) {
                boolean success = updateStats(
                        getClock().elapsedRealtime(), getClock().uptimeMillis());

                if (success) {
                    mConsecutiveFailures = 0;
                    getHandler().postDelayed(this, RAPL_POLL_INTERVAL_MS);
                } else {
                    // RAPL devices are created slightly after the boot. The system_server may try
                    // to read it too early so add the retry logic here.
                    mConsecutiveFailures++;
                    if (mConsecutiveFailures <= 5) {
                        Slog.w(TAG, "Failed to collect CPU energy stats (failure "
                                + mConsecutiveFailures + "). Retrying...");
                        getHandler().postDelayed(this, RAPL_POLL_INTERVAL_MS);
                    } else {
                        Slog.e(TAG, "Failed to collect CPU energy stats after multiple retries."
                                + " Disabling polling.");
                    }
                }
            }
        }
    }

    /**
     * Dumps the current state of the collector.
     */
    @Override
    public void dumpLocked(PrintWriter pw) {
        pw.println("CpuCyclePerUidCollector:");
        pw.print("x86_cpu_energy_attribution flag: ");
        pw.println(Flags.x86CpuEnergyAttribution());
        pw.print("isEnabled: ");
        pw.println(isEnabled());
        pw.print("mBpfProgramStarted: ");
        pw.println(mBpfProgramStarted);
        pw.print("mFirstCollection: ");
        pw.println(mFirstCollection);
        pw.print("mLastCollectionRealtimeMs: ");
        pw.println(mLastCollectionRealtimeMs);
        pw.print("KernelCpuCycleReader.isSupported: ");
        pw.println(mKernelCpuCycleReader.isSupported());

        if (mBpfProgramStarted) {
            pw.print("Package Power: ");
            pw.println(mKernelCpuCycleReader.readPackagePower());
            pw.print("Last Recorded Cycle: ");
            pw.println(mKernelCpuCycleReader.readLastRecordedCycle());
            pw.print("Desync Count: ");
            pw.println(mKernelCpuCycleReader.readDesyncCount());
            long[] uidCpuCycles = mKernelCpuCycleReader.readUidCpuCycles();
            pw.print("UID CPU Cycles: ");
            pw.println(Arrays.toString(uidCpuCycles));
        }
    }
}
