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

package com.android.systemui.accessibility.domain.interactor

import android.annotation.SuppressLint
import com.android.systemui.CoreStartable
import com.android.systemui.accessibility.SystemActions
import com.android.systemui.accessibility.data.repository.SystemActionsRepository
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Helper class for [SystemActions], written in Kotlin so coroutines can be used. */
@SysUISingleton
@SuppressLint("MissingPermission")
class SystemActionsInteractor
@Inject
constructor(
    private val systemActionsRepository: SystemActionsRepository,
    @Background private val backgroundScope: CoroutineScope,
    shadeInteractor: ShadeInteractor,
    deviceEntryInteractor: DeviceEntryInteractor,
) : CoreStartable {
    private val isShadeShowingOverHome =
        combine(shadeInteractor.isAnyExpanded, deviceEntryInteractor.isDeviceEntered) {
                isShadeShowing,
                isDeviceEntered ->
                isShadeShowing && isDeviceEntered
            }
            .distinctUntilChanged()

    override fun start() {
        if (!SceneContainerFlag.isEnabled) {
            return
        }
        backgroundScope.launch {
            isShadeShowingOverHome.collect {
                if (it) {
                    systemActionsRepository.registerDismissShadeSystemAction()
                } else {
                    systemActionsRepository.unregisterDismissShadeSystemAction()
                }
            }
        }
    }
}
