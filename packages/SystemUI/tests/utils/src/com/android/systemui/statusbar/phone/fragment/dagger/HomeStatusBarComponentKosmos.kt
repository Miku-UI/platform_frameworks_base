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

package com.android.systemui.statusbar.phone.fragment.dagger

import android.content.testableContext
import com.android.systemui.battery.BatteryMeterViewController
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.plugins.DarkIconDispatcher
import com.android.systemui.plugins.fakeDarkIconDispatcher
import com.android.systemui.statusbar.layout.StatusBarBoundsProvider
import com.android.systemui.statusbar.phone.HeadsUpAppearanceController
import com.android.systemui.statusbar.phone.PhoneStatusBarTransitions
import com.android.systemui.statusbar.phone.PhoneStatusBarView
import com.android.systemui.statusbar.phone.PhoneStatusBarViewController
import com.android.systemui.statusbar.phone.StatusBarDemoMode
import com.android.systemui.statusbar.phone.phoneStatusBarTransitions
import com.android.systemui.statusbar.phone.phoneStatusBarViewController
import org.mockito.kotlin.mock

fun Kosmos.createFakeHomeStatusBarComponent(
    batteryMeterViewController: BatteryMeterViewController = mock(),
    phoneStatusBarView: PhoneStatusBarView = mock(),
    phoneStatusBarViewController: PhoneStatusBarViewController = this.phoneStatusBarViewController,
    headsUpAppearanceController: HeadsUpAppearanceController = mock(),
    statusBarDemoMode: StatusBarDemoMode = mock(),
    phoneStatusBarTransitions: PhoneStatusBarTransitions = this.phoneStatusBarTransitions,
    startables: MutableSet<HomeStatusBarComponent.Startable> = mutableSetOf(),
    boundsProvider: StatusBarBoundsProvider = mock(),
    darkIconDispatcher: DarkIconDispatcher = this.fakeDarkIconDispatcher,
    displayId: Int = testableContext.displayId,
): HomeStatusBarComponent {
    return object : HomeStatusBarComponent {
        override fun getBatteryMeterViewController(): BatteryMeterViewController {
            return batteryMeterViewController
        }

        override fun getPhoneStatusBarView(): PhoneStatusBarView {
            return phoneStatusBarView
        }

        override fun getPhoneStatusBarViewController(): PhoneStatusBarViewController {
            return phoneStatusBarViewController
        }

        override fun getHeadsUpAppearanceController(): HeadsUpAppearanceController {
            return headsUpAppearanceController
        }

        override fun getStatusBarDemoMode(): StatusBarDemoMode {
            return statusBarDemoMode
        }

        override fun getPhoneStatusBarTransitions(): PhoneStatusBarTransitions {
            return phoneStatusBarTransitions
        }

        override fun getStartables(): MutableSet<HomeStatusBarComponent.Startable> {
            return startables
        }

        override fun getBoundsProvider(): StatusBarBoundsProvider {
            return boundsProvider
        }

        override fun getDarkIconDispatcher(): DarkIconDispatcher {
            return darkIconDispatcher
        }

        override fun getDisplayId(): Int {
            return displayId
        }
    }
}

val Kosmos.defaultHomeStatusBarComponent by Kosmos.Fixture { createFakeHomeStatusBarComponent() }
