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

package com.android.systemui.qs.panels.ui

import com.android.systemui.qs.panels.ui.compose.infinitegrid.EditModeLayoutTab
import com.android.systemui.qs.panels.ui.compose.infinitegrid.EditModeLayoutTabImpl
import com.android.systemui.qs.panels.ui.compose.infinitegrid.EditModeTabs
import com.android.systemui.qs.panels.ui.compose.infinitegrid.EditModeTabsImpl
import com.android.systemui.qs.panels.ui.viewmodel.EditModeLayoutTabViewModel
import com.android.systemui.qs.panels.ui.viewmodel.EditModeLayoutTabViewModelImpl
import dagger.Binds
import dagger.Module

@Module
public interface PanelsUIModule {
    @Binds public fun bindEditModeTabs(impl: EditModeTabsImpl): EditModeTabs

    @Binds public fun bindEditLayoutTab(impl: EditModeLayoutTabImpl): EditModeLayoutTab

    @Binds
    public fun bindEditModeLayoutTabViewModel(
        impl: EditModeLayoutTabViewModelImpl
    ): EditModeLayoutTabViewModel
}
