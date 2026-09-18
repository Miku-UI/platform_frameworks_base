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

package com.android.systemui.decor.dagger

import com.android.systemui.coroutines.newTracingContext
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayId
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.LifecycleListener
import dagger.Module
import dagger.Provides
import dagger.multibindings.Multibinds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/**
 * Provides dependencies for the [SystemDecorationsSubcomponent]. This [Module] will be included in
 * **ALL** variants of SystemUI.
 *
 * If a dependency is not needed in all SystemUI variants, add it to
 * [ReferenceSystemDecorationsSubcomponentModule] instead.
 */
@Module
interface SystemDecorationsSubcomponentModule {

    @Multibinds @SystemDecorationsDisplayAware fun lifecycleListeners(): Set<LifecycleListener>

    companion object {

        @Provides
        @PerDisplaySystemDecorationsSingleton
        @SystemDecorationsDisplayAware
        fun provideCoroutineScope(
            @Background backgroundDispatcher: CoroutineDispatcher,
            @DisplayId displayId: Int,
        ): CoroutineScope {
            return CoroutineScope(
                backgroundDispatcher +
                    newTracingContext("SystemDecorationsScope(displayId=$displayId)")
            )
        }
    }
}
