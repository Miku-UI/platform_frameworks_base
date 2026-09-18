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

import com.android.systemui.kosmos.Kosmos
import com.android.systemui.plugins.activityStarter

val Kosmos.notificationRulesParentViewModel by
    Kosmos.Fixture { NotificationRulesParentViewModelImpl(activityStarter = activityStarter) }

val Kosmos.notificationRulesParentViewModelFactory by
    Kosmos.Fixture {
        object : NotificationRulesParentViewModel.Factory {
            override fun create(): NotificationRulesParentViewModel {
                return notificationRulesParentViewModel
            }
        }
    }
