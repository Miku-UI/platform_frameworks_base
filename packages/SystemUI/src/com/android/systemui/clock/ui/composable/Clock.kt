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

package com.android.systemui.clock.ui.composable

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.android.systemui.clock.ui.viewmodel.AmPmStyle
import com.android.systemui.clock.ui.viewmodel.ClockViewModel
import com.android.systemui.lifecycle.rememberViewModel

/** Composable for the clock UI that is shown on the top left of the status bar and the shade. */
@Composable
fun Clock(
    viewModelFactory: ClockViewModel.Factory,
    modifier: Modifier = Modifier,
    amPmStyle: AmPmStyle = AmPmStyle.Gone,
) {
    val clockViewModel = rememberViewModel("Clock-viewModel") { viewModelFactory.create(amPmStyle) }
    Text(
        text = clockViewModel.clockText,
        modifier = modifier.semantics { contentDescription = clockViewModel.contentDescriptionText },
    )
}
