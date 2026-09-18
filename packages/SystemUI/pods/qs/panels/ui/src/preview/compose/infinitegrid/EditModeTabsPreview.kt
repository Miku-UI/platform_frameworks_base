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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.android.systemui.qs.panels.ui.compose.infinitegrid.EditModeTabs.EditModeTabsColors
import com.android.systemui.qs.panels.ui.viewmodel.EditModeTabsViewModel

@Composable
@Preview
private fun EditModeTabsPreview() {
    MaterialTheme { EditModeTabsScreen() }
}

@Composable
fun EditModeTabsScreen() {
    val colors =
        EditModeTabsColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedTabColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedContentColor = MaterialTheme.colorScheme.onPrimary,
        )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AllTabsSelected(colors)
        WithReversedLayoutDirection { AllTabsSelected(colors) }
    }
}

@Composable
private fun AllTabsSelected(colors: EditModeTabsColors) {
    EditModeTabs(EditModeTabsViewModel(), colors = colors)
    EditModeTabs(EditModeTabsViewModel().apply { selectTab(1) }, colors = colors)
}

@Composable
private fun WithReversedLayoutDirection(content: @Composable () -> Unit) {
    val layoutDirection =
        when (LocalLayoutDirection.current) {
            LayoutDirection.Ltr -> LayoutDirection.Rtl
            LayoutDirection.Rtl -> LayoutDirection.Ltr
        }
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection, content = content)
}
