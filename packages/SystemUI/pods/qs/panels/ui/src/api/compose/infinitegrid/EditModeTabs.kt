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

package com.android.systemui.qs.panels.ui.compose.infinitegrid

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.android.compose.theme.LocalAndroidColorScheme
import com.android.systemui.qs.panels.ui.compose.infinitegrid.EditModeTabs.EditModeTabsColors
import com.android.systemui.qs.panels.ui.viewmodel.EditModeTabsViewModel

/** Interface for the EditModeTabs composer. */
public interface EditModeTabs {
    /** Renders the Edit mode tabs UI. */
    @Composable
    public fun Content(
        viewModel: EditModeTabsViewModel,
        colors: EditModeTabsColors,
        modifier: Modifier,
    )

    data class EditModeTabsColors(
        val containerColor: Color,
        val selectedTabColor: Color,
        val contentColor: Color,
        val selectedContentColor: Color,
    )
}

public object EditModeTabsDefaults {
    @ReadOnlyComposable
    @Composable
    public fun colors(): EditModeTabsColors {
        return EditModeTabsColors(
            containerColor = LocalAndroidColorScheme.current.surfaceEffect1,
            selectedTabColor = LocalAndroidColorScheme.current.surfaceEffect2,
            contentColor = MaterialTheme.colorScheme.primary,
            selectedContentColor = MaterialTheme.colorScheme.primary,
        )
    }
}
