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

package com.android.systemui.qs.panels.ui.viewmodel

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.android.systemui.qs.panels.ui.model.EditModeTab

@Stable
class EditModeTabsViewModel {
    var selectedTabIndex: Int by mutableIntStateOf(0)
        private set

    val selectedTab: EditModeTab by derivedStateOf { Tabs[selectedTabIndex] }

    fun selectTab(index: Int) {
        selectedTabIndex = index.coerceIn(0, Tabs.lastIndex)
    }

    companion object {
        @JvmField
        val Tabs: List<EditModeTab> = listOf(EditModeTab.EditingTab, EditModeTab.LayoutTab)
    }
}
