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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.android.compose.animation.scene.ElementContentScope
import com.android.systemui.keyguard.ui.binder.KeyguardIndicationAreaBinder
import com.android.systemui.keyguard.ui.view.KeyguardIndicationArea
import com.android.systemui.keyguard.ui.viewmodel.KeyguardIndicationAreaViewModel
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElement
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementProvider
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenScope
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.statusbar.KeyguardIndicationController
import javax.inject.Inject
import kotlin.collections.List
import kotlinx.coroutines.DisposableHandle

class IndicationAreaElementProvider
@Inject
constructor(
    @ShadeDisplayAware private val context: Context,
    private val indicationAreaViewModel: KeyguardIndicationAreaViewModel,
    private val indicationController: KeyguardIndicationController,
) : LockscreenElementProvider {
    override val elements: List<LockscreenElement> by lazy { listOf(IndicationAreaElement()) }

    private inner class IndicationAreaElement : LockscreenElement {
        override val key = LockscreenElementKeys.IndicationArea
        override val context = this@IndicationAreaElementProvider.context

        @Composable
        override fun LockscreenScope<ElementContentScope>.LockscreenElement() {
            IndicationArea()
        }
    }

    @Composable
    fun IndicationArea(modifier: Modifier = Modifier) {
        val (disposable, setDisposable) = remember { mutableStateOf<DisposableHandle?>(null) }

        AndroidView(
            factory = { context ->
                val view = KeyguardIndicationArea(context, null)
                view.isFocusable = true
                view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                setDisposable(
                    KeyguardIndicationAreaBinder.bind(
                        view = view,
                        viewModel = indicationAreaViewModel,
                        indicationController = indicationController,
                    )
                )
                view
            },
            onRelease = { disposable?.dispose() },
            modifier = modifier.fillMaxSize(),
        )
    }
}
