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

package com.android.systemui.wallpapers.dagger

import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.shared.Flags.extendedWallpaperEffects
import com.android.systemui.wallpapers.WallpaperPresentationEnabled
import com.android.systemui.wallpapers.data.repository.WallpaperRepository
import com.android.systemui.wallpapers.data.repository.WallpaperRepositoryImpl
import com.android.systemui.wallpapers.domain.interactor.DisplayWallpaperPresentationInteractor.WallpaperPresentationType
import com.android.systemui.wallpapers.domain.interactor.DisplayWallpaperPresentationInteractor.WallpaperPresentationType.KEYGUARD
import com.android.systemui.wallpapers.domain.interactor.DisplayWallpaperPresentationInteractor.WallpaperPresentationType.PROVISIONING
import com.android.systemui.wallpapers.domain.interactor.WallpaperFocalAreaInteractor
import com.android.systemui.wallpapers.ui.presentation.KeyguardWallpaperPresentationFactory
import com.android.systemui.wallpapers.ui.presentation.ProvisioningWallpaperPresentationFactory
import com.android.systemui.wallpapers.ui.presentation.WallpaperPresentationFactory
import com.android.systemui.wallpapers.ui.presentation.WallpaperPresentationTypeKey
import com.android.window.flags.Flags
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import dagger.multibindings.Multibinds

@Module
interface WallpaperModule {
    @Binds
    @SysUISingleton
    fun bindWallpaperRepository(impl: WallpaperRepositoryImpl): WallpaperRepository

    @Multibinds
    fun bindWallpaperPresentationFactories():
        Map<
            @JvmSuppressWildcards
            WallpaperPresentationType,
            @JvmSuppressWildcards
            WallpaperPresentationFactory,
        >

    @Binds
    @IntoMap
    @WallpaperPresentationTypeKey(PROVISIONING)
    fun bindProvisioningWallpaperPresentationFactory(
        impl: ProvisioningWallpaperPresentationFactory
    ): WallpaperPresentationFactory

    @Binds
    @IntoMap
    @WallpaperPresentationTypeKey(KEYGUARD)
    fun bindKeyguardWallpaperPresentationFactory(
        impl: KeyguardWallpaperPresentationFactory
    ): WallpaperPresentationFactory

    companion object {
        @Provides
        @IntoMap
        @ClassKey(WallpaperFocalAreaInteractor::class)
        fun provideWallpaperFocalAreaInteractor(
            wallpaperFocalAreaInteractor: WallpaperFocalAreaInteractor
        ): CoreStartable {
            return if (extendedWallpaperEffects()) {
                wallpaperFocalAreaInteractor
            } else {
                CoreStartable.NOP
            }
        }

        @Provides
        @WallpaperPresentationEnabled
        fun providesWallpaperPresentationEnabled(): Boolean =
            Flags.enableConnectedDisplaysWallpaperPresentations()
    }
}
