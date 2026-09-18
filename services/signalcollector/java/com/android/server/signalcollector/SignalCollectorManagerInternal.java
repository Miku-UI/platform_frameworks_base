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

package com.android.server.signalcollector;

import android.os.binder.BinderCallsStats;
import android.os.binder.SingleSecondBinderStats;

/**
 * Internal interface for the SignalCollectorService.
 *
 * @hide Only for use within the system server.
 */
public abstract class SignalCollectorManagerInternal {
    /**
     * Report some binder stats that is in ~5-second granularity to the collector.
     * Between this and {@link #reportBinderStats(SingleSecondBinderStats[])}, only one will receive
     * stats.
     */
    public abstract void reportBinderStats(BinderCallsStats[] statsArray);

    /**
     * Report a list of single-second binder call stats to the collector.
     * Between this and {@link #reportBinderStats(BinderCallsStats[])}, only one will receive stats.
     */
    public abstract void reportBinderStats(SingleSecondBinderStats[] statsArray);
}
