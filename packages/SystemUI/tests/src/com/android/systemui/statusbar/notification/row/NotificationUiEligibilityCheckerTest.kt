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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import java.util.Set as JavaSet
import kotlin.collections.toHashSet
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
class NotificationUiEligibilityCheckerTest : SysuiTestCase() {
    private val mockStatusBarNotification =
        mock<StatusBarNotification> { on { notification } doReturn Notification() }

    @Test
    fun eligibleForAutomationUi_nullNotification_returnsFalse() {
        val checker = NotificationUiEligibilityChecker {
            emptySet<AutomationNotificationUiEligibilityChecker>().toJavaSet()
        }

        assertThat(checker.eligibleForAutomationUi(null as StatusBarNotification?)).isFalse()
    }

    @Test
    fun eligibleForAutomationUi_noCheckers_returnsTrue() {
        val checker = NotificationUiEligibilityChecker {
            emptySet<AutomationNotificationUiEligibilityChecker>().toJavaSet()
        }

        assertThat(checker.eligibleForAutomationUi(mockStatusBarNotification)).isTrue()
    }

    @Test
    fun eligibleForAutoobjectmationUi_allCheckersReturnTrue_returnsTrue() {
        val checker1 = createAutomationChecker(eligible = true)
        val checker2 = createAutomationChecker(eligible = true)
        val checker = NotificationUiEligibilityChecker { setOf(checker1, checker2).toJavaSet() }

        assertThat(checker.eligibleForAutomationUi(mockStatusBarNotification)).isTrue()
    }

    @Test
    fun eligibleForAutomationUi_oneCheckerReturnsFalse_returnsFalse() {
        val checker1 = createAutomationChecker(eligible = true)
        val checker2 = createAutomationChecker(eligible = false)
        val checker = NotificationUiEligibilityChecker { setOf(checker1, checker2).toJavaSet() }

        assertThat(checker.eligibleForAutomationUi(mockStatusBarNotification)).isFalse()
    }

    fun createAutomationChecker(eligible: Boolean) =
        object : AutomationNotificationUiEligibilityChecker {
            override fun isEligible(notification: Notification) = eligible
        }
}

fun <T> Set<T>.toJavaSet(): JavaSet<T> = this.toHashSet() as JavaSet<T>
