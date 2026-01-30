/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.qs.ui.composable

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.clipScrollableContainer
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.window.core.layout.WindowSizeClass
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.SceneTransitionLayoutState
import com.android.compose.animation.scene.UserAction
import com.android.compose.animation.scene.UserActionResult
import com.android.compose.animation.scene.animateContentFloatAsState
import com.android.compose.animation.scene.rememberMutableSceneTransitionLayoutState
import com.android.compose.animation.scene.transitions
import com.android.compose.gesture.gesturesDisabled
import com.android.compose.lifecycle.DisposableEffectWithLifecycle
import com.android.compose.lifecycle.LaunchedEffectWithLifecycle
import com.android.compose.modifiers.thenIf
import com.android.compose.windowsizeclass.LocalWindowSizeClass
import com.android.internal.jank.InteractionJankMonitor
import com.android.systemui.common.ui.compose.windowinsets.CutoutLocation
import com.android.systemui.common.ui.compose.windowinsets.LocalDisplayCutout
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.notifications.ui.composable.HeadsUpNotificationSpace
import com.android.systemui.notifications.ui.composable.NotificationScrollingStack
import com.android.systemui.qs.composefragment.ui.GridAnchor
import com.android.systemui.qs.footer.ui.compose.FooterActionsWithAnimatedVisibility
import com.android.systemui.qs.panels.ui.compose.EditMode
import com.android.systemui.qs.shared.ui.QuickSettings
import com.android.systemui.qs.ui.composable.QuickSettingsScene.Companion.InternalScenes.Edit
import com.android.systemui.qs.ui.composable.QuickSettingsScene.Companion.InternalScenes.QS
import com.android.systemui.qs.ui.viewmodel.QuickSettingsSceneContentViewModel
import com.android.systemui.qs.ui.viewmodel.QuickSettingsUserActionsViewModel
import com.android.systemui.res.R
import com.android.systemui.scene.session.ui.composable.SaveableSession
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.ui.composable.Scene
import com.android.systemui.shade.ui.composable.CollapsedShadeHeader
import com.android.systemui.shade.ui.composable.ExpandedShadeHeader
import com.android.systemui.shade.ui.composable.ShadeHeader
import com.android.systemui.shade.ui.composable.ShadePanelScrim
import com.android.systemui.shade.ui.viewmodel.ShadeHeaderViewModel
import com.android.systemui.statusbar.notification.stack.ui.view.NotificationScrollView
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.NotificationsPlaceholderViewModel
import dagger.Lazy
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow

/** The Quick Settings (AKA "QS") scene shows the quick setting tiles. */
@SysUISingleton
class QuickSettingsScene
@Inject
constructor(
    private val shadeSession: SaveableSession,
    private val notificationStackScrollView: Lazy<NotificationScrollView>,
    private val notificationsPlaceholderViewModelFactory: NotificationsPlaceholderViewModel.Factory,
    private val actionsViewModelFactory: QuickSettingsUserActionsViewModel.Factory,
    private val contentViewModelFactory: QuickSettingsSceneContentViewModel.Factory,
    private val jankMonitor: InteractionJankMonitor,
) : ExclusiveActivatable(), Scene {
    override val key = Scenes.QuickSettings

    private val actionsViewModel: QuickSettingsUserActionsViewModel by lazy {
        actionsViewModelFactory.create()
    }

    override val userActions: Flow<Map<UserAction, UserActionResult>> = actionsViewModel.actions

    override val alwaysCompose: Boolean = true

    override suspend fun onActivated(): Nothing {
        actionsViewModel.activate()
    }

    @Composable
    override fun ContentScope.Content(modifier: Modifier) {
        val viewModel =
            rememberViewModel("QuickSettingsScene-viewModel") { contentViewModelFactory.create() }
        val notificationsPlaceholderViewModel =
            rememberViewModel("QuickSettingsScene-notifPlaceholderViewModel") {
                notificationsPlaceholderViewModelFactory.create()
            }

        val brightnessMirrorShowing =
            viewModel.qsContainerViewModel.brightnessSliderViewModel.showMirror
        val contentAlpha by
            animateFloatAsState(
                targetValue = if (brightnessMirrorShowing) 0f else 1f,
                label = "alphaAnimationBrightnessMirrorContentHiding",
            )

        LaunchedEffectWithLifecycle(key1 = Unit) {
            try {
                snapshotFlow { contentAlpha }
                    .collect { notificationsPlaceholderViewModel.setAlphaForBrightnessMirror(it) }
            } finally {
                notificationsPlaceholderViewModel.setAlphaForBrightnessMirror(1f)
            }
        }

        LaunchedEffectWithLifecycle(key1 = Unit) { viewModel.detectShadeModeChanges() }

        QuickSettingsScene(
            notificationStackScrollView = notificationStackScrollView.get(),
            viewModel = viewModel,
            headerViewModel = viewModel.qsContainerViewModel.shadeHeaderViewModel,
            notificationsPlaceholderViewModel = notificationsPlaceholderViewModel,
            modifier =
                modifier
                    .graphicsLayer { alpha = contentAlpha }
                    .thenIf(brightnessMirrorShowing) { Modifier.gesturesDisabled() },
            shadeSession = shadeSession,
            jankMonitor = jankMonitor,
        )
    }

    companion object {
        object InternalScenes {
            val QS = SceneKey("QuickSettingsMainPanel")
            val Edit = SceneKey("QuickSettingsEditPanel")

            private const val EDIT_MODE_TIME_MILLIS = 500

            val transitions = transitions {
                from(QS, Edit) {
                    spec = tween(durationMillis = EDIT_MODE_TIME_MILLIS)
                    fractionRange(start = 0.5f) { fade(Edit.rootElementKey) }
                    fractionRange(end = 0.5f) { fade(QS.rootElementKey) }
                }
            }
        }
    }
}

@Composable
private fun ContentScope.QuickSettingsScene(
    notificationStackScrollView: NotificationScrollView,
    viewModel: QuickSettingsSceneContentViewModel,
    headerViewModel: ShadeHeaderViewModel,
    notificationsPlaceholderViewModel: NotificationsPlaceholderViewModel,
    modifier: Modifier = Modifier,
    shadeSession: SaveableSession,
    jankMonitor: InteractionJankMonitor,
) {
    val targetBlur by
        remember(layoutState) {
            derivedStateOf { viewModel.calculateBlur(layoutState.transitionState) }
        }
    val animatedBlurRadiusPx: Float by
        animateFloatAsState(targetValue = targetBlur, label = "QS-blurRadius")
    Box(modifier.blur(with(LocalDensity.current) { animatedBlurRadiusPx.toDp() }).fillMaxSize()) {
        // This is the background for the whole scene, as the elements don't necessarily provide
        // a background that extends to the edges.
        ShadePanelScrim(viewModel.isTransparencyEnabled)

        val sceneState =
            rememberMutableSceneTransitionLayoutState(
                initialScene =
                    remember { if (viewModel.qsContainerViewModel.isEditing) Edit else QS },
                transitions = QuickSettingsScene.Companion.InternalScenes.transitions,
            )

        val coroutineScope = rememberCoroutineScope()

        DisposableEffectWithLifecycle(key1 = viewModel, key2 = sceneState) {
            onDispose {
                viewModel.qsContainerViewModel.editModeViewModel.stopEditing()
                sceneState.snapTo(QS)
            }
        }

        LaunchedEffectWithLifecycle(
            key1 = sceneState,
            key2 = viewModel.qsContainerViewModel.isEditing,
            key3 = coroutineScope,
        ) {
            if (viewModel.qsContainerViewModel.isEditing) {
                sceneState.setTargetScene(Edit, coroutineScope)
            } else {
                sceneState.setTargetScene(QS, coroutineScope)
            }
        }

        NestedSceneTransitionLayout(state = sceneState, modifier = Modifier.fillMaxSize()) {
            scene(QS) {
                Element(QS.rootElementKey, Modifier) {
                    QuickSettingsContent(
                        notificationsPlaceholderViewModel,
                        Modifier,
                        viewModel,
                        headerViewModel,
                        notificationStackScrollView,
                        shadeSession,
                        jankMonitor,
                        this@QuickSettingsScene.verticalOverscrollEffect,
                    )
                }
            }

            scene(Edit) {
                Element(Edit.rootElementKey, Modifier) {
                    GridAnchor()
                    EditMode(
                        viewModel.qsContainerViewModel.editModeViewModel,
                        Modifier.testTag("edit_mode_scene")
                            .padding(horizontal = QuickSettingsShade.Dimensions.Padding)
                            .padding(top = ShadeHeader.Dimensions.StatusBarHeight),
                    )
                }
            }
        }
    }
}

@Composable
private fun ContentScope.QuickSettingsContent(
    notificationsPlaceholderViewModel: NotificationsPlaceholderViewModel,
    modifier: Modifier,
    viewModel: QuickSettingsSceneContentViewModel,
    headerViewModel: ShadeHeaderViewModel,
    notificationStackScrollView: NotificationScrollView,
    shadeSession: SaveableSession,
    jankMonitor: InteractionJankMonitor,
    verticalOverscrollEffect: OverscrollEffect,
) {
    val cutoutLocation = LocalDisplayCutout.current().location

    val shadeHorizontalPadding =
        dimensionResource(id = R.dimen.notification_panel_margin_horizontal)

    val shouldPunchHoleBehindScrim =
        layoutState.isTransitioningBetween(Scenes.Gone, Scenes.QuickSettings) ||
            layoutState.isTransitioningBetween(Scenes.Lockscreen, Scenes.QuickSettings)

    // TODO(b/280887232): implement the real UI.
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .thenIf(shouldPunchHoleBehindScrim) {
                    // Render the scene to an offscreen buffer so that BlendMode.DstOut only clears
                    // this scene (and not the one under it) during a scene transition.
                    Modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                }
                .thenIf(cutoutLocation != CutoutLocation.CENTER) { Modifier.displayCutoutPadding() }
    ) {
        val density = LocalDensity.current
        val screenHeight = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

        val lifecycleOwner = LocalLifecycleOwner.current
        val footerActionsViewModel =
            remember(lifecycleOwner, viewModel) {
                viewModel.getFooterActionsViewModel(lifecycleOwner)
            }
        animateContentFloatAsState(value = 1f, key = QuickSettings.SharedValues.TilesSquishiness)

        // ############## SCROLLING ################

        val scrollState = rememberScrollState()
        // When animating into the scene, we don't want it to be able to scroll, as it could mess
        // up with the expansion animation.
        val isScrollable =
            layoutState.isIdle(Scenes.QuickSettings) ||
                layoutState.isTransitioning(from = Scenes.QuickSettings)

        LaunchedEffectWithLifecycle(isScrollable) {
            if (!isScrollable) {
                scrollState.scrollTo(0)
            }
        }

        // ############# NAV BAR paddings ###############

        val navBarBottomHeight =
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        // ############# Media ###############
        val mediaInRow = viewModel.qsContainerViewModel.showMediaInRow

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier.fillMaxSize()
                    .overscroll(verticalOverscrollEffect)
                    .padding(bottom = navBarBottomHeight.coerceAtLeast(0.dp)),
        ) {
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                Column(
                    modifier =
                        Modifier.verticalScroll(scrollState, enabled = isScrollable)
                            .clipScrollableContainer(Orientation.Horizontal)
                            .fillMaxWidth()
                            .wrapContentHeight(unbounded = true)
                            .align(Alignment.TopCenter)
                            .sysuiResTag("expanded_qs_scroll_view")
                ) {
                    with(LocalWindowSizeClass.current) {
                        when {
                            isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) ->
                                CollapsedShadeHeader(
                                    viewModel = headerViewModel,
                                    isSplitShade = false,
                                )
                            else ->
                                ExpandedShadeHeader(
                                    viewModel = headerViewModel,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    QuickSettingsContent(viewModel.qsContainerViewModel, mediaInRow)
                }
            }

            FooterActionsWithAnimatedVisibility(
                viewModel = footerActionsViewModel,
                isCustomizing = false,
                customizingAnimationDuration = 0,
                modifier =
                    Modifier.align(Alignment.CenterHorizontally)
                        .sysuiResTag("qs_footer_actions")
                        .padding(horizontal = shadeHorizontalPadding),
            )
        }
        HeadsUpNotificationSpace(
            stackScrollView = notificationStackScrollView,
            viewModel = notificationsPlaceholderViewModel,
            useHunBounds = { shouldUseQuickSettingsHunBounds(layoutState) },
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = shadeHorizontalPadding),
        )

        // TODO(b/436646848): remove NotificationScrollingStack from QuickSettings
        // The minimum possible value for the top of the notification stack. In other words: how
        // high is the notification stack allowed to get when the scene is at rest. It may still be
        // translated farther upwards by a transition animation but, at rest, the top edge of its
        // bounds must be limited to be at or below this value.
        //
        // A 1 pixel is added to compensate for any kind of rounding errors to make sure 100% that
        // the notification stack is entirely "below" the entire screen.
        val minNotificationStackTop = screenHeight.roundToInt() + 1
        val notificationStackPadding = dimensionResource(id = R.dimen.notification_side_paddings)
        NotificationScrollingStack(
            shadeSession = shadeSession,
            stackScrollView = notificationStackScrollView,
            viewModel = notificationsPlaceholderViewModel,
            jankMonitor = jankMonitor,
            maxScrimTop = { minNotificationStackTop.toFloat() },
            shouldPunchHoleBehindScrim = shouldPunchHoleBehindScrim,
            stackTopPadding = notificationStackPadding,
            stackBottomPadding = navBarBottomHeight,
            shouldIncludeHeadsUpSpace = false,
            supportNestedScrolling = false,
            modifier =
                Modifier.fillMaxWidth()
                    // Match the screen height with the scrim, so it covers the whole screen,
                    // when the stack "passes by" during the QS -> Gone transition.
                    .height(LocalWindowInfo.current.containerSize.height.dp)
                    .offset { IntOffset(x = 0, y = minNotificationStackTop) }
                    .padding(horizontal = shadeHorizontalPadding),
        )
    }
}

private fun shouldUseQuickSettingsHunBounds(layoutState: SceneTransitionLayoutState): Boolean {
    return layoutState.isIdle(Scenes.QuickSettings)
}
