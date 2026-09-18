/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.server.timezonedetector;

import static com.android.server.SystemTimeZone.TIME_ZONE_CONFIDENCE_LOW;

import android.annotation.CurrentTimeMillisLong;
import android.annotation.ElapsedRealtimeLong;
import android.util.Pair;

import com.android.server.SystemTimeZone;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/** A partially implemented, fake implementation of Environment for tests. */
public class FakeEnvironment implements Environment {

    private final TestState<String> mTimeZoneId = new TestState<>();
    private final TestState<Integer> mTimeZoneConfidence = new TestState<>();
    private final List<Runnable> mAsyncRunnables = new ArrayList<>();
    private final List<Pair<Long, Runnable>> mDelayedRunnables = new ArrayList<>();
    private @ElapsedRealtimeLong long mElapsedRealtimeMillis;
    private @CurrentTimeMillisLong long mInitializationTimeMillis;

    FakeEnvironment() {
        // Ensure the fake environment starts with the defaults a fresh device would.
        initializeTimeZoneSetting("", TIME_ZONE_CONFIDENCE_LOW);
    }

    void initializeClock(
            @CurrentTimeMillisLong long currentTimeMillis,
            @ElapsedRealtimeLong long elapsedRealtimeMillis) {
        mInitializationTimeMillis = currentTimeMillis - elapsedRealtimeMillis;
        mElapsedRealtimeMillis = elapsedRealtimeMillis;
    }

    void initializeTimeZoneSetting(
            String zoneId, @SystemTimeZone.TimeZoneConfidence int timeZoneConfidence) {
        mTimeZoneId.init(zoneId);
        mTimeZoneConfidence.init(timeZoneConfidence);
    }

    void incrementClock() {
        mElapsedRealtimeMillis++;
    }

    void advanceClock(@ElapsedRealtimeLong long durationMillis) {
        mElapsedRealtimeMillis += durationMillis;
    }

    @Override
    public String getDeviceTimeZone() {
        return mTimeZoneId.getLatest();
    }

    @Override
    public int getDeviceTimeZoneConfidence() {
        return mTimeZoneConfidence.getLatest();
    }

    @Override
    public void setDeviceTimeZoneAndConfidence(
            String zoneId, @SystemTimeZone.TimeZoneConfidence int confidence, String logInfo) {
        mTimeZoneId.set(zoneId);
        mTimeZoneConfidence.set(confidence);
    }

    void assertTimeZoneNotChanged() {
        mTimeZoneId.assertHasNotBeenSet();
        mTimeZoneConfidence.assertHasNotBeenSet();
    }

    void assertTimeZoneChangedTo(
            String timeZoneId, @SystemTimeZone.TimeZoneConfidence int confidence) {
        mTimeZoneId.assertHasBeenSet();
        mTimeZoneId.assertChangeCount(1);
        mTimeZoneId.assertLatestEquals(timeZoneId);

        mTimeZoneConfidence.assertHasBeenSet();
        mTimeZoneConfidence.assertChangeCount(1);
        mTimeZoneConfidence.assertLatestEquals(confidence);
    }

    void commitAllChanges() {
        mTimeZoneId.commitLatest();
        mTimeZoneConfidence.commitLatest();
    }

    @Override
    @ElapsedRealtimeLong
    public long elapsedRealtimeMillis() {
        return mElapsedRealtimeMillis;
    }

    @Override
    @CurrentTimeMillisLong
    public long currentTimeMillis() {
        return mInitializationTimeMillis + mElapsedRealtimeMillis;
    }

    @Override
    public void addDebugLogEntry(String logMsg) {
        // No-op for tests
    }

    @Override
    public void dumpDebugLog(PrintWriter printWriter) {
        // No-op for tests
    }

    /**
     * Adds the supplied runnable to a list but does not run them. To run all the runnables that
     * have been supplied, call {@code #runAsyncRunnables}.
     */
    @Override
    public void runAsync(Runnable runnable) {
        mAsyncRunnables.add(runnable);
    }

    /**
     * Requests that the runnable that have been supplied to {@code #runAsync} are invoked
     * asynchronously and cleared.
     */
    public void runAsyncRunnables() {
        for (Runnable runnable : mAsyncRunnables) {
            runnable.run();
        }
        mAsyncRunnables.clear();
    }

    @Override
    public void postDelayed(Runnable runnable, long delayMillis) {
        removePendingRunnable(runnable);
        mDelayedRunnables.add(new Pair<>(delayMillis + elapsedRealtimeMillis(), runnable));
    }

    /**
     * Checks if there are any delayed runnables that have been supplied to {@code #postDelayed} and
     * runs them if time is up.
     */
    public void runDelayedRunnables() {
        ArrayList<Pair<Long, Runnable>> runnablesToRun = new ArrayList<>(mDelayedRunnables);
        mDelayedRunnables.clear();
        for (Pair<Long, Runnable> pair : runnablesToRun) {
            if (pair.first > elapsedRealtimeMillis()) {
                mDelayedRunnables.add(new Pair<>(pair.first, pair.second));
                continue;
            }
            pair.second.run();
        }
    }

    @Override
    public void removePendingRunnable(Runnable runnable) {
        ArrayList<Pair<Long, Runnable>> runnablesToRun = new ArrayList<>(mDelayedRunnables);
        mDelayedRunnables.clear();
        for (Pair<Long, Runnable> pair : runnablesToRun) {
            if (pair.second == runnable) {
                continue;
            }
            mDelayedRunnables.add(new Pair<>(pair.first, pair.second));
        }
    }

    public int getDelayedRunnableCount() {
        return mDelayedRunnables.size();
    }
}
