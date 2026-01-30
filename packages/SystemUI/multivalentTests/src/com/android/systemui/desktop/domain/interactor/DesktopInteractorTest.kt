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

package com.android.systemui.desktop.domain.interactor

import android.content.res.Configuration
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.res.R
import com.android.systemui.statusbar.policy.configurationController
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class DesktopInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest: DesktopInteractor by Kosmos.Fixture { desktopInteractor }

    @Test
    fun isDesktopForFalsingPurposes_false() =
        kosmos.runTest {
            val isDesktopForFalsingPurposes by
                collectLastValue(underTest.isDesktopForFalsingPurposes)

            overrideResource(R.bool.config_isDesktopForFalsingPurposes, false)
            configurationController.onConfigurationChanged(Configuration())

            assertThat(isDesktopForFalsingPurposes).isFalse()
        }

    @Test
    fun isDesktopForFalsingPurposes_true() =
        kosmos.runTest {
            val isDesktopForFalsingPurposes by
                collectLastValue(underTest.isDesktopForFalsingPurposes)

            overrideResource(R.bool.config_isDesktopForFalsingPurposes, true)
            configurationController.onConfigurationChanged(Configuration())

            assertThat(isDesktopForFalsingPurposes).isTrue()
        }

    @Test
    fun useDesktopStatusBar_false() =
        kosmos.runTest {
            val useDesktopStatusBar by collectLastValue(underTest.useDesktopStatusBar)

            overrideResource(R.bool.config_useDesktopStatusBar, false)
            configurationController.onConfigurationChanged(Configuration())

            assertThat(useDesktopStatusBar).isFalse()
        }

    @Test
    fun useDesktopStatusBar_true() =
        kosmos.runTest {
            val useDesktopStatusBar by collectLastValue(underTest.useDesktopStatusBar)

            overrideResource(R.bool.config_useDesktopStatusBar, true)
            configurationController.onConfigurationChanged(Configuration())

            assertThat(useDesktopStatusBar).isTrue()
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_FOR_DESKTOP)
    @EnableSceneContainer
    fun desktopStatusBarEnabled_configEnabled_isNotificationShadeOnTopEndReturnsTrue() =
        kosmos.runTest {
            overrideResource(R.bool.config_notificationShadeOnTopEnd, true)

            assertThat(underTest.isNotificationShadeOnTopEnd).isTrue()
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_FOR_DESKTOP)
    @EnableSceneContainer
    fun desktopStatusBarEnabled_configDisabled_isNotificationShadeOnTopEndReturnsFalse() =
        kosmos.runTest {
            overrideResource(R.bool.config_notificationShadeOnTopEnd, false)

            assertThat(underTest.isNotificationShadeOnTopEnd).isFalse()
        }

    @Test
    @DisableFlags(Flags.FLAG_STATUS_BAR_FOR_DESKTOP)
    fun desktopStatusBarDisabled_configEnabled_isNotificationShadeOnTopEndReturnsFalse() =
        kosmos.runTest {
            overrideResource(R.bool.config_notificationShadeOnTopEnd, true)

            assertThat(underTest.isNotificationShadeOnTopEnd).isFalse()
        }

    @Test
    @DisableFlags(Flags.FLAG_STATUS_BAR_FOR_DESKTOP)
    fun desktopStatusBarDisabled_configDisabled_isNotificationShadeOnTopEndReturnsFalse() =
        kosmos.runTest {
            overrideResource(R.bool.config_notificationShadeOnTopEnd, false)

            assertThat(underTest.isNotificationShadeOnTopEnd).isFalse()
        }
}
