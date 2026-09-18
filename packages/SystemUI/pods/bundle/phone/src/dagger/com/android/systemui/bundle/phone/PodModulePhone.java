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

package com.android.systemui.bundle.phone;

import com.android.systemui.brightness.BrightnessModule;
import com.android.systemui.graphics.ImageLoaderModule;
import com.android.systemui.headline.ui.HeadlineModule;
import com.android.systemui.qs.panels.ui.PanelsUIModule;
import com.android.systemui.retail.impl.RetailModeModule;
import com.android.systemui.statusbar.pipeline.airplane.data.repository.impl.AirplaneModeDataLayerModule;
import com.android.systemui.statusbar.pipeline.airplane.shared.impl.AirplaneModeSharedModule;
import com.android.systemui.util.policy.impl.PolicyRestrictionModule;
import com.android.systemui.util.settings.SettingsUtilModule;

import dagger.Module;

// Do not convert this file to kotlin. For build speed purposes it should remain Java so we can
// skip the slower kotlin compilation process for this module.

@Module(includes = {
        AirplaneModeDataLayerModule.class,
        AirplaneModeSharedModule.class,
        BrightnessModule.class,
        HeadlineModule.class,
        ImageLoaderModule.class,
        PanelsUIModule.class,
        PolicyRestrictionModule.class,
        RetailModeModule.class,
        SettingsUtilModule.class,
})
public interface PodModulePhone {
    // Leave this empty
}
