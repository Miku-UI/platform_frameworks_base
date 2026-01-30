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

package com.android.systemui.actioncorner.domain.interactor

import android.view.IWindowManager
import com.android.systemui.LauncherProxyService
import com.android.systemui.actioncorner.data.model.ActionCornerRegion
import com.android.systemui.actioncorner.data.model.ActionCornerRegion.BOTTOM_LEFT
import com.android.systemui.actioncorner.data.model.ActionCornerRegion.BOTTOM_RIGHT
import com.android.systemui.actioncorner.data.model.ActionCornerRegion.TOP_LEFT
import com.android.systemui.actioncorner.data.model.ActionCornerRegion.TOP_RIGHT
import com.android.systemui.actioncorner.data.model.ActionCornerState.ActiveActionCorner
import com.android.systemui.actioncorner.data.model.ActionType
import com.android.systemui.actioncorner.data.model.ActionType.HOME
import com.android.systemui.actioncorner.data.model.ActionType.LOCKSCREEN
import com.android.systemui.actioncorner.data.model.ActionType.NONE
import com.android.systemui.actioncorner.data.model.ActionType.NOTIFICATIONS
import com.android.systemui.actioncorner.data.model.ActionType.OVERVIEW
import com.android.systemui.actioncorner.data.model.ActionType.QUICK_SETTINGS
import com.android.systemui.actioncorner.data.repository.ActionCornerRepository
import com.android.systemui.actioncorner.data.repository.ActionCornerSettingRepository
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.inputdevice.data.repository.PointerDeviceRepository
import com.android.systemui.keyguard.domain.interactor.WindowManagerLockscreenVisibilityInteractor
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.shared.system.actioncorner.ActionCornerConstants
import com.android.systemui.statusbar.CommandQueue
import com.android.systemui.statusbar.policy.data.repository.UserSetupRepository
import javax.inject.Inject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@SysUISingleton
class ActionCornerInteractor
@Inject
constructor(
    private val repository: ActionCornerRepository,
    private val launcherProxyService: LauncherProxyService,
    private val actionCornerSettingRepository: ActionCornerSettingRepository,
    private val pointerDeviceRepository: PointerDeviceRepository,
    private val lockscreenVisibilityInteractor: WindowManagerLockscreenVisibilityInteractor,
    private val userSetupRepository: UserSetupRepository,
    private val commandQueue: CommandQueue,
    private val windowManager: IWindowManager,
) : ExclusiveActivatable() {

    override suspend fun onActivated(): Nothing {
        combine(
                pointerDeviceRepository.isAnyPointerDeviceConnected,
                actionCornerSettingRepository.isAnyActionConfigured,
                userSetupRepository.isUserSetUp,
            ) { isConnected, isAnyActionConfigured, isUserSetUp ->
                isConnected && isAnyActionConfigured && isUserSetUp
            }
            .distinctUntilChanged()
            .flatMapLatest { shouldCheckLockscreenVisibility ->
                if (shouldCheckLockscreenVisibility) {
                    lockscreenVisibilityInteractor.lockscreenVisibility.map { !it.first }
                } else {
                    flowOf(false)
                }
            }
            .flatMapLatest { shouldMonitorActionCorner ->
                if (shouldMonitorActionCorner) {
                    repository.actionCornerState
                } else {
                    emptyFlow()
                }
            }
            .distinctUntilChanged()
            .filterIsInstance<ActiveActionCorner>()
            .collect {
                val action = getAction(it.region)
                when (action) {
                    HOME ->
                        launcherProxyService.onActionCornerActivated(
                            ActionCornerConstants.HOME,
                            it.displayId,
                        )
                    OVERVIEW ->
                        launcherProxyService.onActionCornerActivated(
                            ActionCornerConstants.OVERVIEW,
                            it.displayId,
                        )
                    NOTIFICATIONS -> commandQueue.toggleNotificationsPanel()
                    QUICK_SETTINGS -> commandQueue.toggleQuickSettingsPanel()
                    LOCKSCREEN -> windowManager.lockNow(/* bundle= */ null)
                    NONE -> {}
                }
            }
        awaitCancellation()
    }

    private fun getAction(region: ActionCornerRegion): ActionType {
        return when (region) {
            TOP_LEFT -> actionCornerSettingRepository.topLeftCornerAction.value
            TOP_RIGHT -> actionCornerSettingRepository.topRightCornerAction.value
            BOTTOM_LEFT -> actionCornerSettingRepository.bottomLeftCornerAction.value
            BOTTOM_RIGHT -> actionCornerSettingRepository.bottomRightCornerAction.value
        }
    }
}
