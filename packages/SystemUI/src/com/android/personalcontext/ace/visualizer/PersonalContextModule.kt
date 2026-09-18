/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.personalcontext.dagger

import android.app.Service
import android.content.Context
import android.service.personalcontext.PersonalContextManager
import com.android.systemui.CoreStartable
import com.android.systemui.personalcontext.AutofillAttributionStartable
import com.android.systemui.personalcontext.AutofillRendererService
import com.android.systemui.personalcontext.SysuiVisualizerService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

/** Dagger module for platform dependencies. */
@Module
interface PersonalContextModule {
    @Binds
    @IntoMap
    @ClassKey(SysuiVisualizerService::class)
    fun bindSysuiVisualizerService(service: SysuiVisualizerService): Service

    /** Binds the renderer service that converts personal context insights to inline autofill UI. */
    @Binds
    @IntoMap
    @ClassKey(AutofillRendererService::class)
    fun bindAutofillRenderService(service: AutofillRendererService): Service

    /**
     * Binds the {@link CoreStartable} that listens to the autofill attribution broadcast and calls
     * into {@link PersonalContextManager}.
     */
    @Binds
    @IntoMap
    @ClassKey(AutofillAttributionStartable::class)
    fun bindAutofillAttributionStartable(impl: AutofillAttributionStartable): CoreStartable

    companion object {

        // TODO(b/493245945): move into FrameworkServicesModule
        @Provides
        fun providePersonalContextManager(context: Context): PersonalContextManager? {
            return context.getSystemService(PersonalContextManager::class.java)
        }
    }
}
