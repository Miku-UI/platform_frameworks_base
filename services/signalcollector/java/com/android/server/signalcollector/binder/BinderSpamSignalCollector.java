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

package com.android.server.signalcollector.binder;

import android.os.binder.BinderCallsStats;
import android.os.binder.SingleSecondBinderStats;

import com.android.os.profiling.anomaly.collector.SignalCollector;
import com.android.os.profiling.anomaly.collector.binder.BinderSpamConfigList;
import com.android.os.profiling.anomaly.collector.binder.BinderSpamData;

/**
 * A signal collector class dedicated to collect binder spam signals.
 */
public abstract class BinderSpamSignalCollector
        implements SignalCollector<BinderSpamConfigList, BinderSpamData> {

    /**
     * Report some binder stats that is in ~5-second granularity.
     * Between this and {@link #onBinderStatsReported(SingleSecondBinderStats[])}, only one will
     * receive stats.
     */
    public abstract void onBinderStatsReported(BinderCallsStats[] statsArray);


    /**
     * Report some binder stats that is in the one-second granularity.
     * Between this and {@link #onBinderStatsReported(BinderCallsStats[])}, only one will receive
     * stats.
     */
    public abstract void onBinderStatsReported(SingleSecondBinderStats[] statsArray);
}
