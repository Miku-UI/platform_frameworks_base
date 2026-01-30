/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.app.jank.tests;

import android.app.jank.AppJankStats;
import android.app.jank.JankTracker;
import android.app.jank.RelativeFrameTimeHistogram;
import android.app.jank.StateTracker;
import android.os.Process;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JankUtils {
    private static final int APP_ID = Process.myUid();

    /**
     * Returns a mock AppJankStats object to be used in tests.
     */
    public static AppJankStats getAppJankStats(int appUID) {
        AppJankStats jankStats = new AppJankStats(
                /*App Uid*/appUID,
                /*Widget Id*/"test widget id",
                /*navigationComponent*/null,
                /*Widget Category*/AppJankStats.WIDGET_CATEGORY_SCROLL,
                /*Widget State*/AppJankStats.WIDGET_STATE_SCROLLING,
                /*Total Frames*/100,
                /*Janky Frames*/25,
                getOverrunHistogram()
        );
        return jankStats;
    }

    public static AppJankStats getAppJankStats() {
        return getAppJankStats(APP_ID);
    }

    /**
     * Returns a mock histogram to be used with an AppJankStats object.
     */
    public static RelativeFrameTimeHistogram getOverrunHistogram() {
        RelativeFrameTimeHistogram overrunHistogram = new RelativeFrameTimeHistogram();
        overrunHistogram.addRelativeFrameTimeMillis(-2);
        overrunHistogram.addRelativeFrameTimeMillis(1);
        overrunHistogram.addRelativeFrameTimeMillis(5);
        overrunHistogram.addRelativeFrameTimeMillis(25);
        return overrunHistogram;
    }

    /**
     * When JankStats are reported they are processed on a background thread. This method checks
     * every 100 ms up to the maxWaitTime to see if the pending stat count is greater than zero.
     * If the pending stat count is greater than zero it will return or keep trying until
     * maxWaitTime has elapsed.
     */
    public static void waitForResults(JankTracker jankTracker, int maxWaitTimeMs) {
        int currentWaitTimeMs = 0;
        int threadSleepTimeMs = 100;
        while (currentWaitTimeMs < maxWaitTimeMs) {
            try {
                Thread.sleep(threadSleepTimeMs);
                if (!jankTracker.getPendingJankStats().isEmpty()) {
                    return;
                }
                currentWaitTimeMs += threadSleepTimeMs;
            } catch (InterruptedException exception) {
                // do nothing and continue.
            }
        }
    }

    /**
     * Aggregates the different states into a single map where state type is the key and the number
     * of times the state was observed is the value.
     *
     * @param stateData data to be aggregated from JankTracker.
     * @param printKeyToLogcat if true will print the different widget states to logcat under the
     *     JANKTRACKER tag.
     * @return a Map where the key is the state and the value is the number of times that state was
     *     observed.
     */
    public static Map<String, Integer> aggregateCountsByState(
            List<StateTracker.StateData> stateData, boolean printKeyToLogcat) {
        Map<String, Integer> aggregateStateData = new HashMap<>();
        if (stateData == null) return aggregateStateData;

        for (StateTracker.StateData data : stateData) {
            if (printKeyToLogcat) {
                Log.d("JANKTRACKER", String.format("Key: %s", data.mWidgetState));
            }
            aggregateStateData.merge(data.mWidgetState, 1, Integer::sum);
        }
        return aggregateStateData;
    }

    /**
     * Aggregates the different states into a single map where state type is the key and the number
     * of times the state was observed is the value..
     *
     * @param stateData data to be aggregated from JankTracker
     * @return a Map where the key is the state and the value is the number of times that state was
     *     observed.
     */
    public static Map<String, Integer> aggregateCountsByState(
            List<StateTracker.StateData> stateData) {
        return aggregateCountsByState(stateData, false);
    }
}
