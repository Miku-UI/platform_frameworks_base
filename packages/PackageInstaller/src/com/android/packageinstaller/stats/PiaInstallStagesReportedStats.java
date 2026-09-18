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

import com.android.packageinstaller.stats.StatsUtil.PiaInstallStage;

import java.util.List;
import java.util.Objects;

/**
 * Value class holding statistics for a specific stage of the Package Installer flow.
 *
 * <p>This class is used to encapsulate data reported to logging services regarding
 * the performance and progress of package installations.
 */
public final class PiaInstallStagesReportedStats {

    private final int mSessionId;
    private final int mApkSize;
    private final List<Integer> mInstallStages;
    private final List<Integer> mInstallStagesLatency;

    private PiaInstallStagesReportedStats(Builder builder) {
        this.mSessionId = builder.mSessionId;
        this.mApkSize = builder.mApkSize;
        this.mInstallStages = builder.mInstallStages;
        this.mInstallStagesLatency = builder.mInstallStagesLatency;
    }

    /**
     * Returns the session ID associated with this installation.
     */
    public int sessionId() {
        return mSessionId;
    }

    /**
     * Returns the size of the APK being installed, in bytes.
     */
    public int apkSize() {
        return mApkSize;
    }

    /**
     * Returns the current stage of the installation flow.
     */
    @PiaInstallStage
    public List<Integer> installStages() {
        return mInstallStages;
    }

    /**
     * Returns the latency (duration) of the current installation stage, in milliseconds.
     */
    public List<Integer> installStagesLatency() {
        return mInstallStagesLatency;
    }

    /** Returns a {@link Builder} for {@link PiaInstallStagesReportedStats}. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link PiaInstallStagesReportedStats}.
     */
    public static final class Builder {
        private int mSessionId;
        private int mApkSize;
        private List<Integer> mInstallStages;
        private List<Integer> mInstallStagesLatency;

        private Builder() {
        }

        /** Sets the session ID. */
        public Builder setSessionId(int sessionId) {
            this.mSessionId = sessionId;
            return this;
        }

        /** Sets the APK size in bytes. */
        public Builder setApkSize(int apkSize) {
            this.mApkSize = apkSize;
            return this;
        }

        /** Sets the current installation stage. */
        public Builder setInstallStages(List<Integer> installStages) {
            this.mInstallStages = installStages;
            return this;
        }

        /** Sets the latency of the current stage in milliseconds. */
        public Builder setInstallStagesLatency(List<Integer> latency) {
            this.mInstallStagesLatency = latency;
            return this;
        }

        /** Builds the {@link PiaInstallStagesReportedStats} instance. */
        public PiaInstallStagesReportedStats build() {
            return new PiaInstallStagesReportedStats(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PiaInstallStagesReportedStats)) return false;
        PiaInstallStagesReportedStats that = (PiaInstallStagesReportedStats) o;
        return mSessionId == that.mSessionId
                && mApkSize == that.mApkSize
                && Objects.equals(mInstallStages, that.mInstallStages)
                && Objects.equals(mInstallStagesLatency, that.mInstallStagesLatency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSessionId, mApkSize, mInstallStages, mInstallStagesLatency);
    }

    @Override
    public String toString() {
        return "PiaInstallStagesReportedStats{"
                + "sessionId=" + mSessionId
                + ", apkSize=" + mApkSize
                + ", installStages=" + mInstallStages
                + ", installStagesLatency=" + mInstallStagesLatency
                + '}';
    }
}
