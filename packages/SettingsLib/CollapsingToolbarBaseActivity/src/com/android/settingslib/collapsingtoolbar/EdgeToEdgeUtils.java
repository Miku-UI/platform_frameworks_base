/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.settingslib.collapsingtoolbar;

import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.os.Build;
import android.view.ViewGroup;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.android.settingslib.widget.SetupWizardHelper;

/**
 * Util class for edge to edge.
 */
public class EdgeToEdgeUtils {
    private EdgeToEdgeUtils() {
    }

    /**
     * Enable Edge to Edge and handle overlaps using insets. It should be called before
     * setContentView.
     */
    public static void enable(@NonNull ComponentActivity activity) {
        enable(activity, false);
    }

    /**
     * Enable Edge to Edge and handle overlaps using insets. It should be called before
     * setContentView.
     *
     * @param useThemeColors whether to use the provided style attributes such as
     *                            statusBarColor or navigationBarColor
     */
    public static void enable(@NonNull ComponentActivity activity, boolean useThemeColors) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return;
        }

        if (SetupWizardHelper.isAnySetupWizard(activity.getIntent())) {
            return;
        }

        if (useThemeColors) {
            final TypedArray typedArray = activity.getTheme().obtainStyledAttributes(
                    new int[] {android.R.attr.statusBarColor, android.R.attr.navigationBarColor});
            final int statusBarColor = typedArray.getColor(0, 0);
            final int navigationBarColor = typedArray.getColor(1, 0);
            typedArray.recycle();

            // EdgeToEdge#getScrimWithEnforcedContrast will make the status/navigation bar color
            // Color.TRANSPARENT if nightMode is UiModeManager.MODE_NIGHT_AUTO. Hence, we can't just
            // use SystemBarStyle.auto(lightScrim, darkScrim) otherwise it will always change it
            // back to Color.TRANSPARENT instead of the statusBarColor or navigationBarColor
            // attribute set by OEMs.
            SystemBarStyle statusBarStyle;
            SystemBarStyle navigationBarStyle;
            int nightMode = activity.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
            if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
                statusBarStyle = SystemBarStyle.dark(statusBarColor);
                navigationBarStyle = SystemBarStyle.dark(navigationBarColor);
            } else {
                statusBarStyle = SystemBarStyle.light(statusBarColor, statusBarColor);
                navigationBarStyle = SystemBarStyle.light(navigationBarColor, navigationBarColor);
            }

            EdgeToEdge.enable(activity, statusBarStyle, navigationBarStyle);
            // In EdgeToEdge we set isNavigationBarContrastEnforced to true if
            // navigationBarStyle.nightMode is UiModeManager.MODE_NIGHT_AUTO. Hence, setting this to
            // true here to maintain the original behavior.
            activity.getWindow().setNavigationBarContrastEnforced(true);
        } else {
            EdgeToEdge.enable(activity);
        }

        ViewCompat.setOnApplyWindowInsetsListener(activity.findViewById(android.R.id.content),
                (v, windowInsets) -> {
                    Insets insets = windowInsets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                                    | WindowInsetsCompat.Type.ime()
                                    | WindowInsetsCompat.Type.displayCutout());
                    int statusBarHeight = activity.getWindow().getDecorView().getRootWindowInsets()
                            .getInsets(WindowInsetsCompat.Type.statusBars()).top;
                    // Apply the insets paddings to the view.
                    v.setPadding(insets.left, statusBarHeight, insets.right, insets.bottom);
                    ((ViewGroup)v).setClipToPadding(false);
                    ((ViewGroup)v).setClipChildren(false);

                    // Return CONSUMED if you don't want the window insets to keep being
                    // passed down to descendant views.
                    return WindowInsetsCompat.CONSUMED;
                });
    }
}
