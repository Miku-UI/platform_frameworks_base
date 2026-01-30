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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import com.android.compose.animation.scene.ElementContentScope
import com.android.systemui.keyguard.ui.composable.elements.LockscreenUpperRegionElementProvider.Companion.LayoutType
import com.android.systemui.keyguard.ui.composable.elements.LockscreenUpperRegionElementProvider.Companion.getLayoutType
import com.android.systemui.keyguard.ui.viewmodel.KeyguardMediaViewModel
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.media.remedia.ui.compose.Media
import com.android.systemui.media.remedia.ui.compose.MediaPresentationStyle
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElement
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementProvider
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenScope
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import javax.inject.Inject
import kotlin.collections.List

class MediaElementProvider
@Inject
constructor(
    @ShadeDisplayAware private val context: Context,
    private val mediaViewModelFactory: KeyguardMediaViewModel.Factory,
) : LockscreenElementProvider {
    override val elements: List<LockscreenElement> by lazy { listOf(MediaCarouselElement()) }

    private inner class MediaCarouselElement : LockscreenElement {
        override val key = LockscreenElementKeys.MediaCarousel
        override val context = this@MediaElementProvider.context

        @Composable
        override fun LockscreenScope<ElementContentScope>.LockscreenElement() {
            val horizontalPadding =
                when (getLayoutType()) {
                    LayoutType.WIDE -> dimensionResource(R.dimen.notification_side_paddings)
                    LayoutType.NARROW ->
                        dimensionResource(R.dimen.notification_side_paddings) +
                            dimensionResource(R.dimen.notification_panel_margin_horizontal)
                }

            val viewModel =
                rememberViewModel("MediaCarouselElement") { mediaViewModelFactory.create() }

            AnimatedVisibility(viewModel.isMediaActive && !viewModel.isDozing) {
                Element(
                    key = Media.Elements.mediaCarousel,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
                ) {
                    Media(
                        viewModelFactory = viewModel.mediaViewModelFactory,
                        presentationStyle = MediaPresentationStyle.Default,
                        behavior = viewModel.mediaUiBehavior,
                        onDismissed = viewModel::onSwipeToDismiss,
                    )
                }
            }
        }
    }
}
