/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.statusbar.notification.icon.ui.viewbinder

import android.view.Display
import androidx.lifecycle.lifecycleScope
import com.android.app.displaylib.PerDisplayRepository
import com.android.app.tracing.traceSection
import com.android.systemui.common.ui.ConfigurationState
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.statusbar.notification.collection.NotifPipeline
import com.android.systemui.statusbar.notification.icon.ui.viewbinder.NotificationIconContainerViewBinder.IconViewStore
import com.android.systemui.statusbar.notification.icon.ui.viewmodel.NotificationIconContainerStatusBarViewModel
import com.android.systemui.statusbar.phone.NotificationIconContainer
import javax.inject.Inject
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.launch

/** Binds a [NotificationIconContainer] to a [NotificationIconContainerStatusBarViewModel]. */
class NotificationIconContainerStatusBarViewBinder
@Inject
constructor(
    private val viewModel: NotificationIconContainerStatusBarViewModel,
    private val perDisplaySubcomponentRepository: PerDisplayRepository<SystemUIDisplaySubcomponent>,
    private val failureTracker: StatusBarIconViewBindingFailureTracker,
    private val defaultDisplayViewStore: StatusBarNotificationIconViewStore,
    private val connectedDisplaysViewStoreFactory:
        ConnectedDisplaysStatusBarNotificationIconViewStore.Factory,
) {

    fun bindWhileAttached(view: NotificationIconContainer, displayId: Int): DisposableHandle {
        return traceSection("NICStatusBar#bindWhileAttached") {
            view.repeatWhenAttached {
                val viewStore =
                    if (displayId == Display.DEFAULT_DISPLAY) {
                        defaultDisplayViewStore
                    } else {
                        connectedDisplaysViewStoreFactory.create(displayId = displayId).also {
                            lifecycleScope.launch { it.activate() }
                        }
                    }
                val displaySubcomponent =
                    perDisplaySubcomponentRepository[displayId]
                        ?: perDisplaySubcomponentRepository[Display.DEFAULT_DISPLAY]!!
                val configurationState: ConfigurationState = displaySubcomponent.configurationState
                val systemBarUtilsState = displaySubcomponent.systemBarUtilsState
                lifecycleScope.launch {
                    NotificationIconContainerViewBinder.bind(
                        displayId = displayId,
                        view = view,
                        viewModel = viewModel,
                        configuration = configurationState,
                        systemBarUtilsState = systemBarUtilsState,
                        failureTracker = failureTracker,
                        viewStore = viewStore,
                    )
                }
            }
        }
    }
}

/** [IconViewStore] for the status bar. */
class StatusBarNotificationIconViewStore @Inject constructor(notifPipeline: NotifPipeline) :
    IconViewStore by (notifPipeline.iconViewStoreBy { it.statusBarIcon })
