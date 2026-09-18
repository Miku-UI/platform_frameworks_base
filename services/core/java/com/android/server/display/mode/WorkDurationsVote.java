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

package com.android.server.display.mode;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.view.SurfaceControl.WorkDuration;

import java.util.Objects;

/**
 * Information about the work durations according to the device state (default, thermal throttling,
 * or low power mode) sent to surface flinger.
 */
class WorkDurationsVote implements Vote{
    @Nullable
    final WorkDuration mWorkDurationsData;

    WorkDurationsVote(@Nullable WorkDuration workDurationsData) {
        mWorkDurationsData = workDurationsData;
    }

    @Override
    public void updateSummary(@NonNull VoteSummary summary) {
        if (summary.workDurationsData == null) {
            summary.workDurationsData = mWorkDurationsData;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorkDurationsVote that)) return false;
        return Objects.equals(this.mWorkDurationsData, that.mWorkDurationsData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mWorkDurationsData);
    }

    @Override
    public String toString() {
        return "WorkDurationsVote{  mWorkDurationsData=" + mWorkDurationsData + " }";
    }

}
