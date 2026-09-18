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

package com.android.systemui.statusbar.notification

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.testing.TestableLooper.RunWithLooper
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.res.R
import com.android.systemui.statusbar.notification.collection.provider.mockNotificationDismissibilityProvider
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import com.android.systemui.statusbar.notification.row.createInitializedRow
import com.android.systemui.statusbar.notification.shared.NotificationXButtonClipFix
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

private fun getCloseButton(row: ExpandableNotificationRow): View {
    return if (NotificationXButtonClipFix.isEnabled) {
        row.findViewById(R.id.dismiss_button)
    } else {
        val contractedView = row.showingLayout?.contractedChild!!
        contractedView.findViewById(com.android.internal.R.id.close_button)
    }
}

private fun getMotionEventFor(action: Int) =
    MotionEvent.obtain(
        /*downTime=*/ 0,
        /*eventTime=*/ 0,
        action,
        /*x=*/ 0f,
        /*y=*/ 0f,
        /*metaState=*/ 0,
    )!!

@SmallTest
@RunWithLooper
@RunWith(AndroidJUnit4::class)
class NotificationCloseButtonTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    @Before
    fun setUp() {
        allowTestableLooperAsMainThread()
    }

    @Test
    @DisableFlags(Flags.FLAG_NOTIFICATION_ADD_X_ON_HOVER_TO_DISMISS)
    fun verifyWhenFeatureDisabled() {
        // By default, the close button should be gone.
        val row = kosmos.createInitializedRow()
        val closeButton = getCloseButton(row)
        assertThat(closeButton).isNotNull()
        assertThat(closeButton.visibility).isEqualTo(View.GONE)

        val hoverEnterEvent = getMotionEventFor(MotionEvent.ACTION_HOVER_ENTER)

        // The close button should not show if the feature is disabled.
        row.onInterceptHoverEvent(hoverEnterEvent)
        assertThat(closeButton.visibility).isEqualTo(View.GONE)
    }
}

@SmallTest
@RunWithLooper
@RunWith(ParameterizedAndroidJunit4::class)
class NotificationCloseButtonParameterizedTest(flags: FlagsParameterization) : SysuiTestCase() {
    private val kosmos = testKosmos()

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Before
    fun setUp() {
        allowTestableLooperAsMainThread()
    }

    @Test
    @EnableFlags(Flags.FLAG_NOTIFICATION_ADD_X_ON_HOVER_TO_DISMISS)
    fun verifyOnDismissableNotification() {
        whenever(kosmos.mockNotificationDismissibilityProvider.isDismissable(any()))
            .thenReturn(true)
        // By default, the close button should be gone.
        val row = kosmos.createInitializedRow()
        val closeButton = getCloseButton(row)
        assertThat(closeButton).isNotNull()
        assertThat(closeButton.visibility).isEqualTo(View.GONE)

        val hoverEnterEvent = getMotionEventFor(MotionEvent.ACTION_HOVER_ENTER)

        // When the row is hovered, the close button should show.
        row.onInterceptHoverEvent(hoverEnterEvent)
        assertThat(closeButton.visibility).isEqualTo(View.VISIBLE)

        val hoverExitEvent = getMotionEventFor(MotionEvent.ACTION_HOVER_EXIT)

        // When hover exits the row, the close button should be gone again.
        row.onInterceptHoverEvent(hoverExitEvent)
        assertThat(closeButton.visibility).isEqualTo(View.GONE)
    }

    @Test
    @EnableFlags(Flags.FLAG_NOTIFICATION_ADD_X_ON_HOVER_TO_DISMISS)
    fun verifyOnUndismissableNotification() {
        // By default, the close button should be gone.
        whenever(kosmos.mockNotificationDismissibilityProvider.isDismissable(any()))
            .thenReturn(false)
        val row = kosmos.createInitializedRow()
        val closeButton = getCloseButton(row)
        assertThat(closeButton).isNotNull()
        assertThat(closeButton.visibility).isEqualTo(View.GONE)

        val hoverEnterEvent = getMotionEventFor(MotionEvent.ACTION_HOVER_ENTER)

        // Because the host notification cannot be dismissed, the close button should not show.
        row.onInterceptHoverEvent(hoverEnterEvent)
        assertThat(closeButton.visibility).isEqualTo(View.GONE)
    }

    private companion object {
        @get:Parameters(name = "{0}")
        @JvmStatic
        val params = FlagsParameterization.allCombinationsOf(NotificationXButtonClipFix.FLAG_NAME)
    }
}
