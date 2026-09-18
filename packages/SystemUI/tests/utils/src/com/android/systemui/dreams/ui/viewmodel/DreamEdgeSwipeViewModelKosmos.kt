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

package com.android.systemui.dreams.ui.viewmodel

import com.android.systemui.common.ui.domain.interactor.configurationInteractor
import com.android.systemui.dreams.domain.interactor.dreamInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.settings.userTracker

val Kosmos.dreamEdgeSwipeViewModel: DreamEdgeSwipeViewModel by
    Kosmos.Fixture {
        DreamEdgeSwipeViewModel(dreamInteractor, configurationInteractor, userTracker)
    }

val Kosmos.dreamEdgeSwipeViewModelFactory: DreamEdgeSwipeViewModel.Factory by
    Kosmos.Fixture {
        object : DreamEdgeSwipeViewModel.Factory {
            override fun create(): DreamEdgeSwipeViewModel = dreamEdgeSwipeViewModel
        }
    }
