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

package com.android.systemui.statusbar.quickactions.data.repository

import androidx.compose.runtime.mutableStateOf
import com.android.systemui.statusbar.quickactions.shared.model.QuickActionPanelModel

/** A fake implementation of [QuickActionChipsRepository] for testing. */
class FakeQuickActionChipsRepository : QuickActionChipsRepository {

    override val activePanel = mutableStateOf<QuickActionPanelModel?>(null)

    override fun setActivePanel(panel: QuickActionPanelModel?) {
        activePanel.value = panel
    }
}
