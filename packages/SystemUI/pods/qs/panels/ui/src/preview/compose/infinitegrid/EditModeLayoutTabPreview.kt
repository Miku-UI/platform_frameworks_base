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

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.systemui.qs.panels.ui.compose.infinitegrid.EditModeLayoutTabDefaults.Brightness
import com.android.systemui.qs.panels.ui.compose.infinitegrid.EditModeLayoutTabDefaults.Media
import com.android.systemui.qs.panels.ui.compose.infinitegrid.EditModeLayoutTabDefaults.TilesGrid
import com.android.systemui.qs.panels.ui.viewmodel.EditModeLayoutTabViewModelImpl

@Composable
@Preview
private fun EditLayoutTabPreview() {
    // TODO(b/449675581): Use PlatformTheme {} once it works with previews or provide a new
    // PlatformThemeForPreviews {} composable.

    MaterialTheme { EditLayoutTabScreen() }
}

@Composable
fun EditLayoutTabScreen(
    viewModel: EditModeLayoutTabViewModelImpl = remember { EditModeLayoutTabViewModelImpl() }
) {
    val composer = remember { EditModeLayoutTabImpl() }
    Box {
        composer.Content(viewModel, { Brightness() }, { TilesGrid() }, { Media() }, Modifier)
        composer.DragShadow(viewModel, { Brightness() }, { TilesGrid() }, { Media() }, Modifier)
    }
}
