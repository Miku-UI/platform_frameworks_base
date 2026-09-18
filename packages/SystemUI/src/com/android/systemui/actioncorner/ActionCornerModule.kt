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

package com.android.systemui.actioncorner

import com.android.systemui.CoreStartable
import com.android.systemui.actioncorner.data.repository.ActionCornerRepository
import com.android.systemui.actioncorner.data.repository.ActionCornerRepositoryImpl
import com.android.systemui.settings.UserSettingsRepositoryModule
import dagger.Binds
import dagger.Module
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

@Module(includes = [UserSettingsRepositoryModule::class])
abstract class ActionCornerModule {
    @Binds
    @IntoMap
    @ClassKey(ActionCornerStartable::class)
    abstract fun bindActionCornerStartable(updater: ActionCornerStartable): CoreStartable

    @Binds
    abstract fun bindActionCornerRepository(
        impl: ActionCornerRepositoryImpl
    ): ActionCornerRepository
}
