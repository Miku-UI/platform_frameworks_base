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

package com.android.systemui.statusbar.notification.row.dagger

import android.app.Flags
import android.app.Notification
import com.android.systemui.statusbar.notification.row.AutomationNotificationUiEligibilityChecker
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import java.util.Set as JavaSet

/** Module for checking eligibility for notification row features. */
@Module(includes = [EligibilityStaticModule::class])
interface EligibilityModule {

    // [Mutlibinds] has a difficult time with kotlin sets, so we'll use a java one.
    @Multibinds
    fun mutlbindsAutomationNotificationUiEligibilityChecker():
        JavaSet<AutomationNotificationUiEligibilityChecker>
}

@Module
object EligibilityStaticModule {
    @Provides
    @IntoSet
    @JvmStatic
    fun providesAFlagEligibility(): AutomationNotificationUiEligibilityChecker {
        return object : AutomationNotificationUiEligibilityChecker {
            override fun isEligible(notification: Notification): Boolean {
                return Flags.enableAutomationNotificationUi()
            }
        }
    }

    @Provides
    @IntoSet
    @JvmStatic
    fun providesNotificationFlagEligibility(): AutomationNotificationUiEligibilityChecker {
        return object : AutomationNotificationUiEligibilityChecker {
            override fun isEligible(notification: Notification): Boolean {
                return (notification.flags and Notification.FLAG_COMPUTER_CONTROL) ==
                    Notification.FLAG_COMPUTER_CONTROL
            }
        }
    }
}
// TODO(b/484385191): Add device feature requirement.
