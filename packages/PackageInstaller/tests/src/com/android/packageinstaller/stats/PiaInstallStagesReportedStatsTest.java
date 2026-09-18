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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

@RunWith(JUnit4.class)
public class PiaInstallStagesReportedStatsTest {

    @Test
    public void testBuilderAndAccessors() {
        int sessionId = 12345;
        int apkSize = 5000000;
        List<Integer> installStages = List.of(
                StatsUtil.PIA_INSTALL_STAGE_INSTALLING,
                StatsUtil.PIA_INSTALL_STAGE_STAGING,
                StatsUtil.PIA_INSTALL_STAGE_USER_ACTION_REQUIRED);
        List<Integer> installStagesLatency = List.of(123, 456, 789);

        PiaInstallStagesReportedStats stats = PiaInstallStagesReportedStats.builder()
                .setSessionId(sessionId)
                .setApkSize(apkSize)
                .setInstallStages(installStages)
                .setInstallStagesLatency(installStagesLatency)
                .build();

        assertEquals(sessionId, stats.sessionId());
        assertEquals(apkSize, stats.apkSize());
        assertEquals(installStages, stats.installStages());
        assertEquals(installStagesLatency, stats.installStagesLatency());
    }

    @Test
    public void testEqualsAndHashCode() {
        List<Integer> installStages = List.of(
                StatsUtil.PIA_INSTALL_STAGE_INSTALLING,
                StatsUtil.PIA_INSTALL_STAGE_STAGING,
                StatsUtil.PIA_INSTALL_STAGE_USER_ACTION_REQUIRED);
        List<Integer> installStagesLatency = List.of(123, 456, 789);


        PiaInstallStagesReportedStats stats1 = PiaInstallStagesReportedStats.builder()
                .setSessionId(100)
                .setApkSize(200)
                .setInstallStages(installStages)
                .setInstallStagesLatency(installStagesLatency)
                .build();

        PiaInstallStagesReportedStats stats2 = PiaInstallStagesReportedStats.builder()
                .setSessionId(100)
                .setApkSize(200)
                .setInstallStages(installStages)
                .setInstallStagesLatency(installStagesLatency)
                .build();

        assertEquals(stats1, stats2);
        assertEquals(stats1.hashCode(), stats2.hashCode());
    }

    @Test
    public void testNotEquals() {
        List<Integer> installStages = List.of(
                StatsUtil.PIA_INSTALL_STAGE_INSTALLING,
                StatsUtil.PIA_INSTALL_STAGE_STAGING,
                StatsUtil.PIA_INSTALL_STAGE_USER_ACTION_REQUIRED);
        List<Integer> installStagesLatency = List.of(123, 456, 789);


        PiaInstallStagesReportedStats baseStats = PiaInstallStagesReportedStats.builder()
                .setSessionId(100)
                .setApkSize(200)
                .setInstallStages(installStages)
                .setInstallStagesLatency(installStagesLatency)
                .build();

        PiaInstallStagesReportedStats diffSession = PiaInstallStagesReportedStats.builder()
                .setSessionId(333)
                .setApkSize(200)
                .setInstallStages(installStages)
                .setInstallStagesLatency(installStagesLatency)
                .build();


        List<Integer> differentInstallStages = List.of(145, 831);
        // Differs by stage
        PiaInstallStagesReportedStats diffStages = PiaInstallStagesReportedStats.builder()
                .setSessionId(333)
                .setApkSize(200)
                .setInstallStages(differentInstallStages)
                .setInstallStagesLatency(installStagesLatency)
                .build();

        assertNotEquals(baseStats, diffSession);
        assertNotEquals(baseStats, diffStages);
    }
}
