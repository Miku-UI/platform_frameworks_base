/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.statusbar.quickactions.popups.ui.viewmodel

import android.view.Display
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.statusbar.pipeline.shared.domain.interactor.statusBarVisibilityInteractor
import com.android.systemui.statusbar.quickactions.assistant.ui.viewmodel.assistantIconViewModelFactory
import com.android.systemui.statusbar.quickactions.av.ui.viewmodel.avControlsChipViewModelFactory
import com.android.systemui.statusbar.quickactions.domain.interactor.quickActionsInteractor
import com.android.systemui.statusbar.quickactions.ime.ui.viewmodel.imeIndicatorChipViewModelFactory
import com.android.systemui.statusbar.quickactions.media.ui.viewmodel.mediaControlChipViewModelFactory
import com.android.systemui.statusbar.quickactions.screenrecord.ui.viewmodel.largeScreenRecordingChipViewModelFactory
import com.android.systemui.statusbar.quickactions.sharescreen.ui.viewmodel.shareScreenPrivacyIndicatorViewModelFactory

private val Kosmos.statusBarPopupChipsViewModel: StatusBarPopupChipsViewModel by
    Kosmos.Fixture {
        StatusBarPopupChipsViewModel(
            displayId = Display.DEFAULT_DISPLAY,
            quickActionsInteractor = quickActionsInteractor,
            statusBarVisibilityInteractor = statusBarVisibilityInteractor,
            mediaControlChipFactory = mediaControlChipViewModelFactory,
            avControlsChipFactory = avControlsChipViewModelFactory,
            shareScreenPrivacyIndicatorFactory = shareScreenPrivacyIndicatorViewModelFactory,
            assistantIconFactory = assistantIconViewModelFactory,
            imeIndicatorChipFactory = imeIndicatorChipViewModelFactory,
            largeScreenRecordingChipViewModelFactory = largeScreenRecordingChipViewModelFactory,
        )
    }

val Kosmos.statusBarPopupChipsViewModelFactory by
    Kosmos.Fixture {
        object : StatusBarPopupChipsViewModel.Factory {
            override fun create(displayId: Int): StatusBarPopupChipsViewModel =
                statusBarPopupChipsViewModel
        }
    }
