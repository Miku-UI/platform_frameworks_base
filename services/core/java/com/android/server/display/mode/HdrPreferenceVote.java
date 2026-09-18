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

package com.android.server.display.mode;

import android.annotation.NonNull;

import java.util.Objects;

public class HdrPreferenceVote implements Vote {
    final boolean mAllowHdr;

    HdrPreferenceVote(boolean allowHdr) {
        mAllowHdr = allowHdr;
    }

    @Override
    public void updateSummary(@NonNull VoteSummary summary) {
        // If other vote has set VoteSummary#allowHdr to be false (e.g. battery, performance)
        // other vote (e.g. user preference) should not override this
        summary.allowHdr = summary.allowHdr && mAllowHdr;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HdrPreferenceVote hdrPreferenceVote)) return false;
        return mAllowHdr == hdrPreferenceVote.mAllowHdr;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAllowHdr);
    }

    @Override
    public String toString() {
        return "HdrPreferenceVote{ mAllowHdr=" + mAllowHdr + " }";
    }
}
