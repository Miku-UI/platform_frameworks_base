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

package com.android.systemui.complication

import android.os.UserHandle
import android.provider.Settings
import com.android.app.tracing.coroutines.launchTraced
import com.android.settingslib.dream.DreamBackend
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.SystemUser
import com.android.systemui.dreams.DreamOverlayStateController
import com.android.systemui.shared.condition.Monitor
import com.android.systemui.shared.settings.data.repository.SecureSettingsRepository
import com.android.systemui.user.domain.interactor.SelectedUserInteractor
import com.android.systemui.util.condition.ConditionalCoreStartable
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * [ComplicationTypesUpdater] observes the state of available complication types set by the user,
 * and pushes updates to [DreamOverlayStateController].
 */
@SysUISingleton
class ComplicationTypesUpdater
@Inject
constructor(
    private val dreamBackend: DreamBackend,
    private val dreamOverlayStateController: DreamOverlayStateController,
    private val secureSettingsRepository: SecureSettingsRepository,
    private val selectedUserInteractor: SelectedUserInteractor,
    @Background private val bgScope: CoroutineScope,
    @SystemUser monitor: Monitor,
) : ConditionalCoreStartable(monitor) {

    override fun onStart() {
        val complicationsEnabled: Flow<Boolean> =
            secureSettingsRepository.boolSetting(Settings.Secure.SCREENSAVER_COMPLICATIONS_ENABLED)
        val homeControlsEnabled: Flow<Boolean> =
            secureSettingsRepository.boolSetting(Settings.Secure.SCREENSAVER_HOME_CONTROLS_ENABLED)
        val lockscreenControlsEnabled: Flow<Boolean> =
            secureSettingsRepository.boolSetting(Settings.Secure.LOCKSCREEN_SHOW_CONTROLS)

        bgScope.launchTraced("ComplicationTypesUpdater#onStart") {
            combine(
                    complicationsEnabled,
                    homeControlsEnabled,
                    lockscreenControlsEnabled,
                    selectedUserInteractor.selectedUserInfo,
                ) { _, _, _, userInfo ->
                    getAvailableComplicationTypes(userInfo.userHandle)
                }
                .distinctUntilChanged()
                .collect { availableComplications ->
                    dreamOverlayStateController.availableComplicationTypes = availableComplications
                }
        }
    }

    /** Returns complication types that are currently available by user setting. */
    @Complication.ComplicationType
    private fun getAvailableComplicationTypes(user: UserHandle): Int {
        return ComplicationUtils.convertComplicationTypes(
            dreamBackend.getEnabledComplications(user)
        )
    }
}
