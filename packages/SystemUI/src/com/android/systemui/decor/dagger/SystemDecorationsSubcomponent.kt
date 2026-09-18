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

import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.LifecycleListener
import dagger.Subcomponent
import kotlinx.coroutines.CoroutineScope

/**
 * A subcomponent that exists for each display that has system decorations.
 *
 * Examples of system decorations are the Status Bar, Navigation Bar, or Privacy Dot.
 *
 * The component will be created when a display is added that should have system decorations, and
 * destroyed when that display is removed, or when that display no longer should have system
 * decorations.
 *
 * One example of when a display should no longer have system decorations, is when the display
 * changes from desktop to mirroring mode. Desktop should have system decorations, but mirroring
 * not.
 */
@PerDisplaySystemDecorationsSingleton
@Subcomponent(modules = [SystemDecorationsSubcomponentModule::class])
interface SystemDecorationsSubcomponent {

    /** The coroutine scope for this [Subcomponent] instance. */
    @get:SystemDecorationsDisplayAware val coroutineScope: CoroutineScope

    /** Lifecycle listeners that are notified when this [Subcomponent] is created and destroyed. */
    @get:SystemDecorationsDisplayAware val lifecycleListeners: Set<LifecycleListener>

    @Subcomponent.Factory
    interface Factory {
        fun create(): SystemDecorationsSubcomponent
    }
}
