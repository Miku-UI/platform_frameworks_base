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

package com.android.systemui.statusbar.featurepods.sharescreen.ui.viewmodel

import androidx.compose.runtime.getValue
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.res.R
import com.android.systemui.statusbar.featurepods.popups.ui.model.ChipIcon
import com.android.systemui.statusbar.featurepods.popups.ui.model.ColorsModel
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupChipId
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupChipModel
import com.android.systemui.statusbar.featurepods.popups.ui.viewmodel.StatusBarPopupChipViewModel
import com.android.systemui.statusbar.featurepods.sharescreen.domain.interactor.ShareScreenPrivacyIndicatorInteractor
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.map

/** ViewModel for the privacy indicator of the screen sharing. */
class ShareScreenPrivacyIndicatorViewModel
@AssistedInject
constructor(shareScreenPrivacyIndicatorInteractor: ShareScreenPrivacyIndicatorInteractor) :
    StatusBarPopupChipViewModel, HydratedActivatable() {

    override val chip: PopupChipModel by
        shareScreenPrivacyIndicatorInteractor.isChipVisible
            .map { toPopupChipModel(it) }
            .hydratedStateOf(
                traceName = "chip",
                initialValue = PopupChipModel.Hidden(PopupChipId.ShareScreenPrivacyIndicator),
            )

    private fun toPopupChipModel(isVisible: Boolean): PopupChipModel {
        return if (isVisible) {
            PopupChipModel.Shown(
                chipId = PopupChipId.ShareScreenPrivacyIndicator,
                icons = listOf(ChipIcon(Icon.Resource(R.drawable.ic_share_screen, null))),
                chipText = null,
                // TODO(b/444293568) Finalize and update the colors of this chip.
                colors = ColorsModel.AvControlsTheme,
            )
        } else {
            PopupChipModel.Hidden(PopupChipId.ShareScreenPrivacyIndicator)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(): ShareScreenPrivacyIndicatorViewModel
    }
}
