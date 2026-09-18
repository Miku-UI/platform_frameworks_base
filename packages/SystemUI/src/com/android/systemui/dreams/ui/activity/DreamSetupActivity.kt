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

package com.android.systemui.dreams.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.android.compose.theme.PlatformTheme
import com.android.systemui.dreams.ui.compose.DreamSetupScreen
import com.android.systemui.dreams.ui.viewmodel.DreamSetupViewModel
import javax.inject.Inject

/**
 * A simple, transparent activity that displays the contextual setup UI as a bottom sheet. This
 * activity is intended to be launched by SystemUI when specific contextual conditions are met.
 */
class DreamSetupActivity
@Inject
constructor(private val viewModelFactory: DreamSetupViewModel.Factory) : ComponentActivity() {
    private val viewModel: DreamSetupViewModel by viewModels { viewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PlatformTheme {
                DreamSetupScreen { event ->
                    viewModel.onEvent(event)
                    finish()
                }
            }
        }
    }
}
