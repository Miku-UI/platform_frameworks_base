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

package com.android.server.wm;

import static android.content.pm.ActivityInfo.OVERRIDE_EXCLUDE_CAPTION_INSETS_FROM_APP_BOUNDS;
import static android.view.WindowManager.PROPERTY_COMPAT_ALLOW_EXCLUDE_CAPTION_INSETS;

import static com.android.server.wm.AppCompatUtils.isChangeEnabled;

import android.annotation.NonNull;

import com.android.server.wm.utils.OptPropFactory;

/**
 * Encapsulates app compat configurations and overrides related to sandboxing bounds.
 */
class AppCompatSandboxOverrides {
    @NonNull
    private final ActivityRecord mActivityRecord;
    @NonNull
    private final OptPropFactory.OptProp mAllowExcludeCaptionInsetsOptProp;

    AppCompatSandboxOverrides(@NonNull ActivityRecord activityRecord,
            @NonNull OptPropFactory optPropBuilder) {
        mActivityRecord = activityRecord;
        mAllowExcludeCaptionInsetsOptProp = optPropBuilder.create(
                PROPERTY_COMPAT_ALLOW_EXCLUDE_CAPTION_INSETS);
    }

    /**
     * Returns {@code true} if
     * {@link android.content.pm.ActivityInfo#OVERRIDE_EXCLUDE_CAPTION_INSETS_FROM_APP_BOUNDS}
     * is enabled and the app has not opted out via the
     * {@link android.view.WindowManager#PROPERTY_COMPAT_ALLOW_EXCLUDE_CAPTION_INSETS} property.
     */
    boolean isOverrideExcludeCaptionInsetsAllowed() {
        return mAllowExcludeCaptionInsetsOptProp.shouldEnableWithOptInOverrideAndOptOutProperty(
                isChangeEnabled(mActivityRecord, OVERRIDE_EXCLUDE_CAPTION_INSETS_FROM_APP_BOUNDS));
    }

}
