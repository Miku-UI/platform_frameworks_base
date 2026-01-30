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

package com.android.systemui.statusbar.featurepods.popups.ui.viewmodel

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.statusbar.featurepods.av.ui.viewmodel.AvControlsChipViewModel
import com.android.systemui.statusbar.featurepods.media.ui.viewmodel.MediaControlChipViewModel
import com.android.systemui.statusbar.featurepods.popups.StatusBarPopupChips
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupChipId
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupChipModel
import com.android.systemui.statusbar.featurepods.sharescreen.ui.viewmodel.ShareScreenPrivacyIndicatorViewModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * View model deciding which system process chips to show in the status bar. Emits a list of
 * PopupChipModels.
 */
class StatusBarPopupChipsViewModel
@AssistedInject
constructor(
    mediaControlChipFactory: MediaControlChipViewModel.Factory,
    avControlsChipFactory: AvControlsChipViewModel.Factory,
    shareScreenPrivacyIndicatorFactory: ShareScreenPrivacyIndicatorViewModel.Factory,
) : ExclusiveActivatable() {

    private val mediaControlChip by lazy { mediaControlChipFactory.create() }
    private val avControlsChip by lazy { avControlsChipFactory.create() }
    private val shareScreenPrivacyIndicator by lazy { shareScreenPrivacyIndicatorFactory.create() }

    /** The ID of the current chip that is showing its popup, or `null` if no chip is shown. */
    private var currentShownPopupChipId by mutableStateOf<PopupChipId?>(null)

    private val incomingPopupChipBundle: PopupChipBundle by derivedStateOf {
        PopupChipBundle(
            media = mediaControlChip.chip,
            privacy = avControlsChip.chip,
            shareScreen = shareScreenPrivacyIndicator.chip,
        )
    }

    val shownPopupChips: List<PopupChipModel.Shown> by derivedStateOf {
        if (StatusBarPopupChips.isEnabled) {
            val bundle = incomingPopupChipBundle

            listOfNotNull(bundle.media, bundle.privacy, bundle.shareScreen)
                .filterIsInstance<PopupChipModel.Shown>()
                .map { chip ->
                    chip.copy(
                        isPopupShown = chip.chipId == currentShownPopupChipId,
                        showPopup = { currentShownPopupChipId = chip.chipId },
                        hidePopup = { currentShownPopupChipId = null },
                    )
                }
        } else {
            emptyList()
        }
    }

    override suspend fun onActivated(): Nothing {
        coroutineScope {
            launch { avControlsChip.activate() }
            launch { mediaControlChip.activate() }
            launch { shareScreenPrivacyIndicator.activate() }
        }
        awaitCancellation()
    }

    private data class PopupChipBundle(
        val media: PopupChipModel = PopupChipModel.Hidden(chipId = PopupChipId.MediaControl),
        val privacy: PopupChipModel =
            PopupChipModel.Hidden(chipId = PopupChipId.AvControlsIndicator),
        val shareScreen: PopupChipModel =
            PopupChipModel.Hidden(chipId = PopupChipId.ShareScreenPrivacyIndicator),
    )

    @AssistedFactory
    interface Factory {
        fun create(): StatusBarPopupChipsViewModel
    }
}
