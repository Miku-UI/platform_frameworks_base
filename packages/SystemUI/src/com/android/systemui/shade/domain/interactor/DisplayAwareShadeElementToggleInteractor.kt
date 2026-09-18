/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.shade.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.shade.display.domain.interactor.ShadeExpansionTargetDisplayInteractor
import com.android.systemui.shade.domain.interactor.ShadeExpandedStateInteractor.ShadeElement
import javax.inject.Inject

/**
 * Handles toggling of shade elements (Quick Settings / Notifications) based on display visibility.
 */
@SysUISingleton
class DisplayAwareShadeElementToggleInteractor
@Inject
constructor(
    private val shadeInteractor: ShadeInteractor,
    private val shadeExpansionTargetDisplayInteractor: ShadeExpansionTargetDisplayInteractor,
    private val shadeDisplaysInteractor: ShadeDisplaysInteractor,
) {

    /**
     * Toggles the given [shadeElement] based on display visibility.
     *
     * If the target [displayId] matches the current display context where the shade is showing, it
     * toggles the element locally. If it differs, it sets an expansion intent for the target
     * display so it can be handled when the user switches to that display context.
     */
    fun toggleShadeElement(shadeElement: ShadeElement, displayId: Int) {
        if (SceneContainerFlag.isUnexpectedlyInLegacyMode()) {
            return
        }

        val isShadeOnThisDisplay = shadeDisplaysInteractor.displayId.value == displayId

        if (isShadeOnThisDisplay) {
            when (shadeElement) {
                is QSShadeElement -> toggleQuickSettingsShade()
                is NotificationShadeElement -> toggleNotificationsShade()
            }
        } else {
            when (shadeElement) {
                is QSShadeElement -> setExpansionIntentForQsElement(displayId)
                is NotificationShadeElement -> setExpansionIntentForNotificationElement(displayId)
            }
        }
    }

    private fun toggleQuickSettingsShade() {
        shadeInteractor.toggleQuickSettingsShade(
            loggingReason = "DisplayAwareShadeElementToggleInteractor.toggleQuickSettingsShade"
        )
    }

    private fun toggleNotificationsShade() {
        shadeInteractor.toggleNotificationsShade(
            loggingReason = "DisplayAwareShadeElementToggleInteractor.toggleNotificationsShade"
        )
    }

    private fun setExpansionIntentForQsElement(displayId: Int) {
        shadeExpansionTargetDisplayInteractor.setExpansionIntentForQsElement(displayId)
    }

    private fun setExpansionIntentForNotificationElement(displayId: Int) {
        shadeExpansionTargetDisplayInteractor.setExpansionIntentForNotificationElement(displayId)
    }
}
