/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.keyguard.ui.composable.elements

import android.content.Context
import androidx.compose.runtime.Composable
import com.android.compose.animation.scene.ElementContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.plugins.keyguard.ui.composable.elements.BaseLockscreenElement.ElementSource
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElement
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementProvider
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenScope
import com.android.systemui.shade.ShadeDisplayAware
import javax.inject.Inject

@SysUISingleton
class AmbientIndicationAreaProvider
@Inject
constructor(@ShadeDisplayAware private val context: Context) : LockscreenElementProvider {

    override val elements: List<LockscreenElement> by lazy {
        listOf(AmbientIndicationAreaElement())
    }

    private inner class AmbientIndicationAreaElement : LockscreenElement {

        override val key: ElementKey = LockscreenElementKeys.AmbientIndicationArea

        @Composable
        override fun LockscreenScope<ElementContentScope>.LockscreenElement() {
            // This is the AOSP implementation, this is intentionally empty.
        }

        override val context = this@AmbientIndicationAreaProvider.context
        override val source = ElementSource.STANDARD
    }
}
