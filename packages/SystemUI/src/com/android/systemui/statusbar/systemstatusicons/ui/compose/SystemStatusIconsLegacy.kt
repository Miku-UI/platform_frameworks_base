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

package com.android.systemui.statusbar.systemstatusicons.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.annotation.RememberInComposition
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.android.systemui.statusbar.phone.StatusIconContainer
import com.android.systemui.statusbar.phone.ui.StatusBarIconController
import com.android.systemui.statusbar.phone.ui.TintedIconManager

@Suppress("NAME_SHADOWING")
@Composable
fun SystemStatusIconsLegacy(
    statusBarIconController: StatusBarIconController,
    iconContainer: StatusIconContainer,
    iconManager: TintedIconManager,
    useExpandedFormat: Boolean,
    isTransitioning: Boolean,
    foregroundColor: Int,
    backgroundColor: Int,
    isSingleCarrier: Boolean,
    isMicCameraIndicationEnabled: Boolean,
    isPrivacyChipEnabled: Boolean,
    isLocationIndicationEnabled: Boolean,
    modifier: Modifier = Modifier,
    /**
     * This content should call through to [SystemStatusIconsLegacyAndroidView]. One way is to
     * provide it using [movableSystemStatusIconsLegacyAndroidView]. If remembered tied to the
     * [TintedIconManager], it will be re-used and avoid issues of re-using the same
     * [TintedIconManager].
     */
    content: MovableSystemStatusIconLegacy,
) {
    val carrierIconSlots =
        listOf(
            stringResource(id = com.android.internal.R.string.status_bar_mobile),
            stringResource(id = com.android.internal.R.string.status_bar_stacked_mobile),
        )
    val cameraSlot = stringResource(id = com.android.internal.R.string.status_bar_camera)
    val micSlot = stringResource(id = com.android.internal.R.string.status_bar_microphone)
    val locationSlot = stringResource(id = com.android.internal.R.string.status_bar_location)

    /*
     * Use `rememberUpdatedState` to guarantee that a state will be exposed (without recomposition)
     * for all these parameters, so the update block will be called when any of them changes.
     */
    val useExpandedFormat by rememberUpdatedState(useExpandedFormat)
    val isTransitioning by rememberUpdatedState(isTransitioning)
    val foregroundColor by rememberUpdatedState(foregroundColor)
    val backgroundColor by rememberUpdatedState(backgroundColor)
    val isSingleCarrier by rememberUpdatedState(isSingleCarrier)
    val isMicCameraIndicationEnabled by rememberUpdatedState(isMicCameraIndicationEnabled)
    val isPrivacyChipEnabled by rememberUpdatedState(isPrivacyChipEnabled)
    val isLocationIndicationEnabled by rememberUpdatedState(isLocationIndicationEnabled)

    val update =
        remember(
            statusBarIconController,
            iconManager,
            carrierIconSlots,
            cameraSlot,
            micSlot,
            locationSlot,
        ) {
            { container: StatusIconContainer ->
                container.setQsExpansionTransitioning(isTransitioning)

                if (isSingleCarrier || !useExpandedFormat) {
                    container.removeIgnoredSlots(carrierIconSlots)
                } else {
                    container.addIgnoredSlots(carrierIconSlots)
                }

                if (isPrivacyChipEnabled) {
                    if (isMicCameraIndicationEnabled) {
                        container.addIgnoredSlot(cameraSlot)
                        container.addIgnoredSlot(micSlot)
                    } else {
                        container.removeIgnoredSlot(cameraSlot)
                        container.removeIgnoredSlot(micSlot)
                    }
                    if (isLocationIndicationEnabled) {
                        container.addIgnoredSlot(locationSlot)
                    } else {
                        container.removeIgnoredSlot(locationSlot)
                    }
                } else {
                    container.removeIgnoredSlot(cameraSlot)
                    container.removeIgnoredSlot(micSlot)
                    container.removeIgnoredSlot(locationSlot)
                }

                iconManager.setTint(foregroundColor, backgroundColor)
            }
        }

    content(statusBarIconController, iconContainer, update, modifier)
}

/** Alias for [movableSystemStatusIconsLegacyAndroidView] */
typealias MovableSystemStatusIconLegacy =
    @Composable
    (StatusBarIconController, StatusIconContainer, (StatusIconContainer) -> Unit, Modifier) -> Unit

/**
 * Returns a movable content for the given `TintedIconManager`. This can be used to guarantee that
 * the same one is always used (to prevent double registration with [StatusBarIconController]) when
 * used with a cache.
 */
@RememberInComposition
fun movableSystemStatusIconsLegacyAndroidView(
    iconManager: TintedIconManager
): MovableSystemStatusIconLegacy {
    return movableContentOf {
        statusBarIconController: StatusBarIconController,
        iconContainer: StatusIconContainer,
        update: (StatusIconContainer) -> Unit,
        modifier: Modifier ->
        SystemStatusIconsLegacyAndroidView(
            statusBarIconController,
            iconManager,
            iconContainer,
            update,
            modifier,
        )
    }
}

@Composable
private fun SystemStatusIconsLegacyAndroidView(
    statusBarIconController: StatusBarIconController,
    iconManager: TintedIconManager,
    iconContainer: StatusIconContainer,
    update: (StatusIconContainer) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = {
            statusBarIconController.addIconGroup(iconManager)
            iconContainer
        },
        onRelease = { statusBarIconController.removeIconGroup(iconManager) },
        update = update,
        modifier = modifier,
    )
}
