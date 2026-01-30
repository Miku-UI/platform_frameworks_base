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

package com.android.systemui.statusbar.notification.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.util.kotlin.pairwise
import kotlinx.coroutines.flow.filter
import javax.inject.Inject

/**
 * Manages notification-related logic that needs to persist across scene changes within the scene
 * container.
 */
@SysUISingleton
class NotificationContainerInteractor
@Inject
constructor(
    private val shadeInteractor: ShadeInteractor,
    private val headsUpNotificationInteractor: HeadsUpNotificationInteractor,
) : ExclusiveActivatable() {

    override suspend fun onActivated() {
        // Unpin all HUNs only when the shade transitions from closed to open.
        shadeInteractor.isAnyExpanded
            .pairwise()
            .filter { (wasExpanded, isExpanded) -> !wasExpanded && isExpanded }
            .collect {
                headsUpNotificationInteractor.unpinAll(true)
            }
    }
}
