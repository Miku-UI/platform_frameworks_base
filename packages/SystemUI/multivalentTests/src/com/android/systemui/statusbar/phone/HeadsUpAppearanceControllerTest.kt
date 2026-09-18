/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.systemui.statusbar.phone

import android.platform.test.annotations.DisableFlags
import android.testing.TestableLooper.RunWithLooper
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.plugins.fakeDarkIconDispatcher
import com.android.systemui.plugins.statusbar.statusBarStateController
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeHeadsUpTracker
import com.android.systemui.shade.shadeViewController
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.buildNotificationEntry
import com.android.systemui.statusbar.notification.headsup.PinnedStatus
import com.android.systemui.statusbar.notification.headsup.mockHeadsUpManager
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import com.android.systemui.statusbar.notification.row.createRowWithEntry
import com.android.systemui.statusbar.notification.stack.NotificationRoundnessManager
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayoutController
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
class HeadsUpAppearanceControllerTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val stackScrollerController = mock<NotificationStackScrollLayoutController>()
    private val shadeViewController = kosmos.shadeViewController
    private val shadeHeadsUpTracker = mock<ShadeHeadsUpTracker>()
    private val darkIconDispatcher = kosmos.fakeDarkIconDispatcher
    private val phoneStatusBarTransitions = kosmos.mockPhoneStatusBarTransitions
    private val notificationRoundnessManager = mock<NotificationRoundnessManager>()
    private var headsUpManager = kosmos.mockHeadsUpManager

    private lateinit var row: ExpandableNotificationRow
    private lateinit var entry: NotificationEntry
    private lateinit var phoneStatusBarView: PhoneStatusBarView

    private lateinit var underTest: HeadsUpAppearanceController

    @Before
    @Throws(Exception::class)
    fun setUp() {
        allowTestableLooperAsMainThread()
        entry = kosmos.buildNotificationEntry()
        row = kosmos.createRowWithEntry(entry)
        phoneStatusBarView =
            LayoutInflater.from(context)
                .inflate(
                    R.layout.status_bar,
                    /* root= */ FrameLayout(context),
                    /* attachToRoot = */ false,
                ) as PhoneStatusBarView

        whenever(shadeViewController.shadeHeadsUpTracker).thenReturn(shadeHeadsUpTracker)
        underTest =
            HeadsUpAppearanceController(
                headsUpManager,
                phoneStatusBarTransitions,
                stackScrollerController,
                shadeViewController,
                notificationRoundnessManager,
                phoneStatusBarView,
            )
        underTest.setAppearFraction(0.0f, 0.0f)
    }

    @Test
    fun constructor_animationValuesUpdated() {
        val appearFraction = .75f
        val expandedHeight = 400f
        whenever(stackScrollerController.appearFraction).thenReturn(appearFraction)
        whenever(stackScrollerController.expandedHeight).thenReturn(expandedHeight)

        val newController =
            HeadsUpAppearanceController(
                headsUpManager,
                phoneStatusBarTransitions,
                stackScrollerController,
                shadeViewController,
                notificationRoundnessManager,
                phoneStatusBarView,
            )

        assertThat(newController.mExpandedHeight).isEqualTo(expandedHeight)
        assertThat(newController.mAppearFraction).isEqualTo(appearFraction)
    }

    @Test
    fun testDestroy() {
        reset(headsUpManager)
        reset(shadeHeadsUpTracker)
        reset(stackScrollerController)

        underTest.onViewDetached()

        verify(headsUpManager).removeListener(any())
        assertThat(darkIconDispatcher.receivers).isEmpty()
        verify(shadeHeadsUpTracker).removeTrackingHeadsUpListener(any())
        verify(shadeHeadsUpTracker).setHeadsUpAppearanceController(null)
        verify(stackScrollerController).removeOnExpandedHeightChangedListener(any())
    }

    @Test
    fun testPulsingRoundness_onUpdateHeadsUpAndPulsingRoundness() {
        // Pulsing: Enable flag and dozing
        whenever(notificationRoundnessManager.shouldRoundNotificationPulsing()).thenReturn(true)
        kosmos.statusBarStateController.setIsDozing(true)

        // Pulsing: Enabled
        row.isHeadsUp = true
        underTest.updateHeadsUpAndPulsingRoundness(row)

        val debugString: String = row.roundableState.debugString()
        // If Pulsing is enabled, roundness should be set to 1
        assertThat(row.topRoundness.toDouble()).isWithin(0.001).of(1.0)
        assertThat(debugString).contains("Pulsing")

        // Pulsing: Disabled
        row.isHeadsUp = false
        underTest.updateHeadsUpAndPulsingRoundness(row)

        // If Pulsing is disabled, roundness should be set to 0
        assertThat(row.topRoundness.toDouble()).isWithin(0.001).of(0.0)
    }

    @Test
    fun testPulsingRoundness_onHeadsUpStateChanged() {
        // Pulsing: Enable flag and dozing
        whenever(notificationRoundnessManager.shouldRoundNotificationPulsing()).thenReturn(true)
        kosmos.statusBarStateController.setIsDozing(true)

        // Pulsing: Enabled
        entry.setHeadsUp(true)
        underTest.onHeadsUpStateChanged(entry, true)

        val debugString: String = row.roundableState.debugString()
        // If Pulsing is enabled, roundness should be set to 1
        assertThat(row.topRoundness.toDouble()).isWithin(0.001).of(1.0)
        assertThat(debugString).contains("Pulsing")

        // Pulsing: Disabled
        entry.setHeadsUp(false)
        underTest.onHeadsUpStateChanged(entry, false)

        // If Pulsing is disabled, roundness should be set to 0
        assertThat(row.topRoundness.toDouble()).isWithin(0.001).of(0.0)
    }

    @Test
    fun onHeadsUpStateChanged_true_transitionsNotified() {
        underTest.onHeadsUpStateChanged(entry, true)

        verify(phoneStatusBarTransitions).onHeadsUpStateChanged(true)
    }

    @Test
    fun onHeadsUpStateChanged_false_transitionsNotified() {
        underTest.onHeadsUpStateChanged(entry, false)

        verify(phoneStatusBarTransitions).onHeadsUpStateChanged(false)
    }

    private fun setHeadsUpNotifOnManager(entry: NotificationEntry?) {
        if (entry != null) {
            whenever(headsUpManager.hasPinnedHeadsUp()).thenReturn(true)
            whenever(headsUpManager.pinnedHeadsUpStatus()).thenReturn(entry.pinnedStatus)
            whenever(headsUpManager.getTopEntry()).thenReturn(entry)
        } else {
            whenever(headsUpManager.hasPinnedHeadsUp()).thenReturn(false)
            whenever(headsUpManager.pinnedHeadsUpStatus()).thenReturn(PinnedStatus.NotPinned)
            whenever(headsUpManager.getTopEntry()).thenReturn(null)
        }
    }
}
