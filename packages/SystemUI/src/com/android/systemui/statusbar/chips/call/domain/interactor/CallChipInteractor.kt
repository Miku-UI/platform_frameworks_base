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

package com.android.systemui.statusbar.chips.call.domain.interactor

import android.app.Flags
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.statusbar.chips.StatusBarChipLogTags.pad
import com.android.systemui.statusbar.chips.StatusBarChipsLog
import com.android.systemui.statusbar.notification.promoted.PromotedNotificationUi
import com.android.systemui.statusbar.phone.ongoingcall.StatusBarChipsModernization
import com.android.systemui.statusbar.phone.ongoingcall.data.repository.OngoingCallRepository
import com.android.systemui.statusbar.phone.ongoingcall.domain.interactor.OngoingCallInteractor
import com.android.systemui.statusbar.phone.ongoingcall.shared.model.OngoingCallModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/** Interactor for the ongoing phone call chip shown in the status bar. */
@SysUISingleton
class CallChipInteractor
@Inject
constructor(
    @Application private val scope: CoroutineScope,
    ongoingCallInteractor: OngoingCallInteractor,
    repository: OngoingCallRepository,
    @StatusBarChipsLog private val logBuffer: LogBuffer,
) {
    private val logger = Logger(logBuffer, "CallChip".pad())

    val ongoingCallState: StateFlow<OngoingCallModel> =
        (if (StatusBarChipsModernization.isEnabled) {
                ongoingCallInteractor.ongoingCallState
            } else {
                repository.ongoingCallState
            })
            .map { state ->
                if (
                    PromotedNotificationUi.isEnabled &&
                        state is OngoingCallModel.InCall &&
                        state.requestedPromotion
                ) {
                    // If this notification requested promotion, then the promoted notification
                    // chips will handle everything and we don't ever need to show a call chip. See
                    // b/414830065.
                    OngoingCallModel.NoCall
                } else {
                    state
                }
            }
            .distinctUntilChanged()
            .onEach {
                logger.d({ "Call chip state updated: newState=$str1" }) { str1 = it.logString() }
            }
            .stateIn(scope, SharingStarted.Lazily, OngoingCallModel.NoCall)
}
