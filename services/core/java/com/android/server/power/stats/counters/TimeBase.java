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

import static android.os.BatteryStats.STATS_SINCE_CHARGED;

import android.os.Parcel;

import com.android.server.power.stats.BatteryStatsImpl;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

/**
 * Manages a time base for tracking durations under specific conditions,
 * such as when the device is on battery. This class allows observers
 * to be notified of state changes (starting/stopping) and provides the
 * foundation for conditional timers used within battery related statistics.
 */
public class TimeBase {
    final Collection<TimeBaseObs> mObservers;

    // All below time metrics are in microseconds.
    long mUptimeUs;
    long mRealtimeUs;

    boolean mRunning;

    long mPastUptimeUs;
    long mUptimeStartUs;
    long mPastRealtimeUs;
    long mRealtimeStartUs;
    long mUnpluggedUptimeUs;
    long mUnpluggedRealtimeUs;

    /**
     * Dumps the current state of the TimeBase.
     * @param pw The PrintWriter to dump the state to.
     * @param prefix The prefix to use for each line.
     */
    public void dump(PrintWriter pw, String prefix) {
        StringBuilder sb = new StringBuilder(128);
        pw.print(prefix); pw.print("mRunning="); pw.println(mRunning);
        sb.setLength(0);
        sb.append(prefix);
        sb.append("mUptime=");
        BatteryStatsImpl.formatTimeMs(sb, mUptimeUs / 1000);
        pw.println(sb.toString());
        sb.setLength(0);
        sb.append(prefix);
        sb.append("mRealtime=");
        BatteryStatsImpl.formatTimeMs(sb, mRealtimeUs / 1000);
        pw.println(sb.toString());
        sb.setLength(0);
        sb.append(prefix);
        sb.append("mPastUptime=");
        BatteryStatsImpl.formatTimeMs(sb, mPastUptimeUs / 1000); sb.append("mUptimeStart=");
        BatteryStatsImpl.formatTimeMs(sb, mUptimeStartUs / 1000);
        sb.append("mUnpluggedUptime=");
        BatteryStatsImpl.formatTimeMs(sb, mUnpluggedUptimeUs / 1000);
        pw.println(sb.toString());
        sb.setLength(0);
        sb.append(prefix);
        sb.append("mPastRealtime=");
        BatteryStatsImpl.formatTimeMs(sb, mPastRealtimeUs / 1000); sb.append("mRealtimeStart=");
        BatteryStatsImpl.formatTimeMs(sb, mRealtimeStartUs / 1000);
        sb.append("mUnpluggedRealtime=");
        BatteryStatsImpl.formatTimeMs(sb, mUnpluggedRealtimeUs / 1000);
        pw.println(sb.toString());
    }

    /**
     * Constructor for TimeBase.
     * The mObservers of TimeBase in BatteryStatsImpl object can contain up to 20k entries.
     * The mObservers of TimeBase in BatteryStatsImpl.Uid object only contains a few or tens of
     * entries.
     * mObservers must have good performance on add(), remove(), also be memory efficient.
     * This is why we provide isLongList parameter for long and short list user cases.
     * @param isLongList If true, use HashSet for mObservers list.
     *                   If false, use ArrayList for mObservers list.
    */
    public TimeBase(boolean isLongList) {
        mObservers = isLongList ? new HashSet<>() : new ArrayList<>();
    }

    /**
     * Default constructor for TimeBase. Uses an ArrayList for observers.
     */
    public TimeBase() {
        this(false);
    }

    /**
     * Adds an observer to be notified of TimeBase state changes.
     * @param observer The observer to add.
     */
    public void add(TimeBaseObs observer) {
        mObservers.add(observer);
    }

    /**
     * Removes an observer from the TimeBase.
     * @param observer The observer to remove.
     */
    public void remove(TimeBaseObs observer) {
        mObservers.remove(observer);
    }

    /**
     * Checks if the given observer is already added to this TimeBase.
     * @param observer The observer to check.
     * @return True if the observer is present, false otherwise.
     */
    public boolean hasObserver(TimeBaseObs observer) {
        return mObservers.contains(observer);
    }

    /**
     * Initializes the TimeBase with the current uptime and realtime.
     * @param uptimeUs Current uptime in microseconds.
     * @param elapsedRealtimeUs Current elapsed realtime in microseconds.
     */
    public void init(long uptimeUs, long elapsedRealtimeUs) {
        mRealtimeUs = 0;
        mUptimeUs = 0;
        mPastUptimeUs = 0;
        mPastRealtimeUs = 0;
        mUptimeStartUs = uptimeUs;
        mRealtimeStartUs = elapsedRealtimeUs;
        mUnpluggedUptimeUs = getUptime(mUptimeStartUs);
        mUnpluggedRealtimeUs = getRealtime(mRealtimeStartUs);
    }

    /**
     * Resets the TimeBase. If the time base is running, it resets the start times.
     * Otherwise, it clears the accumulated past times.
     * @param uptimeUs Current uptime in microseconds.
     * @param elapsedRealtimeUs Current elapsed realtime in microseconds.
     */
    public void reset(long uptimeUs, long elapsedRealtimeUs) {
        if (!mRunning) {
            mPastUptimeUs = 0;
            mPastRealtimeUs = 0;
        } else {
            mUptimeStartUs = uptimeUs;
            mRealtimeStartUs = elapsedRealtimeUs;
            // TODO: Since mUptimeStartUs was just reset and we are running, getUptime will
            // just return mPastUptimeUs. Also, are we sure we don't want to reset that?
            mUnpluggedUptimeUs = getUptime(uptimeUs);
            // TODO: likewise.
            mUnpluggedRealtimeUs = getRealtime(elapsedRealtimeUs);
        }
    }

    /**
     * Computes the total uptime for the given time base.
     * @param curTimeUs Current uptime in microseconds.
     * @param which The time period to compute for (e.g., STATS_SINCE_CHARGED).
     * @return Total uptime in microseconds.
     */
    public long computeUptime(long curTimeUs, int which) {
        return mUptimeUs + getUptime(curTimeUs);
    }

    /**
     * Computes the total realtime for the given time base.
     * @param curTimeUs Current elapsed realtime in microseconds.
     * @param which The time period to compute for (e.g., STATS_SINCE_CHARGED).
     * @return Total realtime in microseconds.
     */
    public long computeRealtime(long curTimeUs, int which) {
        return mRealtimeUs + getRealtime(curTimeUs);
    }

    /**
     * Gets the current accumulated uptime on this time base.
     * @param curTimeUs Current uptime in microseconds.
     * @return Accumulated uptime in microseconds.
     */
    public long getUptime(long curTimeUs) {
        long time = mPastUptimeUs;
        if (mRunning) {
            time += curTimeUs - mUptimeStartUs;
        }
        return time;
    }

    /**
     * Gets the current accumulated realtime on this time base.
     * @param curTimeUs Current elapsed realtime in microseconds.
     * @return Accumulated realtime in microseconds.
     */
    public long getRealtime(long curTimeUs) {
        long time = mPastRealtimeUs;
        if (mRunning) {
            time += curTimeUs - mRealtimeStartUs;
        }
        return time;
    }

    /**
     * Gets the uptime start time.
     * @return Uptime start time in microseconds.
     */
    public long getUptimeStart() {
        return mUptimeStartUs;
    }

    /**
     * Gets the realtime start time.
     * @return Realtime start time in microseconds.
     */
    public long getRealtimeStart() {
        return mRealtimeStartUs;
    }

    /**
     * Checks if the TimeBase is currently running.
     * @return True if running, false otherwise.
     */
    public boolean isRunning() {
        return mRunning;
    }

    /**
     * Sets the running state of the time base and notifies observers.
     * @param running True if the time base should be running, false otherwise.
     * @param uptimeUs Current uptime in microseconds.
     * @param elapsedRealtimeUs Current elapsed realtime in microseconds.
     * @return True if the running state changed, false otherwise.
     */
    public boolean setRunning(boolean running, long uptimeUs, long elapsedRealtimeUs) {
        if (mRunning != running) {
            mRunning = running;
            if (running) {
                mUptimeStartUs = uptimeUs;
                mRealtimeStartUs = elapsedRealtimeUs;
                mUnpluggedUptimeUs = getUptime(uptimeUs);
                final long batteryUptimeUs = mUnpluggedUptimeUs;
                mUnpluggedRealtimeUs = getRealtime(elapsedRealtimeUs);
                final long batteryRealtimeUs = mUnpluggedRealtimeUs;
                // Normally we do not use Iterator in framework code to avoid alloc/dealloc
                // Iterator object, here is an exception because mObservers' type is Collection
                // instead of list.
                final Iterator<TimeBaseObs> iter = mObservers.iterator();
                while (iter.hasNext()) {
                    iter.next().onTimeStarted(
                            elapsedRealtimeUs, batteryUptimeUs, batteryRealtimeUs);
                }
            } else {
                mPastUptimeUs += uptimeUs - mUptimeStartUs;
                mPastRealtimeUs += elapsedRealtimeUs - mRealtimeStartUs;
                final long batteryUptimeUs = getUptime(uptimeUs);
                final long batteryRealtimeUs = getRealtime(elapsedRealtimeUs);
                // Normally we do not use Iterator in framework code to avoid alloc/dealloc
                // Iterator object, here is an exception because mObservers' type is Collection
                // instead of list.
                final Iterator<TimeBaseObs> iter = mObservers.iterator();
                while (iter.hasNext()) {
                    iter.next().onTimeStopped(
                            elapsedRealtimeUs, batteryUptimeUs, batteryRealtimeUs);
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Reads the summary from a Parcel.
     * @param in The Parcel to read from.
     */
    public void readSummaryFromParcel(Parcel in) {
        mUptimeUs = in.readLong();
        mRealtimeUs = in.readLong();
    }

    /**
     * Writes the summary to a Parcel.
     * @param out The Parcel to write to.
     * @param uptimeUs Current uptime in microseconds.
     * @param elapsedRealtimeUs Current elapsed realtime in microseconds.
     */
    public void writeSummaryToParcel(Parcel out, long uptimeUs, long elapsedRealtimeUs) {
        out.writeLong(computeUptime(uptimeUs, STATS_SINCE_CHARGED));
        out.writeLong(computeRealtime(elapsedRealtimeUs, STATS_SINCE_CHARGED));
    }

    /**
     * Reads the full state from a Parcel.
     * @param in The Parcel to read from.
     */
    public void readFromParcel(Parcel in) {
        mRunning = false;
        mUptimeUs = in.readLong();
        mPastUptimeUs = in.readLong();
        mUptimeStartUs = in.readLong();
        mRealtimeUs = in.readLong();
        mPastRealtimeUs = in.readLong();
        mRealtimeStartUs = in.readLong();
        mUnpluggedUptimeUs = in.readLong();
        mUnpluggedRealtimeUs = in.readLong();
    }

    /**
     * Writes the full state to a Parcel.
     * @param out The Parcel to write to.
     * @param uptimeUs Current uptime in microseconds.
     * @param elapsedRealtimeUs Current elapsed realtime in microseconds.
     */
    public void writeToParcel(Parcel out, long uptimeUs, long elapsedRealtimeUs) {
        final long runningUptime = getUptime(uptimeUs);
        final long runningRealtime = getRealtime(elapsedRealtimeUs);
        out.writeLong(mUptimeUs);
        out.writeLong(runningUptime);
        out.writeLong(mUptimeStartUs);
        out.writeLong(mRealtimeUs);
        out.writeLong(runningRealtime);
        out.writeLong(mRealtimeStartUs);
        out.writeLong(mUnpluggedUptimeUs);
        out.writeLong(mUnpluggedRealtimeUs);
    }
}

