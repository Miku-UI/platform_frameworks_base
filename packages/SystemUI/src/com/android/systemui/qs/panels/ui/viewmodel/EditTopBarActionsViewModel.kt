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

package com.android.systemui.qs.panels.ui.viewmodel

import android.content.Intent
import com.android.systemui.common.ui.icons.Settings as SettingsIcon
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.res.R
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/** Base actions for the top app bar of [DefaultEditTileGrid]. */
class EditTopBarActionsViewModel
@AssistedInject
constructor(private val activityStarter: ActivityStarter) {
    val actions: List<EditTopBarActionViewModel> = buildList {
        if (SceneContainerFlag.isEnabled) {
            add(
                EditTopBarActionViewModel(
                    SettingsIcon,
                    R.string.qs_edit_settings,
                    {
                        val intent = Intent("com.android.settings.SHADE_SETTINGS")
                        activityStarter.startActivity(intent, /* dismissShade= */ true)
                    },
                )
            )
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(): EditTopBarActionsViewModel
    }
}
