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

package com.android.systemui.notifications.intelligence.rules.ui.viewmodel

import android.content.Context
import com.android.systemui.lifecycle.Activatable

/** A top-level view model for controlling the display of the overall notification rules screen. */
public interface NotificationRulesParentViewModel : Activatable {
    /** Launches an activity to control notification rules. */
    public fun launchNotificationRulesActivity(context: Context)

    public interface Factory {
        public fun create(): NotificationRulesParentViewModel
    }
}
