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

package com.android.systemui.shade.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.android.compose.animation.scene.ContentScope
import com.android.systemui.shade.ui.ShadeColors

/** Scrim for QS/Shade, this will fill the max size, so it should be inside a Box like layout. */
@Composable
fun ContentScope.ShadePanelScrim(isTransparencyEnabled: Boolean, modifier: Modifier = Modifier) {
    // This is the background for the whole scene, as the elements don't necessarily provide
    // a background that extends to the edges.
    Spacer(
        modifier =
            modifier
                .element(Shade.Elements.BackgroundScrim)
                .fillMaxSize()
                .background(color = scrimColor(isTransparencyEnabled))
    )
}

@Composable
@ReadOnlyComposable
private fun scrimColor(isTransparencyEnabled: Boolean): Color {
    // Read configuration to invalidate on changes
    LocalConfiguration.current
    return Color(
        ShadeColors.shadePanel(
            context = LocalContext.current,
            blurSupported = isTransparencyEnabled,
            withScrim = true,
        )
    )
}
