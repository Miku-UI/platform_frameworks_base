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

package com.android.systemui.keyguard.ui.composable.elements

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.viewinterop.NoOpUpdate
import androidx.core.view.isEmpty
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.scene.MovableElementContentScope
import com.android.compose.animation.scene.MovableElementKey
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.KeyguardUnlockAnimationController
import com.android.systemui.keyguard.ui.composable.elements.LockscreenUpperRegionElementProvider.Companion.LayoutType
import com.android.systemui.keyguard.ui.composable.elements.LockscreenUpperRegionElementProvider.Companion.getLayoutType
import com.android.systemui.keyguard.ui.viewmodel.KeyguardSmartspaceViewModel
import com.android.systemui.plugins.BcSmartspaceDataPlugin.SmartspaceTargetListener
import com.android.systemui.plugins.keyguard.ui.composable.elements.BaseLockscreenElement.ElementSource
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.Smartspace
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementProvider
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenScope
import com.android.systemui.plugins.keyguard.ui.composable.elements.MovableLockscreenElement
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.shared.R as sharedR
import com.android.systemui.statusbar.lockscreen.LockscreenSmartspaceController
import javax.inject.Inject

@SysUISingleton
class SmartspaceElementProvider
@Inject
constructor(
    @ShadeDisplayAware private val context: Context,
    private val smartspaceController: LockscreenSmartspaceController,
    private val keyguardUnlockAnimationController: KeyguardUnlockAnimationController,
    private val keyguardSmartspaceViewModel: KeyguardSmartspaceViewModel,
) : LockscreenElementProvider {
    override val elements: List<MovableLockscreenElement> by lazy {
        listOf(
            DWAColumnElement(Smartspace.DWA.SmallClock.Column, isLargeClock = false),
            DWARowElement(Smartspace.DWA.SmallClock.Row, isLargeClock = false),
            DWARowElement(Smartspace.DWA.LargeClock.Above, isLargeClock = true),
            DWARowElement(Smartspace.DWA.LargeClock.Below, isLargeClock = true),
            CardsElement(),
        )
    }

    private inner class DWAColumnElement(
        override val key: MovableElementKey,
        private val isLargeClock: Boolean,
    ) : MovableLockscreenElement {
        override val context = this@SmartspaceElementProvider.context
        override val source = ElementSource.STANDARD

        private val view: View by lazy {
            setupDate(context, isLargeClock) { it.orientation = LinearLayout.VERTICAL }
        }

        @Composable
        override fun LockscreenScope<MovableElementContentScope>.LockscreenElement() {
            if (!keyguardSmartspaceViewModel.isSmartspaceEnabled) {
                return
            }

            val isWeatherEnabled: Boolean by
                keyguardSmartspaceViewModel.isWeatherEnabled.collectAsStateWithLifecycle(false)

            LookaheadAndroidView(
                factory = { view },
                modifier = Modifier.burnInAware(isClock = false).nonAuthUI(),
                update = { view -> updateDWA(view as LinearLayout, isWeatherEnabled, isLargeClock) },
            )
        }
    }

    private inner class DWARowElement(
        override val key: MovableElementKey,
        private val isLargeClock: Boolean,
    ) : MovableLockscreenElement {
        override val context = this@SmartspaceElementProvider.context
        override val source = ElementSource.STANDARD

        private val view: View by lazy {
            setupDate(context, isLargeClock) { it.orientation = LinearLayout.HORIZONTAL }
        }

        @Composable
        override fun LockscreenScope<MovableElementContentScope>.LockscreenElement() {
            if (!keyguardSmartspaceViewModel.isSmartspaceEnabled) {
                return
            }

            val isWeatherEnabled: Boolean by
                keyguardSmartspaceViewModel.isWeatherEnabled.collectAsStateWithLifecycle(false)

            LookaheadAndroidView(
                factory = { view },
                modifier = Modifier.burnInAware(isClock = false).nonAuthUI(),
                update = { view -> updateDWA(view as LinearLayout, isWeatherEnabled, isLargeClock) },
            )
        }
    }

    private fun setupDate(
        ctx: Context,
        isLargeClock: Boolean,
        callback: (LinearLayout) -> Unit,
    ): View {
        val dateView =
            smartspaceController.buildAndConnectDateView(ctx, isLargeClock) as LinearLayout
        callback(dateView)
        return dateView
    }

    private fun updateDWA(
        linearLayout: LinearLayout,
        isWeatherEnabled: Boolean,
        isLargeClock: Boolean,
    ) {
        val id =
            if (isLargeClock) {
                sharedR.id.weather_smartspace_view_large
            } else {
                sharedR.id.weather_smartspace_view
            }
        val weatherView: View? = linearLayout.findViewById(id)
        if (weatherView == null) {
            if (isWeatherEnabled) {
                smartspaceController
                    .buildAndConnectWeatherView(linearLayout.context, isLargeClock)
                    ?.let { view ->
                        // Place weather right after the date, before the extras (alarm and DND).
                        val index = if (linearLayout.isEmpty()) 0 else 1
                        linearLayout.addView(view, index)
                    }
            }
        } else if (!isWeatherEnabled) {
            linearLayout.removeView(weatherView)
        }
    }

    private inner class CardsElement : MovableLockscreenElement {
        override val key = Smartspace.Cards
        override val context = this@SmartspaceElementProvider.context
        override val source = ElementSource.STANDARD

        private val view: View by lazy { smartspaceController.buildAndConnectView(context)!! }

        @Composable
        override fun LockscreenScope<MovableElementContentScope>.LockscreenElement() {
            if (!keyguardSmartspaceViewModel.isSmartspaceEnabled) {
                return
            }

            // In wide-layouts limit the maximum width of the card to be half the screen width.
            val shadeMode by keyguardSmartspaceViewModel.shadeMode.collectAsStateWithLifecycle()
            val widthMod =
                when (getLayoutType(shadeMode)) {
                    LayoutType.NARROW -> Modifier.fillMaxWidth()
                    LayoutType.WIDE -> {
                        val width = LocalConfiguration.current.screenWidthDp.dp
                        Modifier.widthIn(max = width / 2f)
                    }
                }

            var hasTargets by remember { mutableStateOf(false) }
            // Using DisposableEffect here ties the lifetime of the smartspace listener to the
            // lifecycle of the composable function. This listener will be removed and cleaned up
            // when the composable leaves scope. Listening directly in this way provides a signal
            // to smartspace to keep the session connected, even when the view is detached.
            DisposableEffect(Unit) {
                val listener = SmartspaceTargetListener { targets ->
                    hasTargets = targets.count() > 0
                }
                smartspaceController.addListener(listener)
                onDispose { smartspaceController.removeListener(listener) }
            }

            val isDozing by keyguardSmartspaceViewModel.isDozing.collectAsStateWithLifecycle()
            val hasMediaTarget = smartspaceController.mediaTarget != null && isDozing

            AnimatedVisibility(
                visible = hasTargets || hasMediaTarget,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                LookaheadAndroidView(
                    factory = { _ ->
                        keyguardUnlockAnimationController.lockscreenSmartspace = view
                        view
                    },
                    onRelease = { view ->
                        if (keyguardUnlockAnimationController.lockscreenSmartspace == view) {
                            keyguardUnlockAnimationController.lockscreenSmartspace = null
                        }
                    },
                    modifier =
                        Modifier.then(widthMod)
                            .padding(
                                bottom =
                                    dimensionResource(R.dimen.keyguard_status_view_bottom_margin)
                            )
                            .burnInAware(isClock = false)
                            .nonAuthUI(),
                )
            }
        }
    }

    companion object {
        @Composable
        @Deprecated("This is a hack. Do not use generally.")
        private fun <T : View> LookaheadAndroidView(
            factory: (Context) -> T,
            modifier: Modifier = Modifier,
            onReset: ((T) -> Unit)? = null,
            onRelease: (T) -> Unit = NoOpUpdate,
            update: (T) -> Unit = NoOpUpdate,
        ) {
            // If the AndroidView resizes, the Lookahead pass might remain in an old state.
            // TODO(b/460044592) We track this to force Lookahead to update to the new size.
            var lookaheadInvalidationTrigger by remember { mutableStateOf(IntOffset(0, 0)) }
            val listener = remember {
                View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                    lookaheadInvalidationTrigger = IntOffset(view.width, view.height)
                }
            }

            AndroidView(
                factory = { ctx ->
                    val view = factory(ctx)
                    view.addOnLayoutChangeListener(listener)
                    view
                },
                onRelease = { view ->
                    onRelease(view)
                    view.removeOnLayoutChangeListener(listener)
                },
                onReset = onReset,
                update = update,
                modifier =
                    Modifier.layout { measurable, constraints ->
                            if (isLookingAhead) {
                                // If the AndroidView resizes, this state changes, forcing Compose
                                // to re-run this Lookahead measure block with the correct size.
                                @Suppress("UNUSED_EXPRESSION") lookaheadInvalidationTrigger
                            }

                            measurable.measure(constraints).run {
                                layout(width, height) { place(IntOffset.Zero) }
                            }
                        }
                        .then(modifier),
            )
        }
    }
}
