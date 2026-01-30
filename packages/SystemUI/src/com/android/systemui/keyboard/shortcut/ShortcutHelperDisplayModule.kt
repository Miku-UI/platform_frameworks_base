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

package com.android.systemui.keyboard.shortcut

import android.view.Display
import com.android.systemui.Flags
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.PerDisplaySingleton
import com.android.systemui.keyboard.shortcut.ui.ShortcutHelperDialogStarter
import com.android.systemui.keyboard.shortcut.ui.viewmodel.ShortcutHelperViewModel
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet

/**
 * Contains bindings that are [SystemUIDisplaySubcomponent.DisplayAware] related to the shortcut
 * helper.
 */
@Module
interface ShortcutHelperDisplayModule {

    @Binds
    @PerDisplaySingleton
    @DisplayAware
    fun shortcutHelperViewModel(impl: ShortcutHelperViewModel): ShortcutHelperViewModel

    @Binds
    @PerDisplaySingleton
    @DisplayAware
    fun provideShortcutHelperDialogStarter(
        impl: ShortcutHelperDialogStarter
    ): ShortcutHelperDialogStarter

    companion object {
        @Provides
        @PerDisplaySingleton
        @DisplayAware
        @IntoSet
        fun provideShortcutHelperDialogStarterLifeCycleObserver(
            @DisplayAware implLazy: Lazy<ShortcutHelperDialogStarter>,
            @DisplayAware displayId: Int,
        ): SystemUIDisplaySubcomponent.LifecycleListener {
            return if (
                displayId == Display.DEFAULT_DISPLAY || Flags.shortcutHelperMultiDisplaySupport()
            ) {
                implLazy.get()
            } else {
                SystemUIDisplaySubcomponent.LifecycleListener.NOP
            }
        }
    }
}
