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

package com.android.systemui.notifications.ui.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.scene.ContentScope
import com.android.compose.modifiers.onUnplaced
import com.android.compose.modifiers.thenIf
import com.android.compose.modifiers.width
import com.android.systemui.res.R
import com.android.systemui.statusbar.notification.stack.ui.view.NotificationScrollView
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.NotificationsPlaceholderViewModel
import kotlin.math.roundToInt

/**
 * Defines the area where heads up notifications (HUNs) can appear.
 *
 * This is a simple placeholder that reports its bounds and does not handle any user input. For an
 * interactive version that supports snoozing, see [SnoozableHeadsUpNotificationPlaceholder].
 *
 * @param stackScrollView The legacy view that hosts the notification stack.
 * @param viewModel The view model for placeholder state.
 * @param modifier The [Modifier] to be applied to this placeholder.
 */
@Composable
fun ContentScope.HeadsUpNotificationPlaceholder(
    tag: String,
    stackScrollView: NotificationScrollView,
    viewModel: NotificationsPlaceholderViewModel,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .element(Notifications.Elements.HeadsUpNotificationPlaceholder)
                .fillMaxWidth()
                .notificationHeadsUpHeight(stackScrollView)
                .debugBackground(viewModel, DEBUG_HUN_COLOR)
                .onPlaced { coordinates: LayoutCoordinates ->
                    // Note: boundsInWindow doesn't scroll off the screen, so use rawBoundsInWindow,
                    // which can scroll off screen while snoozing.
                    val rawBounds = coordinates.rawBoundsInWindow()
                    viewModel.setHeadsUpBounds(rawBounds)
                    debugLog(viewModel) {
                        "$tag.HUNS onPlaced:" + " size=${coordinates.size}" + " bounds=$rawBounds"
                    }
                }
                .onUnplaced {
                    debugLog(viewModel) { "$tag.HUNS onUnplaced" }
                    viewModel.resetHeadsUpBounds()
                }
    ) {
        if (viewModel.isVisualDebuggingEnabled) {
            Text(
                text = "$tag.HUNPlaceholder",
                color = DEBUG_HUN_COLOR.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

/**
 * A version of [HeadsUpNotificationPlaceholder] that can be swiped up off the top edge of the
 * screen by the user. When swiped up, the heads up notification is snoozed.
 *
 * @param useStackBounds Whether to communicate stackBounds updated to the [stackScrollView]. This
 *   should be `true` when content rendering the regular stack is not setting draw bounds anymore,
 *   but HUNs can still appear.
 * @param stackScrollView The legacy view that hosts the notification stack.
 * @param viewModel The view model for placeholder state.
 * @param modifier The [Modifier] to be applied to this placeholder.
 */
@Composable
fun ContentScope.SnoozableHeadsUpNotificationPlaceholder(
    tag: String,
    stackScrollView: NotificationScrollView,
    viewModel: NotificationsPlaceholderViewModel,
    modifier: Modifier = Modifier,
) {
    val isSnoozable by viewModel.isHeadsUpOrAnimatingAway.collectAsStateWithLifecycle(false)
    val headsUpInset = with(LocalDensity.current) { headsUpTopInset().toPx() }

    val snoozeController =
        rememberHeadsUpSnoozeController(
            minOffset = { -headsUpInset },
            isEnabled = { isSnoozable },
            onSnoozed = {
                viewModel.setHeadsUpAnimatingAway(false)
                viewModel.snoozeHun()
            },
            onSnoozeStateChanged = { isSnoozing ->
                debugLog(viewModel) { "isSnoozing:$isSnoozing" }
            },
        )

    LaunchedEffect(isSnoozable) { snoozeController.reset() }

    val horizontalAlignment = viewModel.horizontalAlignment
    val halfScreenWidth = LocalWindowInfo.current.containerSize.width / 2

    HeadsUpNotificationPlaceholder(
        tag = "$tag.Snoozable",
        stackScrollView = stackScrollView,
        viewModel = viewModel,
        modifier =
            modifier
                // In side-aligned layouts, HUNs are limited to half the screen width.
                .thenIf(horizontalAlignment != Alignment.CenterHorizontally) {
                    Modifier.width { halfScreenWidth }
                }
                .headsUpSnoozeDraggable(controller = snoozeController)
                .offset {
                    IntOffset(
                        x = if (horizontalAlignment == Alignment.End) halfScreenWidth else 0,
                        y =
                            snoozeController
                                .calculateHeadsUpPlaceholderYOffset(
                                    restPosition = headsUpInset,
                                    topHeadsUpHeight = stackScrollView.topHeadsUpHeight.toFloat(),
                                )
                                .roundToInt(),
                    )
                },
    )
}

/** Y position of the HUNs at rest, when the shade is closed. */
@Composable
private fun headsUpTopInset(): Dp {
    val safeTop = WindowInsets.safeContent.asPaddingValues().calculateTopPadding()
    // A constant min padding when the device doesn't require safeTop (such as on Desktop)
    return maxOf(safeTop, dimensionResource(R.dimen.heads_up_status_bar_padding))
}

private val DEBUG_HUN_COLOR = Color(0f, 0f, 1f, 0.2f)
