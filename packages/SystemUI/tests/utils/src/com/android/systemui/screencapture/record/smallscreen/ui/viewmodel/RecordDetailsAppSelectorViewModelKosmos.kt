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

package com.android.systemui.screencapture.record.smallscreen.ui.viewmodel

import com.android.systemui.kosmos.Kosmos
import com.android.systemui.screencapture.common.ui.viewmodel.recentTaskViewModelFactory
import com.android.systemui.screencapture.record.smallscreen.domain.interactor.recordDetailsTargetInteractor

val Kosmos.recordDetailsAppSelectorViewModelFactory by
    Kosmos.Fixture {
        object : RecordDetailsAppSelectorViewModel.Factory {
            override fun create(): RecordDetailsAppSelectorViewModel =
                RecordDetailsAppSelectorViewModel(
                    interactor = recordDetailsTargetInteractor,
                    recentTaskViewModelFactory = recentTaskViewModelFactory,
                )
        }
    }
