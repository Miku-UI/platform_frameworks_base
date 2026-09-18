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

package com.android.server.companion.datatransfer.crossdevicesync.common;

import static com.android.internal.util.FrameworkStatsLog.CROSS_DEVICE_SYNC_EVENT;

import com.android.internal.util.FrameworkStatsLog;

/** Implementation of {@link FrameworkStatsLogProxy}. */
public class FrameworkStatsLogProxyImpl implements FrameworkStatsLogProxy {
    public FrameworkStatsLogProxyImpl() {}

    @Override
    public void logSyncEvent(int event, int feature) {
        FrameworkStatsLog.write(CROSS_DEVICE_SYNC_EVENT, event, feature);
    }

    @Override
    public void logSyncEvent(int event, int feature, long duration) {
        FrameworkStatsLog.write(CROSS_DEVICE_SYNC_EVENT, event, feature, duration);
    }
}
