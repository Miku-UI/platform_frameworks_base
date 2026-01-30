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

package com.android.systemui.media.remedia.ui

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.media.remedia.ui.viewmodel.MediaFalsingSystem
import com.android.systemui.media.remedia.ui.viewmodel.MediaFalsingSystemImpl
import dagger.Module
import dagger.Provides

/** Dagger module for injecting media controls UI interfaces. */
@Module
interface MediaUiModule {

    companion object {
        @Provides
        @SysUISingleton
        fun providesMediaFalsingSystem(falsingSystem: MediaFalsingSystemImpl): MediaFalsingSystem {
            return falsingSystem
        }
    }
}
