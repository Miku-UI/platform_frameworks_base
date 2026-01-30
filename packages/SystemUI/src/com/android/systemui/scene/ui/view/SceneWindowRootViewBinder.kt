/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.systemui.scene.ui.view

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.view.isVisible
import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.SceneKey
import com.android.compose.theme.PlatformTheme
import com.android.systemui.common.ui.compose.windowinsets.LocalDisplayCutout
import com.android.systemui.common.ui.compose.windowinsets.LocalScreenCornerRadius
import com.android.systemui.common.ui.compose.windowinsets.ScreenDecorProvider
import com.android.systemui.common.ui.compose.windowinsets.rememberDisplayCutout
import com.android.systemui.common.ui.compose.windowinsets.rememberScreenCornerRadius
import com.android.systemui.compose.modifiers.sysUiResTagContainer
import com.android.systemui.initOnBackPressedDispatcherOwner
import com.android.systemui.keyboard.shortcut.ui.composable.InteractionsConfig
import com.android.systemui.keyboard.shortcut.ui.composable.rememberShortcutHelperIndication
import com.android.systemui.lifecycle.WindowLifecycleState
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.lifecycle.setSnapshotBinding
import com.android.systemui.lifecycle.viewModel
import com.android.systemui.res.R
import com.android.systemui.scene.shared.model.SceneContainerConfig
import com.android.systemui.scene.shared.model.SceneDataSourceDelegator
import com.android.systemui.scene.ui.composable.DualShadeEducationalTooltips
import com.android.systemui.scene.ui.composable.Overlay
import com.android.systemui.scene.ui.composable.Scene
import com.android.systemui.scene.ui.composable.SceneContainer
import com.android.systemui.scene.ui.viewmodel.DualShadeEducationalTooltipsViewModel
import com.android.systemui.scene.ui.viewmodel.SceneContainerViewModel
import com.android.systemui.shade.ui.composable.LocalStatusIconContext
import com.android.systemui.shade.ui.composable.rememberStatusIconContext
import com.android.systemui.statusbar.notification.stack.ui.view.SharedNotificationContainer
import com.android.systemui.statusbar.phone.ui.TintedIconManager
import kotlinx.coroutines.awaitCancellation

/** View binder that wires up scene container specific view bindings. */
object SceneWindowRootViewBinder {

    /** Binds between the view and view-model pertaining to a specific scene container. */
    fun bind(
        view: ViewGroup,
        viewModelFactory: SceneContainerViewModel.Factory,
        motionEventHandlerReceiver: (SceneContainerViewModel.MotionEventHandler?) -> Unit,
        windowInsets: State<WindowInsets?>,
        containerConfig: SceneContainerConfig,
        sharedNotificationContainer: SharedNotificationContainer,
        scenes: Set<Scene>,
        overlays: Set<Overlay>,
        onVisibilityChangedInternal: (isVisible: Boolean) -> Unit,
        dataSourceDelegator: SceneDataSourceDelegator,
        sceneJankMonitorFactory: SceneJankMonitor.Factory,
        tintedIconManagerFactory: TintedIconManager.Factory,
    ) {
        val unsortedSceneByKey: Map<SceneKey, Scene> = scenes.associateBy { scene -> scene.key }
        val sortedSceneByKey: Map<SceneKey, Scene> =
            LinkedHashMap<SceneKey, Scene>(containerConfig.sceneKeys.size).apply {
                containerConfig.sceneKeys.forEach { sceneKey ->
                    val scene =
                        checkNotNull(unsortedSceneByKey[sceneKey]) {
                            "Scene not found for key \"$sceneKey\"!"
                        }

                    put(sceneKey, scene)
                }
            }

        val unsortedOverlayByKey: Map<OverlayKey, Overlay> =
            overlays.associateBy { overlay -> overlay.key }
        val sortedOverlayByKey: Map<OverlayKey, Overlay> =
            LinkedHashMap<OverlayKey, Overlay>(containerConfig.overlayKeys.size).apply {
                containerConfig.overlayKeys.forEach { overlayKey ->
                    val overlay =
                        checkNotNull(unsortedOverlayByKey[overlayKey]) {
                            "Overlay not found for key \"$overlayKey\"!"
                        }

                    put(overlayKey, overlay)
                }
            }

        view.repeatWhenAttached {
            view.viewModel(
                traceName = "SceneWindowRootViewBinder",
                minWindowLifecycleState = WindowLifecycleState.ATTACHED,
                factory = { viewModelFactory.create(view, motionEventHandlerReceiver) },
            ) { viewModel ->
                try {
                    view.initOnBackPressedDispatcherOwner(
                        lifecycle = this@repeatWhenAttached.lifecycle,
                        force = true,
                    )

                    view.addView(
                        createSceneContainerView(
                                context = view.context,
                                viewModel = viewModel,
                                windowInsets = windowInsets,
                                sceneByKey = sortedSceneByKey,
                                overlayByKey = sortedOverlayByKey,
                                dataSourceDelegator = dataSourceDelegator,
                                containerConfig = containerConfig,
                                sceneJankMonitorFactory = sceneJankMonitorFactory,
                                tintedIconManagerFactory = tintedIconManagerFactory,
                            )
                            .also { it.id = R.id.scene_container_root_composable }
                    )

                    val legacyView = view.requireViewById<View>(R.id.legacy_window_root)
                    legacyView.isVisible = false

                    // This moves the SharedNotificationContainer to the WindowRootView just after
                    //  the SceneContainerView. This SharedNotificationContainer should contain NSSL
                    //  due to the NotificationStackScrollLayoutSection (legacy) or
                    //  NotificationSection (scene container) moving it there.
                    (sharedNotificationContainer.parent as? ViewGroup)?.removeView(
                        sharedNotificationContainer
                    )
                    view.addView(sharedNotificationContainer)

                    view.addView(
                        createDualShadeEducationalTooltipsView(
                            context = view.context,
                            viewModelFactory =
                                viewModel.dualShadeEducationalTooltipsViewModelFactory,
                            windowInsets = windowInsets,
                        )
                    )

                    view.setSnapshotBinding { onVisibilityChangedInternal(viewModel.isVisible) }
                    awaitCancellation()
                } finally {
                    // Here when destroyed.
                    view.removeAllViews()
                }
            }
        }
    }

    private fun createSceneContainerView(
        context: Context,
        viewModel: SceneContainerViewModel,
        windowInsets: State<WindowInsets?>,
        sceneByKey: Map<SceneKey, Scene>,
        overlayByKey: Map<OverlayKey, Overlay>,
        dataSourceDelegator: SceneDataSourceDelegator,
        containerConfig: SceneContainerConfig,
        sceneJankMonitorFactory: SceneJankMonitor.Factory,
        tintedIconManagerFactory: TintedIconManager.Factory,
    ): View {
        return ComposeView(context).apply {
            setContent {
                PlatformTheme {
                    CompositionLocalProvider(
                        LocalScreenCornerRadius provides rememberScreenCornerRadius(),
                        LocalDisplayCutout provides rememberDisplayCutout { windowInsets.value },
                        LocalStatusIconContext provides
                            rememberStatusIconContext(tintedIconManagerFactory),
                        LocalIndication provides
                            rememberShortcutHelperIndication(
                                InteractionsConfig(
                                    hoverOverlayColor = MaterialTheme.colorScheme.onSurface,
                                    hoverOverlayAlpha = 0.11f,
                                    pressedOverlayColor = MaterialTheme.colorScheme.onSurface,
                                    pressedOverlayAlpha = 0.15f,
                                    // we are OK using this as our content is clipped and all
                                    // corner radius are larger than this
                                    surfaceCornerRadius = 16.dp,
                                )
                            ),
                    ) {
                        SceneContainer(
                            viewModel = viewModel,
                            sceneByKey = sceneByKey,
                            overlayByKey = overlayByKey,
                            initialSceneKey = containerConfig.initialSceneKey,
                            transitionsBuilder = containerConfig.transitionsBuilder,
                            dataSourceDelegator = dataSourceDelegator,
                            sceneJankMonitorFactory = sceneJankMonitorFactory,
                            modifier = Modifier.sysUiResTagContainer(),
                        )
                    }
                }
            }
        }
    }

    private fun createDualShadeEducationalTooltipsView(
        context: Context,
        viewModelFactory: DualShadeEducationalTooltipsViewModel.Factory,
        windowInsets: State<WindowInsets?>,
    ): View {
        return ComposeView(context).apply {
            layoutParams =
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                )
            setContent {
                PlatformTheme {
                    ScreenDecorProvider(windowInsets = { windowInsets.value }) {
                        DualShadeEducationalTooltips(viewModelFactory = viewModelFactory)
                    }
                }
            }
        }
    }
}
