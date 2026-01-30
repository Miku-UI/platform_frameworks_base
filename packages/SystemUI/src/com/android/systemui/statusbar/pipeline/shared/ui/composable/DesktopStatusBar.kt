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

package com.android.systemui.statusbar.pipeline.shared.ui.composable

import android.graphics.Rect
import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.android.systemui.clock.ui.composable.ClockLegacy
import com.android.systemui.clock.ui.viewmodel.AmPmStyle
import com.android.systemui.clock.ui.viewmodel.ClockViewModel
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.compose.modifiers.sysUiResTagContainer
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.media.controls.ui.controller.MediaHierarchyManager
import com.android.systemui.media.controls.ui.view.MediaHost
import com.android.systemui.media.remedia.ui.viewmodel.MediaViewModel
import com.android.systemui.res.R
import com.android.systemui.shade.ui.composable.ChipHighlightModel
import com.android.systemui.shade.ui.composable.ShadeHighlightChip
import com.android.systemui.shade.ui.composable.VariableDayDate
import com.android.systemui.statusbar.chips.ui.compose.OngoingActivityChips
import com.android.systemui.statusbar.featurepods.popups.StatusBarPopupChips
import com.android.systemui.statusbar.featurepods.popups.ui.compose.StatusBarPopupChipsContainer
import com.android.systemui.statusbar.notification.icon.ui.viewbinder.NotificationIconContainerViewBinder
import com.android.systemui.statusbar.phone.StatusBarLocation
import com.android.systemui.statusbar.phone.StatusIconContainer
import com.android.systemui.statusbar.phone.ui.StatusBarIconController
import com.android.systemui.statusbar.phone.ui.TintedIconManager
import com.android.systemui.statusbar.pipeline.battery.ui.composable.UnifiedBattery
import com.android.systemui.statusbar.pipeline.battery.ui.viewmodel.BatteryViewModel
import com.android.systemui.statusbar.pipeline.shared.ui.viewmodel.HomeStatusBarViewModel
import com.android.systemui.statusbar.systemstatusicons.SystemStatusIconsInCompose
import com.android.systemui.statusbar.systemstatusicons.ui.compose.SystemStatusIcons
import com.android.systemui.statusbar.systemstatusicons.ui.compose.SystemStatusIconsLegacy
import com.android.systemui.statusbar.systemstatusicons.ui.compose.movableSystemStatusIconsLegacyAndroidView

object DesktopStatusBar {
    object Dimensions {
        val ElementSpacing = 8.dp
        val ChipInternalSpacing = 6.dp
    }
}

// TODO(b/343358983): Add support for color themes in this composable.
/** Top level composable responsible for all UI shown for the Status Bar for DesktopMode. */
@Composable
fun DesktopStatusBar(
    viewModel: HomeStatusBarViewModel,
    clockViewModelFactory: ClockViewModel.Factory,
    statusBarIconController: StatusBarIconController,
    iconManagerFactory: TintedIconManager.Factory,
    mediaHierarchyManager: MediaHierarchyManager,
    mediaViewModelFactory: MediaViewModel.Factory,
    mediaHost: MediaHost,
    iconViewStore: NotificationIconContainerViewBinder.IconViewStore?,
    modifier: Modifier = Modifier,
) {
    // TODO(433589833): Update padding values to match UX specs.
    Row(modifier = modifier.fillMaxWidth().padding(top = 4.dp, start = 12.dp, end = 12.dp)) {
        WithAdaptiveTint(
            isDarkProvider = { bounds -> viewModel.areaDark.isDarkTheme(bounds) },
            isHighlighted = false,
        ) { tint ->
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        DesktopStatusBar.Dimensions.ElementSpacing,
                        Alignment.Start,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ClockLegacy(textColor = tint, onClick = null)

                val clockViewModel =
                    rememberViewModel("HomeStatusBar.Clock") {
                        clockViewModelFactory.create(AmPmStyle.Gone)
                    }
                VariableDayDate(
                    longerDateText = clockViewModel.longerDateText,
                    shorterDateText = clockViewModel.shorterDateText,
                    textColor = tint,
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(DesktopStatusBar.Dimensions.ElementSpacing, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val chipsVisibilityModel = viewModel.ongoingActivityChips
            if (chipsVisibilityModel.areChipsAllowed) {
                OngoingActivityChips(
                    chips = chipsVisibilityModel.chips,
                    iconViewStore = iconViewStore,
                    onChipBoundsChanged = viewModel::onChipBoundsChanged,
                    modifier = Modifier.sysUiResTagContainer(),
                )
            }

            if (StatusBarPopupChips.isEnabled) {
                StatusBarPopupChipsContainer(
                    chips = viewModel.popupChips,
                    mediaViewModelFactory = mediaViewModelFactory,
                    mediaHost = mediaHost,
                    onMediaControlPopupVisibilityChanged = { popupShowing ->
                        mediaHierarchyManager.isMediaControlPopupShowing = popupShowing
                    },
                )
            }

            NotificationsChip(viewModel = viewModel)

            QuickSettingsChip(
                viewModel = viewModel,
                statusBarIconController = statusBarIconController,
                iconManagerFactory = iconManagerFactory,
            )
        }
    }
}

@Composable
private fun NotificationsChip(viewModel: HomeStatusBarViewModel, modifier: Modifier = Modifier) {
    val chipHighlightModel =
        if (viewModel.isNotificationsChipHighlighted) {
            ChipHighlightModel.Strong
        } else {
            ChipHighlightModel.Transparent
        }
    ShadeHighlightChip(
        modifier = modifier,
        onClick = { viewModel.onNotificationIconChipClicked() },
        backgroundColor = chipHighlightModel.backgroundColor,
        onHoveredBackgroundColor = chipHighlightModel.onHoveredBackgroundColor,
        horizontalArrangement =
            Arrangement.spacedBy(DesktopStatusBar.Dimensions.ChipInternalSpacing, Alignment.Start),
    ) {
        // TODO(433589833): Add new icon resources for the notification chip icon.
        WithAdaptiveTint(
            isHighlighted = viewModel.isNotificationsChipHighlighted,
            isDarkProvider = { bounds -> viewModel.areaDark.isDarkTheme(bounds) },
        ) { tint ->
            Icon(
                icon =
                    Icon.Resource(
                        resId = R.drawable.ic_notification_bell,
                        contentDescription = null,
                    ),
                tint = tint,
                modifier = Modifier.size(20.dp).padding(1.dp),
            )
        }
    }
}

@Composable
private fun QuickSettingsChip(
    viewModel: HomeStatusBarViewModel,
    statusBarIconController: StatusBarIconController,
    iconManagerFactory: TintedIconManager.Factory,
    modifier: Modifier = Modifier,
) {
    val chipHighlightModel =
        if (viewModel.isQuickSettingsChipHighlighted) {
            ChipHighlightModel.Strong
        } else {
            ChipHighlightModel.Transparent
        }
    ShadeHighlightChip(
        modifier = modifier,
        onClick = { viewModel.onQuickSettingsChipClicked() },
        backgroundColor = chipHighlightModel.backgroundColor,
        onHoveredBackgroundColor = chipHighlightModel.onHoveredBackgroundColor,
        horizontalArrangement =
            Arrangement.spacedBy(DesktopStatusBar.Dimensions.ChipInternalSpacing, Alignment.Start),
    ) {
        if (SystemStatusIconsInCompose.isEnabled) {
            WithAdaptiveTint(
                isHighlighted = viewModel.isQuickSettingsChipHighlighted,
                isDarkProvider = { bounds -> viewModel.areaDark.isDarkTheme(bounds) },
            ) { tint ->
                SystemStatusIcons(
                    viewModelFactory = viewModel.systemStatusIconsViewModelFactory,
                    tint = tint,
                    modifier = modifier,
                )
            }
        } else {
            val localContext = LocalContext.current
            val iconContainer =
                remember(localContext, iconManagerFactory) {
                    StatusIconContainer(
                        ContextThemeWrapper(localContext, R.style.Theme_SystemUI),
                        null,
                    )
                }
            val iconManager =
                remember(iconContainer) {
                    iconManagerFactory.create(iconContainer, StatusBarLocation.HOME)
                }

            val movableContent =
                remember(iconManager) { movableSystemStatusIconsLegacyAndroidView(iconManager) }

            WithAdaptiveTint(
                isHighlighted = viewModel.isQuickSettingsChipHighlighted,
                isDarkProvider = { bounds -> viewModel.areaDark.isDarkTheme(bounds) },
            ) { tint ->
                SystemStatusIconsLegacy(
                    statusBarIconController = statusBarIconController,
                    iconContainer = iconContainer,
                    iconManager = iconManager,
                    useExpandedFormat = true,
                    foregroundColor = tint.toArgb(),
                    backgroundColor = ChipHighlightModel.Strong.backgroundColor.toArgb(),
                    isSingleCarrier = true,
                    isMicCameraIndicationEnabled = true,
                    isPrivacyChipEnabled = true,
                    isTransitioning = false,
                    isLocationIndicationEnabled = true,
                    content = movableContent,
                )
            }
        }

        val batteryHeight =
            with(LocalDensity.current) {
                BatteryViewModel.getStatusBarBatteryHeight(LocalContext.current).toDp()
            }
        UnifiedBattery(
            viewModel =
                rememberViewModel("DesktopStatusBar.BatteryViewModel") {
                    viewModel.unifiedBatteryViewModel.create()
                },
            isDarkProvider = { viewModel.areaDark },
            modifier = Modifier.height(batteryHeight),
        )
    }
}

/**
 * A helper composable that calculates the correct tint for UI elements.
 *
 * It manages its own bounds state and provides the calculated tint and a modifier to its content,
 * abstracting away the boilerplate of tint calculation.
 */
@Composable
private fun WithAdaptiveTint(
    isDarkProvider: (Rect) -> Boolean,
    isHighlighted: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (tint: Color) -> Unit,
) {
    var bounds by remember { mutableStateOf(Rect()) }
    val tint =
        if (isHighlighted) {
            ChipHighlightModel.Strong.foregroundColor
        } else if (isDarkProvider(bounds)) {
            Color.White
        } else {
            Color.Black
        }

    Box(
        propagateMinConstraints = true,
        modifier =
            modifier.onLayoutRectChanged { layoutCoordinates ->
                bounds = with(layoutCoordinates.boundsInScreen) { Rect(left, top, right, bottom) }
            },
    ) {
        content(tint)
    }
}
