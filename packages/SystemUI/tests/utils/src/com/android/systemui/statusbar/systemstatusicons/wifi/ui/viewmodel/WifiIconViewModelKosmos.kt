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

package com.android.systemui.statusbar.systemstatusicons.wifi.ui.viewmodel

import android.content.Context
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.statusbar.pipeline.wifi.ui.viewmodel.wifiViewModel

val Kosmos.wifiIconViewModelFactory: WifiIconViewModel.Factory by
    Kosmos.Fixture {
        object : WifiIconViewModel.Factory {
            override fun create(context: Context): WifiIconViewModel =
                WifiIconViewModel(context, wifiViewModel)
        }
    }
