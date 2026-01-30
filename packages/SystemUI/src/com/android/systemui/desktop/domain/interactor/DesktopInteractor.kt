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

package com.android.systemui.desktop.domain.interactor

import android.content.res.Resources
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.res.R
import com.android.systemui.statusbar.core.StatusBarForDesktop
import com.android.systemui.statusbar.policy.ConfigurationController
import com.android.systemui.statusbar.policy.onConfigChanged
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

@SysUISingleton
class DesktopInteractor
@Inject
constructor(
    @Main private val resources: Resources,
    @Background private val scope: CoroutineScope,
    configurationController: ConfigurationController,
) {
    /** Whether this is a desktop device for the purposes of falsing. */
    val isDesktopForFalsingPurposes: StateFlow<Boolean> =
        configurationController.onConfigChanged
            .map { resources.getBoolean(R.bool.config_isDesktopForFalsingPurposes) }
            .onStart { emit(resources.getBoolean(R.bool.config_isDesktopForFalsingPurposes)) }
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(),
                resources.getBoolean(R.bool.config_isDesktopForFalsingPurposes),
            )

    // TODO(441100057): This StateFlow should support Connected Displays.
    /** Whether showing the desktop status bar is enabled. */
    val useDesktopStatusBar: StateFlow<Boolean> =
        configurationController.onConfigChanged
            .map { resources.getBoolean(R.bool.config_useDesktopStatusBar) }
            .onStart { emit(resources.getBoolean(R.bool.config_useDesktopStatusBar)) }
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(),
                resources.getBoolean(R.bool.config_useDesktopStatusBar),
            )

    // TODO(441100057): This should support Connected Displays.
    val isNotificationShadeOnTopEnd: Boolean =
        resources.getBoolean(R.bool.config_notificationShadeOnTopEnd) &&
            StatusBarForDesktop.isEnabled
}
