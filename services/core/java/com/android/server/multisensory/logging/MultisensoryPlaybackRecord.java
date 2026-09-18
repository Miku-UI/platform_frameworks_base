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

package com.android.server.multisensory.logging;

import android.icu.text.SimpleDateFormat;
import android.os.multisensory.MultisensoryManager;

import com.android.server.multisensory.MultisensoryService;

import java.util.Locale;

/**
 * A single record of playback in the {@link MultisensoryService} that results from {@link
 * MultisensoryService#playToken(int)}.
 *
 * @hide
 */
public class MultisensoryPlaybackRecord {

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);

    public enum PlayerType {
        DEFAULT,
        REMOTE,
    }

    private final long mTimeStampMillis;
    private final @MultisensoryManager.Token int mTokenPlayed;
    private final PlayerType mPlayerUsed;

    public MultisensoryPlaybackRecord(
            @MultisensoryManager.Token int tokenPlayed,
            PlayerType playerUsed,
            long timeStampMillis) {
        mTokenPlayed = tokenPlayed;
        mPlayerUsed = playerUsed;
        mTimeStampMillis = timeStampMillis;
    }

    public PlayerType getPlayerUsed() {
        return mPlayerUsed;
    }

    private String getFormattedTimeStamp() {
        return DATE_FORMAT.format(mTimeStampMillis);
    }

    @Override
    public String toString() {
        return "MultisensoryPlaybackRecord {"
                + " time="
                + getFormattedTimeStamp()
                + " tokenPlayed="
                + mTokenPlayed
                + " playerUsed="
                + mPlayerUsed.name()
                + " }";
    }
}
