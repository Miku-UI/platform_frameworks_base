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

package com.android.systemui.statusbar.systemstatusicons.mobile.ui.viewmodel

import android.content.Context
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.mobileIconsViewModel
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.stackedMobileIconViewModelFactory

val Kosmos.mobileSystemStatusIconsViewModelFactory: MobileSystemStatusIconsViewModel.Factory by
    Kosmos.Fixture {
        object : MobileSystemStatusIconsViewModel.Factory {
            override fun create(context: Context): MobileSystemStatusIconsViewModel =
                MobileSystemStatusIconsViewModel(
                    context,
                    mobileIconsViewModel,
                    stackedMobileIconViewModelFactory,
                )
        }
    }
