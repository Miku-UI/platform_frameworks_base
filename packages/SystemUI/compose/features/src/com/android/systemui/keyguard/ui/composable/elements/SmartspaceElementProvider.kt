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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.scene.MovableElementContentScope
import com.android.compose.animation.scene.MovableElementKey
import com.android.compose.modifiers.padding
import com.android.systemui.customization.clocks.R as clocksR
import com.android.systemui.keyguard.KeyguardUnlockAnimationController
import com.android.systemui.keyguard.ui.viewmodel.AodBurnInViewModel
import com.android.systemui.keyguard.ui.viewmodel.KeyguardSmartspaceViewModel
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.Smartspace
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementProvider
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenScope
import com.android.systemui.plugins.keyguard.ui.composable.elements.MovableLockscreenElement
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.statusbar.lockscreen.LockscreenSmartspaceController
import javax.inject.Inject
import kotlin.collections.List

class SmartspaceElementProvider
@Inject
constructor(
    @ShadeDisplayAware private val context: Context,
    private val smartspaceController: LockscreenSmartspaceController,
    private val keyguardUnlockAnimationController: KeyguardUnlockAnimationController,
    private val keyguardSmartspaceViewModel: KeyguardSmartspaceViewModel,
    private val aodBurnInViewModel: AodBurnInViewModel,
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

        @Composable
        override fun LockscreenScope<MovableElementContentScope>.LockscreenElement() {
            if (!keyguardSmartspaceViewModel.isDateWeatherDecoupled) {
                return
            }

            val isWeatherEnabled: Boolean by
                keyguardSmartspaceViewModel.isWeatherEnabled.collectAsStateWithLifecycle(false)

            AndroidView(
                factory = { ctx ->
                    setupDWA(ctx, isWeatherEnabled, isLargeClock) {
                        it.orientation = LinearLayout.VERTICAL
                    }
                },
                modifier = context.burnInModifier,
            )
        }
    }

    private inner class DWARowElement(
        override val key: MovableElementKey,
        private val isLargeClock: Boolean,
    ) : MovableLockscreenElement {
        override val context = this@SmartspaceElementProvider.context

        @Composable
        override fun LockscreenScope<MovableElementContentScope>.LockscreenElement() {
            if (!keyguardSmartspaceViewModel.isDateWeatherDecoupled) {
                return
            }

            val isWeatherEnabled: Boolean by
                keyguardSmartspaceViewModel.isWeatherEnabled.collectAsStateWithLifecycle(false)

            AndroidView(
                factory = { ctx ->
                    setupDWA(ctx, isWeatherEnabled, isLargeClock) {
                        it.orientation = LinearLayout.HORIZONTAL
                    }
                },
                modifier = context.burnInModifier,
            )
        }
    }

    private fun setupDWA(
        ctx: Context,
        isWeatherEnabled: Boolean,
        isLargeClock: Boolean,
        callback: (LinearLayout) -> Unit,
    ): View {
        val dateView =
            smartspaceController.buildAndConnectDateView(ctx, isLargeClock) as LinearLayout
        if (isWeatherEnabled) {
            smartspaceController.buildAndConnectWeatherView(ctx, isLargeClock)?.let {
                // Place weather right after the date, before the extras (alarm and dnd)
                val index = if (dateView.childCount == 0) 0 else 1
                dateView.addView(it, index)
            }
        }
        callback(dateView)
        return dateView
    }

    private inner class CardsElement : MovableLockscreenElement {
        override val key = Smartspace.Cards
        override val context = this@SmartspaceElementProvider.context

        @Composable
        override fun LockscreenScope<MovableElementContentScope>.LockscreenElement() {
            if (!keyguardSmartspaceViewModel.isSmartspaceEnabled) {
                return
            }

            val clockPadding = dimensionResource(clocksR.dimen.clock_padding_start)

            AndroidView(
                factory = { ctx ->
                    val view = smartspaceController.buildAndConnectView(ctx)!!
                    keyguardUnlockAnimationController.lockscreenSmartspace = view
                    view
                },
                onRelease = { view ->
                    if (keyguardUnlockAnimationController.lockscreenSmartspace == view) {
                        keyguardUnlockAnimationController.lockscreenSmartspace = null
                    }
                },
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(
                            // Note: smartspace adds 16dp of start padding internally
                            start = clockPadding - 16.dp,
                            end = clockPadding,
                            bottom = dimensionResource(R.dimen.keyguard_status_view_bottom_margin),
                        )
                        .then(context.burnInModifier),
            )
        }
    }
}
