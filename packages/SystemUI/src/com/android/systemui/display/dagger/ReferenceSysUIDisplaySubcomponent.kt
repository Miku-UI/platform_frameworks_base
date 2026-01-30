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

package com.android.systemui.display.dagger

import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayId
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.PerDisplaySingleton
import com.android.systemui.statusbar.phone.fragment.CollapsedStatusBarFragment
import com.android.systemui.statusbar.phone.fragment.dagger.HomeStatusBarComponent
import com.android.systemui.statusbar.pipeline.shared.ui.binder.HomeStatusBarViewBinder
import com.android.systemui.statusbar.pipeline.shared.ui.composable.StatusBarRootFactory
import com.android.systemui.statusbar.pipeline.shared.ui.viewmodel.HomeStatusBarViewModel.HomeStatusBarViewModelFactory
import dagger.BindsInstance
import dagger.Subcomponent
import javax.inject.Provider

/**
 * AOSP subcomponent for SysUI classes that should be instantiated once per display.
 *
 * If the class is not specific to AOSP SysUI, it should be added to the parent
 * [SystemUIDisplaySubcomponent] instead.
 */
@PerDisplaySingleton
@Subcomponent(modules = [PerDisplaySystemUIModule::class, PerDisplayReferenceSystemUIModule::class])
interface ReferenceSysUIDisplaySubcomponent : SystemUIDisplaySubcomponent {

    /**
     * A display specific factory that allows to create new instances of [HomeStatusBarComponent],
     * which is now a [Subcomponent] of [SystemUIDisplaySubcomponent].
     *
     * The newly created [HomeStatusBarComponent] will automatically inherit all of the display
     * specific objects in this [SystemUIDisplaySubcomponent] and [SystemUIComponent].
     */
    @get:DisplayAware val homeStatusBarComponentFactory: HomeStatusBarComponent.Factory

    @Deprecated(
        """
        Provided just for backwards compatibility with the [CollapsedStatusBarFragment] 
        infrastructure, which is going to be deleted once the [StatusBarRootModernization] flag us 
        fully rolled out."""
    )
    @get:DisplayAware
    val statusBarFragmentProvider: Provider<CollapsedStatusBarFragment>

    @get:DisplayAware val homeStatusBarViewModelFactory: HomeStatusBarViewModelFactory

    @get:DisplayAware val homeStatusBarViewBinder: HomeStatusBarViewBinder

    @get:DisplayAware val statusBarRootFactory: StatusBarRootFactory

    @Subcomponent.Factory
    interface Factory : SystemUIDisplaySubcomponent.Factory {
        override fun create(
            @BindsInstance @DisplayId displayId: Int
        ): ReferenceSysUIDisplaySubcomponent
    }
}
