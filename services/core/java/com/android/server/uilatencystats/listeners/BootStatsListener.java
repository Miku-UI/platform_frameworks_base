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

package com.android.server.uilatencystats.listeners;

import android.uilatencystats.Event;
import android.uilatencystats.EventType;
import android.uilatencystats.UiLatencyEventListener;
import android.util.Slog;

import com.android.internal.util.FrameworkStatsLog;
import com.android.server.SystemServiceManager;

import java.util.List;

/**
 * Listener for boot-related UI latency events.
 *
 * @hide
 */
public final class BootStatsListener implements UiLatencyEventListener {
    private static final String TAG = "BootStatsListener";
    private final SystemServiceManager mSystemServiceManager;
    private boolean mHasSentLauncherShownReport = false;

    public BootStatsListener(SystemServiceManager systemServiceManager) {
        mSystemServiceManager = systemServiceManager;
    }

    private boolean hasSentLauncherShownReport() {
        // Skip if this is a runtime restart, assuming we sent it already for this boot before.
        if (mSystemServiceManager.isRuntimeRestarted()) {
            mHasSentLauncherShownReport = true;
        }
        return mHasSentLauncherShownReport;
    }

    @Override
    public List<Integer> getEventIdsToListen() {
        return List.of(EventType.EVENT_LAUNCHER_SHOWN);
    }

    @Override
    public void onEvent(Event event) {
        if (event.getType() instanceof EventType.LauncherShown) {
            if (hasSentLauncherShownReport()) {
                Slog.d(TAG, "launcher shown was already reported for this boot");
                return;
            }
            FrameworkStatsLog.write(
                    FrameworkStatsLog.BOOT_TIME_EVENT_ELAPSED_TIME_REPORTED,
                    FrameworkStatsLog.BOOT_TIME_EVENT_ELAPSED_TIME__EVENT__LAUNCHER_SHOWN,
                    event.getTimestamp());
            mHasSentLauncherShownReport = true;
            Slog.i(TAG, "reported launcher shown as " + event.getTimestamp());
        }
    }
}
