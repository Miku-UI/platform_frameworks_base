/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.shade.display.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.display.data.repository.FocusedDisplayRepository
import com.android.systemui.shade.display.StatusBarTouchShadeDisplayPolicy
import com.android.systemui.shade.domain.interactor.NotificationShadeElement
import com.android.systemui.shade.domain.interactor.QSShadeElement
import com.android.systemui.shade.domain.interactor.ShadeExpandedStateInteractor.ShadeElement
import dagger.Lazy
import javax.inject.Inject

/** Handles business logic for shade expansion intents based on user interaction. */
@SysUISingleton
class ShadeExpansionTargetDisplayInteractor
@Inject
constructor(
    private val policy: StatusBarTouchShadeDisplayPolicy,
    private val focusedDisplayRepository: FocusedDisplayRepository,
    private val qsShadeElement: Lazy<QSShadeElement>,
    private val notificationElement: Lazy<NotificationShadeElement>,
) {

    /** Called when the status bar on the given display is touched/clicked. */
    fun setExpansionIntentFromStatusBarEvent(
        eventX: Float,
        displayId: Int,
        statusBarWidth: Int,
        shadeInvocationSplitRatio: Float,
        isRtl: Boolean,
    ) {
        val element =
            classifyStatusBarEvent(eventX, statusBarWidth, shadeInvocationSplitRatio, isRtl)
        policy.setExpansionIntentForElement(element, displayId)
    }

    /**
     * Called when we need to move the notification shade to a specific display. For e.g. when
     * launcher homescreen on the given display is touched/clicked.
     */
    fun setExpansionIntentForNotificationElement(displayId: Int) {
        policy.setExpansionIntentForElement(notificationElement.get(), displayId)
    }

    /**
     * Called when we need to move the quicksettings shade to a specific display. For e.g. when the
     * quick settings chip on the given display is touched/clicked.
     */
    fun setExpansionIntentForQsElement(displayId: Int) {
        policy.setExpansionIntentForElement(qsShadeElement.get(), displayId)
    }

    /** Updates the target display for the shade, if necessary. */
    fun updateShadeDisplayIfNeeded(displayId: Int) {
        policy.updateShadeDisplayIfNeeded(displayId)
    }

    /** Called when notification panel keyboard shortcut is pressed. */
    fun onNotificationPanelKeyboardShortcut() {
        policy.setExpansionIntentForElement(
            element = notificationElement.get(),
            displayId = focusedDisplayRepository.focusedDisplayId.value,
        )
    }

    /** Called when quick settings panel keyboard shortcut is pressed. */
    fun onQSPanelKeyboardShortcut() {
        policy.setExpansionIntentForElement(
            element = qsShadeElement.get(),
            displayId = focusedDisplayRepository.focusedDisplayId.value,
        )
    }

    // TODO(450582765): Completely remove all UI state information from this class once we remove
    // the callback for this function in [PhoneStatusBarViewController]. We can then move the event
    // classification logic into [HomeStatusBarViewModel].
    private fun classifyStatusBarEvent(
        eventX: Float,
        statusBarWidth: Int,
        shadeInvocationSplitRatio: Float,
        isRtl: Boolean,
    ): ShadeElement {
        // Normalize the percentage to be from the "start" edge of the status bar.
        // For LTR, this is the left edge (xPercentage).
        // For RTL, this is the right edge (1 - xPercentage).
        val xPercentage = eventX / statusBarWidth
        val percentageFromStart = if (isRtl) 1 - xPercentage else xPercentage

        return if (percentageFromStart < shadeInvocationSplitRatio) {
            notificationElement.get()
        } else {
            qsShadeElement.get()
        }
    }
}
