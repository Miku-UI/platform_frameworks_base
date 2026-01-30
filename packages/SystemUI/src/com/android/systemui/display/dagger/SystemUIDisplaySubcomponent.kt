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

import com.android.systemui.SysUICutoutProvider
import com.android.systemui.common.ui.ConfigurationState
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.PerDisplaySingleton
import com.android.systemui.display.data.repository.DisplayStateRepository
import com.android.systemui.display.domain.interactor.DisplayStateInteractor
import com.android.systemui.plugins.DarkIconDispatcher
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipsViewModel
import com.android.systemui.statusbar.domain.interactor.StatusBarIconRefreshInteractor
import com.android.systemui.statusbar.phone.SysuiDarkIconDispatcher
import com.android.systemui.statusbar.ui.SystemBarUtilsState
import com.android.systemui.statusbar.window.StatusBarWindowStateController
import dagger.BindsInstance
import dagger.Subcomponent
import javax.inject.Qualifier
import javax.inject.Scope
import kotlinx.coroutines.CoroutineScope

/**
 * Subcomponent for SysUI classes that should be instantiated once per display.
 *
 * All display specific classes should be provided with the @DisplayAware annotation. Once the
 * display is removed, [displayCoroutineScope] gets cancelled. This means that if classes have some
 * teardown step it should be executed when the scope is cancelled. Also note that the scope is
 * cancelled in the background, so any teardown logic should be threadsafe. Cancelling on the main
 * thread is not feasible as it would cause jank.
 */
@PerDisplaySingleton
@Subcomponent(modules = [PerDisplaySystemUIModule::class])
interface SystemUIDisplaySubcomponent {

    @get:DisplayAware val displayCoroutineScope: CoroutineScope

    @get:DisplayAware val displayStateRepository: DisplayStateRepository

    @get:DisplayAware val displayStateInteractor: DisplayStateInteractor

    @get:DisplayAware val statusBarIconRefreshInteractor: StatusBarIconRefreshInteractor

    @get:DisplayAware val lifecycleListeners: Set<LifecycleListener>

    @get:DisplayAware val statusBarWindowStateController: StatusBarWindowStateController

    @get:DisplayAware val ongoingActivityChipsViewModel: OngoingActivityChipsViewModel

    @get:DisplayAware val darkIconDispatcher: DarkIconDispatcher

    @get:DisplayAware val sysuiDarkIconDispatcher: SysuiDarkIconDispatcher

    @get:DisplayAware val systemBarUtilsState: SystemBarUtilsState

    @get:DisplayAware val configurationState: ConfigurationState

    @get:DisplayAware val sysUICutoutProvider: SysUICutoutProvider

    @Subcomponent.Factory
    interface Factory {
        fun create(@BindsInstance @DisplayId displayId: Int): SystemUIDisplaySubcomponent
    }

    /** Scope annotation for singletons associated to a display. */
    @MustBeDocumented
    @Retention(AnnotationRetention.RUNTIME)
    @Scope
    annotation class PerDisplaySingleton

    /** Qualifier used to represent that the object is provided/bound with the proper display. */
    @Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class DisplayAware

    /** Annotates the display id inside the subcomponent. */
    @Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class DisplayId

    /**
     * Annotates the displaylib implementation of a class.
     *
     * TODO(b/408503553): Remove this annotation once the flag is cleaned up.
     */
    @Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class DisplayLib

    /**
     * Listens for lifecycle events of the [SystemUIDisplaySubcomponent], which correspond to the
     * lifecycle of the display associated with this [Subcomponent].
     */
    interface LifecycleListener {
        /**
         * Called when the display associated with this [SystemUIDisplaySubcomponent] has been
         * created, and the [Subcomponent] has been created.
         */
        fun start() {}

        /**
         * Called when the display associated with this [SystemUIDisplaySubcomponent] has been
         * removed, and the component will be destroyed.
         */
        fun stop() {}

        companion object {
            val NOP: LifecycleListener = object : LifecycleListener {}
        }
    }
}
