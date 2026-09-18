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

package com.android.systemui.motioncues

import android.content.Context
import android.view.WindowManager
import com.android.systemui.CoreStartable
import com.android.systemui.motioncues.MotionCuesCommand
import com.android.systemui.motioncues.MotionCuesManager
import com.android.systemui.motioncues.MotionCuesUi
import com.android.systemui.statusbar.policy.ConfigurationController
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

@Module
abstract class MotionCuesModule {
    @Binds
    @IntoMap
    @ClassKey(MotionCuesManager::class)
    abstract fun bindMotionCuesManager(manager: MotionCuesManager): CoreStartable

    @Binds
    @IntoMap
    @ClassKey(MotionCuesCommand::class)
    abstract fun bindMotionCuesCommand(command: MotionCuesCommand): CoreStartable

    companion object {
        @JvmStatic
        @Provides
        fun provideMotionCuesUi(
            context: Context,
            windowManager: WindowManager,
            configurationController: ConfigurationController
        ): MotionCuesUi {
            return MotionCuesUi(context, windowManager, configurationController)
        }
    }
}
