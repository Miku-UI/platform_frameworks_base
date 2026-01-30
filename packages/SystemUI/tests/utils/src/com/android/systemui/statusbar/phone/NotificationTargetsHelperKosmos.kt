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

package com.android.systemui.statusbar.phone

import com.android.systemui.kosmos.Kosmos
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import com.android.systemui.statusbar.notification.stack.MagneticRoundableTarget
import com.android.systemui.statusbar.notification.stack.NotificationSectionsManager
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout
import com.android.systemui.statusbar.notification.stack.NotificationTargetsHelper
import com.android.systemui.statusbar.notification.stack.RoundableTargets

val Kosmos.notificationTargetsHelper: NotificationTargetsHelper by
    Kosmos.Fixture { FakeNotificationTargetsHelper() }

class FakeNotificationTargetsHelper : NotificationTargetsHelper {

    // Overridable targets
    var roundableTargets = RoundableTargets(null, null, null)
    var magneticRoundableTargets = listOf(MagneticRoundableTarget.Empty)

    override fun findRoundableTargets(
        viewSwiped: ExpandableNotificationRow,
        stackScrollLayout: NotificationStackScrollLayout,
        sectionsManager: NotificationSectionsManager,
    ): RoundableTargets = roundableTargets

    override fun findMagneticRoundableTargets(
        viewSwiped: ExpandableNotificationRow,
        stackScrollLayout: NotificationStackScrollLayout,
        sectionsManager: NotificationSectionsManager,
        numTargets: Int,
    ): List<MagneticRoundableTarget> = buildList {
        add(MagneticRoundableTarget.Empty)
        addAll(magneticRoundableTargets)
        add(MagneticRoundableTarget.Empty)
    }
}

val NotificationTargetsHelper.fake
    get() = this as FakeNotificationTargetsHelper
