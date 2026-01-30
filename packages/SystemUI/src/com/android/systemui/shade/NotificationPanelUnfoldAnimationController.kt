/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.shade

import android.content.Context
import android.view.Display
import android.view.ViewGroup
import com.android.app.tracing.coroutines.launchTraced
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.res.R
import com.android.systemui.shade.domain.interactor.ShadeDisplaysInteractor
import com.android.systemui.shade.shared.flag.ShadeWindowGoesAround
import com.android.systemui.shared.animation.UnfoldConstantTranslateAnimator
import com.android.systemui.shared.animation.UnfoldConstantTranslateAnimator.Direction.END
import com.android.systemui.shared.animation.UnfoldConstantTranslateAnimator.Direction.START
import com.android.systemui.shared.animation.UnfoldConstantTranslateAnimator.ViewIdToTranslate
import com.android.systemui.statusbar.StatusBarState.SHADE
import com.android.systemui.statusbar.StatusBarState.SHADE_LOCKED
import com.android.systemui.unfold.SysUIUnfoldScope
import com.android.systemui.unfold.UnfoldTransitionProgressProvider
import com.android.systemui.unfold.util.NaturalRotationUnfoldProgressProvider
import com.android.systemui.unfold.util.ScopedUnfoldTransitionProgressProvider
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

@SysUIUnfoldScope
class NotificationPanelUnfoldAnimationController
@Inject
constructor(
    @ShadeDisplayAware private val context: Context,
    statusBarStateController: StatusBarStateController,
    progressProviderFromConstructor: NaturalRotationUnfoldProgressProvider,
    private val shadeDisplaysInteractor: Lazy<ShadeDisplaysInteractor>,
    @Background private val backgroundScope: CoroutineScope,
) {

    private val scopedProgressProvider: ScopedUnfoldTransitionProgressProvider by lazy {
        ScopedUnfoldTransitionProgressProvider(progressProviderFromConstructor).apply {
            setReadyToHandleTransition(true)
        }
    }

    private val progressProvider: UnfoldTransitionProgressProvider =
        if (ShadeWindowGoesAround.isEnabled) {
            scopedProgressProvider
        } else {
            progressProviderFromConstructor
        }

    private val filterShade: () -> Boolean = {
        statusBarStateController.getState() == SHADE ||
            statusBarStateController.getState() == SHADE_LOCKED
    }

    private val translateAnimator by lazy {
        UnfoldConstantTranslateAnimator(
            viewsIdToTranslate =
                setOf(
                    ViewIdToTranslate(R.id.quick_settings_panel, START, filterShade),
                    ViewIdToTranslate(R.id.qs_footer_actions, START, filterShade),
                    ViewIdToTranslate(R.id.notification_stack_scroller, END, filterShade),
                ),
            progressProvider = progressProvider,
        )
    }

    private val translateAnimatorStatusBar by lazy {
        UnfoldConstantTranslateAnimator(
            viewsIdToTranslate =
                setOf(
                    ViewIdToTranslate(R.id.shade_header_system_icons, END, filterShade),
                    ViewIdToTranslate(R.id.privacy_container, END, filterShade),
                    ViewIdToTranslate(R.id.carrier_group, END, filterShade),
                    ViewIdToTranslate(R.id.clock, START, filterShade),
                    ViewIdToTranslate(R.id.date, START, filterShade),
                ),
            progressProvider = progressProvider,
        )
    }

    fun setup(root: ViewGroup) {
        val translationMax =
            context.resources.getDimensionPixelSize(R.dimen.notification_side_paddings).toFloat()
        translateAnimator.init(root, translationMax)
        val splitShadeStatusBarViewGroup: ViewGroup? =
            root.findViewById(R.id.split_shade_status_bar)
        if (splitShadeStatusBarViewGroup != null) {
            translateAnimatorStatusBar.init(splitShadeStatusBarViewGroup, translationMax)
        }
        if (ShadeWindowGoesAround.isEnabled) {
            listenForShadeDisplayChanges()
        }
    }

    private fun listenForShadeDisplayChanges() {
        ShadeWindowGoesAround.isUnexpectedlyInLegacyMode()
        backgroundScope.launchTraced("NotificationPanelUnfoldAnimationController") {
            scopedProgressProvider.setReadyToHandleTransition(
                shadeDisplaysInteractor.get().displayId.value == Display.DEFAULT_DISPLAY
            )
            shadeDisplaysInteractor.get().displayId.collect {
                scopedProgressProvider.setReadyToHandleTransition(it == Display.DEFAULT_DISPLAY)
            }
        }
    }
}
