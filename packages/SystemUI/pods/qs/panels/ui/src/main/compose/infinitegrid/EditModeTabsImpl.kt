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

package com.android.systemui.qs.panels.ui.compose.infinitegrid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.systemui.qs.panels.ui.compose.infinitegrid.EditModeTabs.EditModeTabsColors
import com.android.systemui.qs.panels.ui.viewmodel.EditModeTabsViewModel
import com.android.systemui.qs.panels.ui.viewmodel.EditModeTabsViewModel.Companion.Tabs
import javax.inject.Inject

/** Implementation of the [EditModeTabs] composer. */
public class EditModeTabsImpl @Inject constructor() : EditModeTabs {
    @Composable
    override fun Content(
        viewModel: EditModeTabsViewModel,
        colors: EditModeTabsColors,
        modifier: Modifier,
    ) {
        EditModeTabs(viewModel, modifier, colors)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditModeTabs(
    viewModel: EditModeTabsViewModel,
    modifier: Modifier = Modifier,
    colors: EditModeTabsColors = EditModeTabsDefaults.colors(),
) {
    HorizontalFloatingToolbar(
        modifier = modifier.height(60.dp),
        expanded = true,
        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 8.dp),
        colors =
            FloatingToolbarDefaults.standardFloatingToolbarColors(
                toolbarContainerColor = colors.containerColor
            ),
    ) {
        Tabs.forEachIndexed { index, tab ->
            val isSelected = updateTransition(viewModel.selectedTab == tab)
            val selectionBackgroundAlpha by isSelected.animateFloat { if (it) 1f else 0f }
            val contentColor by
                isSelected.animateColor {
                    if (it) colors.selectedContentColor else colors.contentColor
                }
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier.fillMaxHeight()
                        .clickable { viewModel.selectTab(index) }
                        .padding(horizontal = 5.dp)
                        .drawBehind {
                            drawRoundRect(
                                color = colors.selectedTabColor,
                                alpha = selectionBackgroundAlpha,
                                cornerRadius = CornerRadius(size.height / 2),
                            )
                        }
                        .padding(horizontal = 16.dp),
            ) {
                isSelected.AnimatedVisibility(
                    visible = { it },
                    enter = (fadeIn() + expandIn(expandFrom = Alignment.Center)),
                    exit = (fadeOut() + shrinkOut(shrinkTowards = Alignment.Center)),
                ) {
                    // The icon only shows up for the selected tab, so no need to use the
                    // animated color
                    Icon(
                        imageVector = tab.titleIcon,
                        contentDescription = null,
                        tint = colors.selectedContentColor,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                BasicText(stringResource(tab.titleResId), color = { contentColor })
            }
        }
    }
}
