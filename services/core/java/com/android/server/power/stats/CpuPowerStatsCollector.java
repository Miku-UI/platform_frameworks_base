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

import android.os.Handler;

import com.android.internal.os.Clock;

import java.io.PrintWriter;

/**
 * Abstract base class for collecting CPU power statistics.
 * Implementations include {@link CpuTimeInStateCollector} for ARM and
 * {@link CpuCyclePerUidCollector} for x86.
 */
public abstract class CpuPowerStatsCollector extends PowerStatsCollector {

    public CpuPowerStatsCollector(Handler handler, long throttlePeriodMs,
            PowerStatsUidResolver uidResolver, Clock clock) {
        super(handler, throttlePeriodMs, uidResolver, clock);
    }

    /**
     * Dumps the CPU power stats collector state.
     */
    public void dumpLocked(PrintWriter pw) {
    }
}
