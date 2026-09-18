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

package com.android.systemui.volume.panel.component.mnc.ui.viewmodel

import android.content.Context
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.res.R
import com.android.systemui.statusbar.quickactions.av.domain.interactor.DesktopEffectInteractor
import com.android.systemui.user.data.repository.UserRepository
import com.android.systemui.volume.panel.component.button.ui.viewmodel.ButtonViewModel
import com.android.systemui.volume.panel.dagger.scope.VolumePanelScope
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Volume Panel mic noise cancellation UI model. */
@VolumePanelScope
class MncViewModel
@Inject
constructor(
    private val context: Context,
    @VolumePanelScope private val coroutineScope: CoroutineScope,
    private val userRepository: UserRepository,
    private val desktopEffectInteractor: DesktopEffectInteractor,
) {
    val buttonViewModel: StateFlow<ButtonViewModel?> =
        userRepository.selectedUserInfo
            .flatMapLatest { userInfo -> desktopEffectInteractor.effectsForUser(userInfo.id) }
            .map { effects ->
                ButtonViewModel(
                    isActive = effects.studioMic,
                    icon =
                        Icon.Resource(
                            if (effects.studioMic) {
                                R.drawable.ic_mic_noise_cancel_high
                            } else {
                                R.drawable.ic_mic_noise_cancel_off
                            },
                            null,
                        ),
                    label = context.getString(R.string.volume_panel_mic_noise_cancellation_title),
                )
            }
            .stateIn(coroutineScope, SharingStarted.Eagerly, null)

    fun setIsMncEnabled(enabled: Boolean) {
        coroutineScope.launch {
            val userId = userRepository.selectedUserInfo.first().id
            desktopEffectInteractor.setStudioMic(userId = userId, newValue = enabled)
        }
    }
}
