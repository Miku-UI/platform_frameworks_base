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

package com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel

import android.content.testableContext
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.log.table.TableLogBuffer
import com.android.systemui.log.table.logcatTableLogBuffer
import com.android.systemui.statusbar.connectivity.ui.mobileContextProvider
import com.android.systemui.statusbar.pipeline.mobile.StatusBarMobileIconKairos

val Kosmos.stackedMobileIconViewModelFactory: StackedMobileIconViewModel.Factory
    get() =
        if (StatusBarMobileIconKairos.isEnabled) {
            stackedMobileIconViewModelKairosFactory
        } else {
            stackedMobileIconViewModelFactoryImpl
        }

val Kosmos.stackedMobileIconViewModelFactoryImpl: StackedMobileIconViewModelImpl.Factory by
    Kosmos.Fixture {
        object : StackedMobileIconViewModelImpl.Factory {
            override fun create(): StackedMobileIconViewModelImpl =
                StackedMobileIconViewModelImpl(
                    mobileIconsViewModel,
                    tableLogBuffer,
                    testableContext,
                    mobileContextProvider,
                )
        }
    }

private val Kosmos.tableLogBuffer: TableLogBuffer by
    Kosmos.Fixture { logcatTableLogBuffer(this, "stackedMobileIconTableLogger") }
