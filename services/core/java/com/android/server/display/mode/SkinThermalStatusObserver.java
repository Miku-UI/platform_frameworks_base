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

package com.android.server.display.mode;

import android.annotation.Nullable;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.IThermalEventListener;
import android.os.Temperature;
import android.util.Slog;
import android.util.SparseArray;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.SurfaceControl;
import android.view.SurfaceControl.WorkDuration;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.BackgroundThread;
import com.android.server.display.DisplayDeviceConfig;
import com.android.server.display.config.ThermalThrottlingData;
import com.android.server.display.feature.flags.Flags;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class SkinThermalStatusObserver extends IThermalEventListener.Stub implements
        DisplayManager.DisplayListener {
    private static final String TAG = "SkinThermalStatusObserver";

    private final VotesStorage mVotesStorage;
    private final DisplayModeDirector.Injector mInjector;

    private boolean mLoggingEnabled;

    private final Handler mHandler;

    private final DisplayModeDirector.DisplayDeviceConfigProvider mDisplayDeviceConfigProvider;
    private final Object mThermalObserverLock = new Object();
    @GuardedBy("mThermalObserverLock")
    @Temperature.ThrottlingStatus
    private int mStatus = Temperature.THROTTLING_NONE;
    @GuardedBy("mThermalObserverLock")
    private final SparseArray<SparseArray<SurfaceControl.RefreshRateRange>>
            mThermalThrottlingByDisplay = new SparseArray<>();

    @GuardedBy("mThermalObserverLock")
    private final SparseArray<SparseArray<SurfaceControl.WorkDuration>>
            mThermalWorkDurationsByDisplay = new SparseArray<>();
    public SparseArray<Map<String, SparseArray<WorkDuration>>> workDurations =
            new SparseArray<>();

    SkinThermalStatusObserver(DisplayModeDirector.Injector injector,
                              VotesStorage votesStorage,
                              DisplayModeDirector.DisplayDeviceConfigProvider
                                      displayDeviceConfigProvider) {
        this(injector, votesStorage, displayDeviceConfigProvider, BackgroundThread.getHandler());
    }

    @VisibleForTesting
    SkinThermalStatusObserver(DisplayModeDirector.Injector injector,
                              VotesStorage votesStorage,
                              DisplayModeDirector.DisplayDeviceConfigProvider
                                      displayDeviceConfigProvider, Handler handler) {
        mInjector = injector;
        mVotesStorage = votesStorage;
        mHandler = handler;
        mDisplayDeviceConfigProvider = displayDeviceConfigProvider;
    }

    @Nullable
    public static SurfaceControl.RefreshRateRange findBestMatchingRefreshRateRange(
            @Temperature.ThrottlingStatus int currentStatus,
            SparseArray<SurfaceControl.RefreshRateRange> throttlingMap) {
        if (throttlingMap == null || throttlingMap.size() == 0) {
            if (currentStatus >= Temperature.THROTTLING_CRITICAL) {
                return new SurfaceControl.RefreshRateRange(0f, 60f); // default values
            } else {
                return null;
            }
        }

        SurfaceControl.RefreshRateRange foundRange = null;
        for (int status = currentStatus; status >= 0; status--) {
            foundRange = throttlingMap.get(status);
            if (foundRange != null) {
                break;
            }
        }
        return foundRange;
    }

    @Nullable
    public static SurfaceControl.WorkDuration findBestMatchingWorkDurations(
            @Temperature.ThrottlingStatus int currentStatus,
            SparseArray<SurfaceControl.WorkDuration> throttlingWorkDurationsMap) {
        SurfaceControl.WorkDuration foundWorkDurations = null;
        if (throttlingWorkDurationsMap != null) {
            for (int status = currentStatus; status >= 0; status--) {
                foundWorkDurations = throttlingWorkDurationsMap.get(status);
                if (foundWorkDurations != null) {
                    break;
                }
            }
        }
        return foundWorkDurations;
    }

    void observe() {
        // if failed to register thermal service listener, don't register display listener
        if (!mInjector.registerThermalEventListener(this)) {
            return;
        }
        registerDisplayListener();
        populateInitialDisplayInfo();
    }

    private void registerDisplayListener() {
        mInjector.registerDisplayListener(this, mHandler,
                DisplayManager.EVENT_TYPE_DISPLAY_ADDED
                        | DisplayManager.EVENT_TYPE_DISPLAY_CHANGED
                        | DisplayManager.EVENT_TYPE_DISPLAY_REMOVED);
    }

    void setLoggingEnabled(boolean enabled) {
        mLoggingEnabled = enabled;
    }

    @Override
    public void notifyThrottling(Temperature temp) {
        @Temperature.ThrottlingStatus int currentStatus = temp.getStatus();

        synchronized (mThermalObserverLock) {
            if (mStatus == currentStatus) {
                return; // status not changed, skip update
            }
            mStatus = currentStatus;
            mHandler.post(this::updateVotes);
        }

        if (mLoggingEnabled) {
            Slog.d(TAG, "New thermal throttling status " + ", current thermal status = "
                    + currentStatus);
        }
    }

    //region DisplayManager.DisplayListener
    @Override
    public void onDisplayAdded(int displayId) {
        updateThermalThrottlingConfigs(displayId);
        if (mLoggingEnabled) {
            Slog.d(TAG, "Display added:" + displayId);
        }
    }

    @Override
    public void onDisplayRemoved(int displayId) {
        synchronized (mThermalObserverLock) {
            mThermalThrottlingByDisplay.remove(displayId);
            mHandler.post(() -> mVotesStorage.updateVote(displayId,
                    Vote.PRIORITY_SKIN_TEMPERATURE, null));
        }
        if (mLoggingEnabled) {
            Slog.d(TAG, "Display removed and voted: displayId=" + displayId);
        }
    }

    @Override
    public void onDisplayChanged(int displayId) {
        updateThermalThrottlingConfigs(displayId);
        if (mLoggingEnabled) {
            Slog.d(TAG, "Display changed:" + displayId);
        }
    }
    //endregion

    private void populateInitialDisplayInfo() {
        DisplayInfo info = new DisplayInfo();
        Display[] displays = mInjector.getDisplays();
        int size = displays.length;
        SparseArray<SparseArray<SurfaceControl.RefreshRateRange>> localRefreshRateRangesMap =
                new SparseArray<>(size);
        for (Display d : displays) {
            final int displayId = d.getDisplayId();
            d.getDisplayInfo(info);
            localRefreshRateRangesMap.put(displayId, info.thermalRefreshRateThrottling);
        }
        synchronized (mThermalObserverLock) {
            for (int i = 0; i < size; i++) {
                mThermalThrottlingByDisplay.put(localRefreshRateRangesMap.keyAt(i),
                        localRefreshRateRangesMap.valueAt(i));
            }
        }
        if (mLoggingEnabled) {
            Slog.d(TAG, "Display initial info:" + localRefreshRateRangesMap);
        }
    }

    private void updateThermalThrottlingConfigs(int displayId) {
        DisplayInfo displayInfo = new DisplayInfo();
        mInjector.getDisplayInfo(displayId, displayInfo);
        SparseArray<SurfaceControl.RefreshRateRange> throttlingMap =
                displayInfo.thermalRefreshRateThrottling;

        SparseArray<SurfaceControl.WorkDuration> workDurationMap = new SparseArray<>();
        if (Flags.enableWorkDurations()) {
            workDurationMap = getThermalWorkDurations(displayId,
                    displayInfo.thermalBrightnessThrottlingDataId);
        }

        synchronized (mThermalObserverLock) {
            mThermalThrottlingByDisplay.put(displayId, throttlingMap);
            mThermalWorkDurationsByDisplay.put(displayId, workDurationMap);
            mHandler.post(() -> updateVoteForDisplay(displayId));
        }
        if (mLoggingEnabled) {
            Slog.d(TAG,
                    "Thermal throttling updated: display=" + displayId + ", map=" + throttlingMap);
        }
    }

    //region in mHandler thread
    private void updateVotes() {
        @Temperature.ThrottlingStatus int localStatus;
        SparseArray<SparseArray<SurfaceControl.RefreshRateRange>> localRefreshRateRangesMap;
        SparseArray<SparseArray<SurfaceControl.WorkDuration>> localWorkDurationsMap;

        synchronized (mThermalObserverLock) {
            localStatus = mStatus;
            localRefreshRateRangesMap = mThermalThrottlingByDisplay.clone();
            localWorkDurationsMap = mThermalWorkDurationsByDisplay.clone();
        }
        if (mLoggingEnabled) {
            Slog.d(TAG, "Updating votes for status=" + localStatus + ","
                    + " refreshRateRangesMap=" + localRefreshRateRangesMap
                    + ", workDurationsMap=" + localWorkDurationsMap);
        }

        Set<Integer> allIds = new HashSet<>();
        for (int i = 0; i < localRefreshRateRangesMap.size(); i++) {
            allIds.add(localRefreshRateRangesMap.keyAt(i));
        }
        for (int i = 0; i < localWorkDurationsMap.size(); i++) {
            allIds.add(localWorkDurationsMap.keyAt(i));
        }

        for (int displayId : allIds) {
            reportThrottlingIfNeeded(displayId, localStatus,
                    localRefreshRateRangesMap.get(displayId), localWorkDurationsMap.get(displayId));
        }
    }

    private void updateVoteForDisplay(int displayId) {
        @Temperature.ThrottlingStatus int localStatus;
        SparseArray<SurfaceControl.RefreshRateRange> localRefreshRateRangesMap;
        SparseArray<SurfaceControl.WorkDuration> localWorkDurationsMap;

        synchronized (mThermalObserverLock) {
            localStatus = mStatus;
            localRefreshRateRangesMap = mThermalThrottlingByDisplay.get(displayId);
            localWorkDurationsMap = mThermalWorkDurationsByDisplay.get(displayId);
        }
        if (localRefreshRateRangesMap == null) {
            Slog.d(TAG, "Updating votes, display already removed, display=" + displayId);
            return;
        }
        if (mLoggingEnabled) {
            Slog.d(TAG, "Updating votes for status=" + localStatus + ", display =" + displayId
                    + ", refreshRateRangesMap=" + localRefreshRateRangesMap
                    + ", workDurationsMap=" + localWorkDurationsMap);
        }
        reportThrottlingIfNeeded(displayId, localStatus, localRefreshRateRangesMap,
                localWorkDurationsMap);
    }

    private void reportThrottlingIfNeeded(int displayId,
                                          @Temperature.ThrottlingStatus int currentStatus,
                                          @Nullable SparseArray<SurfaceControl.RefreshRateRange>
                                                  throttlingMap,
                                          @Nullable SparseArray<SurfaceControl.WorkDuration>
                                                  throttlingWorkDurationsMap) {
        if (currentStatus == -1) { // no throttling status reported from thermal sensor yet
            return;
        }

        SurfaceControl.RefreshRateRange foundRange = findBestMatchingRefreshRateRange(currentStatus,
                throttlingMap);
        SurfaceControl.WorkDuration foundWorkDurations =
                findBestMatchingWorkDurations(currentStatus, throttlingWorkDurationsMap);

        Vote vote = Vote.forVotes(Arrays.asList(Vote.forRenderFrameRates(foundRange),
                Vote.forWorkDurations(foundWorkDurations)));
        mVotesStorage.updateVote(displayId, Vote.PRIORITY_SKIN_TEMPERATURE, vote);
        if (mLoggingEnabled) {
            Slog.d(TAG, "Voted: vote=" + vote + ", display =" + displayId);
        }
    }

    private SparseArray<SurfaceControl.WorkDuration> getThermalWorkDurations(
            int displayId, String thermalThrottlingId) {
        DisplayDeviceConfig config = mDisplayDeviceConfigProvider != null
                ? mDisplayDeviceConfigProvider.getDisplayDeviceConfig(displayId)
                : null;
        ThermalThrottlingData thermalThrottlingData = config != null
                ? config.getThermalThrottlingData()
                : null;
        Map<String, SparseArray<SurfaceControl.WorkDuration>> workDurationMap =
                thermalThrottlingData != null
                        ? thermalThrottlingData.getThermalThrottlingWorkDurations()
                        : null;
        return workDurationMap != null ? workDurationMap.get(thermalThrottlingId) : null;
    }
    //endregion

    void dumpLocked(PrintWriter writer) {
        @Temperature.ThrottlingStatus int localStatus;
        SparseArray<SparseArray<SurfaceControl.RefreshRateRange>> localRefreshRateRangesMap;
        SparseArray<SparseArray<SurfaceControl.WorkDuration>> localWorkDurationsMap;

        synchronized (mThermalObserverLock) {
            localStatus = mStatus;
            localRefreshRateRangesMap = mThermalThrottlingByDisplay.clone();
            localWorkDurationsMap = mThermalWorkDurationsByDisplay.clone();
        }

        writer.println("  SkinThermalStatusObserver:");
        writer.println("    mStatus: " + localStatus);
        writer.println("    mThermalThrottlingByDisplay: " + localRefreshRateRangesMap);
        writer.println("    mThermalWorkDurationsByDisplay: " + localWorkDurationsMap);
    }
}
