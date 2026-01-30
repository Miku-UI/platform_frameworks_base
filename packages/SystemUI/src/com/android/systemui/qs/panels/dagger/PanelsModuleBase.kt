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

package com.android.systemui.qs.panels.dagger

import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.LogBufferFactory
import com.android.systemui.log.table.TableLogBuffer
import com.android.systemui.log.table.TableLogBufferFactory
import com.android.systemui.qs.panels.data.repository.AppIconRepository
import com.android.systemui.qs.panels.data.repository.AppIconRepositoryImpl
import com.android.systemui.qs.panels.domain.interactor.EditTilesResetInteractor
import com.android.systemui.qs.panels.domain.interactor.SizedTilesResetInteractor
import com.android.systemui.qs.panels.domain.startable.QSLargeSpecsCommand
import com.android.systemui.qs.panels.domain.startable.QSPanelsCoreStartable
import com.android.systemui.qs.panels.shared.model.GridLayoutType
import com.android.systemui.qs.panels.shared.model.InfiniteGridLayoutType
import com.android.systemui.qs.panels.shared.model.PaginatedGridLayoutType
import com.android.systemui.qs.panels.shared.model.PanelsLog
import com.android.systemui.qs.panels.shared.model.QSFragmentComposeClippingTableLog
import com.android.systemui.qs.panels.ui.compose.GridLayout
import com.android.systemui.qs.panels.ui.compose.PaginatableGridLayout
import com.android.systemui.qs.panels.ui.compose.PaginatedGridLayout
import com.android.systemui.qs.panels.ui.compose.infinitegrid.InfiniteGridLayout
import com.android.systemui.qs.panels.ui.viewmodel.IconTilesViewModel
import com.android.systemui.qs.panels.ui.viewmodel.IconTilesViewModelImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import dagger.multibindings.IntoSet
import javax.inject.Named

@Module
interface PanelsModuleBase {

    @Binds
    fun bindEditTilesResetInteractor(impl: SizedTilesResetInteractor): EditTilesResetInteractor

    @Binds fun bindIconTilesViewModel(impl: IconTilesViewModelImpl): IconTilesViewModel

    @Binds
    @PaginatedBaseLayoutType
    fun bindPaginatedBaseGridLayout(impl: InfiniteGridLayout): PaginatableGridLayout

    @Binds @Named("Default") fun bindDefaultGridLayout(impl: PaginatedGridLayout): GridLayout

    @Binds
    @IntoMap
    @ClassKey(QSPanelsCoreStartable::class)
    fun bindQSPanelsCoreStartable(impl: QSPanelsCoreStartable): CoreStartable

    @Binds
    @IntoMap
    @ClassKey(QSLargeSpecsCommand::class)
    fun bindQSLargeSpecsCommand(impl: QSLargeSpecsCommand): CoreStartable

    @Binds
    fun bindsAppIconRepositoryFactory(
        impl: AppIconRepositoryImpl.Factory
    ): AppIconRepository.Factory

    companion object {
        @Provides
        @SysUISingleton
        @PanelsLog
        fun providesPanelsLog(factory: LogBufferFactory): LogBuffer {
            return factory.create("PanelsLog", 50)
        }

        @Provides
        @SysUISingleton
        @QSFragmentComposeClippingTableLog
        fun providesQSFragmentComposeClippingLog(factory: TableLogBufferFactory): TableLogBuffer {
            return factory.create("QSFragmentComposeClippingTableLog", 100)
        }

        @Provides
        @IntoSet
        fun provideGridLayout(gridLayout: InfiniteGridLayout): Pair<GridLayoutType, GridLayout> {
            return Pair(InfiniteGridLayoutType, gridLayout)
        }

        @Provides
        @IntoSet
        fun providePaginatedGridLayout(
            gridLayout: PaginatedGridLayout
        ): Pair<GridLayoutType, GridLayout> {
            return Pair(PaginatedGridLayoutType, gridLayout)
        }

        @Provides
        fun provideGridLayoutMap(
            entries: Set<@JvmSuppressWildcards Pair<GridLayoutType, GridLayout>>
        ): Map<GridLayoutType, GridLayout> {
            return entries.toMap()
        }

        @Provides
        fun provideGridLayoutTypes(
            entries: Set<@JvmSuppressWildcards Pair<GridLayoutType, GridLayout>>
        ): Set<GridLayoutType> {
            return entries.map { it.first }.toSet()
        }
    }
}
