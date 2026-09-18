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

package com.android.systemui.statusbar.dagger

import android.content.Context
import android.view.Display
import android.view.WindowManager.LayoutParams.TYPE_STATUS_BAR
import com.android.app.displaylib.PerDisplayRepository
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent
import com.android.systemui.display.data.repository.DisplayWindowPropertiesRepositoryImpl
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.LogBufferFactory
import com.android.systemui.statusbar.chips.sharetoapp.ui.viewmodel.ShareToAppChipViewModel
import com.android.systemui.statusbar.data.StatusBarDataLayerModule
import com.android.systemui.statusbar.data.repository.LightBarControllerStore
import com.android.systemui.statusbar.layout.StatusBarContentInsetsProvider
import com.android.systemui.statusbar.layout.ui.viewmodel.StatusBarContentInsetsViewModel
import com.android.systemui.statusbar.phone.AutoHideController
import com.android.systemui.statusbar.phone.AutoHideControllerImpl
import com.android.systemui.statusbar.phone.LightBarController
import com.android.systemui.statusbar.phone.StatusBarSignalPolicy
import com.android.systemui.statusbar.phone.ongoingcall.OngoingCallLog
import com.android.systemui.statusbar.quickactions.QuickActionsModule
import com.android.systemui.statusbar.ui.StatusBarUiLayerModule
import com.android.systemui.statusbar.ui.SystemBarUtilsProxyImpl
import com.android.systemui.statusbar.window.MultiDisplayStatusBarWindowControllerStore
import com.android.systemui.statusbar.window.StatusBarWindowController
import com.android.systemui.statusbar.window.StatusBarWindowControllerImpl
import com.android.systemui.statusbar.window.StatusBarWindowControllerStore
import com.android.systemui.statusbar.window.StatusBarWindowLog
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

/**
 * A module for **only** classes related to the status bar **UI element**. This module specifically
 * should **not** include:
 * - Classes in the `statusbar` package that are unrelated to the status bar UI.
 * - Status bar classes that are already provided by other modules
 *   ([com.android.systemui.statusbar.pipeline.dagger.StatusBarPipelineModule],
 *   [com.android.systemui.statusbar.policy.dagger.StatusBarPolicyModule], etc.).
 */
@Module(
    includes =
        [
            QuickActionsModule::class,
            StatusBarDataLayerModule::class,
            StatusBarUiLayerModule::class,
            SystemBarUtilsProxyImpl.Module::class,
        ]
)
interface StatusBarModule {

    @Binds
    @IntoMap
    @ClassKey(LightBarController::class)
    fun lightBarControllerAsCoreStartable(controller: LightBarController): CoreStartable

    @Binds
    @IntoMap
    @ClassKey(StatusBarSignalPolicy::class)
    fun bindStatusBarSignalPolicy(impl: StatusBarSignalPolicy): CoreStartable

    @Binds
    @SysUISingleton
    fun statusBarWindowControllerFactory(
        implFactory: StatusBarWindowControllerImpl.Factory
    ): StatusBarWindowController.Factory

    @Binds @SysUISingleton fun autoHideController(impl: AutoHideControllerImpl): AutoHideController

    @Binds
    @SysUISingleton
    fun windowControllerStore(
        store: MultiDisplayStatusBarWindowControllerStore
    ): StatusBarWindowControllerStore

    @Binds
    @SysUISingleton
    @IntoMap
    @ClassKey(MultiDisplayStatusBarWindowControllerStore::class)
    fun multiDisplayControllerStoreAsCoreStartable(
        store: MultiDisplayStatusBarWindowControllerStore
    ): CoreStartable

    companion object {

        @Provides
        @SysUISingleton
        fun lightBarController(store: LightBarControllerStore): LightBarController {
            return store.defaultDisplay
        }

        @Provides
        @SysUISingleton
        @IntoMap
        @ClassKey(ShareToAppChipViewModel::class)
        fun providesShareToAppChipViewModel(
            shareToAppChipViewModelLazy: Lazy<ShareToAppChipViewModel>
        ): CoreStartable {
            return if (com.android.media.projection.flags.Flags.showStopDialogPostCallEnd()) {
                shareToAppChipViewModelLazy.get()
            } else {
                CoreStartable.NOP
            }
        }

        @Provides
        @SysUISingleton
        @OngoingCallLog
        fun provideOngoingCallLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("OngoingCall", 75)
        }

        @Provides
        @SysUISingleton
        @StatusBarWindowLog
        fun provideWindowLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("StatusBarWindow", 120)
        }

        @Provides
        @SysUISingleton
        fun contentInsetsProvider(
            displaySubcomponentRepo: PerDisplayRepository<SystemUIDisplaySubcomponent>
        ): StatusBarContentInsetsProvider {
            val displaySubcomponent = displaySubcomponentRepo[Display.DEFAULT_DISPLAY]!!
            return displaySubcomponent.statusBarContentInsetsProvider
        }

        @Provides
        @SysUISingleton
        fun contentInsetsViewModel(
            insetsProvider: StatusBarContentInsetsProvider
        ): StatusBarContentInsetsViewModel {
            return StatusBarContentInsetsViewModel(insetsProvider)
        }

        @Provides
        @StatusBarMain
        fun provideDefaultStatusBarContext(
            repo: DisplayWindowPropertiesRepositoryImpl,
            @Main appContext: Context,
        ): Context {
            return repo.get(Display.DEFAULT_DISPLAY, TYPE_STATUS_BAR)?.context ?: appContext
        }
    }
}
