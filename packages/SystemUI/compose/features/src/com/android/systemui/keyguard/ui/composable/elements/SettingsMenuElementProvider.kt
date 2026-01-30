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
import android.view.LayoutInflater
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.isVisible
import com.android.compose.animation.scene.ElementContentScope
import com.android.compose.modifiers.padding
import com.android.systemui.keyguard.ui.binder.KeyguardSettingsViewBinder
import com.android.systemui.keyguard.ui.viewmodel.KeyguardSettingsMenuViewModel
import com.android.systemui.keyguard.ui.viewmodel.KeyguardTouchHandlingViewModel
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElement
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementProvider
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenScope
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.statusbar.VibratorHelper
import javax.inject.Inject
import kotlin.collections.List
import kotlinx.coroutines.DisposableHandle

class SettingsMenuElementProvider
@Inject
constructor(
    @ShadeDisplayAware private val context: Context,
    private val viewModel: KeyguardSettingsMenuViewModel,
    private val touchHandlingViewModelFactory: KeyguardTouchHandlingViewModel.Factory,
    private val vibratorHelper: VibratorHelper,
    private val activityStarter: ActivityStarter,
) : LockscreenElementProvider {
    override val elements: List<LockscreenElement> by lazy { listOf(SettingsMenuElement()) }

    private inner class SettingsMenuElement : LockscreenElement {
        override val key = LockscreenElementKeys.SettingsMenu
        override val context = this@SettingsMenuElementProvider.context

        @Composable
        override fun LockscreenScope<ElementContentScope>.LockscreenElement() {
            SettingsMenu()
        }
    }

    @Composable
    @SuppressWarnings("InflateParams") // null is passed into the inflate call, on purpose.
    fun SettingsMenu(modifier: Modifier = Modifier) {
        val (disposableHandle, setDisposableHandle) =
            remember { mutableStateOf<DisposableHandle?>(null) }
        AndroidView(
            factory = { context ->
                LayoutInflater.from(context)
                    .inflate(R.layout.keyguard_settings_popup_menu, null)
                    .apply {
                        isVisible = false
                        alpha = 0f

                        setDisposableHandle(
                            KeyguardSettingsViewBinder.bind(
                                view = this,
                                viewModel = viewModel,
                                touchHandlingViewModelFactory = touchHandlingViewModelFactory,
                                rootViewModel = null,
                                vibratorHelper = vibratorHelper,
                                activityStarter = activityStarter,
                            )
                        )
                    }
            },
            onRelease = { disposableHandle?.dispose() },
            modifier =
                modifier
                    .padding(
                        bottom = dimensionResource(R.dimen.keyguard_affordance_vertical_offset)
                    )
                    .padding(
                        horizontal =
                            dimensionResource(R.dimen.keyguard_affordance_horizontal_offset)
                    ),
        )
    }
}
