/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.systemui.statusbar.phone.dagger

import android.content.Context
import android.view.Display
import com.android.app.displaylib.PerDisplayInstanceRepositoryImpl
import com.android.app.displaylib.PerDisplayRepository
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Default
import com.android.systemui.display.dagger.ReferenceSysUIDisplaySubcomponent
import com.android.systemui.statusbar.CommandQueue
import com.android.systemui.statusbar.core.CommandQueueInitializer
import com.android.systemui.statusbar.core.MultiDisplayStatusBarInitializerStore
import com.android.systemui.statusbar.core.MultiDisplayStatusBarOrchestratorStore
import com.android.systemui.statusbar.core.MultiDisplayStatusBarStarter
import com.android.systemui.statusbar.core.StatusBarInitializer
import com.android.systemui.statusbar.core.StatusBarInitializerImpl
import com.android.systemui.statusbar.core.StatusBarInitializerStore
import com.android.systemui.statusbar.data.repository.PrivacyDotViewControllerStoreModule
import com.android.systemui.statusbar.data.repository.PrivacyDotWindowControllerStoreModule
import com.android.systemui.statusbar.domain.interactor.StatusBarIconRefreshInteractor
import com.android.systemui.statusbar.domain.interactor.StatusBarIconRefreshPerDisplayInstanceProvider
import com.android.systemui.statusbar.events.PrivacyDotViewControllerModule
import com.android.systemui.statusbar.gesture.StatusBarLongPressGestureDetector
import com.android.systemui.statusbar.phone.AutoHideControllerStore
import com.android.systemui.statusbar.phone.CentralSurfacesCommandQueueCallbacks
import com.android.systemui.statusbar.phone.MultiDisplayAutoHideControllerStore
import com.android.systemui.statusbar.window.StatusBarWindowControllerStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

/** Similar in purpose to [StatusBarModule], but scoped only to phones */
@Module(
    includes =
        [
            PrivacyDotViewControllerModule::class,
            PrivacyDotWindowControllerStoreModule::class,
            PrivacyDotViewControllerStoreModule::class,
        ]
)
interface StatusBarPhoneModule {

    @Binds
    abstract fun commandQCallbacks(
        impl: CentralSurfacesCommandQueueCallbacks
    ): CommandQueue.Callbacks

    @Binds
    fun initializerFactory(
        implFactory: StatusBarInitializerImpl.Factory
    ): StatusBarInitializer.Factory

    @Binds fun statusBarInitializer(@Default impl: StatusBarInitializerImpl): StatusBarInitializer

    @Binds
    @SysUISingleton
    @IntoMap
    @ClassKey(MultiDisplayStatusBarStarter::class)
    fun multiDisplayStarter(
        multiDisplayStatusBarStarter: MultiDisplayStatusBarStarter
    ): CoreStartable

    @Binds
    @SysUISingleton
    @IntoMap
    @ClassKey(CommandQueueInitializer::class)
    fun commandQueueInitializerCoreStartable(initializer: CommandQueueInitializer): CoreStartable

    @Binds
    @SysUISingleton
    @IntoMap
    @ClassKey(StatusBarInitializerStore::class)
    fun initializerStoreAsCoreStartable(
        multiDisplayStore: MultiDisplayStatusBarInitializerStore
    ): CoreStartable

    @Binds
    @SysUISingleton
    fun initializerStore(
        multiDisplayStore: MultiDisplayStatusBarInitializerStore
    ): StatusBarInitializerStore

    @Binds
    @SysUISingleton
    fun autoHideStore(multiDisplay: MultiDisplayAutoHideControllerStore): AutoHideControllerStore

    @Binds
    @SysUISingleton
    @IntoMap
    @ClassKey(AutoHideControllerStore::class)
    fun storeAsCoreStartable(multiDisplay: MultiDisplayAutoHideControllerStore): CoreStartable

    @Binds
    @SysUISingleton
    @IntoMap
    @ClassKey(MultiDisplayStatusBarOrchestratorStore::class)
    fun orchestratorStoreAsCoreStartable(
        multiDisplay: MultiDisplayStatusBarOrchestratorStore
    ): CoreStartable

    companion object {

        // Dagger doesn't support providing AssistedInject types, without a qualifier. Using the
        // Default qualifier for this reason.
        @Default
        @Provides
        @SysUISingleton
        fun statusBarInitializerImpl(
            implFactory: StatusBarInitializerImpl.Factory,
            statusBarWindowControllerStore: StatusBarWindowControllerStore,
            displayComponentRepo: PerDisplayRepository<ReferenceSysUIDisplaySubcomponent>,
        ): StatusBarInitializerImpl {
            val systemUIDisplaySubcomponent = displayComponentRepo[Display.DEFAULT_DISPLAY]!!
            return implFactory.create(
                Display.DEFAULT_DISPLAY,
                statusBarWindowControllerStore.defaultDisplay,
                systemUIDisplaySubcomponent.statusBarModeRepo,
                systemUIDisplaySubcomponent.statusBarRootFactory,
                systemUIDisplaySubcomponent.homeStatusBarComponentFactory,
            )
        }

        @Provides
        @Default
        @SysUISingleton
        fun defaultLongPressGestureDetector(
            context: Context,
            factory: StatusBarLongPressGestureDetector.Factory,
        ): StatusBarLongPressGestureDetector {
            return factory.create(context)
        }

        /**
         * This is added for compat with SysUISingleton scoped objects.
         * StatusBarIconRefreshInteractor is provided as @PerDisplaysingleton, and should be used as
         * such from per-display classes.
         */
        @SysUISingleton
        @Provides
        fun provideStatusBarIconRefreshPerDisplayProvider(
            repositoryFactory:
                PerDisplayInstanceRepositoryImpl.Factory<StatusBarIconRefreshInteractor>,
            instanceProvider: StatusBarIconRefreshPerDisplayInstanceProvider,
        ): PerDisplayRepository<StatusBarIconRefreshInteractor> {
            return repositoryFactory.create(
                debugName = "StatusBarIconRefreshInteractor",
                instanceProvider,
            )
        }
    }
}
