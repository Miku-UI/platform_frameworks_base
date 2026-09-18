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
package com.android.server.accessibility;

import static android.security.advancedprotection.AdvancedProtectionManager.FEATURE_ID_RESTRICT_NON_TOOL_A11Y_SERVICES;

import android.annotation.NonNull;
import android.content.Context;

import com.android.server.security.advancedprotection.features.AdvancedProtectionProvider;

import java.util.List;

/**
 * Provides information to the system regarding Advanced Protection Mode (APM) features
 * specifically related to accessibility services.
 *
 * <p>This provider registers and exposes a single feature ID to the AdvancedProtectionManager,
 * allowing the system to restrict non-tool accessibility services when APM is enabled.</p>
 */
public class AccessibilityServiceAdvancedProtectionProvider extends AdvancedProtectionProvider {

    /**
     * Retrieves the list of active Advanced Protection features related to accessibility.
     *
     * @param context The application context.
     * @return A list of AdvancedProtectionFeature objects.
     */
    @Override
    public @NonNull List<Integer> getFeatureIds(@NonNull Context context) {
        return List.of(FEATURE_ID_RESTRICT_NON_TOOL_A11Y_SERVICES);
    }
}
