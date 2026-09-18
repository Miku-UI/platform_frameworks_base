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

package com.android.server.theming;

import android.content.Context;
import android.content.theming.FieldColorSource;
import android.content.theming.ThemeSettings;
import android.content.theming.ThemeStyle;
import android.graphics.Color;

import com.android.internal.R;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.pm.RoSystemFeatures;

import com.google.ux.material.libmonet.dynamiccolor.ColorSpec.SpecVersion;
import com.google.ux.material.libmonet.dynamiccolor.DynamicScheme.Platform;

/**
 * Immutable system configuration for the Theme Service.
 *
 * @hide
 */
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
public record ThemeConfig(
        /** The target platform for color generation (e.g., PHONE, WATCH). */
        Platform platform,
        /** The version of the color specification being used. */
        SpecVersion specVersion,
        /** The hardware color code for the device. */
        String hardwareColorCode,
        /** List of legacy overlay packages that should be cleaned up on boot. */
        String[] legacyOverlays,
        /** Device-specific default theme data from resources. */
        String[] defaultThemeData,
        /** The ultimate fallback theme settings when no user or device defaults are available. */
        ThemeSettings hardcodedFallback,
        /** On iHSUM, the system user can be switched to and therefore maintain their own theme. */
        boolean canSwitchToHeadlessSystemUser) {
    public ThemeConfig(Context context, SystemPropertiesReader reader) {
        this(
                /* platform */ RoSystemFeatures.hasFeatureWatch(context)
                        ? Platform.WATCH
                        : Platform.PHONE,
                /* specVersion */ SpecVersion.SPEC_2026,
                /* hardwareColorCode */ reader.get("ro.boot.hardware.color", ""),
                /* legacyOverlays */context.getResources()
                        .getStringArray(R.array.theming_legacy_overlays),
                /* defaultThemeData */context.getResources()
                        .getStringArray(R.array.theming_defaults),
                /* hardcodedFallback */ new ThemeSettings.Builder()
                        .setThemeStyle(ThemeStyle.TONAL_SPOT)
                        .setColorSource(FieldColorSource.VALUE_PRESET)
                        .setSeedColors(Color.valueOf(0xFF1B6EF3))
                        .build(),
                /* canSwitchToHeadlessSystemUser */ context.getResources()
                        .getBoolean(R.bool.config_canSwitchToHeadlessSystemUser));
    }
}
