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



/**
 * Logger helper for reporting Package Installer metrics to StatsD.
 *
 * <p>This class wraps the {@link PackageInstallerStatsLog} calls to ensure consistent logging
 * of Package Installer Activity (PIA) install stages.
 */
public class StatsdLogger {

    /**
     * Logs the reported statistics for a specific install stage.
     *
     * <p>Writes the {@code PIA_INSTALL_STAGES_REPORTED} atom to the stats log.
     *
     * @param stats The statistics object containing session ID, APK size, stage, and latency.
     */
    public void logInstallStageStats(PiaInstallStagesReportedStats stats) {
        PackageInstallerStatsLog.write(
                PackageInstallerStatsLog.PIA_INSTALL_STAGES_REPORTED,
                stats.sessionId(),
                stats.apkSize(),
                stats.installStages()
                        .stream()
                        .mapToInt(Integer::intValue)
                        .toArray(),
                stats.installStagesLatency()
                        .stream()
                        .mapToInt(Integer::intValue)
                        .toArray());
    };
}
