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
package com.android.server.companion.datatransfer.crossdevicesync.notification

import android.app.Notification
import android.app.NotificationManager
import android.os.UserHandle
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.internal.R
import com.android.server.companion.datatransfer.crossdevicesync.notification.NotificationReason.AIRPLANE_MODE_SYNCED
import com.android.server.companion.datatransfer.crossdevicesync.services.SyncServiceTestBase
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationHelperImplTest : SyncServiceTestBase() {

    @Test
    fun testInit_registersUserListener() {
        mNotificationHelperImpl.init()
        assertThat(mFakeUserHelper.registeredListeners).contains(mNotificationHelperImpl)
    }

    @Test
    fun testDestroy_unregistersUserListener() {
        mNotificationHelperImpl.init()
        mNotificationHelperImpl.destroy()
        assertThat(mFakeUserHelper.registeredListeners).doesNotContain(mNotificationHelperImpl)
    }

    @Test
    fun testMethodsDoNothingBeforeInit() {
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED, UserHandle.ALL)
        assertThat(mFakeNotificationManagerProxy.getUserNotifications()).isEmpty()

        mNotificationHelperImpl.dismissNotification(AIRPLANE_MODE_SYNCED, UserHandle.ALL)
        // No crash/exception means pass, plus we verify no interactions if we could (mocking).
        // Since it's state-based verification:
        assertThat(mFakeNotificationManagerProxy.getUserNotifications()).isEmpty()
    }

    @Test
    fun testMethodsDoNothingAfterDestroy() {
        mNotificationHelperImpl.init()
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED, UserHandle.ALL)
        assertThat(mFakeNotificationManagerProxy.getUserNotifications()).isNotEmpty()

        mNotificationHelperImpl.destroy()

        // Clear existing to verify new ones aren't added
        mFakeNotificationManagerProxy.clear()
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED, UserHandle.ALL)
        assertThat(mFakeNotificationManagerProxy.getUserNotifications()).isEmpty()
    }

    @Test
    fun testMaybeShowNotification_firstTime_showsNotificationWithCorrectParams() {
        mNotificationHelperImpl.init()
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED, UserHandle.ALL)

        // Verify Channel
        assertThat(mFakeNotificationManagerProxy.getChannels().containsKey("apm_sync_channel"))
            .isTrue()
        val channel = mFakeNotificationManagerProxy.getChannels()["apm_sync_channel"]!!
        assertThat(channel.name)
            .isEqualTo(mContext.getString(R.string.airplane_mode_sync_notification_channel_name))
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)

        // Verify Notification
        assertThat(mFakeNotificationManagerProxy.getUserNotifications())
            .containsKey(1 to UserHandle.ALL)
        val notification =
            mFakeNotificationManagerProxy.getUserNotifications()[1 to UserHandle.ALL]!!
        assertThat(notification.channelId).isEqualTo("apm_sync_channel")
        assertThat(notification.smallIcon.resId).isEqualTo(R.drawable.ic_cross_device_sync_airplane)
        assertThat(notification.visibility).isEqualTo(Notification.VISIBILITY_PUBLIC)
        assertThat(notification.flags and Notification.FLAG_AUTO_CANCEL).isNotEqualTo(0)
        assertThat(notification.flags and Notification.FLAG_LOCAL_ONLY).isNotEqualTo(0)
        val message = mContext.getString(R.string.airplane_mode_sync_notification_content)
        val extras = notification.extras
        assertThat(extras.getString(Notification.EXTRA_TITLE))
            .isEqualTo(mContext.getString(R.string.airplane_mode_sync_notification_title))
        assertThat(extras.getString(Notification.EXTRA_TEXT)).isEqualTo(message)
        assertThat(extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString())
            .isEqualTo(message)

        assertThat(notification.contentIntent.intent.action)
            .isEqualTo(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
        assertThat(notification.actions).isNull()

        // Verify SharedPrefs
        assertThat(getRecordedCount()).isEqualTo(1)
        assertThat(mSharedPreferences.contains(getDateKey())).isTrue()
    }

    @Test
    fun testMaybeShowNotification_onWatch_showsNotificationWithAction() {
        mNotificationHelperImpl.init()
        mFakeDeviceUtil.isWatch = true

        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED, UserHandle.ALL)

        // Verify Notification
        assertThat(mFakeNotificationManagerProxy.getUserNotifications())
            .containsKey(1 to UserHandle.ALL)
        val notification =
            mFakeNotificationManagerProxy.getUserNotifications()[1 to UserHandle.ALL]!!
        assertThat(notification.contentIntent).isNull()
        assertThat(notification.actions).hasLength(1)
        assertThat(notification.actions[0].title)
            .isEqualTo(mContext.getString(R.string.apm_sync_notification_settings_action))
        assertThat(notification.actions[0].actionIntent.intent.action)
            .isEqualTo(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
    }

    @Test
    fun testMaybeShowNotification_secondTimeWithinWeek_doesNotShowNotification() {
        mNotificationHelperImpl.init()
        // First time
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED, UserHandle.ALL)
        mFakeNotificationManagerProxy.clear()

        // Advance clock by 1 day
        mFakeClock.advanceTime(24 * 60 * 60 * 1000)

        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED, UserHandle.ALL)

        assertThat(mFakeNotificationManagerProxy.getUserNotifications())
            .doesNotContainKey(1 to UserHandle.ALL)
        assertThat(getRecordedCount()).isEqualTo(1)
    }

    @Test
    fun testMaybeShowNotification_secondTimeAfterWeek_showsNotification() {
        mNotificationHelperImpl.init()
        // First time
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED, UserHandle.ALL)
        mFakeNotificationManagerProxy.clear()

        // Advance clock by 8 days
        mFakeClock.advanceTime(8L * 24 * 60 * 60 * 1000)

        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED, UserHandle.ALL)

        assertThat(mFakeNotificationManagerProxy.getUserNotifications())
            .containsKey(1 to UserHandle.ALL)
        assertThat(getRecordedCount()).isEqualTo(2)
    }

    @Test
    fun testMaybeShowNotification_thirdTimeWithin6Months_doesNotShowNotification() {
        mNotificationHelperImpl.init()
        // 1st time
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED, UserHandle.ALL)
        mFakeNotificationManagerProxy.clear()

        // 2nd time (after 8 days)
        mFakeClock.advanceTime(8L * 24 * 60 * 60 * 1000)
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED, UserHandle.ALL)
        mFakeNotificationManagerProxy.clear()

        // Advance clock by 1 day (still within 6 months of last display)
        mFakeClock.advanceTime(1L * 24 * 60 * 60 * 1000)

        // Should not show again, as less than 6 months passed from second notification and count is
        // 2
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED, UserHandle.ALL)

        assertThat(mFakeNotificationManagerProxy.getUserNotifications())
            .doesNotContainKey(1 to UserHandle.ALL)
        assertThat(getRecordedCount()).isEqualTo(2)
    }

    @Test
    fun testMaybeShowNotification_thirdTimeAfter6Months_showsNotification() {
        mNotificationHelperImpl.init()
        // 1st
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED, UserHandle.ALL)
        mFakeNotificationManagerProxy.clear()

        // 2nd (after 8 days)
        mFakeClock.advanceTime(8L * 24 * 60 * 60 * 1000)
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED, UserHandle.ALL)
        mFakeNotificationManagerProxy.clear()

        assertThat(getRecordedCount()).isEqualTo(2)

        // Advance clock by 6 months + 1 day
        mFakeClock.advanceTime(185L * 24 * 60 * 60 * 1000)

        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED, UserHandle.ALL)

        assertThat(mFakeNotificationManagerProxy.getUserNotifications())
            .containsKey(1 to UserHandle.ALL)
        assertThat(getRecordedCount()).isEqualTo(3)
    }

    @Test
    fun testMaybeShowNotification_hundredthTimeBefore6Months_doesNotShowNotification() {
        mNotificationHelperImpl.init()
        // Show 99 times
        // 1st
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED, UserHandle.ALL)
        mFakeNotificationManagerProxy.clear()

        // 2nd (after 8 days)
        mFakeClock.advanceTime(8L * 24 * 60 * 60 * 1000)
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED, UserHandle.ALL)
        mFakeNotificationManagerProxy.clear()

        // 3rd - 99th (after 6 months)
        repeat(97) {
            mFakeClock.advanceTime(185L * 24 * 60 * 60 * 1000) // Advance by ~6 months + 1 day
            mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED, UserHandle.ALL)
            mFakeNotificationManagerProxy.clear()
        }

        // 100th (before 6 months)
        mFakeClock.advanceTime(1L * 24 * 60 * 60 * 1000) // Advance by 1 day
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED, UserHandle.ALL)

        assertThat(mFakeNotificationManagerProxy.getUserNotifications())
            .doesNotContainKey(1 to UserHandle.ALL)
        assertThat(getRecordedCount()).isEqualTo(99)
    }

    @Test
    fun testMaybeShowNotification_hundredthTimeAfter6Months_showsNotification() {
        mNotificationHelperImpl.init()
        // 1st
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED, UserHandle.ALL)
        mFakeNotificationManagerProxy.clear()

        // 2nd (after 8 days)
        mFakeClock.advanceTime(8L * 24 * 60 * 60 * 1000)
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED, UserHandle.ALL)
        mFakeNotificationManagerProxy.clear()

        // 3rd - 99th (after 6 months)
        repeat(97) {
            mFakeClock.advanceTime(185L * 24 * 60 * 60 * 1000)
            mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED, UserHandle.ALL)
            mFakeNotificationManagerProxy.clear()
        }

        assertThat(getRecordedCount()).isEqualTo(99)

        // 100th (after another 6 months)
        mFakeClock.advanceTime(185L * 24 * 60 * 60 * 1000)
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED, UserHandle.ALL)

        assertThat(mFakeNotificationManagerProxy.getUserNotifications())
            .containsKey(1 to UserHandle.ALL)
        assertThat(getRecordedCount()).isEqualTo(100)
    }

    @Test
    fun testClearAllCounters_clearsSharedPrefs() {
        mNotificationHelperImpl.init()
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED, UserHandle.ALL)
        assertThat(getRecordedCount()).isEqualTo(1)
        assertThat(mSharedPreferences.contains(getDateKey())).isTrue()

        mNotificationHelperImpl.reset()

        assertThat(mSharedPreferences.contains(getCountKey())).isFalse()
        assertThat(mSharedPreferences.contains(getDateKey())).isFalse()
    }

    @Test
    fun testDismissNotification_dismissesNotification() {
        mNotificationHelperImpl.init()
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED, UserHandle.ALL)
        assertThat(mFakeNotificationManagerProxy.getUserNotifications())
            .containsKey(1 to UserHandle.ALL)

        mNotificationHelperImpl.dismissNotification(AIRPLANE_MODE_SYNCED, UserHandle.ALL)
        assertThat(mFakeNotificationManagerProxy.getUserNotifications())
            .doesNotContainKey(1 to UserHandle.ALL)
    }

    @Test
    fun testOnUserRemoved_clearsSharedPrefs() {
        mNotificationHelperImpl.init()
        val user = UserHandle.of(10)
        mFakeUserHelper.addUser(user)

        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED, user)

        assertThat(mSharedPreferences.contains(getCountKey(user = user))).isTrue()
        assertThat(mSharedPreferences.contains(getDateKey(user = user))).isTrue()

        mFakeUserHelper.removeUser(user)

        assertThat(mSharedPreferences.contains(getCountKey(user = user))).isFalse()
        assertThat(mSharedPreferences.contains(getDateKey(user = user))).isFalse()
    }

    private fun getRecordedCount(
        reason: String = AIRPLANE_MODE_SYNCED,
        user: UserHandle = UserHandle.ALL,
    ) = mSharedPreferences.getInt(getCountKey(reason, user), 0)

    private fun getCountKey(
        reason: String = AIRPLANE_MODE_SYNCED,
        user: UserHandle = UserHandle.ALL,
    ) = "${reason}_${user.identifier}"

    private fun getDateKey(
        reason: String = AIRPLANE_MODE_SYNCED,
        user: UserHandle = UserHandle.ALL,
    ) = "${getCountKey(reason, user)}_date"
}
