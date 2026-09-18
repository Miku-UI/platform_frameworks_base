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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.notifications.intelligence.rules.ui.composable.NotificationRulesActivity
import com.android.systemui.plugins.ActivityStartOptions
import com.android.systemui.plugins.ActivityStarter
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

public class NotificationRulesParentViewModelImpl
@AssistedInject
constructor(private val activityStarter: ActivityStarter) :
    NotificationRulesParentViewModel, HydratedActivatable() {

    override fun launchNotificationRulesActivity(context: Context) {
        val rulesIntent =
            Intent().apply {
                component = ComponentName(context, NotificationRulesActivity::class.java)
            }

        activityStarter.startActivityDismissingKeyguard(
            ActivityStartOptions(intent = rulesIntent, dismissShade = true, onlyProvisioned = true)
        )
    }

    @AssistedFactory
    interface Factory : NotificationRulesParentViewModel.Factory {
        override fun create(): NotificationRulesParentViewModelImpl
    }
}
