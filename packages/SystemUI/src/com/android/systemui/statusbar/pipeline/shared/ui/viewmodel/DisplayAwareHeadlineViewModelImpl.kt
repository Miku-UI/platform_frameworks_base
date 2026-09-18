/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.statusbar.pipeline.shared.ui.viewmodel

import android.widget.ImageView
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayId
import com.android.systemui.headline.ui.viewmodel.HeadlineItemsAdapter
import com.android.systemui.headline.ui.viewmodel.HeadlineViewModel
import com.android.systemui.headline.ui.viewmodel.MutableHeadlineViewModel
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.statusbar.notification.icon.ui.viewbinder.ConnectedDisplaysStatusBarNotificationIconViewStore
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class DisplayAwareHeadlineViewModelImpl
@AssistedInject
constructor(
    @field:DisplayId @DisplayAware thisDisplayId: Int,
    @DisplayAware private val adapter: HeadlineItemsAdapter,
    iconViewStoreFactory: ConnectedDisplaysStatusBarNotificationIconViewStore.Factory,
    private val delegateViewModel: MutableHeadlineViewModel,
) : HeadlineViewModel by delegateViewModel, HydratedActivatable() {
    private val iconViewStore: ConnectedDisplaysStatusBarNotificationIconViewStore =
        iconViewStoreFactory.create(thisDisplayId)

    override fun iconView(key: String): ImageView? {
        return iconViewStore.iconView(key)
    }

    override suspend fun onActivated() {
        coroutineScope {
            launch { iconViewStore.activate() }
            launch { adapter.headlineItems.collect { delegateViewModel.updateItems(it) } }
        }
    }

    @AssistedFactory
    interface Factory : HeadlineViewModel.Factory {
        override fun create(): DisplayAwareHeadlineViewModelImpl
    }
}
