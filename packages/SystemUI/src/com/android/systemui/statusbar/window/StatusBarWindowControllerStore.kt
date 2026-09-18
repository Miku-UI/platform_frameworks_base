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

package com.android.systemui.statusbar.window

import android.view.WindowManager
import com.android.app.displaylib.PerDisplayRepository
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent
import com.android.systemui.display.data.repository.DisplayRepository
import com.android.systemui.display.data.repository.DisplayWindowPropertiesRepository
import com.android.systemui.display.data.repository.PerDisplayStore
import com.android.systemui.statusbar.data.repository.StatusBarPerDisplayStoreImpl
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/** Store that allows to retrieve per display instances of [StatusBarWindowController]. */
interface StatusBarWindowControllerStore : PerDisplayStore<StatusBarWindowController>

@SysUISingleton
class MultiDisplayStatusBarWindowControllerStore
@Inject
constructor(
    @Background backgroundApplicationScope: CoroutineScope,
    private val controllerFactory: StatusBarWindowController.Factory,
    private val displayWindowPropertiesRepository: DisplayWindowPropertiesRepository,
    private val perDisplaySubcomponentRepo: PerDisplayRepository<SystemUIDisplaySubcomponent>,
    displayRepository: DisplayRepository,
) :
    StatusBarWindowControllerStore,
    StatusBarPerDisplayStoreImpl<StatusBarWindowController>(
        backgroundApplicationScope,
        displayRepository,
    ) {

    override fun createInstanceForDisplay(displayId: Int): StatusBarWindowController? {
        val displaySubcomponent = perDisplaySubcomponentRepo[displayId] ?: return null
        val statusBarDisplayContext =
            displayWindowPropertiesRepository.get(
                displayId = displayId,
                windowType = WindowManager.LayoutParams.TYPE_STATUS_BAR,
            ) ?: return null
        return controllerFactory.create(
            statusBarDisplayContext.context,
            statusBarDisplayContext.windowManager,
            displaySubcomponent.statusBarConfigurationController,
            displaySubcomponent.statusBarContentInsetsProvider,
            displayId,
        )
    }

    override suspend fun onDisplayRemovalAction(instance: StatusBarWindowController) {
        instance.stop()
    }

    override val instanceClass = StatusBarWindowController::class.java
}
