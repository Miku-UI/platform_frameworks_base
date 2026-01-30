/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.systemui.statusbar.notification.collection.coordinator

import android.app.Notification
import android.content.Context
import android.os.UserHandle
import android.platform.test.annotations.EnableFlags
import android.service.notification.StatusBarNotification
import android.testing.TestableLooper.RunWithLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.server.notification.Flags.FLAG_SCREENSHARE_NOTIFICATION_HIDING
import com.android.systemui.Flags.FLAG_SCREENSHARE_NOTIFICATION_HIDING_BUG_FIX
import com.android.systemui.SysuiTestCase
import com.android.systemui.res.R
import com.android.systemui.statusbar.notification.collection.BundleEntry
import com.android.systemui.statusbar.notification.collection.GroupEntry
import com.android.systemui.statusbar.notification.collection.GroupEntryBuilder
import com.android.systemui.statusbar.notification.collection.InternalNotificationsApi
import com.android.systemui.statusbar.notification.collection.ListEntry
import com.android.systemui.statusbar.notification.collection.NotifPipeline
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.NotificationEntryBuilder
import com.android.systemui.statusbar.notification.collection.PipelineEntry
import com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.NotifComparator
import com.android.systemui.statusbar.notification.collection.listbuilder.NotifSection
import com.android.systemui.statusbar.notification.collection.listbuilder.OnAfterRenderListListener
import com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.NotifSectioner
import com.android.systemui.statusbar.notification.collection.render.GroupExpansionManagerImpl
import com.android.systemui.statusbar.notification.data.model.NotifStats
import com.android.systemui.statusbar.notification.domain.interactor.ActiveNotificationsInteractor
import com.android.systemui.statusbar.notification.domain.interactor.RenderNotificationListInteractor
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import com.android.systemui.statusbar.notification.row.data.repository.TEST_BUNDLE_SPEC
import com.android.systemui.statusbar.notification.stack.BUCKET_ALERTING
import com.android.systemui.statusbar.notification.stack.BUCKET_SILENT
import com.android.systemui.statusbar.policy.SensitiveNotificationProtectionController
import com.android.systemui.util.mockito.withArgCaptor
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
class StackCoordinatorTest : SysuiTestCase() {
    private lateinit var entry: NotificationEntry
    private lateinit var coordinator: StackCoordinator
    private lateinit var afterRenderListListener: OnAfterRenderListListener
    private lateinit var testUtil: TestUtil

    private val pipeline: NotifPipeline = mock()
    private val groupExpansionManagerImpl: GroupExpansionManagerImpl = mock()
    private val renderListInteractor: RenderNotificationListInteractor = mock()
    private val activeNotificationsInteractor: ActiveNotificationsInteractor = mock()
    private val sensitiveNotificationProtectionController: SensitiveNotificationProtectionController =
        mock()
    private val section: NotifSection = mock()
    private val row: ExpandableNotificationRow = mock()

    private val alertingSectioner: NotifSectioner = mock()
    private val silentSectioner: NotifSectioner = mock()
    private lateinit var alertingSection: NotifSection
    private lateinit var silentSection: NotifSection

    private val clearableAlerting =
        TestUtil.TestEntry("clearableAlerting", isSilent = false, isClearable = true)
    private val nonClearableAlerting =
        TestUtil.TestEntry("nonClearableAlerting", isSilent = false, isClearable = false)
    private val clearableSilent =
        TestUtil.TestEntry("clearableSilent", isSilent = true, isClearable = true)
    private val nonClearableSilent =
        TestUtil.TestEntry("nonClearableSilent", isSilent = true, isClearable = false)

    @Before
    fun setUp() {
        whenever(sensitiveNotificationProtectionController.isSensitiveStateActive).thenReturn(false)

        entry = NotificationEntryBuilder().setSection(section).build()
        entry.row = row
        entry.setSensitive(false, false)

        whenever(alertingSectioner.bucket).thenReturn(BUCKET_ALERTING)
        whenever(alertingSectioner.comparator).thenReturn(mock<NotifComparator>())
        alertingSection = NotifSection(alertingSectioner, 0)

        whenever(silentSectioner.bucket).thenReturn(BUCKET_SILENT)
        whenever(silentSectioner.comparator).thenReturn(mock<NotifComparator>())
        silentSection = NotifSection(silentSectioner, 1)

        testUtil = TestUtil(context, alertingSection, silentSection)

        coordinator =
            StackCoordinator(
                groupExpansionManagerImpl,
                renderListInteractor,
                activeNotificationsInteractor,
                sensitiveNotificationProtectionController,
            )
        coordinator.attach(pipeline)
        afterRenderListListener = withArgCaptor {
            verify(pipeline).addOnAfterRenderListListener(capture())
        }
    }

    @Test
    fun testSetRenderedListOnInteractor() {
        afterRenderListListener.onAfterRenderList(listOf(entry))
        verify(renderListInteractor).setRenderedList(eq(listOf(entry)))
    }

    @Test
    fun testSetNotificationStats_clearableAlerting() {
        whenever(section.bucket).thenReturn(BUCKET_ALERTING)
        afterRenderListListener.onAfterRenderList(listOf(entry))
        verify(activeNotificationsInteractor)
            .setNotifStats(
                NotifStats(
                    hasNonClearableAlertingNotifs = false,
                    hasClearableAlertingNotifs = true,
                    hasNonClearableSilentNotifs = false,
                    hasClearableSilentNotifs = false,
                )
            )
    }

    @Test
    @EnableFlags(FLAG_SCREENSHARE_NOTIFICATION_HIDING, FLAG_SCREENSHARE_NOTIFICATION_HIDING_BUG_FIX)
    fun testSetNotificationStats_isSensitiveStateActive_nonClearableAlerting() {
        whenever(sensitiveNotificationProtectionController.isSensitiveStateActive).thenReturn(true)
        whenever(section.bucket).thenReturn(BUCKET_ALERTING)
        afterRenderListListener.onAfterRenderList(listOf(entry))
        verify(activeNotificationsInteractor)
            .setNotifStats(
                NotifStats(
                    hasNonClearableAlertingNotifs = true,
                    hasClearableAlertingNotifs = false,
                    hasNonClearableSilentNotifs = false,
                    hasClearableSilentNotifs = false,
                )
            )
    }

    @Test
    fun testSetNotificationStats_clearableSilent() {
        whenever(section.bucket).thenReturn(BUCKET_SILENT)
        afterRenderListListener.onAfterRenderList(listOf(entry))
        verify(activeNotificationsInteractor)
            .setNotifStats(
                NotifStats(
                    hasNonClearableAlertingNotifs = false,
                    hasClearableAlertingNotifs = false,
                    hasNonClearableSilentNotifs = false,
                    hasClearableSilentNotifs = true,
                )
            )
    }

    @Test
    @EnableFlags(FLAG_SCREENSHARE_NOTIFICATION_HIDING, FLAG_SCREENSHARE_NOTIFICATION_HIDING_BUG_FIX)
    fun testSetNotificationStats_isSensitiveStateActive_nonClearableSilent() {
        whenever(sensitiveNotificationProtectionController.isSensitiveStateActive).thenReturn(true)
        whenever(section.bucket).thenReturn(BUCKET_SILENT)
        afterRenderListListener.onAfterRenderList(listOf(entry))
        verify(activeNotificationsInteractor)
            .setNotifStats(
                NotifStats(
                    hasNonClearableAlertingNotifs = false,
                    hasClearableAlertingNotifs = false,
                    hasNonClearableSilentNotifs = true,
                    hasClearableSilentNotifs = false,
                )
            )
    }

    @Test
    fun testSetNotificationStats_nonClearableRedacted() {
        entry.setSensitive(true, true)
        whenever(section.bucket).thenReturn(BUCKET_ALERTING)
        afterRenderListListener.onAfterRenderList(listOf(entry))
        verify(activeNotificationsInteractor)
            .setNotifStats(
                NotifStats(
                    hasNonClearableAlertingNotifs = true,
                    hasClearableAlertingNotifs = false,
                    hasNonClearableSilentNotifs = false,
                    hasClearableSilentNotifs = false,
                )
            )
    }

    @Test
    fun stats_forBundle_setsAlertingFlags() {
        val bundle = testUtil.buildBundle(
            listOf(
                testUtil.buildEntry(clearableAlerting),
                testUtil.buildEntry(nonClearableAlerting),
            )
        )
        runTestAndAssertStats(
            listOf(bundle),
            hasClearableAlertingNotifs = true,
            hasNonClearableAlertingNotifs = true
        )
    }


    @Test
    fun stats_forBundle_setsSilentFlags() {
        val bundle = testUtil.buildBundle(
            listOf(
                testUtil.buildEntry(clearableSilent),
                testUtil.buildEntry(nonClearableSilent),
            )
        )
        runTestAndAssertStats(
            listOf(bundle),
            hasClearableSilentNotifs = true,
            hasNonClearableSilentNotifs = true
        )
    }

    @Test
    fun stats_forEmptyGroupAndBundle_setsNoFlags() {
        val emptyGroup = testUtil.buildGroup("emptyGroup", emptyList())
        val emptyBundle = testUtil.buildBundle(emptyList())
        runTestAndAssertStats(listOf(emptyGroup, emptyBundle))
    }

    @Test
    fun stats_forMix_setsAllFlags() {
        val alertingBundle = testUtil.buildBundle(listOf(testUtil.buildEntry(clearableAlerting)))
        val silentGroup = testUtil.buildGroup("g1", listOf(testUtil.buildEntry(clearableSilent)))
        val nonClearableAlertingEntry = testUtil.buildEntry(nonClearableAlerting)
        val nonClearableSilentEntry = testUtil.buildEntry(nonClearableSilent)

        runTestAndAssertStats(
            listOf(
                alertingBundle,
                silentGroup,
                nonClearableAlertingEntry,
                nonClearableSilentEntry,
            ),
            hasClearableAlertingNotifs = true,
            hasNonClearableAlertingNotifs = true,
            hasClearableSilentNotifs = true,
            hasNonClearableSilentNotifs = true
        )
    }

    private fun runTestAndAssertStats(
        entries: List<PipelineEntry>,
        hasClearableAlertingNotifs: Boolean = false,
        hasNonClearableAlertingNotifs: Boolean = false,
        hasClearableSilentNotifs: Boolean = false,
        hasNonClearableSilentNotifs: Boolean = false,
    ) {
        afterRenderListListener.onAfterRenderList(entries)
        verify(activeNotificationsInteractor).setNotifStats(
            NotifStats(
                hasClearableAlertingNotifs = hasClearableAlertingNotifs,
                hasNonClearableAlertingNotifs = hasNonClearableAlertingNotifs,
                hasClearableSilentNotifs = hasClearableSilentNotifs,
                hasNonClearableSilentNotifs = hasNonClearableSilentNotifs,
            )
        )
    }

    @OptIn(InternalNotificationsApi::class)
    private class TestUtil(
        private val context: Context,
        private val alertingSection: NotifSection,
        private val silentSection: NotifSection,
    ) {
        private val testPkg = "testPkg"
        private val testUid = 12345

        /** Hold data for testing */
        data class TestEntry(val key: String, val isSilent: Boolean, val isClearable: Boolean)

        private fun createSbn(key: String): StatusBarNotification {
            val notification = Notification.Builder(context, "test_channel_id")
                .setSmallIcon(R.drawable.ic_person)
                .setContentTitle("Title for $key")
                .build()

            // Use test key's hashcode as notif ID to ensure SBN key is unique for each test entry
            return StatusBarNotification(
                testPkg,
                testPkg,
                key.hashCode(),
                null, // tag
                testUid,
                0, // initialPid
                notification,
                UserHandle.of(0),
                null, // overrideGroupKey
                0L // postTime
            )
        }

        /** Builds NotificationEntry based on TestEntry */
        fun buildEntry(spec: TestEntry): NotificationEntry {
            val sbn = createSbn(spec.key)

            if (!spec.isClearable) {
                // Make non-clearable by making it ongoing
                sbn.notification.flags = sbn.notification.flags or Notification.FLAG_ONGOING_EVENT
            }

            return NotificationEntryBuilder(context)
                .setSbn(sbn)
                .setSection(if (spec.isSilent) silentSection else alertingSection)
                .build()
                .apply {
                    // isClearable requires non-null row
                    row = mock<ExpandableNotificationRow>()
                    setSensitive(/* sensitive= */ false, /* deviceSensitive= */ false)
                }
        }

        /** Builds a real GroupEntry using a builder */
        fun buildGroup(key: String, children: List<NotificationEntry>): GroupEntry {
            val builder = GroupEntryBuilder()
                .setKey(key)
                .setChildren(children)

            // Only create a summary if the group has children
            if (children.isNotEmpty()) {
                val summary = buildEntry(TestEntry(key, isSilent = false, isClearable = false))
                builder.setSummary(summary)
            }

            return builder.build()
        }

        /**
         * Builds a real BundleEntry using a test spec
         */
        fun buildBundle(children: List<ListEntry>): BundleEntry {
            val bundle = BundleEntry(TEST_BUNDLE_SPEC)
            children.forEach { bundle.addChild(it) }
            return bundle
        }
    }
}