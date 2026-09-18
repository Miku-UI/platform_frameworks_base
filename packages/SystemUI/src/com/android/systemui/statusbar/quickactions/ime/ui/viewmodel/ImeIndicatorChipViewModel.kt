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

package com.android.systemui.statusbar.quickactions.ime.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.shared.model.asIcon
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayId
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.res.R
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.statusbar.quickactions.domain.interactor.QuickActionsInteractor
import com.android.systemui.statusbar.quickactions.ime.domain.interactor.ImeIndicatorChipInteractor
import com.android.systemui.statusbar.quickactions.ime.shared.model.ImeIndicatorChipModel
import com.android.systemui.statusbar.quickactions.popups.ui.viewmodel.StatusBarPopupChipViewModel
import com.android.systemui.statusbar.quickactions.shared.model.ChipContent
import com.android.systemui.statusbar.quickactions.shared.model.QuickActionChipId
import com.android.systemui.statusbar.quickactions.shared.model.QuickActionChipModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/** View model for the IME indicator chip in the status bar. */
class ImeIndicatorChipViewModel
@AssistedInject
constructor(
    @param:Application private val context: Context,
    @Assisted private val displayId: Int,
    private val sceneInteractor: SceneInteractor,
    private val quickActionsInteractor: QuickActionsInteractor,
    private val imeIndicatorChipInteractor: ImeIndicatorChipInteractor,
) : StatusBarPopupChipViewModel, HydratedActivatable() {

    override suspend fun onActivated() {
        // TODO: b/478352392 - Consider doing this only when ImeIndicator chip becomes active (i.e.
        // not in Hidden state) for performance improvement.
        coroutineScope {
            // ImeIndicator is a QuickActions LaunchChip (not PopupChip), with no associated popups.
            // On click it launches the InputMethodPicker (SystemUI Dialog, via InputMethodManager
            // API) that appears as if it were a StatusBar popup, thus is required to mimic the
            // behaviour of StatusBar popups. In particular, to ensure mutually exclusive visibility
            // with StatusBar popups (NotificationsShade, QuickSettingsShade, QuickActions popups),
            // it needs to be hidden upon showing another popup.
            snapshotFlow {
                sceneInteractor.transitionState.currentOverlays.isNotEmpty() ||
                quickActionsInteractor.activePanel?.chipId != null
            }
            .collect { anotherOverlayIsActive ->
                if (anotherOverlayIsActive) {
                    imeIndicatorChipInteractor.hideInputMethodPicker(displayId)
                }
            }
        }
    }

    override val chip: QuickActionChipModel by
        imeIndicatorChipInteractor.chipModel
            .map { toChipModel(it) }
            .hydratedStateOf(
                traceName = "imeIndicatorChip",
                initialValue = QuickActionChipModel.Hidden(QuickActionChipId.ImeIndicator),
            )

    private fun toChipModel(model: ImeIndicatorChipModel): QuickActionChipModel {
        if (!model.isVisible) {
            return QuickActionChipModel.Hidden(QuickActionChipId.ImeIndicator)
        }

        val subtype = model.selectedSubtype
        val subtypeIcon =
            subtype?.icon?.let { icon ->
                android.graphics.drawable.Icon.createWithResource(icon.packageName, icon.resId)
                    .loadDrawable(context)
                    ?.asIcon(resId = icon.resId, resPackage = icon.packageName)
            }

        val content =
            when {
                subtypeIcon != null -> ChipContent.IconOnly(subtypeIcon)
                !subtype?.shortLabel.isNullOrBlank() -> ChipContent.Text(subtype!!.shortLabel!!)
                else ->
                    ChipContent.IconOnly(
                        Icon.Resource(
                            R.drawable.ic_keyboard,
                            ContentDescription.Resource(
                                R.string.accessibility_status_bar_input_method_indicator
                            ),
                        )
                    )
            }

        return QuickActionChipModel.LaunchChip(
            chipId = QuickActionChipId.ImeIndicator,
            chipContent = content,
            onClick = {
                // ImeIndicator is a QuickActions LaunchChip (not PopupChip), with no associated
                // popups. It launches InputMethodPicker (SystemUI Dialog, via InputMethodManager
                // API) that appears as if it were a StatusBar popup, thus is required to mimic the
                // behaviour of StatusBar popups. In particular, clicks on ImeIndicator chip should
                // toggle InputMethodPicker, like PopupChip clicks toggling their respective popups.
                imeIndicatorChipInteractor.toggleInputMethodPicker(displayId)
            },
            contentDescription =
                ContentDescription.Resource(
                    R.string.accessibility_status_bar_input_method_indicator
                ),
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(@DisplayId displayId: Int): ImeIndicatorChipViewModel
    }
}
