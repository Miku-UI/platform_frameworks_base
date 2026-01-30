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

package com.android.systemui.actioncorner.data.repository

import android.provider.Settings.Secure.ACTION_CORNER_ACTION_HOME
import android.provider.Settings.Secure.ACTION_CORNER_ACTION_LOCKSCREEN
import android.provider.Settings.Secure.ACTION_CORNER_ACTION_NOTIFICATIONS
import android.provider.Settings.Secure.ACTION_CORNER_ACTION_OVERVIEW
import android.provider.Settings.Secure.ACTION_CORNER_ACTION_QUICK_SETTINGS
import android.provider.Settings.Secure.ACTION_CORNER_BOTTOM_LEFT_ACTION
import android.provider.Settings.Secure.ACTION_CORNER_BOTTOM_RIGHT_ACTION
import android.provider.Settings.Secure.ACTION_CORNER_TOP_LEFT_ACTION
import android.provider.Settings.Secure.ACTION_CORNER_TOP_RIGHT_ACTION
import android.provider.Settings.Secure.ActionCornerActionType
import com.android.systemui.actioncorner.data.model.ActionType
import com.android.systemui.actioncorner.data.model.ActionType.HOME
import com.android.systemui.actioncorner.data.model.ActionType.LOCKSCREEN
import com.android.systemui.actioncorner.data.model.ActionType.NONE
import com.android.systemui.actioncorner.data.model.ActionType.NOTIFICATIONS
import com.android.systemui.actioncorner.data.model.ActionType.OVERVIEW
import com.android.systemui.actioncorner.data.model.ActionType.QUICK_SETTINGS
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.shared.settings.data.repository.SecureSettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Repository for action configured for each action corner.Reads corresponding settings from
 * [SecureSettingsRepository] and convert it to action type for each corner.
 */
class ActionCornerSettingRepository
@Inject
constructor(
    private val settingsRepository: SecureSettingsRepository,
    @Background private val backgroundScope: CoroutineScope,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
) {
    val topLeftCornerAction: StateFlow<ActionType> =
        getCornerActionFlow(ACTION_CORNER_TOP_LEFT_ACTION)

    val topRightCornerAction: StateFlow<ActionType> =
        getCornerActionFlow(ACTION_CORNER_TOP_RIGHT_ACTION)

    val bottomLeftCornerAction: StateFlow<ActionType> =
        getCornerActionFlow(ACTION_CORNER_BOTTOM_LEFT_ACTION)

    val bottomRightCornerAction: StateFlow<ActionType> =
        getCornerActionFlow(ACTION_CORNER_BOTTOM_RIGHT_ACTION)

    val isAnyActionConfigured: Flow<Boolean> =
        combine(
                topLeftCornerAction,
                topRightCornerAction,
                bottomLeftCornerAction,
                bottomRightCornerAction,
            ) { topLeft, topRight, bottomLeft, bottomRight ->
                listOf(topLeft, topRight, bottomLeft, bottomRight).any { action -> action != NONE }
            }
            .distinctUntilChanged()

    private fun getCornerActionFlow(settingName: String): StateFlow<ActionType> {
        return settingsRepository
            .intSetting(name = settingName)
            .map(::actionMapper)
            .flowOn(backgroundDispatcher)
            // Start it eagerly to avoid latency on reading settings when user hits the corner
            .stateIn(backgroundScope, started = SharingStarted.Eagerly, initialValue = NONE)
    }

    private fun actionMapper(@ActionCornerActionType action: Int): ActionType =
        when (action) {
            ACTION_CORNER_ACTION_HOME -> HOME
            ACTION_CORNER_ACTION_OVERVIEW -> OVERVIEW
            ACTION_CORNER_ACTION_NOTIFICATIONS -> NOTIFICATIONS
            ACTION_CORNER_ACTION_QUICK_SETTINGS -> QUICK_SETTINGS
            ACTION_CORNER_ACTION_LOCKSCREEN -> LOCKSCREEN
            else -> NONE
        }
}
