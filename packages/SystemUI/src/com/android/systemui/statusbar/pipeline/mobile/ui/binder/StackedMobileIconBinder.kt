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

package com.android.systemui.statusbar.pipeline.mobile.ui.binder

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.statusbar.pipeline.mobile.StatusBarMobileIconKairos
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.MobileIconsViewModel
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.MobileIconsViewModelKairos
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.StackedMobileIconViewModel
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.StackedMobileIconViewModelImpl
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.StackedMobileIconViewModelKairos
import com.android.systemui.statusbar.pipeline.shared.ui.binder.ModernStatusBarViewBinding
import com.android.systemui.statusbar.pipeline.shared.ui.composable.StackedMobileIcon
import com.android.systemui.statusbar.pipeline.shared.ui.view.SingleBindableStatusBarComposeIconView
import dagger.Lazy

object StackedMobileIconBinder {
    fun bind(
        view: SingleBindableStatusBarComposeIconView,
        mobileIconsViewModel: Lazy<MobileIconsViewModel>,
        mobileIconsViewModelKairos: Lazy<MobileIconsViewModelKairos>,
        viewModelFactory: StackedMobileIconViewModelImpl.Factory,
        kairosViewModelFactory: StackedMobileIconViewModelKairos.Factory,
    ): ModernStatusBarViewBinding {
        return SingleBindableStatusBarComposeIconView.withDefaultBinding(
            view = view,
            shouldBeVisible =
                if (StatusBarMobileIconKairos.isEnabled) {
                    mobileIconsViewModelKairos.get().isStackableFlow
                } else {
                    mobileIconsViewModel.get().isStackable
                },
        ) { _, tintFlow ->
            view.composeView.apply {
                setViewCompositionStrategy(
                    if (SceneContainerFlag.isEnabled) {
                        ViewCompositionStrategy.Default
                    } else {
                        ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                    }
                )
                setContent {
                    val viewModel: StackedMobileIconViewModel =
                        rememberViewModel("StackedMobileIconBinder") {
                            if (StatusBarMobileIconKairos.isEnabled) {
                                kairosViewModelFactory.create()
                            } else {
                                viewModelFactory.create()
                            }
                        }
                    val tint by tintFlow.collectAsStateWithLifecycle()
                    if (viewModel.isIconVisible) {
                        CompositionLocalProvider(LocalContentColor provides Color(tint)) {
                            StackedMobileIcon(
                                viewModel,
                                modifier = Modifier.onSizeChanged { view.requestLayout() },
                            )
                        }
                    }
                }
            }
        }
    }
}
