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

package com.android.systemui.shade.ui.composable

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.LowestZIndexContentPicker
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.UserAction
import com.android.compose.animation.scene.UserActionResult
import com.android.compose.animation.scene.animateContentFloatAsState
import com.android.compose.animation.scene.rememberMutableSceneTransitionLayoutState
import com.android.compose.animation.scene.transitions
import com.android.compose.gesture.gesturesDisabled
import com.android.compose.lifecycle.LaunchedEffectWithLifecycle
import com.android.compose.modifiers.padding
import com.android.compose.modifiers.thenIf
import com.android.internal.jank.InteractionJankMonitor
import com.android.systemui.common.ui.compose.windowinsets.CutoutLocation
import com.android.systemui.common.ui.compose.windowinsets.LocalDisplayCutout
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.media.remedia.ui.compose.Media
import com.android.systemui.media.remedia.ui.compose.MediaPresentationStyle
import com.android.systemui.notifications.ui.composable.NotificationScrollingStack
import com.android.systemui.qs.composefragment.ui.GridAnchor
import com.android.systemui.qs.footer.ui.compose.FooterActionsWithAnimatedVisibility
import com.android.systemui.qs.panels.ui.compose.EditMode
import com.android.systemui.qs.panels.ui.compose.QuickQuickSettings
import com.android.systemui.qs.shared.ui.QuickSettings
import com.android.systemui.qs.shared.ui.QuickSettings.Elements.SplitShadeQuickSettings
import com.android.systemui.qs.ui.composable.QuickSettingsContent
import com.android.systemui.qs.ui.composable.QuickSettingsShade
import com.android.systemui.res.R
import com.android.systemui.scene.session.ui.composable.SaveableSession
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.ui.composable.Scene
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.shade.ui.composable.ShadeScene.Companion.SplitShadeInternalScenes.Edit
import com.android.systemui.shade.ui.composable.ShadeScene.Companion.SplitShadeInternalScenes.QS
import com.android.systemui.shade.ui.composable.ShadeScene.Companion.SplitShadeInternalScenes.transitions
import com.android.systemui.shade.ui.viewmodel.ShadeHeaderViewModel
import com.android.systemui.shade.ui.viewmodel.ShadeSceneContentViewModel
import com.android.systemui.shade.ui.viewmodel.ShadeUserActionsViewModel
import com.android.systemui.statusbar.notification.stack.ui.view.NotificationScrollView
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.NotificationsPlaceholderViewModel
import dagger.Lazy
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

object Shade {
    object Elements {
        val BackgroundScrim =
            ElementKey("ShadeBackgroundScrim", contentPicker = LowestZIndexContentPicker)
    }

    object Dimensions {
        val HorizontalPadding = 16.dp
    }
}

/** The shade scene shows scrolling list of notifications and some of the quick setting tiles. */
@SysUISingleton
class ShadeScene
@Inject
constructor(
    private val shadeSession: SaveableSession,
    private val notificationStackScrollView: Lazy<NotificationScrollView>,
    private val actionsViewModelFactory: ShadeUserActionsViewModel.Factory,
    private val contentViewModelFactory: ShadeSceneContentViewModel.Factory,
    private val notificationsPlaceholderViewModelFactory: NotificationsPlaceholderViewModel.Factory,
    private val jankMonitor: InteractionJankMonitor,
) : ExclusiveActivatable(), Scene {

    override val key = Scenes.Shade

    private val actionsViewModel: ShadeUserActionsViewModel by lazy {
        actionsViewModelFactory.create()
    }

    override suspend fun onActivated(): Nothing {
        actionsViewModel.activate()
    }

    override val userActions: Flow<Map<UserAction, UserActionResult>> = actionsViewModel.actions

    override val alwaysCompose: Boolean = false

    @Composable
    override fun ContentScope.Content(modifier: Modifier) {
        val viewModel =
            rememberViewModel("ShadeScene-viewModel") { contentViewModelFactory.create() }
        val headerViewModel =
            rememberViewModel("ShadeScene-headerViewModel") {
                viewModel.shadeHeaderViewModelFactory.create()
            }
        val notificationsPlaceholderViewModel =
            rememberViewModel("ShadeScene-notifPlaceholderViewModel") {
                notificationsPlaceholderViewModelFactory.create()
            }
        val isShadeBlurred = viewModel.isShadeBlurred
        val shadeBlurRadius = with(LocalDensity.current) { viewModel.shadeBlurRadius.toDp() }
        ShadeScene(
            notificationStackScrollView.get(),
            viewModel = viewModel,
            headerViewModel = headerViewModel,
            notificationsPlaceholderViewModel = notificationsPlaceholderViewModel,
            jankMonitor = jankMonitor,
            modifier = modifier.thenIf(isShadeBlurred) { Modifier.blur(shadeBlurRadius) },
            shadeSession = shadeSession,
        )
    }

    companion object {
        object SplitShadeInternalScenes {
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
private fun ContentScope.ShadeScene(
    notificationStackScrollView: NotificationScrollView,
    viewModel: ShadeSceneContentViewModel,
    headerViewModel: ShadeHeaderViewModel,
    notificationsPlaceholderViewModel: NotificationsPlaceholderViewModel,
    jankMonitor: InteractionJankMonitor,
    modifier: Modifier = Modifier,
    shadeSession: SaveableSession,
) {
    if (viewModel.shadeMode is ShadeMode.Split) {
        SplitShade(
            notificationStackScrollView = notificationStackScrollView,
            viewModel = viewModel,
            headerViewModel = headerViewModel,
            notificationsPlaceholderViewModel = notificationsPlaceholderViewModel,
            modifier = modifier,
            shadeSession = shadeSession,
            jankMonitor = jankMonitor,
        )
    } else {
        // Compose SingleShade even if we're in Dual shade mode; the view-model will take care of
        // switching scenes.
        SingleShade(
            notificationStackScrollView = notificationStackScrollView,
            viewModel = viewModel,
            headerViewModel = headerViewModel,
            notificationsPlaceholderViewModel = notificationsPlaceholderViewModel,
            modifier = modifier,
            shadeSession = shadeSession,
            jankMonitor = jankMonitor,
        )
    }
}

@Composable
private fun ContentScope.SingleShade(
    notificationStackScrollView: NotificationScrollView,
    viewModel: ShadeSceneContentViewModel,
    headerViewModel: ShadeHeaderViewModel,
    notificationsPlaceholderViewModel: NotificationsPlaceholderViewModel,
    jankMonitor: InteractionJankMonitor,
    modifier: Modifier = Modifier,
    shadeSession: SaveableSession,
) {
    val cutoutLocation = LocalDisplayCutout.current().location
    val cutoutInsets = WindowInsets.Companion.displayCutout

    var maxNotifScrimTop by remember { mutableIntStateOf(0) }
    val tileSquishiness by
        animateContentFloatAsState(
            value = 1f,
            key = QuickSettings.SharedValues.TilesSquishiness,
            canOverflow = false,
        )

    LaunchedEffect(Unit) {
        snapshotFlow { tileSquishiness }.collect { viewModel.setTileSquishiness(it) }
    }

    val shouldPunchHoleBehindScrim =
        layoutState.isTransitioningBetween(Scenes.Gone, Scenes.Shade) ||
            layoutState.isTransitioning(from = Scenes.Lockscreen, to = Scenes.Shade)
    val mediaInRow = viewModel.showMediaInRow
    val notificationStackPadding = dimensionResource(id = R.dimen.notification_side_paddings)
    val navBarHeight = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

    val shadeHorizontalPadding =
        dimensionResource(id = R.dimen.notification_panel_margin_horizontal)
    val shadeMeasurePolicy =
        remember(cutoutLocation, cutoutInsets) {
            SingleShadeMeasurePolicy(
                onNotificationsTopChanged = { maxNotifScrimTop = it },
                cutoutInsetsProvider = {
                    if (cutoutLocation == CutoutLocation.CENTER) {
                        null
                    } else {
                        cutoutInsets
                    }
                },
            )
        }

    Box(
        modifier =
            modifier.thenIf(shouldPunchHoleBehindScrim) {
                // Render the scene to an offscreen buffer so that BlendMode.DstOut only clears this
                // scene (and not the one under it) during a scene transition.
                Modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            }
    ) {
        ShadePanelScrim(viewModel.isTransparencyEnabled)
        Layout(
            modifier =
                Modifier.thenIf(viewModel.isEmptySpaceClickable) {
                    Modifier.clickable(interactionSource = null, indication = null) {
                        viewModel.onEmptySpaceClicked()
                    }
                },
            content = {
                CollapsedShadeHeader(
                    viewModel = headerViewModel,
                    isSplitShade = false,
                    modifier = Modifier.layoutId(SingleShadeMeasurePolicy.LayoutId.ShadeHeader),
                )

                val qqsLayoutPaddingBottom = 16.dp
                val qsHorizontalMargin =
                    shadeHorizontalPadding + dimensionResource(id = R.dimen.qs_horizontal_margin)
                MediaAndQqsLayout(
                    modifier =
                        Modifier.element(QuickSettings.Elements.QuickQuickSettingsAndMedia)
                            .layoutId(SingleShadeMeasurePolicy.LayoutId.MediaAndQqs)
                            .padding(bottom = qqsLayoutPaddingBottom)
                            .padding(horizontal = qsHorizontalMargin),
                    tiles = {
                        Box {
                            val qqsViewModel =
                                rememberViewModel(traceName = "shade_scene_qqs") {
                                    viewModel.quickQuickSettingsViewModel.create()
                                }
                            if (viewModel.isQsEnabled) {
                                QuickQuickSettings(
                                    qqsViewModel,
                                    listening = { true },
                                    modifier = Modifier.sysuiResTag("quick_qs_panel"),
                                )
                            }
                        }
                    },
                    media =
                        @Composable {
                            if (viewModel.isQsEnabled && viewModel.showMedia) {
                                Element(key = Media.Elements.mediaCarousel, modifier = Modifier) {
                                    Media(
                                        viewModelFactory = viewModel.mediaViewModelFactory,
                                        presentationStyle =
                                            if (mediaInRow) {
                                                MediaPresentationStyle.Compressed
                                            } else {
                                                MediaPresentationStyle.Default
                                            },
                                        behavior = ShadeSceneContentViewModel.qqsMediaUiBehavior,
                                        onDismissed = viewModel::onMediaSwipeToDismiss,
                                    )
                                }
                            }
                        },
                    mediaInRow = mediaInRow,
                )

                NotificationScrollingStack(
                    shadeSession = shadeSession,
                    stackScrollView = notificationStackScrollView,
                    viewModel = notificationsPlaceholderViewModel,
                    jankMonitor = jankMonitor,
                    maxScrimTop = { maxNotifScrimTop.toFloat() },
                    shouldPunchHoleBehindScrim = shouldPunchHoleBehindScrim,
                    stackTopPadding = notificationStackPadding,
                    stackBottomPadding = navBarHeight,
                    supportNestedScrolling = true,
                    onEmptySpaceClick =
                        viewModel::onEmptySpaceClicked.takeIf { viewModel.isEmptySpaceClickable },
                    modifier =
                        Modifier.layoutId(SingleShadeMeasurePolicy.LayoutId.Notifications)
                            .padding(horizontal = shadeHorizontalPadding),
                )
            },
            measurePolicy = shadeMeasurePolicy,
        )
        Box(
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .height(navBarHeight)
                    // Intercepts touches, prevents the scrollable container behind from scrolling.
                    .clickable(interactionSource = null, indication = null) { /* do nothing */ }
        )
    }
}

@Composable
private fun MediaAndQqsLayout(
    tiles: @Composable () -> Unit,
    media: @Composable () -> Unit,
    mediaInRow: Boolean,
    modifier: Modifier = Modifier,
) {
    if (mediaInRow) {
        Row(
            modifier = modifier,
            horizontalArrangement = spacedBy(dimensionResource(R.dimen.qs_tile_margin_vertical)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) { tiles() }
            Box(modifier = Modifier.weight(1f)) { media() }
        }
    } else {
        Column(modifier = modifier, verticalArrangement = spacedBy(16.dp)) {
            tiles()
            media()
        }
    }
}

@Composable
private fun ContentScope.SplitShade(
    notificationStackScrollView: NotificationScrollView,
    viewModel: ShadeSceneContentViewModel,
    headerViewModel: ShadeHeaderViewModel,
    notificationsPlaceholderViewModel: NotificationsPlaceholderViewModel,
    modifier: Modifier = Modifier,
    shadeSession: SaveableSession,
    jankMonitor: InteractionJankMonitor,
) {

    val lifecycleOwner = LocalLifecycleOwner.current
    val footerActionsViewModel =
        remember(lifecycleOwner, viewModel) { viewModel.getFooterActionsViewModel(lifecycleOwner) }

    val qsContainerViewModel =
        rememberViewModel(traceName = "SplitShade.QSContainerViewModel") {
            viewModel.qsContainerViewModelFactory.create(supportsBrightnessMirroring = true)
        }

    val notificationStackPadding = dimensionResource(id = R.dimen.notification_side_paddings)
    val navBarBottomHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val brightnessMirrorShowing = qsContainerViewModel.brightnessSliderViewModel.showMirror

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

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .graphicsLayer { alpha = contentAlpha }
                .thenIf(brightnessMirrorShowing) { Modifier.gesturesDisabled() }
    ) {
        ShadePanelScrim(viewModel.isTransparencyEnabled)

        Column(modifier = Modifier.fillMaxSize()) {
            val unfoldTranslationXForStartSide = viewModel.unfoldTranslationXForStartSide

            CollapsedShadeHeader(
                viewModel = headerViewModel,
                isSplitShade = true,
                modifier =
                    Modifier.padding(horizontal = { unfoldTranslationXForStartSide.roundToInt() }),
            )

            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Box(
                    modifier =
                        Modifier.element(SplitShadeQuickSettings)
                            .overscroll(verticalOverscrollEffect)
                            .weight(1f)
                            .graphicsLayer { translationX = unfoldTranslationXForStartSide }
                            .fillMaxSize()
                            .padding(bottom = navBarBottomHeight)
                ) {
                    val sceneState =
                        rememberMutableSceneTransitionLayoutState(
                            initialScene =
                                remember { if (qsContainerViewModel.isEditing) Edit else QS },
                            transitions = transitions,
                        )

                    val coroutineScope = rememberCoroutineScope()

                    LaunchedEffect(sceneState, qsContainerViewModel.isEditing, coroutineScope) {
                        if (qsContainerViewModel.isEditing) {
                            sceneState.setTargetScene(Edit, coroutineScope)
                        } else {
                            sceneState.setTargetScene(QS, coroutineScope)
                        }
                    }

                    NestedSceneTransitionLayout(
                        state = sceneState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        scene(QS) {
                            val tileSquishiness by
                                with(this@SplitShade) {
                                    animateContentFloatAsState(
                                        value = 1f,
                                        key = QuickSettings.SharedValues.TilesSquishiness,
                                        canOverflow = false,
                                    )
                                }

                            LaunchedEffect(Unit) {
                                snapshotFlow { tileSquishiness }
                                    .collect { viewModel.setTileSquishiness(it) }
                            }

                            Element(QS.rootElementKey, Modifier) {
                                Column {
                                    Box(
                                        Modifier.weight(1f)
                                            .sysuiResTag("expanded_qs_scroll_view")
                                            .verticalScroll(rememberScrollState())
                                            .wrapContentHeight(
                                                align = Alignment.Top,
                                                unbounded = true,
                                            )
                                    ) {
                                        QuickSettingsContent(
                                            qsContainerViewModel,
                                            mediaInRow = false,
                                        )
                                    }
                                    FooterActionsWithAnimatedVisibility(
                                        viewModel = footerActionsViewModel,
                                        isCustomizing = false,
                                        customizingAnimationDuration = 0,
                                        modifier =
                                            Modifier.align(Alignment.CenterHorizontally)
                                                .sysuiResTag("qs_footer_actions"),
                                    )
                                }
                            }
                        }

                        scene(Edit) {
                            Element(Edit.rootElementKey, Modifier) {
                                GridAnchor()
                                EditMode(
                                    qsContainerViewModel.editModeViewModel,
                                    Modifier.testTag("edit_mode_scene")
                                        .padding(horizontal = QuickSettingsShade.Dimensions.Padding),
                                )
                            }
                        }
                    }
                }

                NotificationScrollingStack(
                    shadeSession = shadeSession,
                    stackScrollView = notificationStackScrollView,
                    viewModel = notificationsPlaceholderViewModel,
                    jankMonitor = jankMonitor,
                    maxScrimTop = { 0f },
                    stackTopPadding = notificationStackPadding,
                    stackBottomPadding = notificationStackPadding,
                    shouldPunchHoleBehindScrim = false,
                    supportNestedScrolling = false,
                    onEmptySpaceClick =
                        viewModel::onEmptySpaceClicked.takeIf { viewModel.isEmptySpaceClickable },
                    modifier =
                        Modifier.weight(weight = 1f)
                            .fillMaxHeight()
                            .padding(
                                end =
                                    dimensionResource(R.dimen.notification_panel_margin_horizontal),
                                bottom = navBarBottomHeight,
                            ),
                )
            }
        }
    }
}
