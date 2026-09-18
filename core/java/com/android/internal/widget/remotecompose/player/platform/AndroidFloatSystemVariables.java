/*
 * Copyright 2026 The Android Open Source Project
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

package com.android.internal.widget.remotecompose.player.platform;

import android.annotation.NonNull;
import android.content.res.Resources;

import com.android.internal.widget.remotecompose.player.RemoteComposePlayer;

public class AndroidFloatSystemVariables implements RemoteComposePlayer.FloatSystemVariables {
    private static final String BACKGROUND_RADIUS = "system.system_app_widget_background_radius";
    private static final String INNER_RADIUS = "system.system_app_widget_inner_radius";
    private static final String FONT_WEIGHT = "system.font_weight";

    @Override
    public void loadSystemVariables(@NonNull RemoteComposeView player, @NonNull String[] var) {
        Resources res = player.getResources();
        int resId = 0;
        for (int i = 0; i < var.length; i++) {
            switch (var[i]) {
                case BACKGROUND_RADIUS:
                    resId =
                            res.getIdentifier(
                                    "system_app_widget_background_radius", "dimen", "android");
                    if (resId != 0) {
                        // 2. If found (Android 12+), return the pixel value
                        player.setLocalFloat(BACKGROUND_RADIUS, res.getDimension(resId));
                    } else {
                        // 3. Fallback: Use a standard Material 3 "Extra Large" rounding (28dp)
                        // or a common widget default (16dp).
                        float density = res.getDisplayMetrics().density;
                        player.setLocalFloat(BACKGROUND_RADIUS, 28 * density);
                    }
                    break;

                case INNER_RADIUS:
                    resId = res.getIdentifier("system_app_widget_inner_radius", "dimen", "android");
                    if (resId != 0) {
                        player.setLocalFloat(INNER_RADIUS, res.getDimension(resId));
                    } else {
                        // Fallback for Android 11 and below (8dp is a common inner rounding)
                        float density = res.getDisplayMetrics().density;
                        player.setLocalFloat(INNER_RADIUS, 8 * density);
                    }
                    break;
                case FONT_WEIGHT:
                    float baseWeight = 400; // Normal
                    int userAdjustment = 0;

                    userAdjustment = res.getConfiguration().fontWeightAdjustment;

                    player.setLocalFloat(FONT_WEIGHT, (baseWeight + userAdjustment));
            }
        }
    }
}
