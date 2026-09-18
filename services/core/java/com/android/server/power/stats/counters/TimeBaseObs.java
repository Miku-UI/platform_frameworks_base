/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.power.stats.counters;

import android.os.SystemClock;

/**
 * Interface for observers that need to be notified of TimeBase state changes.
 */
public interface TimeBaseObs {
    /**
     * Called when the time base starts running.
     *
     * @param elapsedRealtimeUs Current elapsed realtime in microseconds.
     * @param baseUptimeUs Current time base uptime in microseconds.
     * @param baseRealtimeUs Current time base realtime in microseconds.
     */
    void onTimeStarted(long elapsedRealtimeUs, long baseUptimeUs, long baseRealtimeUs);

    /**
     * Called when the time base stops running.
     *
     * @param elapsedRealtimeUs Current elapsed realtime in microseconds.
     * @param baseUptimeUs Current time base uptime in microseconds.
     * @param baseRealtimeUs Current time base realtime in microseconds.
     */
    void onTimeStopped(long elapsedRealtimeUs, long baseUptimeUs, long baseRealtimeUs);

    /**
     * Reset the observer's state, returns true if the timer/counter is inactive
     * so it can be destroyed.
     * @param detachIfReset detach if true, no-op if false.
     * @return Returns true if the timer/counter is inactive and can be destroyed.
     */
    default boolean reset(boolean detachIfReset) {
        return reset(detachIfReset, SystemClock.elapsedRealtime() * 1000);
    }

    /**
     * @see #reset(boolean)
     * @param detachIfReset detach if true, no-op if false.
     * @param elapsedRealtimeUs the timestamp when this reset is actually reequested
     * @return Returns true if the timer/counter is inactive and can be destroyed.
     */
    boolean reset(boolean detachIfReset, long elapsedRealtimeUs);

    /**
     * Detach the observer from TimeBase.
     */
    void detach();
}
