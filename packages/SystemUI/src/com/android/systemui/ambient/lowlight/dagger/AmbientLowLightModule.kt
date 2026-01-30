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

package com.android.systemui.ambient.lowlight.dagger

import android.os.PowerManager
import com.android.systemui.lowlight.shared.model.LowLightActionEntry
import com.android.systemui.lowlight.shared.model.LowLightDisplayBehavior
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet

@Module(subcomponents = [AmbientSuppressionComponent::class])
object AmbientLowLightModule {

    @Provides
    @IntoSet
    fun providesScreenOffLowLightActionEntry(
        factory: AmbientSuppressionComponent.Factory
    ): LowLightActionEntry {
        return LowLightActionEntry(LowLightDisplayBehavior.SCREEN_OFF) {
            factory.create(PowerManager.FLAG_AMBIENT_SUPPRESSION_ALL).getLowLightAction()
        }
    }

    @Provides
    @IntoSet
    fun providesDreamOffLowLightActionEntry(
        factory: AmbientSuppressionComponent.Factory
    ): LowLightActionEntry {
        return LowLightActionEntry(LowLightDisplayBehavior.NO_DREAM) {
            factory.create(PowerManager.FLAG_AMBIENT_SUPPRESSION_DREAM).getLowLightAction()
        }
    }
}
