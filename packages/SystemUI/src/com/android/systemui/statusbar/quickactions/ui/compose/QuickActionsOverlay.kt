/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.statusbar.quickactions.ui.compose

import android.graphics.RectF
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.ui.composable.Overlay
import com.android.systemui.shade.ui.composable.OverlayScrim
import com.android.systemui.statusbar.quickactions.av.ui.compose.AvControlsChipPopup
import com.android.systemui.statusbar.quickactions.av.ui.viewmodel.AvControlsPopupViewModel
import com.android.systemui.statusbar.quickactions.media.ui.compose.MediaControlPopup
import com.android.systemui.statusbar.quickactions.media.ui.viewmodel.MediaControlPopupViewModel
import com.android.systemui.statusbar.quickactions.popups.ui.viewmodel.StatusBarPopupViewModel
import com.android.systemui.statusbar.quickactions.screenrecord.ui.compose.LargeScreenStopRecordingPopup
import com.android.systemui.statusbar.quickactions.screenrecord.ui.viewmodel.LargeScreenStopRecordingPopupViewModel2
import com.android.systemui.statusbar.quickactions.sharescreen.ui.compose.ShareScreenPrivacyIndicatorPopup
import com.android.systemui.statusbar.quickactions.sharescreen.ui.viewmodel.ShareScreenPrivacyIndicatorPopupViewModel
import com.android.systemui.statusbar.quickactions.ui.viewmodel.QuickActionOverlayViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.awaitCancellation

@SysUISingleton
class QuickActionsOverlay
@Inject
constructor(private val viewModelFactory: QuickActionOverlayViewModel.Factory) : Overlay {

    override val key = Overlays.QuickActions
    override val alwaysCompose: Boolean = false

    override suspend fun activate(): Nothing {
        awaitCancellation()
    }

    @Composable
    override fun ContentScope.Content(modifier: Modifier) {
        val viewModel = rememberViewModel("QuickActionsOverlay") { viewModelFactory.create() }

        val activeChip = viewModel.activePanel

        // Break early if there's nothing to render
        if (activeChip == null) {
            return
        }

        Box(modifier = modifier.fillMaxSize()) {
            OverlayScrim(onClicked = viewModel::close, showBackgroundColor = false)

            val popupViewModel =
                rememberViewModel("popupViewModel", key = activeChip) {
                    activeChip.panelContentViewModelFactory.create()
                }
            QuickActionsPanel(
                popupViewModel,
                activeChip.anchorBounds,
                viewModel::onShadeOverlayBoundsChanged,
            )
        }
    }
}

@Composable
fun ContentScope.QuickActionsPanel(
    viewModel: StatusBarPopupViewModel,
    anchorBounds: RectF?,
    onBoundsChanged: (androidx.compose.ui.geometry.Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val edgePaddingPx = with(density) { QuickActions.Dimensions.EdgePadding.roundToPx() }

    val panelModifier =
        modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val popupWidth = placeable.width
                val popupHeight = placeable.height

                var x = 0
                var y = 0

                if (anchorBounds != null) {
                    val chipCenterX =
                        anchorBounds.left + ((anchorBounds.right - anchorBounds.left) / 2f)

                    val desiredX = chipCenterX - (popupWidth / 2f)
                    val maxRight = constraints.maxWidth - edgePaddingPx
                    x =
                        desiredX
                            .roundToInt()
                            .coerceIn(edgePaddingPx, maxOf(edgePaddingPx, maxRight - popupWidth))

                    val desiredY = anchorBounds.bottom
                    val maxBottom = constraints.maxHeight - edgePaddingPx
                    y =
                        desiredY
                            .roundToInt()
                            .coerceIn(edgePaddingPx, maxOf(edgePaddingPx, maxBottom - popupHeight))
                }

                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.placeRelative(x, y)
                }
            }
            .onPlaced { coordinates ->
                val bounds = coordinates.boundsInWindow()
                onBoundsChanged(bounds)
            }
            .element(QuickActions.Elements.Panel)

    val viewRootImpl = LocalView.current.viewRootImpl
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val owner =
        remember(viewRootImpl, lifecycle) {
            object : OnBackPressedDispatcherOwner {
                override val onBackPressedDispatcher =
                    OnBackPressedDispatcher().apply {
                        // viewRootImpl can be null in some preview/test environments
                        viewRootImpl?.let { setOnBackInvokedDispatcher(it.onBackInvokedDispatcher) }
                    }

                override val lifecycle: Lifecycle = lifecycle
            }
        }

    CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides owner) {
        when (viewModel) {
            is MediaControlPopupViewModel -> {
                MediaControlPopup(viewModel = viewModel, modifier = panelModifier)
            }
            is AvControlsPopupViewModel -> {
                AvControlsChipPopup(viewModel = viewModel, modifier = panelModifier)
            }
            is ShareScreenPrivacyIndicatorPopupViewModel -> {
                ShareScreenPrivacyIndicatorPopup(viewModel = viewModel, modifier = panelModifier)
            }
            is LargeScreenStopRecordingPopupViewModel2 -> {
                LargeScreenStopRecordingPopup(viewModel = viewModel, modifier = panelModifier)
            }
            else ->
                error(
                    "Unsupported QuickActions ViewModel type: ${viewModel::class.java.simpleName}"
                )
        }
    }
}

object QuickActions {
    object Elements {
        val Panel = ElementKey("QuickActionsOverlayPanel")
    }

    object Dimensions {
        val EdgePadding: Dp
            @Composable @ReadOnlyComposable get() = 12.dp
    }
}
