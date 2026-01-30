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

package com.android.systemui.scene.ui.viewmodel

import android.app.ActivityManager
import android.content.Context
import androidx.compose.ui.unit.IntRect
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.res.R
import com.android.systemui.scene.domain.interactor.DualShadeEducationInteractor
import com.android.systemui.scene.domain.model.DualShadeEducationModel
import com.android.systemui.scene.shared.model.DualShadeEducationElement
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope

class DualShadeEducationalTooltipsViewModel
@AssistedInject
constructor(
    private val interactor: DualShadeEducationInteractor,
    @Assisted private val context: Context,
    @Assisted private val ignoreTestHarness: Boolean,
) : ExclusiveActivatable() {

    /**
     * Avoid showing the dual shade educational tooltips in test harness mode and not explicitly
     * allowed, as the tooltip may interfere with test automation.
     */
    private val disableEducationTooltips =
        !ignoreTestHarness && ActivityManager.isRunningInUserTestHarness()

    /**
     * The tooltip to show, or `null` if none should be shown.
     *
     * Please call [DualShadeEducationalTooltipViewModel.onShown] and
     * [DualShadeEducationalTooltipViewModel.onDismissed] when the tooltip is shown or hidden,
     * respectively.
     */
    val visibleTooltip: DualShadeEducationalTooltipViewModel?
        get() =
            if (disableEducationTooltips) {
                null
            } else {
                when (interactor.education) {
                    DualShadeEducationModel.ForNotificationsShade -> notificationsTooltip()
                    DualShadeEducationModel.ForQuickSettingsShade -> quickSettingsTooltip()
                    else -> null
                }
            }

    override suspend fun onActivated(): Nothing = coroutineScope { awaitCancellation() }

    private fun notificationsTooltip(): DualShadeEducationalTooltipViewModel? {
        val bounds =
            interactor.elementBounds[DualShadeEducationElement.Notifications] ?: return null

        return object : DualShadeEducationalTooltipViewModel {
            override val text: String =
                context.getString(R.string.dual_shade_educational_tooltip_notifs)

            override val anchorBounds: IntRect = bounds

            override val onShown: () -> Unit = interactor::recordNotificationsShadeTooltipImpression

            override val onDismissed: () -> Unit = interactor::dismissNotificationsShadeTooltip
        }
    }

    private fun quickSettingsTooltip(): DualShadeEducationalTooltipViewModel? {
        val bounds =
            interactor.elementBounds[DualShadeEducationElement.QuickSettings] ?: return null

        return object : DualShadeEducationalTooltipViewModel {
            override val text: String =
                context.getString(R.string.dual_shade_educational_tooltip_qs)

            override val anchorBounds: IntRect = bounds

            override val onShown: () -> Unit = interactor::recordQuickSettingsShadeTooltipImpression

            override val onDismissed: () -> Unit = interactor::dismissQuickSettingsShadeTooltip
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(
            context: Context,
            ignoreTestHarness: Boolean = false,
        ): DualShadeEducationalTooltipsViewModel
    }
}
