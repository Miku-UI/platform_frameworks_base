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

package com.android.systemui.statusbar.quickactions.data.repository.impl

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.quickactions.data.repository.QuickActionChipsRepository
import com.android.systemui.statusbar.quickactions.shared.model.QuickActionPanelModel
import javax.inject.Inject

@SysUISingleton
class QuickActionChipsRepositoryImpl @Inject constructor() : QuickActionChipsRepository {

    private val _activePanel = mutableStateOf<QuickActionPanelModel?>(null)
    override val activePanel: State<QuickActionPanelModel?> = _activePanel

    override fun setActivePanel(panel: QuickActionPanelModel?) {
        _activePanel.value = panel
    }
}
