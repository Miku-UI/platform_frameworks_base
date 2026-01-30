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

package com.android.systemui.statusbar.data.repository

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.core.StatusBarConnectedDisplays
import com.android.systemui.statusbar.core.StatusBarInitializer.StatusBarViewLifecycleListener
import com.android.systemui.statusbar.phone.fragment.dagger.HomeStatusBarComponent
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.multibindings.ElementsIntoSet
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A central repository that holds and tracks all active [HomeStatusBarComponent] instances, where
 * each component is associated with the status bar on a single display.
 */
@SysUISingleton
class HomeStatusBarComponentsRepository @Inject constructor() : StatusBarViewLifecycleListener {

    private val _componentsByDisplayId = MutableStateFlow(emptyMap<Int, HomeStatusBarComponent>())

    /**
     * Emits the map of all currently initialized [HomeStatusBarComponent] instances across all
     * active displays.
     *
     * The key of the map is the display ID.
     */
    val componentsByDisplayId: StateFlow<Map<Int, HomeStatusBarComponent>> =
        _componentsByDisplayId.asStateFlow()

    override fun onStatusBarViewInitialized(component: HomeStatusBarComponent) {
        _componentsByDisplayId.value += component.getDisplayId() to component
    }

    override fun onStatusBarViewDestroyed(component: HomeStatusBarComponent) {
        _componentsByDisplayId.value -= component.displayId
    }
}

@Module
object HomeStatusBarComponentsRepositoryModule {

    @Provides
    @ElementsIntoSet
    fun repositoryAsLifecycleListener(
        repo: Lazy<HomeStatusBarComponentsRepository>
    ): Set<StatusBarViewLifecycleListener> {
        return if (StatusBarConnectedDisplays.isEnabled) {
            setOf(repo.get())
        } else {
            emptySet()
        }
    }
}
