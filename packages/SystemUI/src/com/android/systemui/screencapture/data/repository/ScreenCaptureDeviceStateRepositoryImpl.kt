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

package com.android.systemui.screencapture.data.repository

import android.content.res.Resources
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.dagger.qualifiers.UiBackground
import com.android.systemui.res.R
import com.android.systemui.statusbar.policy.ConfigurationController
import com.android.systemui.statusbar.policy.onConfigChanged
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** Provides information about the current state of the device related to screen capture. */
@SysUISingleton
class ScreenCaptureDeviceStateRepositoryImpl
@Inject
constructor(
    @param:Main private val resources: Resources,
    @param:Application private val scope: CoroutineScope,
    @param:UiBackground private val uiBackgroundDispatcher: CoroutineDispatcher,
    configurationController: ConfigurationController,
) : ScreenCaptureDeviceStateRepository {
    /** Emits `true` if the device is considered a large screen for screen capture purposes. */
    override val isLargeScreen: StateFlow<Boolean> =
        configurationController.onConfigChanged
            .onStart { emit(resources.configuration) }
            .map { resources.getBoolean(R.bool.config_enableLargeScreenScreencapture) }
            .flowOn(uiBackgroundDispatcher)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                resources.getBoolean(R.bool.config_enableLargeScreenScreencapture),
            )
}
