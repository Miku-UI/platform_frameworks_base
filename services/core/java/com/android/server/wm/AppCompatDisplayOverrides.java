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

package com.android.server.wm;

import static android.content.pm.ActivityInfo.OVERRIDE_AUTO_RESTART_ON_DISPLAY_MOVE;

import static com.android.server.wm.AppCompatUtils.isChangeEnabled;

import android.annotation.NonNull;

/**
 * Encapsulates app compat configurations and overrides related to display.
 */
class AppCompatDisplayOverrides {

    @NonNull
    private final ActivityRecord mActivityRecord;

    AppCompatDisplayOverrides(@NonNull ActivityRecord activityRecord) {
        mActivityRecord = activityRecord;
    }

    /**
     * Whether the activity should be restarted when moved to a different display.
     */
    boolean shouldRestartOnDisplayMove() {
        return isChangeEnabled(mActivityRecord, OVERRIDE_AUTO_RESTART_ON_DISPLAY_MOVE);
    }
}
