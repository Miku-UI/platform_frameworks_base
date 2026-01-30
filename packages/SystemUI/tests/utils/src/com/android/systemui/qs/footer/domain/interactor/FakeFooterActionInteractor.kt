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

package com.android.systemui.qs.footer.domain.interactor

import android.content.Context
import com.android.systemui.animation.Expandable
import com.android.systemui.globalactions.GlobalActionsDialogLite
import com.android.systemui.qs.footer.data.model.UserSwitcherStatusModel
import com.android.systemui.qs.footer.domain.model.SecurityButtonConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * This fake is due to the fact that a real QSSecurityUtils is too complex to mock, instead this can
 * be used to directly provide values of the config for viewmodels.
 *
 * Only [securityButtonConfig] is implemented.
 */
class FakeFooterActionInteractor : FooterActionsInteractor {
    override val securityButtonConfig = MutableStateFlow<SecurityButtonConfig?>(null)

    override val foregroundServicesCount: Flow<Int>
        get() = emptyFlow()

    override val hasNewForegroundServices: Flow<Boolean>
        get() = emptyFlow()

    override val userSwitcherStatus: Flow<UserSwitcherStatusModel>
        get() = emptyFlow()

    override val deviceMonitoringDialogRequests: Flow<Unit>
        get() = emptyFlow()

    override fun showDeviceMonitoringDialog(
        quickSettingsContext: Context,
        expandable: Expandable?,
    ) {
        // EMPTY
    }

    override fun showForegroundServicesDialog(expandable: Expandable) {
        // EMPTY
    }

    override fun showPowerMenuDialog(
        globalActionsDialogLite: GlobalActionsDialogLite,
        expandable: Expandable,
    ) {
        // EMPTY
    }

    override fun showSettings(expandable: Expandable) {
        // EMPTY
    }

    override fun showUserSwitcher(expandable: Expandable) {
        // EMPTY
    }

    fun setSecurityConfig(config: SecurityButtonConfig?) {
        securityButtonConfig.value = config
    }
}
