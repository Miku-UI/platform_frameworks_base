/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.locationbutton.ui.compose

import android.content.res.Configuration
import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.trace
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.locationbutton.ui.viewmodel.LocationButtonViewModel
import com.android.systemui.res.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LocationButton(
    viewModelFactory: LocationButtonViewModel.Factory,
    sessionId: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    trace("LocationButton#composable") {
        val buttonViewModel =
            rememberViewModel("LocationButton") { viewModelFactory.create(sessionId) }
        val viewModel = buttonViewModel.getButtonViewModel() ?: return

        val systemContext = LocalContext.current
        val clientLocales = viewModel.configuration.locales

        // Only use locales from client configuration. Using densityDpi, fontScale etc. from client
        // configuration isn't safe. Layout direction is derived from locales.
        val context =
            remember(clientLocales, systemContext) {
                val configuration = Configuration(systemContext.resources.configuration)
                configuration.setLocales(clientLocales)
                systemContext.createConfigurationContext(configuration)
            }

        val layoutDirection =
            if (context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                LayoutDirection.Rtl
            } else {
                LayoutDirection.Ltr
            }

        CompositionLocalProvider(
            LocalContext provides context,
            LocalLayoutDirection provides layoutDirection,
        ) {
            val contentWidth = viewModel.width - viewModel.paddingLeft - viewModel.paddingRight
            val contentHeight = viewModel.height - viewModel.paddingTop - viewModel.paddingBottom

            // Use the smaller dimension as the reference height for scaling internal elements
            // (icon, text, padding). This prevents clipping in extremely tall and narrow buttons.
            val referenceHeight = minOf(contentWidth, contentHeight)

            val contentPadding =
                remember(viewModel.textResId, referenceHeight) {
                    if (viewModel.textResId != null) {
                        val defaultPadding = ButtonDefaults.contentPaddingFor(referenceHeight)
                        // The content is already centered vertically, and vertical padding can make
                        // the text clipped when height is smaller to fit the text.
                        PaddingValues(
                            start = defaultPadding.calculateStartPadding(layoutDirection),
                            top = 0.dp,
                            end = defaultPadding.calculateEndPadding(layoutDirection),
                            bottom = 0.dp
                        )
                    } else {
                        PaddingValues()
                    }
                }

            val defaultShapes = ButtonDefaults.shapesFor(referenceHeight)
            val shape =
                if (viewModel.cornerRadius != null) {
                    RoundedCornerShape(viewModel.cornerRadius)
                } else {
                    defaultShapes.shape
                }
            val pressedShape =
                if (viewModel.pressedCornerRadius != null) {
                    RoundedCornerShape(viewModel.pressedCornerRadius)
                } else {
                    defaultShapes.pressedShape
                }

            Button(
                onClick = onClick,
                modifier =
                    modifier
                        .absolutePadding(
                            viewModel.paddingLeft,
                            viewModel.paddingTop,
                            viewModel.paddingRight,
                            viewModel.paddingBottom,
                        )
                        .size(contentWidth, contentHeight),
                shapes = ButtonShapes(shape = shape, pressedShape = pressedShape),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = viewModel.backgroundColor,
                        contentColor = viewModel.textColor,
                    ),
                border = BorderStroke(viewModel.strokeWidth, viewModel.strokeColor),
                contentPadding = contentPadding,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_my_location),
                    contentDescription = null,
                    // requiredSize is needed to prevent squashing of Icon.
                    modifier = Modifier.requiredSize(ButtonDefaults.iconSizeFor(referenceHeight)),
                    tint = viewModel.iconTint,
                )
                viewModel.textResId?.let { textResId ->
                    Text(
                        text = stringResource(textResId),
                        modifier =
                            Modifier.padding(
                                start = ButtonDefaults.iconSpacingFor(referenceHeight)
                            ),
                        style = ButtonDefaults.textStyleFor(referenceHeight),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}
