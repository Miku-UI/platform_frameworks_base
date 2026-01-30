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

package com.android.systemui.statusbar.notification.stack.ui.viewbinder

import android.content.Intent
import android.provider.Settings
import com.android.systemui.lifecycle.repeatWhenAttachedToWindow
import com.android.systemui.statusbar.notification.NotificationActivityStarter
import com.android.systemui.statusbar.notification.stack.OnboardingAffordanceView
import com.android.systemui.statusbar.notification.stack.activityStarterScope
import com.android.systemui.statusbar.notification.stack.onDismissClicked
import com.android.systemui.statusbar.notification.stack.onTurnOnClicked
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.BundleOnboardingViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

class BundleOnboardingViewBinder
@Inject
constructor(private val activityStarter: NotificationActivityStarter) {
    suspend fun bind(viewModel: BundleOnboardingViewModel, view: OnboardingAffordanceView) {
        view.repeatWhenAttachedToWindow {
            launch { view.onDismissClicked.collect { viewModel.dismissAffordance() } }
            launch {
                view.onTurnOnClicked.collect {
                    view.activityStarterScope(activityStarter) {
                        startSettingsIntent(settingsIntent)
                        viewModel.dismissAffordance()
                    }
                }
            }
        }
    }
}

private val settingsIntent =
    NotificationActivityStarter.SettingsIntent(Intent(Settings.ACTION_NOTIFICATION_BUNDLES))
