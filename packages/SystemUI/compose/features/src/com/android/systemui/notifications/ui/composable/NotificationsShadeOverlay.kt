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

package com.android.systemui.notifications.ui.composable

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.UserAction
import com.android.compose.animation.scene.UserActionResult
import com.android.compose.lifecycle.DisposableEffectWithLifecycle
import com.android.compose.lifecycle.LaunchedEffectWithLifecycle
import com.android.internal.jank.InteractionJankMonitor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.media.remedia.ui.compose.Media
import com.android.systemui.media.remedia.ui.compose.MediaPresentationStyle
import com.android.systemui.notifications.ui.viewmodel.NotificationsShadeOverlayActionsViewModel
import com.android.systemui.notifications.ui.viewmodel.NotificationsShadeOverlayContentViewModel
import com.android.systemui.res.R
import com.android.systemui.scene.session.ui.composable.SaveableSession
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.ui.composable.Overlay
import com.android.systemui.shade.ui.composable.ChipHighlightModel
import com.android.systemui.shade.ui.composable.OverlayShade
import com.android.systemui.shade.ui.composable.OverlayShadeHeader
import com.android.systemui.shade.ui.composable.isFullWidthShade
import com.android.systemui.statusbar.notification.stack.ui.view.NotificationScrollView
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

@SysUISingleton
class NotificationsShadeOverlay
@Inject
constructor(
    private val actionsViewModelFactory: NotificationsShadeOverlayActionsViewModel.Factory,
    private val contentViewModelFactory: NotificationsShadeOverlayContentViewModel.Factory,
    private val shadeSession: SaveableSession,
    private val stackScrollView: Lazy<NotificationScrollView>,
    private val jankMonitor: InteractionJankMonitor,
) : Overlay {
    override val key = Overlays.NotificationsShade

    private val actionsViewModel: NotificationsShadeOverlayActionsViewModel by lazy {
        actionsViewModelFactory.create()
    }

    override val userActions: Flow<Map<UserAction, UserActionResult>> = actionsViewModel.actions

    override val alwaysCompose: Boolean = false

    override suspend fun activate(): Nothing {
        actionsViewModel.activate()
    }

    @Composable
    override fun ContentScope.Content(modifier: Modifier) {
        val notificationStackPadding = dimensionResource(id = R.dimen.notification_side_paddings)

        val viewModel =
            rememberViewModel("NotificationsShadeOverlay-viewModel") {
                contentViewModelFactory.create()
            }
        val placeholderViewModel =
            rememberViewModel("NotificationsShadeOverlay-notifPlaceholderViewModel") {
                viewModel.notificationsPlaceholderViewModelFactory.create()
            }

        DisposableEffectWithLifecycle(Unit) {
            onDispose { viewModel.onShadeOverlayBoundsChanged(null) }
        }

        val isFullWidth = isFullWidthShade()

        val targetBlurRadiusPx: Float by
            remember(layoutState) {
                derivedStateOf { viewModel.calculateTargetBlurRadius(layoutState.transitionState) }
            }
        val animatedBlurRadiusPx: Float by
            animateFloatAsState(targetValue = targetBlurRadiusPx, label = "NSOverlay-blurRadius")

        OverlayShade(
            panelElement = NotificationsShade.Elements.Panel,
            alignmentOnWideScreens = viewModel.alignmentOnWideScreens,
            enableTransparency = viewModel.isTransparencyEnabled,
            modifier = modifier.blur(with(LocalDensity.current) { animatedBlurRadiusPx.toDp() }),
            onScrimClicked = viewModel::onScrimClicked,
            onBackgroundPlaced = { bounds, _, _ -> viewModel.onShadeOverlayBoundsChanged(bounds) },
            header = {
                if (viewModel.showHeader) {
                    val headerViewModel =
                        rememberViewModel("NotificationsShadeOverlayHeader") {
                            viewModel.shadeHeaderViewModelFactory.create()
                        }
                    OverlayShadeHeader(
                        viewModel = headerViewModel,
                        notificationsHighlight = ChipHighlightModel.Strong,
                        quickSettingsHighlight = headerViewModel.inactiveChipHighlight,
                        showClock = !isFullWidth,
                        modifier = Modifier.element(NotificationsShade.Elements.StatusBar),
                    )
                }
            },
        ) {
            val focusRequester = remember { FocusRequester() }

            LaunchedEffectWithLifecycle(focusRequester) {
                // Request focus on the content's column without user interaction so that the user
                // can press the tab key once to enter the notification area. Without this line, the
                // user has to tab through unrelated views of the higher view hierarchy level.
                focusRequester.requestFocus()
            }

            Column(modifier = Modifier.focusRequester(focusRequester).focusable()) {
                if (viewModel.showMedia) {
                    Element(
                        key = Media.Elements.mediaCarousel,
                        modifier =
                            Modifier.padding(
                                top = notificationStackPadding,
                                start = notificationStackPadding,
                                end = notificationStackPadding,
                            ),
                    ) {
                        Media(
                            viewModelFactory = viewModel.mediaViewModelFactory,
                            presentationStyle = MediaPresentationStyle.Default,
                            behavior = viewModel.mediaUiBehavior,
                            onDismissed = viewModel::onMediaSwipeToDismiss,
                        )
                    }
                }

                val stackScrollView = stackScrollView.get()
                NotificationScrollingStack(
                    shadeSession = shadeSession,
                    stackScrollView = stackScrollView,
                    viewModel = placeholderViewModel,
                    jankMonitor = jankMonitor,
                    maxScrimTop = { 0f },
                    shouldPunchHoleBehindScrim = false,
                    stackTopPadding = notificationStackPadding,
                    stackBottomPadding = notificationStackPadding,
                    shouldFillMaxSize = false,
                    shouldShowScrim = false,
                    supportNestedScrolling = false,
                    modifier =
                        Modifier.fillMaxWidth().focusProperties {
                            // The `NotificationScrollingStack` is a compose placeholder. Therefore,
                            // focus on the view that actually shows notifications.
                            onEnter = { stackScrollView.asView().requestFocus() }
                        },
                )
            }
        }
    }
}

object NotificationsShade {
    object Elements {
        val Panel = ElementKey("NotificationsShadeOverlayPanel")
        val StatusBar = ElementKey("NotificationsShadeOverlayStatusBar")
    }
}
