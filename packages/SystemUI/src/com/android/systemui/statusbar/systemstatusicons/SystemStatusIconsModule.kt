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

package com.android.systemui.statusbar.systemstatusicons

import android.content.Context
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import dagger.Module
import dagger.Provides
import javax.inject.Qualifier

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class SystemStatusOrderedIconSlotNames

@Module
object SystemStatusIconsModule {

    /**
     * Provides the ordered list of status bar icon slot names read from the `config_statusBarIcons`
     * resource array.
     */
    @Provides
    @SysUISingleton
    @SystemStatusOrderedIconSlotNames
    fun provideSystemStatusOrderedIconSlotNames(@Application context: Context): Array<String> {
        return context.resources.getStringArray(com.android.internal.R.array.config_statusBarIcons)
    }
}
