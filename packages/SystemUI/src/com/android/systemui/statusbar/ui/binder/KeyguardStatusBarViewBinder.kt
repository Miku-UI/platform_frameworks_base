/*
 * Copyright (C) 2023 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.statusbar.ui.binder

import android.view.View
import androidx.core.animation.Animator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.app.animation.Interpolators
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.lifecycle.setSnapshotBinding
import com.android.systemui.res.R
import com.android.systemui.statusbar.events.shared.model.SystemEventAnimationState
import com.android.systemui.statusbar.events.shared.model.SystemEventAnimationState.AnimatingIn
import com.android.systemui.statusbar.events.shared.model.SystemEventAnimationState.AnimatingOut
import com.android.systemui.statusbar.events.shared.model.SystemEventAnimationState.RunningChipAnim
import com.android.systemui.statusbar.phone.KeyguardStatusBarView
import com.android.systemui.statusbar.phone.fragment.StatusBarSystemEventDefaultAnimator
import com.android.systemui.statusbar.ui.viewmodel.KeyguardStatusBarViewModel
import kotlinx.coroutines.flow.distinctUntilChanged

/** Binds [KeyguardStatusBarViewModel] to [KeyguardStatusBarView]. */
object KeyguardStatusBarViewBinder {
    @JvmStatic
    fun bind(view: KeyguardStatusBarView, viewModel: KeyguardStatusBarViewModel) {

        val systemInfoView = view.requireViewById<View>(R.id.system_icons_container)
        val resources = view.resources
        val translationXIn =
            resources.getDimensionPixelSize(
                R.dimen.ongoing_appops_chip_animation_in_status_bar_translation_x
            )
        val translationXOut =
            resources.getDimensionPixelSize(
                R.dimen.ongoing_appops_chip_animation_out_status_bar_translation_x
            )

        var currentAnimator: Animator? = null
        var lastAnimState: SystemEventAnimationState? = null

        view.repeatWhenAttached {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isVisible.collect { isVisible ->
                        if (isVisible) {
                            view.show(shouldAnimateChange = true)
                        } else {
                            view.hide(shouldAnimateChange = true)
                        }
                    }
                }

                launch {
                    viewModel.isKeyguardUserSwitcherEnabled.distinctUntilChanged().collect {
                        view.setKeyguardUserSwitcherEnabled(it)
                    }
                }

                view.setSnapshotBinding {
                    val (baseVis, animState) = viewModel.systemInfoCombinedVis

                    // Always update base visibility first
                    systemInfoView.visibility = baseVis.visibility

                    if (animState != lastAnimState) {
                        currentAnimator?.cancel()
                    }

                    if (animState.isAnimatingChip()) {
                        when (animState) {
                            AnimatingIn -> {
                                currentAnimator =
                                    StatusBarSystemEventDefaultAnimator
                                        .getDefaultStatusBarAnimationForChipEnter(
                                            targetTranslation = translationXIn,
                                            setX = { systemInfoView.translationX = it },
                                            setAlpha = { systemInfoView.alpha = it },
                                        )
                                        .apply { start() }
                            }

                            AnimatingOut -> {
                                currentAnimator =
                                    StatusBarSystemEventDefaultAnimator
                                        .getDefaultStatusBarAnimationForChipExit(
                                            targetTranslation = translationXOut,
                                            setX = { systemInfoView.translationX = it },
                                            setAlpha = { systemInfoView.alpha = it },
                                        )
                                        .apply { start() }
                            }

                            RunningChipAnim -> {
                                // When the chip animation is running, the system info must remain
                                // hidden.
                                systemInfoView.alpha = 0f
                                currentAnimator = null
                            }

                            else -> {}
                        }
                    } else {
                        // Idle State (Not animating):
                        // We must forcibly reset properties here because we might have just
                        // transitioned from RunningChipAnim (where alpha was 0).
                        systemInfoView.alpha = 1f
                        systemInfoView.translationX = 0f
                    }
                    lastAnimState = animState
                }
            }
        }
    }

    private fun View.show(shouldAnimateChange: Boolean) {
        animate().cancel()
        if (visibility == View.VISIBLE && alpha >= 1f) {
            return
        }
        visibility = View.VISIBLE
        if (!shouldAnimateChange) {
            alpha = 1f
            return
        }
        animate()
            .alpha(1f)
            .setDuration(FADE_IN_DURATION.toLong())
            .setInterpolator(Interpolators.ALPHA_IN)
            .setStartDelay(FADE_IN_DELAY.toLong())
            .withEndAction(null)
    }

    private fun View.hide(nextVisibility: Int = View.INVISIBLE, shouldAnimateChange: Boolean) {
        animate().cancel()

        if (
            (visibility == View.INVISIBLE && nextVisibility == View.INVISIBLE) ||
                (visibility == View.GONE && nextVisibility == View.GONE)
        ) {
            return
        }
        val isAlreadyHidden = visibility == View.INVISIBLE || visibility == View.GONE
        if (!shouldAnimateChange || isAlreadyHidden) {
            alpha = 0f
            visibility = nextVisibility
            return
        }

        animate()
            .alpha(0f)
            .setDuration(FADE_OUT_DURATION.toLong())
            .setStartDelay(0)
            .setInterpolator(Interpolators.ALPHA_OUT)
            .withEndAction { visibility = nextVisibility }
    }

    private fun SystemEventAnimationState.isAnimatingChip() =
        when (this) {
            AnimatingIn,
            AnimatingOut,
            RunningChipAnim -> true
            else -> false
        }

    const val FADE_IN_DURATION = 320
    const val FADE_OUT_DURATION = 160
    const val FADE_IN_DELAY = 50
}
