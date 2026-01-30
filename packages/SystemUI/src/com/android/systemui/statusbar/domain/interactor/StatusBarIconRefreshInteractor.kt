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

package com.android.systemui.statusbar.domain.interactor

import com.android.app.displaylib.PerDisplayInstanceProvider
import com.android.app.displaylib.PerDisplayRepository
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.PerDisplaySingleton
import com.android.systemui.statusbar.data.repository.StatusBarConfigurationController
import com.android.systemui.statusbar.phone.ui.StatusBarIconController
import com.android.systemui.statusbar.policy.ConfigurationController.ConfigurationListener
import javax.inject.Inject

/** Propagates per display events to refresh icon groups to [StatusBarIconController]. */
interface StatusBarIconRefreshInteractor {
    /** Starts monitoring for config changes to refresh icons. */
    fun start()

    /** Stops monitoring for config changes to refresh icons. */
    fun stop()
}

@PerDisplaySingleton
class StatusBarIconRefreshInteractorImpl
@Inject
constructor(
    @DisplayAware private val displayId: Int,
    @DisplayAware private val statusBarConfigurationController: StatusBarConfigurationController,
    private val statusBarIconController: StatusBarIconController,
) : StatusBarIconRefreshInteractor {

    private val configurationListener =
        object : ConfigurationListener {
            override fun onDensityOrFontScaleChanged() {
                statusBarIconController.refreshIconGroups(displayId)
            }
        }

    override fun start() {
        statusBarConfigurationController.addCallback(configurationListener)
    }

    override fun stop() {
        statusBarConfigurationController.removeCallback(configurationListener)
    }
}

/** Builds an instance of [StatusBarIconRefreshInteractor] for each display. */
@SysUISingleton
class StatusBarIconRefreshPerDisplayInstanceProvider
@Inject
constructor(private val displayComponent: PerDisplayRepository<SystemUIDisplaySubcomponent>) :
    PerDisplayInstanceProvider<StatusBarIconRefreshInteractor> {
    override fun createInstance(displayId: Int): StatusBarIconRefreshInteractor? {
        return displayComponent[displayId]?.statusBarIconRefreshInteractor
    }
}
