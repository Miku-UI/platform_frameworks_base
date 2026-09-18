/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.android.systemui.statusbar.notification.collection.coordinator

import android.app.NotificationManager
import android.multiuser.Flags.FLAG_HSU_ALLOWLIST_NOTIFICATIONS
import android.multiuser.Flags.FLAG_HSU_DISABLE_NOTIFICATIONS
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.statusbar.notification.collection.buildNotificationEntry
import com.android.systemui.statusbar.notification.collection.coordinator.HsuCoordinator.Companion.STATUS_ALLOWED_DISABLED_MODE
import com.android.systemui.statusbar.notification.collection.coordinator.HsuCoordinator.Companion.STATUS_DISALLOWED_FEATURE_DISABLED
import com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.NotifFilter
import com.android.systemui.statusbar.notification.collection.notifPipeline
import com.android.systemui.testKosmos
import com.android.systemui.user.domain.interactor.fakeSelectedUserInteractor
import com.android.systemui.util.mockito.withArgCaptor
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.whenever

// TODO(b/491189122): this class is testing the combination of multiple conditions in such a
// way that it could be refactored to use JUNit parameters. For now there are just 3 conditions
// (HSU/non-HSU, FLAG_HSU_ALLOWLIST_NOTIFICATIONS, and  FLAG_HSU_DISABLE_NOTIFICATIONS) so it's
// simpler to just have 2^3 tests, but once more conditions (like calling the allowlist infra to get
// the status), it will be worth to refactor it.
@SmallTest
@RunWith(AndroidJUnit4::class)
class HsuCoordinatorTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val pipeline = kosmos.notifPipeline
    private val selectedUserInteractor = kosmos.fakeSelectedUserInteractor
    private val entry = kosmos.buildNotificationEntry()

    @JvmField @Rule val mockito = MockitoJUnit.rule()

    @Mock private lateinit var notificationManager: NotificationManager
    private lateinit var filter: NotifFilter

    @Before
    fun setUp() {
        val coordinator = HsuCoordinator(selectedUserInteractor, notificationManager)
        coordinator.attach(pipeline)
        filter = withArgCaptor { verify(pipeline).addPreGroupFilter(capture()) }
    }

    // Non-HSU user tests - the behavior should be the same (regardless of the flags)
    private fun doNotFilterOutNotifsOrLogWhenUserIsNotHsu() {
        // GIVEN that the current user is NOT the headless system user
        mockIsNonHsu()

        // THEN notifications should NOT be filtered out
        assertNotFilteredOut()
        // THEN mgr should NOT be notified
        verifyLogHsuNotificationPostStatusNotCalled()
    }

    @DisableFlags(FLAG_HSU_ALLOWLIST_NOTIFICATIONS)
    @DisableFlags(FLAG_HSU_DISABLE_NOTIFICATIONS)
    @Test
    fun testNonHsu_allFlagsDisabled() {
        doNotFilterOutNotifsOrLogWhenUserIsNotHsu()
    }

    @EnableFlags(FLAG_HSU_ALLOWLIST_NOTIFICATIONS)
    @DisableFlags(FLAG_HSU_DISABLE_NOTIFICATIONS)
    @Test
    fun testNonHsu_loggingFlagEnabledBlockingFlagDisabled() {
        doNotFilterOutNotifsOrLogWhenUserIsNotHsu()
    }

    @DisableFlags(FLAG_HSU_ALLOWLIST_NOTIFICATIONS)
    @EnableFlags(FLAG_HSU_DISABLE_NOTIFICATIONS)
    @Test
    fun testNonHsu_loggingFlagDisabledBlockingFlagEnabled() {
        doNotFilterOutNotifsOrLogWhenUserIsNotHsu()
    }

    @EnableFlags(FLAG_HSU_ALLOWLIST_NOTIFICATIONS)
    @EnableFlags(FLAG_HSU_DISABLE_NOTIFICATIONS)
    @Test
    fun testNonHsu_allFlagsEnabled() {
        doNotFilterOutNotifsOrLogWhenUserIsNotHsu()
    }

    // HSU tests

    @DisableFlags(FLAG_HSU_ALLOWLIST_NOTIFICATIONS)
    @DisableFlags(FLAG_HSU_DISABLE_NOTIFICATIONS)
    @Test
    fun testHsu_allFlagsDisabled() {
        // GIVEN that the current user IS the headless system user
        mockIsHsu()

        // THEN notifications should NOT be filtered out
        assertNotFilteredOut()
        // THEN mgr should NOT be notified
        verifyLogHsuNotificationPostStatusNotCalled()
    }

    @EnableFlags(FLAG_HSU_ALLOWLIST_NOTIFICATIONS)
    @DisableFlags(FLAG_HSU_DISABLE_NOTIFICATIONS)
    @Test
    fun testHsu_loggingFlagEnabledBlockingFlagDisabled() {
        // GIVEN that the current user IS the headless system user
        mockIsHsu()

        // THEN notifications should NOT be filtered out
        assertNotFilteredOut()
        // THEN mgr SHOULD be notified
        verifyLogHsuNotificationPostStatusCalled(STATUS_ALLOWED_DISABLED_MODE)
    }

    @DisableFlags(FLAG_HSU_ALLOWLIST_NOTIFICATIONS)
    @EnableFlags(FLAG_HSU_DISABLE_NOTIFICATIONS)
    @Test
    fun testHsu_loggingFlagDisabledBlockingFlagEnabled() {
        // GIVEN that the current user IS the headless system user
        mockIsHsu()

        // THEN notifications SHOULD be filtered out
        assertFilteredOut()
        // THEN mgr should NOT be notified
        verifyLogHsuNotificationPostStatusNotCalled()
    }

    @EnableFlags(FLAG_HSU_ALLOWLIST_NOTIFICATIONS)
    @EnableFlags(FLAG_HSU_DISABLE_NOTIFICATIONS)
    @Test
    fun testHsu_allFlagsEnabled() {
        // GIVEN that the current user IS the headless system user
        mockIsHsu()

        // THEN notifications SHOULD be filtered out
        assertFilteredOut()
        // THEN mgr SHOULD be notified
        verifyLogHsuNotificationPostStatusCalled(STATUS_DISALLOWED_FEATURE_DISABLED)
    }

    // Helper methods

    private fun mockIsNonHsu() {
        whenever(selectedUserInteractor.isCurrentUserHeadlessSystemUser)
            .thenReturn(MutableStateFlow(false))
    }

    private fun mockIsHsu() {
        whenever(selectedUserInteractor.isCurrentUserHeadlessSystemUser)
            .thenReturn(MutableStateFlow(true))
    }

    private fun assertFilteredOut() {
        assertWithMessage("shouldFilterOut()").that(filter.shouldFilterOut(entry, 0)).isTrue()
    }

    private fun assertNotFilteredOut() {
        assertWithMessage("shouldFilterOut()").that(filter.shouldFilterOut(entry, 0)).isFalse()
    }

    private fun verifyLogHsuNotificationPostStatusNotCalled() {
        verify(notificationManager, never()).logHsuNotificationPostStatus(any(), anyInt())
    }

    private fun verifyLogHsuNotificationPostStatusCalled(status: Int) {
        verify(notificationManager).logHsuNotificationPostStatus(entry.sbn, status)
    }
}
