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

package com.android.systemui.flashlight

import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.flashlight.data.repository.FlashlightRepository
import com.android.systemui.flashlight.data.repository.FlashlightRepositoryImpl
import com.android.systemui.flashlight.flags.FlashlightStrength
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.LogBufferFactory
import com.android.systemui.log.dagger.FlashlightLog
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

/** Module for repositories that provide data regarding flashlight state. */
@Module
interface FlashlightModule {
    @Binds fun bindsFlashlightRepoImpl(impl: FlashlightRepositoryImpl): FlashlightRepository

    companion object {
        @Provides
        @IntoMap
        @ClassKey(FlashlightRepositoryImpl::class)
        fun binds(flashlightRepositoryImpl: Lazy<FlashlightRepositoryImpl>): CoreStartable {
            return if (FlashlightStrength.isEnabled) {
                flashlightRepositoryImpl.get()
            } else {
                CoreStartable.NOP
            }
        }

        /** Provides a logging buffer for logs related to Flashlight. */
        @Provides
        @SysUISingleton
        @FlashlightLog
        fun provideFlashlightLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("FlashlightLog", 100, true)
        }
    }
}
