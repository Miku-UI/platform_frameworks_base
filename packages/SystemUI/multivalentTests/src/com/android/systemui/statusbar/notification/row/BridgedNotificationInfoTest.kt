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
 * limitations under the License
 */
package com.android.systemui.statusbar.notification.row

import android.app.INotificationManager
import android.app.Notification
import android.app.Notification.BridgedNotificationMetadata
import android.app.NotificationChannel
import android.app.NotificationChannel.SOCIAL_MEDIA_ID
import android.app.NotificationManager.IMPORTANCE_LOW
import android.content.ComponentName
import android.content.mockPackageManager
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.UserHandle
import android.os.testableLooper
import android.platform.test.annotations.EnableFlags
import android.print.PrintManager
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import android.testing.TestableLooper.RunWithLooper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.MetricsLogger
import com.android.internal.logging.UiEventLogger
import com.android.internal.logging.metricsLogger
import com.android.internal.logging.uiEventLoggerFake
import com.android.systemui.Dependency
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.testCase
import com.android.systemui.notifications.content.icon.AppIconProvider
import com.android.systemui.notifications.content.icon.mockAppIconProvider
import com.android.systemui.res.R
import com.android.systemui.statusbar.notification.collection.EntryAdapter
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.NotificationEntryBuilder
import com.android.systemui.statusbar.notification.promoted.domain.interactor.PackageDemotionInteractor
import com.android.systemui.statusbar.notification.row.icon.NotificationIconStyleProvider
import com.android.systemui.statusbar.notification.row.icon.mockNotificationIconStyleProvider
import com.android.systemui.testKosmos
import com.android.telecom.telecomManager
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
@EnableFlags(android.app.Flags.FLAG_BRIDGED_NOTIFICATIONS)
class BridgedNotificationInfoTest : SysuiTestCase() {
    private val kosmos = testKosmos().also { it.testCase = this }

    private lateinit var underTest: BridgedNotificationInfo
    private lateinit var notificationChannel: NotificationChannel
    private lateinit var classifiedNotificationChannel: NotificationChannel
    private lateinit var sbn: StatusBarNotification
    private lateinit var entry: NotificationEntry
    private lateinit var entryAdapter: EntryAdapter

    private val mockPackageManager = kosmos.mockPackageManager
    private val mockAppIconProvider = kosmos.mockAppIconProvider
    private val mockIconStyleProvider = kosmos.mockNotificationIconStyleProvider
    private val uiEventLogger = kosmos.uiEventLoggerFake
    private val testableLooper by lazy { kosmos.testableLooper }

    private val onUserInteractionCallback = mock<OnUserInteractionCallback>()
    private val mockINotificationManager = mock<INotificationManager>()
    private val channelEditorDialogController = mock<ChannelEditorDialogController>()
    private val packageDemotionInteractor = mock<PackageDemotionInteractor>()
    private val onSettingsClick = mock<NotificationInfo.OnSettingsClickListener>()

    @Before
    fun setUp() {
        mContext.addMockSystemService(TelecomManager::class.java, kosmos.telecomManager)

        mDependency.injectTestDependency(Dependency.BG_LOOPER, testableLooper.looper)

        // Inflate the layout
        val inflater = LayoutInflater.from(mContext)
        underTest =
            inflater.inflate(R.layout.notification_bridged_info, null) as BridgedNotificationInfo

        underTest.setGutsParent(mock<NotificationGuts>())

        // Our view is never attached to a window so the View#post methods in NotificationInfo never
        // get called. Setting this will skip the post and do the action immediately.
        underTest.mSkipPost = true

        // PackageManager must return a packageInfo and applicationInfo.
        val packageInfo = PackageInfo()
        packageInfo.packageName = TEST_PACKAGE_NAME
        whenever(mockPackageManager.getPackageInfo(eq(TEST_PACKAGE_NAME), anyInt()))
            .thenReturn(packageInfo)
        val applicationInfo = ApplicationInfo()
        applicationInfo.uid = TEST_UID // non-zero
        val systemPackageInfo = PackageInfo()
        systemPackageInfo.packageName = TEST_SYSTEM_PACKAGE_NAME
        whenever(mockPackageManager.getPackageInfo(eq(TEST_SYSTEM_PACKAGE_NAME), anyInt()))
            .thenReturn(systemPackageInfo)
        whenever(mockPackageManager.getPackageInfo(eq("android"), anyInt())).thenReturn(packageInfo)
        whenever(mockPackageManager.getApplicationLabel(applicationInfo)).thenReturn("App")

        val assistant = ComponentName("package", "service")
        whenever(mockINotificationManager.allowedNotificationAssistant).thenReturn(assistant)
        val ri = ResolveInfo()
        ri.activityInfo = ActivityInfo()
        ri.activityInfo.packageName = assistant.packageName
        ri.activityInfo.name = "activity"
        whenever(mockPackageManager.queryIntentActivities(any(), anyInt())).thenReturn(listOf(ri))

        // Package has one channel by default.
        whenever(
                mockINotificationManager.getNumNotificationChannelsForPackage(
                    eq(TEST_PACKAGE_NAME),
                    eq(TEST_UID),
                    anyBoolean(),
                )
            )
            .thenReturn(1)

        // Some test channels.
        notificationChannel = NotificationChannel(TEST_CHANNEL, TEST_CHANNEL_NAME, IMPORTANCE_LOW)
        classifiedNotificationChannel =
            NotificationChannel(SOCIAL_MEDIA_ID, "social", IMPORTANCE_LOW)

        val icon = Icon.createWithBitmap(Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888))
        val bridgedMetadata =
            BridgedNotificationMetadata(
                "test_display_name",
                "test_package",
                "test_channel_id",
                icon,
            )
        val notification =
            Notification.Builder(mContext, notificationChannel.id)
                .setBridgedNotificationMetadata(bridgedMetadata)
                .build()
        notification.extras.putParcelable(
            Notification.EXTRA_BUILDER_APPLICATION_INFO,
            applicationInfo,
        )
        sbn =
            StatusBarNotification(
                TEST_PACKAGE_NAME,
                TEST_PACKAGE_NAME,
                0,
                null,
                TEST_UID,
                0,
                notification,
                UserHandle.getUserHandleForUid(TEST_UID),
                null,
                0,
            )
        entry =
            NotificationEntryBuilder()
                .setSbn(sbn)
                .updateRanking { it.setChannel(notificationChannel) }
                .build()
        entryAdapter = kosmos.entryAdapterFactory.create(entry)

        whenever(
                mockINotificationManager.getNotificationChannel(
                    anyString(),
                    anyInt(),
                    eq(sbn.packageName),
                    eq(notificationChannel.id),
                )
            )
            .thenReturn(notificationChannel)
    }

    @Test
    fun testHandleCloseControls_DoesNotMakeBinderCalllIfUnchanged() {
        bindNotification()

        underTest.handleCloseControls(true, false)
        testableLooper.processAllMessages()
        verify(mockINotificationManager, never())
            .setAdjustmentSupportedForPackage(anyInt(), anyString(), anyString(), anyBoolean())
    }

    @Test
    fun testSummaryText() {
        bindNotification()
        assertThat((underTest.findViewById(R.id.bridged_summary) as TextView).text)
            .isEqualTo(
                "Synced notification priority and settings can be adjusted on your test_display_name"
            )
    }

    private fun bindNotification(
        pm: PackageManager = this.mockPackageManager,
        iNotificationManager: INotificationManager = this.mockINotificationManager,
        appIconProvider: AppIconProvider = this.mockAppIconProvider,
        iconStyleProvider: NotificationIconStyleProvider = this.mockIconStyleProvider,
        onUserInteractionCallback: OnUserInteractionCallback = this.onUserInteractionCallback,
        channelEditorDialogController: ChannelEditorDialogController =
            this.channelEditorDialogController,
        packageDemotionInteractor: PackageDemotionInteractor = this.packageDemotionInteractor,
        pkg: String = TEST_PACKAGE_NAME,
        entry: NotificationEntry = this.entry,
        entryAdapter: EntryAdapter = this.entryAdapter,
        onSettingsClick: NotificationInfo.OnSettingsClickListener? = this.onSettingsClick,
        onAppSettingsClick: NotificationInfo.OnAppSettingsClickListener? = mock(),
        onFeedbackClickListener: NotificationInfo.OnFeedbackClickListener? = mock(),
        uiEventLogger: UiEventLogger = this.uiEventLogger,
        isDeviceProvisioned: Boolean = true,
        isNonblockable: Boolean = false,
        isDismissable: Boolean = true,
        wasShownHighPriority: Boolean = true,
        metricsLogger: MetricsLogger = kosmos.metricsLogger,
        onCloseClick: View.OnClickListener? = mock(),
    ) {
        underTest.bindNotification(
            pm,
            iNotificationManager,
            appIconProvider,
            iconStyleProvider,
            onUserInteractionCallback,
            channelEditorDialogController,
            packageDemotionInteractor,
            pkg,
            entry.ranking,
            entry.sbn,
            entryAdapter,
            onSettingsClick,
            onAppSettingsClick,
            onFeedbackClickListener,
            uiEventLogger,
            isDeviceProvisioned,
            isNonblockable,
            isDismissable,
            wasShownHighPriority,
            metricsLogger,
            onCloseClick,
        )
    }

    companion object {
        private const val TEST_PACKAGE_NAME = "test_package"
        private const val TEST_SYSTEM_PACKAGE_NAME = PrintManager.PRINT_SPOOLER_PACKAGE_NAME
        private const val TEST_UID = 1
        private const val TEST_CHANNEL = "test_channel"
        private const val TEST_CHANNEL_NAME = "TEST CHANNEL NAME"
    }
}
