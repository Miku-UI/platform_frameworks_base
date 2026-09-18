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

package android.os.binder;

import android.os.binder.BinderCallsStats;
import android.os.binder.BinderSpamStats;
import android.os.binder.SingleSecondBinderStats;

/**
 * Service to help collect binder stats.
 *
 * <p>Processes receiving binder transactions collect and send binder stats to this service.
 * This process then packs and forwards them to statsd.
 *
 * <p>This proxy is necessary because binder stats are collected in {@code libbinder}, which cannot
 * send data directly to the statsd socket as the {@code libstatssocket} library is not available
 * to bootstrap processes.
 *
 * <p><b>Performance:</b> The interface uses {@code oneway} methods to avoid blocking callers and
 * allows sending multiple stats in a single call to improve efficiency.
 *
 * @hide
 */
@RequiresNoPermission
interface IBinderStatsConsumerService {
    /**
     * Reports binder call stats.
     *
     * @param callStatsArray Call reports to be pushed to statsd.
     */
    oneway void reportCallStats(in BinderCallsStats[] callStatsArray);

    /**
     * Reports binder spam stats.
     *
     * @param spamStatsArray spam reports to be pushed to statsd.
     */
    oneway void reportSpamStats(in BinderSpamStats[] spamStatsArray);

    /**
     * Reports aggregated binder statistics, with each entry representing calls made within a
     * single second to a particular Binder API.
     *
     * @param singleSecondBinderStats An array of {@link PerSecondBinderStats} reports to be sent
     * to statsd.
     */
    oneway void reportSecondGranularityStats(in SingleSecondBinderStats[] singleSecondBinderStats);
}
