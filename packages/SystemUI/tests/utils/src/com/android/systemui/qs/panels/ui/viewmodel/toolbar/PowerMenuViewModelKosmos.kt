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

package com.android.systemui.qs.panels.ui.viewmodel.toolbar

import com.android.systemui.globalactions.domain.interactor.globalActionsInteractor
import com.android.systemui.globalactions.ui.viewmodel.lockGlobalActionViewModelFactory
import com.android.systemui.globalactions.ui.viewmodel.logoutGlobalActionViewModelFactory
import com.android.systemui.globalactions.ui.viewmodel.restartGlobalActionViewModelFactory
import com.android.systemui.globalactions.ui.viewmodel.shutdownGlobalActionViewModelFactory
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.log.logcatLogBuffer

val Kosmos.powerMenuViewModelFactory by
    Kosmos.Fixture {
        object : PowerMenuViewModel.Factory {
            override fun create(): PowerMenuViewModel =
                PowerMenuViewModel(
                    interactor = globalActionsInteractor,
                    logBuffer = logcatLogBuffer("PowerMenuViewModel"),
                    lockFactory = lockGlobalActionViewModelFactory,
                    logoutFactory = logoutGlobalActionViewModelFactory,
                    shutdownFactory = shutdownGlobalActionViewModelFactory,
                    restartFactory = restartGlobalActionViewModelFactory,
                )
        }
    }
