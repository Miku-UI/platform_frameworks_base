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

package com.android.systemui.statusbar.quickactions.screenrecord.ui.viewmodel

import androidx.compose.runtime.getValue
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.res.R
import com.android.systemui.screenrecord.domain.interactor.ScreenRecordingServiceInteractor
import com.android.systemui.screenrecord.shared.model.ScreenRecordingStatus
import com.android.systemui.statusbar.chips.ui.model.Chronometer
import com.android.systemui.statusbar.chips.ui.model.EventTime
import com.android.systemui.statusbar.quickactions.popups.ui.viewmodel.StatusBarPopupChipViewModel
import com.android.systemui.statusbar.quickactions.shared.model.ChipContent
import com.android.systemui.statusbar.quickactions.shared.model.ChipIcon
import com.android.systemui.statusbar.quickactions.shared.model.QuickActionChipId
import com.android.systemui.statusbar.quickactions.shared.model.QuickActionChipModel
import com.android.systemui.statusbar.quickactions.ui.compose.ChipColors
import com.android.systemui.util.time.SystemClock
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** ViewModel used to display screen recording chip in the status bar. */
class LargeScreenRecordingChipViewModel
@AssistedInject
constructor(
    private val screenRecordingServiceInteractor: ScreenRecordingServiceInteractor,
    private val popupViewModelFactory: LargeScreenStopRecordingPopupViewModel2.Factory,
    private val systemClock: SystemClock,
) : StatusBarPopupChipViewModel, HydratedActivatable() {

    // A flow that emits a long (count down or timer base time) according to the recording status
    private val timerContent: Flow<Long?> =
        screenRecordingServiceInteractor.status.map { status ->
            when (status) {
                is ScreenRecordingStatus.Starting -> status.untilStarted.inWholeSeconds
                is ScreenRecordingStatus.Started -> systemClock.elapsedRealtime()
                else -> null
            }
        }

    override val chip: QuickActionChipModel by
        combine(screenRecordingServiceInteractor.status, timerContent) { status, timeValue ->
                toPopupChipModel(status, timeValue)
            }
            .hydratedStateOf(
                traceName = "chip",
                initialValue = QuickActionChipModel.Hidden(QuickActionChipId.ScreenRecording),
            )

    private fun toPopupChipModel(
        status: ScreenRecordingStatus,
        timeValue: Long?,
    ): QuickActionChipModel {
        return when (status) {
            is ScreenRecordingStatus.Starting -> {
                QuickActionChipModel.PopupChip(
                    chipId = QuickActionChipId.ScreenRecording,
                    icons = emptyList(),
                    chipContent = ChipContent.Text((timeValue ?: 0L).toString()),
                    colors = ChipColors.AvControlsTheme,
                    contentDescription =
                        ContentDescription.Resource(R.string.screenrecord_start_description),
                    popupViewModelFactory = popupViewModelFactory,
                )
            }
            is ScreenRecordingStatus.Started -> {
                QuickActionChipModel.PopupChip(
                    chipId = QuickActionChipId.ScreenRecording,
                    icons = listOf(ChipIcon(Icon.Resource(R.drawable.ic_screenrecord, null))),
                    chipContent =
                        ChipContent.Timer(
                            chronometer =
                                Chronometer.Running(
                                    eventTime =
                                        EventTime.ElapsedRealtime(
                                            timeValue ?: systemClock.elapsedRealtime()
                                        )
                                ),
                            timeSource = systemClock,
                        ),
                    colors = ChipColors.AvControlsTheme,
                    contentDescription =
                        ContentDescription.Resource(R.string.screenrecord_ongoing_screen_only),
                    popupViewModelFactory = popupViewModelFactory,
                )
            }
            else -> {
                QuickActionChipModel.Hidden(QuickActionChipId.ScreenRecording)
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(): LargeScreenRecordingChipViewModel
    }
}
