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

package com.android.systemui.statusbar.notification.row

import android.app.Notification
import android.service.notification.StatusBarNotification
import dagger.Lazy
import java.util.Set as JavaSet
import javax.inject.Inject

/** Used to check whether a notification is eligible for some ui feature. */
class NotificationUiEligibilityChecker
@Inject
constructor(
    private val automationEligibilityCheckers:
        Lazy<JavaSet<AutomationNotificationUiEligibilityChecker>>
) {

    fun eligibleForAutomationUi(sbn: StatusBarNotification?) =
        eligibleForAutomationUi(sbn?.notification)

    private fun eligibleForAutomationUi(notification: Notification?): Boolean {
        if (notification == null) {
            return false
        }
        for (checker in automationEligibilityCheckers.get()) {
            if (!checker.isEligible(notification)) return false
        }
        return true
    }
}
