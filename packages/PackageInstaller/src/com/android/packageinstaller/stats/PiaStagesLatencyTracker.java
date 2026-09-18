/*
 * Copyright 2026 The Android Open Source Project
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

package com.android.packageinstaller.stats;

import static com.android.packageinstaller.stats.StatsUtil.PIA_INSTALL_STAGE_UNKNOWN;

import android.os.SystemClock;

import com.android.packageinstaller.stats.StatsUtil.PiaInstallStage;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for tracking and reporting latency of Package Installer Activity (PIA) stages.
 */
public class PiaStagesLatencyTracker {

    private @PiaInstallStage int mCurrentStage;
    private long mCurrentStageStartTime;
    private int mSessionId;
    private int mApkSize;

    private final StatsdLogger mPiaLogger;

    List<Integer> mStagesList;

    List<Integer> mStagesLatencyList;

    /**
     * Initializes the latency tracker.
     *
     * @param apkSize The size of the APK being installed, in bytes.
     */
    public PiaStagesLatencyTracker(
            int apkSize, StatsdLogger statsdLogger) {
        mPiaLogger = statsdLogger;
        mStagesList = new ArrayList<>();
        mStagesLatencyList = new ArrayList<>();
        mApkSize = apkSize;
        mCurrentStage = PIA_INSTALL_STAGE_UNKNOWN;
    }

    public PiaStagesLatencyTracker(StatsdLogger statsdLogger) {
        mPiaLogger = statsdLogger;
        mStagesList = new ArrayList<>();
        mStagesLatencyList = new ArrayList<>();
    }

    /**
     * Sets or updates the installation session ID.
     * <p>
     * This can be called after initialization since the session ID might be
     * generated dynamically later in the flow for URI-based installs.
     *
     * @param sessionId The ID of the current installation session.
     */
    public void setSessionId(int sessionId) {
        mSessionId = sessionId;
    }

    public void setApkSize(int apkSize) {
        mApkSize = apkSize;
    }

    /**
     * Ends the currently active stage (if any), records its latency, and begins tracking
     * the specified next stage.
     *
     * @param newStage The next {@link PiaInstallStage} to begin recording.
     */
    public void startRecordingNextStage(@PiaInstallStage int newStage) {
        endAndStorePreviousStage();
        mCurrentStageStartTime = SystemClock.elapsedRealtime();
        mCurrentStage = newStage;
    }

    /**
     * Calculates the duration of the currently active stage and stores the stage
     * along with its latency in the tracking lists.
     */
    private void endAndStorePreviousStage() {
        if (mCurrentStageStartTime == 0) {
            return;
        }
        long currentTime = SystemClock.elapsedRealtime();
        long currentStageDuration = currentTime - mCurrentStageStartTime;
        mStagesList.add(mCurrentStage);
        mStagesLatencyList.add((int) currentStageDuration);
    }

    /**
     * Ends the final active stage and logs the aggregated stages and their respective
     * latencies to the StatsD logger.
     */
    public void stopRecordingAndLog() {
        if (mCurrentStageStartTime == PIA_INSTALL_STAGE_UNKNOWN) {
            return;
        }
        endAndStorePreviousStage();

        PiaInstallStagesReportedStats stats = PiaInstallStagesReportedStats.builder()
                .setSessionId(mSessionId)
                .setApkSize(mApkSize)
                .setInstallStages(mStagesList)
                .setInstallStagesLatency(mStagesLatencyList)
                .build();
        mPiaLogger.logInstallStageStats(stats);
    }
}
