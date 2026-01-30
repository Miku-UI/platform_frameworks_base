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

package com.android.systemui.keyboard.shortcut.data.repository

import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.keyboard.shortcut.shared.model.AppShortcutCustomizationState
import com.android.systemui.keyboard.shortcut.shared.model.AppShortcutInfo
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutHelperState
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ShortcutHelperCustomizationModeRepository
@Inject
constructor(
    shortcutHelperStateRepository: ShortcutHelperStateRepository,
    @Background backgroundScope: CoroutineScope,
) {
    private val _isCustomizationModeRequested = MutableStateFlow(false)
    private val appShortcutsPreCustomizationStates =
        hashMapOf<AppShortcutInfo, AppShortcutCustomizationState>()

    val isCustomizationModeEnabled =
        combine(_isCustomizationModeRequested, shortcutHelperStateRepository.state) {
                isCustomizationModeRequested,
                shortcutHelperState ->
                isCustomizationModeRequested && shortcutHelperState is ShortcutHelperState.Active
            }
            .stateIn(scope = backgroundScope, started = SharingStarted.Lazily, initialValue = false)

    fun toggleCustomizationMode(isEnabled: Boolean) {
        _isCustomizationModeRequested.value = isEnabled

        if (!isCustomizationModeEnabled.value) {
            appShortcutsPreCustomizationStates.clear()
        }
    }

    fun updateAppShortcutPreCustomizationState(
        appShortcutInfo: AppShortcutInfo,
        previousCustomizationState: AppShortcutCustomizationState,
    ) {
        if (
            isCustomizationModeEnabled.value &&
                !appShortcutsPreCustomizationStates.contains(appShortcutInfo)
        ) {
            appShortcutsPreCustomizationStates[appShortcutInfo] = previousCustomizationState
        }
    }

    fun getPreCustomizationStateForAppShortcut(
        appShortcutInfo: AppShortcutInfo
    ): AppShortcutCustomizationState? {
        return appShortcutsPreCustomizationStates[appShortcutInfo]
    }
}
