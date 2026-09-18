/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.app.INotificationManager
import android.app.NotificationChannel
import android.app.NotificationChannel.PROMOTIONS_ID
import android.app.NotificationManager.ACTION_DYNAMIC_BUNDLE_MODIFIED
import android.app.NotificationManager.DYNAMIC_BUNDLE_MODIFICATION_TYPE_ADDED
import android.app.NotificationManager.DYNAMIC_BUNDLE_MODIFICATION_TYPE_REMOVED
import android.app.NotificationManager.EXTRA_DYNAMIC_BUNDLE
import android.app.NotificationManager.EXTRA_DYNAMIC_BUNDLE_MODIFICATION_TYPE
import android.content.Intent
import android.content.applicationContext
import android.os.UserHandle
import android.platform.test.annotations.EnableFlags
import android.service.notification.DynamicBundle
import android.testing.TestableLooper
import android.util.Pair
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.MutableSceneTransitionLayoutState
import com.android.systemui.SysuiTestCase
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.kosmos.currentValue
import com.android.systemui.notifications.ui.composable.row.BundleHeader
import com.android.systemui.settings.UserTracker
import com.android.systemui.statusbar.notification.OnboardingAffordanceManager
import com.android.systemui.statusbar.notification.collection.BundleEntry
import com.android.systemui.statusbar.notification.collection.BundleSpec
import com.android.systemui.statusbar.notification.collection.GroupEntry
import com.android.systemui.statusbar.notification.collection.InternalNotificationsApi
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.NotificationEntryBuilder
import com.android.systemui.statusbar.notification.collection.provider.SectionHeaderVisibilityProvider
import com.android.systemui.statusbar.notification.collection.render.BundleBarn
import com.android.systemui.statusbar.notification.row.data.model.AppData
import com.android.systemui.statusbar.notification.row.data.repository.TEST_BUNDLE_SPEC
import com.android.systemui.statusbar.notification.row.data.repository.TEST_BUNDLE_SPEC_2
import com.android.systemui.testKosmos
import com.android.systemui.util.concurrency.FakeExecutor
import com.android.systemui.util.time.FakeSystemClock
import com.android.systemui.util.time.SystemClock
import com.google.common.truth.Correspondence
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations

@OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    InternalNotificationsApi::class,
    ExperimentalCoroutinesApi::class,
)
@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
class BundleCoordinatorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    @Mock private lateinit var bundleBarn: BundleBarn
    @Mock private lateinit var systemClock: SystemClock
    @Mock private lateinit var sectionHeaderVisProvider: SectionHeaderVisibilityProvider

    @Mock private lateinit var notificationManager: INotificationManager
    @Mock private lateinit var userTracker: UserTracker

    private var executor: FakeExecutor = FakeExecutor(FakeSystemClock())

    private val broadcastDispatcher = kosmos.broadcastDispatcher

    private val onboardingMgr by lazy {
        OnboardingAffordanceManager("test bundle onboarding", sectionHeaderVisProvider)
    }

    private lateinit var coordinator: BundleCoordinator

    private val pkg1 = "pkg1"
    private val pkg2 = "pkg2"

    private val user1 = UserHandle.of(0)
    private val user2 = UserHandle.of(1)

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        coordinator =
            BundleCoordinator(
                bundleBarn,
                systemClock,
                TestScope(UnconfinedTestDispatcher()),
                onboardingMgr,
                notificationManager,
                executor,
                executor,
                userTracker,
                broadcastDispatcher,
            )
    }

    @Test
    fun testBundler_getBundleIdOrNull_returnBundleId() {
        val classifiedEntry = makeEntryOfChannelType(PROMOTIONS_ID)
        assertEquals(coordinator.bundler.getBundleIdOrNull(classifiedEntry), PROMOTIONS_ID)
    }

    @Test
    fun testBundler_getBundleIdOrNull_returnNull() {
        val unclassifiedEntry = makeEntryOfChannelType("not system channel")
        assertEquals(coordinator.bundler.getBundleIdOrNull(unclassifiedEntry), null)
    }

    @Test
    fun membershipUpdater_notif_setKeyAndTime() {
        val testTime = 1000L
        whenever(systemClock.uptimeMillis()).thenReturn(testTime)

        val bundle = BundleEntry(TEST_BUNDLE_SPEC)
        val notifEntry = NotificationEntryBuilder().setPkg(pkg1).setUser(user1).build()
        bundle.addChild(notifEntry)

        coordinator.bundleMembershipUpdater.onBeforeRenderList(listOf(bundle))

        assertThat(notifEntry.timeAddedToBundle.first).isEqualTo(bundle.key)
        assertThat(notifEntry.timeAddedToBundle.second).isEqualTo(testTime)
    }

    @Test
    fun membershipUpdater_group_setKeyAndTime() {
        val testTime = 2000L
        whenever(systemClock.uptimeMillis()).thenReturn(testTime)

        val bundle = BundleEntry(TEST_BUNDLE_SPEC)

        val group1 = GroupEntry("key", 0L)
        val groupChild = NotificationEntryBuilder().setPkg(pkg2).setUser(user2).build()
        groupChild.timeAddedToBundle = Pair(bundle.key, testTime)
        group1.rawChildren.add(groupChild)

        val groupSummary =
            NotificationEntryBuilder()
                .setPkg(pkg2)
                .setUser(user2)
                .setGroupSummary(context, true)
                .build()
        group1.summary = groupSummary

        bundle.addChild(group1)

        coordinator.bundleMembershipUpdater.onBeforeRenderList(listOf(bundle))

        assertThat(groupSummary.timeAddedToBundle.first).isEqualTo(bundle.key)
        assertThat(groupSummary.timeAddedToBundle.second).isEqualTo(testTime)
        assertThat(groupChild.timeAddedToBundle.first).isEqualTo(bundle.key)
        assertThat(groupChild.timeAddedToBundle.second).isEqualTo(testTime)
    }

    @Test
    fun membershipUpdater_notifStaysInSameBundle_nonZeroTimeNotUpdated() {
        val initialTime = 100L
        val currentTime = 200L
        whenever(systemClock.uptimeMillis()).thenReturn(currentTime)

        val bundle = BundleEntry(TEST_BUNDLE_SPEC)
        val notif = NotificationEntryBuilder().setPkg(pkg1).setUser(user1).build()
        bundle.addChild(notif)
        notif.timeAddedToBundle = Pair(bundle.key, initialTime) // Pre-set time

        coordinator.bundleMembershipUpdater.onBeforeRenderList(listOf(bundle))

        // Timestamp should not change if key is same and time was already > 0L
        assertThat(notif.timeAddedToBundle.first).isEqualTo(bundle.key)
        assertThat(notif.timeAddedToBundle.second).isEqualTo(initialTime)
    }

    @Test
    fun membershipUpdater_notifStaysInSameBundle_zeroTimeUpdated() {
        val currentTime = 200L
        whenever(systemClock.uptimeMillis()).thenReturn(currentTime)

        val bundle = BundleEntry(TEST_BUNDLE_SPEC)
        val notif = NotificationEntryBuilder().setPkg(pkg1).setUser(user1).build()
        bundle.addChild(notif)
        notif.timeAddedToBundle = Pair(bundle.key, 0L)

        coordinator.bundleMembershipUpdater.onBeforeRenderList(listOf(bundle))

        // Timestamp should update because it was 0L
        assertThat(notif.timeAddedToBundle.first).isEqualTo(bundle.key)
        assertThat(notif.timeAddedToBundle.second).isEqualTo(currentTime)
    }

    @Test
    fun membershipUpdater_entryMovesToNewBundle_updatesKeyAndTime() {
        val time1 = 100L
        val time2 = 200L
        whenever(systemClock.uptimeMillis()).thenReturn(time2)

        val bundleOld = BundleEntry(TEST_BUNDLE_SPEC)
        val bundleNew = BundleEntry(TEST_BUNDLE_SPEC_2)

        val notif = NotificationEntryBuilder().setPkg(pkg1).setUser(user1).build()
        notif.timeAddedToBundle = Pair(bundleOld.key, time1)

        bundleNew.addChild(notif)
        coordinator.bundleMembershipUpdater.onBeforeRenderList(listOf(bundleNew))

        assertThat(notif.timeAddedToBundle.first).isEqualTo(bundleNew.key)
        assertThat(notif.timeAddedToBundle.second).isEqualTo(time2)
    }

    @Test
    fun appDataUpdater_emptyChildren_setsEmptyAppListWhenCollapsed() = runTest {
        val bundle = BundleEntry(TEST_BUNDLE_SPEC)
        bundle.bundleRepository.state =
            MutableSceneTransitionLayoutState(
                BundleHeader.Scenes.Collapsed,
                MotionScheme.standard(),
            )
        assertThat(bundle.children).isEmpty()

        coordinator.bundleAppDataUpdater.onBeforeRenderList(listOf(bundle))
        assertThat(currentValue(bundle.bundleRepository.appDataList)).isEmpty()
    }

    @Test
    fun appDataUpdater_emptyChildren_setsEmptyAppListWhenExpanded() {
        val bundle = BundleEntry(TEST_BUNDLE_SPEC)
        bundle.bundleRepository.state =
            MutableSceneTransitionLayoutState(BundleHeader.Scenes.Expanded, MotionScheme.standard())
        assertThat(bundle.children).isEmpty()

        coordinator.bundleAppDataUpdater.onBeforeRenderList(listOf(bundle))
        assertThat(bundle.bundleRepository.appDataList.value).isEmpty()
    }

    @Test
    fun appDataUpdater_twoNotifs_whileCollapsed() = runTest {
        val bundle = BundleEntry(TEST_BUNDLE_SPEC)
        bundle.bundleRepository.state =
            MutableSceneTransitionLayoutState(
                BundleHeader.Scenes.Collapsed,
                MotionScheme.standard(),
            )
        val time1 = 100L
        val time2 = 200L

        val notif1 = NotificationEntryBuilder().setPkg(pkg1).setUser(user1).build()
        notif1.timeAddedToBundle = Pair(bundle.key, time1)

        val notif2 = NotificationEntryBuilder().setPkg(pkg2).setUser(user2).build()
        notif2.timeAddedToBundle = Pair(bundle.key, time2)

        bundle.addChild(notif1)
        bundle.addChild(notif2)

        coordinator.bundleAppDataUpdater.onBeforeRenderList(listOf(bundle))

        assertThat(currentValue(bundle.bundleRepository.appDataList))
            .containsExactly(AppData(pkg1, user1, time1), AppData(pkg2, user2, time2))
    }

    @Test
    fun appDataUpdater_notifAndGroup_whileCollapsed() = runTest {
        val bundle = BundleEntry(TEST_BUNDLE_SPEC)
        bundle.bundleRepository.state =
            MutableSceneTransitionLayoutState(
                BundleHeader.Scenes.Collapsed,
                MotionScheme.standard(),
            )

        val time1 = 100L
        val time2 = 200L
        val notif1 = NotificationEntryBuilder().setPkg(pkg1).setUser(user1).build()
        notif1.timeAddedToBundle = Pair(bundle.key, time1)

        val group1 = GroupEntry("key", 0L)
        val groupChild = NotificationEntryBuilder().setPkg(pkg2).setUser(user2).build()
        groupChild.timeAddedToBundle = Pair(bundle.key, time2)
        group1.rawChildren.add(groupChild)

        val groupSummary =
            NotificationEntryBuilder()
                .setPkg(pkg2)
                .setUser(user2)
                .setGroupSummary(context, true)
                .build()
        group1.summary = groupSummary

        bundle.addChild(notif1)
        bundle.addChild(group1)

        coordinator.bundleAppDataUpdater.onBeforeRenderList(listOf(bundle))

        assertThat(currentValue(bundle.bundleRepository.appDataList))
            .containsExactly(AppData(pkg1, user1, time1), AppData(pkg2, user2, time2))
    }

    @Test
    fun appDataUpdater_notifAndGroup_usesMaxTimeFromSummaryOrChildren() {
        val bundle = BundleEntry(TEST_BUNDLE_SPEC)
        bundle.bundleRepository.state =
            MutableSceneTransitionLayoutState(
                BundleHeader.Scenes.Collapsed,
                MotionScheme.standard(),
            )

        val timeNotif1 = 100L
        val timeGroupChild = 150L
        val timeGroupSummary = 200L

        val notif1 = NotificationEntryBuilder().setPkg(pkg1).setUser(user1).build()
        notif1.timeAddedToBundle = Pair(bundle.key, timeNotif1)

        val group1 = GroupEntry("key", 0L)
        val groupSummary =
            NotificationEntryBuilder()
                .setPkg(pkg2)
                .setUser(user2)
                .setGroupSummary(context, true)
                .build()
        groupSummary.timeAddedToBundle = Pair(bundle.key, timeGroupSummary)
        group1.summary = groupSummary

        val groupChild = NotificationEntryBuilder().setPkg(pkg2).setUser(user2).build()
        groupChild.timeAddedToBundle = Pair(bundle.key, timeGroupChild)
        group1.rawChildren.add(groupChild)

        bundle.addChild(notif1)
        bundle.addChild(group1)

        coordinator.bundleAppDataUpdater.onBeforeRenderList(listOf(bundle))

        // 1. For pkg1, user1: AppData(pkg1, user1, timeNotif1)
        // 2. For pkg2, user2 (from group):
        //    - Summary: AppData(pkg2, user2, timeGroupSummary=200L)
        //    - Child:   AppData(pkg2, user2, timeGroupChild=150L)
        //    The `maxByOrNull` will pick the one with timeGroupSummary (200L)
        assertThat(bundle.bundleRepository.appDataList.value)
            .containsExactly(
                AppData(pkg1, user1, timeNotif1),
                AppData(pkg2, user2, timeGroupSummary),
            )
    }

    @Test
    fun appDataUpdater_twoNotifsWhileExpanded_updatedWhenRemoved() = runTest {
        val bundle = BundleEntry(TEST_BUNDLE_SPEC)
        bundle.bundleRepository.state =
            MutableSceneTransitionLayoutState(BundleHeader.Scenes.Expanded, MotionScheme.standard())

        val time1 = 100L
        val time2 = 200L

        val notif1 = NotificationEntryBuilder().setPkg(pkg1).setUser(user1).build()
        notif1.timeAddedToBundle = Pair(bundle.key, time1)

        val notif2 = NotificationEntryBuilder().setPkg(pkg2).setUser(user2).build()
        notif2.timeAddedToBundle = Pair(bundle.key, time2)

        bundle.addChild(notif1)
        bundle.addChild(notif2)

        coordinator.bundleAppDataUpdater.onBeforeRenderList(listOf(bundle))

        assertThat(currentValue(bundle.bundleRepository.appDataList))
            .containsExactly(AppData(pkg1, user1, time1), AppData(pkg2, user2, time2))

        bundle.removeChild(notif1)

        coordinator.bundleAppDataUpdater.onBeforeRenderList(listOf(bundle))
        assertThat(currentValue(bundle.bundleRepository.appDataList))
            .containsExactly(AppData(pkg2, user2, time2))
    }

    @Test
    fun appDataUpdater_closedBundle_usesMaxTimeAddedToBundleForSameAppUser() {
        val bundle = BundleEntry(TEST_BUNDLE_SPEC)
        bundle.bundleRepository.state =
            MutableSceneTransitionLayoutState(
                BundleHeader.Scenes.Collapsed,
                MotionScheme.standard(),
            )

        val time1 = 1000L
        val time2 = 2000L // Latest time, should be chosen
        val time3 = 500L

        val notif1 = NotificationEntryBuilder().setPkg(pkg1).setUser(user1).build()
        notif1.timeAddedToBundle = Pair(bundle.key, time1)

        val notif2 = NotificationEntryBuilder().setPkg(pkg1).setUser(user1).build()
        notif2.timeAddedToBundle = Pair(bundle.key, time2)

        val notif3 = NotificationEntryBuilder().setPkg(pkg1).setUser(user1).build()
        notif3.timeAddedToBundle = Pair(bundle.key, time3)

        val notifOtherApp = NotificationEntryBuilder().setPkg(pkg2).setUser(user2).build()
        notifOtherApp.timeAddedToBundle = Pair(bundle.key, time1)

        bundle.addChild(notif1)
        bundle.addChild(notif2)
        bundle.addChild(notif3)
        bundle.addChild(notifOtherApp)

        coordinator.bundleAppDataUpdater.onBeforeRenderList(listOf(bundle))

        // Expected processing:
        // 1. Collect AppData for notif1, notif2, notif3, notifOtherApp
        // 2. Filter by time > 0L (all pass).
        // 3. Group by (pkg, user).
        //    - Group (pkg1, user1) will have AppData with times T1, T2, T3.
        //    - Group (pkg2, user2) will have AppData with time T1.
        // 4. mapNotNull with maxByOrNull on timeAddedToBundle for each group.
        //    - For (pkg1, user1), max is T2.
        //    - For (pkg2, user2), max is T1.
        assertThat(bundle.bundleRepository.appDataList.value)
            .containsExactly(AppData(pkg1, user1, time2), AppData(pkg2, user2, time1))
    }

    @Test
    fun appDataUpdater_filtersOutZeroTimeAddedToBundleInFinalList() {
        val bundle = BundleEntry(TEST_BUNDLE_SPEC)
        bundle.bundleRepository.state =
            MutableSceneTransitionLayoutState(
                BundleHeader.Scenes.Collapsed,
                MotionScheme.standard(),
            )
        val validTime = 3000L

        val notif1 = NotificationEntryBuilder().setPkg(pkg1).setUser(user1).build()
        notif1.timeAddedToBundle = Pair(bundle.key, 0L)

        val notif2 = NotificationEntryBuilder().setPkg(pkg2).setUser(user2).build()
        notif2.timeAddedToBundle = Pair(bundle.key, validTime)

        bundle.addChild(notif1)
        bundle.addChild(notif2)

        coordinator.bundleAppDataUpdater.onBeforeRenderList(listOf(bundle))

        assertThat(bundle.bundleRepository.appDataList.value)
            .containsExactly(AppData(pkg2, user2, validTime))
    }

    @Test
    fun testTotalCount_initial_isZero() {
        val bundle = BundleEntry(TEST_BUNDLE_SPEC)

        assertThat(bundle.bundleRepository.numberOfChildren).isEqualTo(0)
    }

    @Test
    fun testTotalCount_addNotif() {
        val bundle = BundleEntry(TEST_BUNDLE_SPEC)
        bundle.addChild(NotificationEntryBuilder().build())

        coordinator.bundleCountUpdater.onBeforeFinalizeFilter(listOf(bundle))
        assertThat(bundle.bundleRepository.numberOfChildren).isEqualTo(1)
    }

    @Test
    fun testTotalCount_addGroup() {
        val bundle = BundleEntry(TEST_BUNDLE_SPEC)
        val groupEntry = GroupEntry("groupKey1", 0L)
        groupEntry.rawChildren.add(NotificationEntryBuilder().build())
        groupEntry.rawChildren.add(NotificationEntryBuilder().build())
        bundle.addChild(groupEntry)

        coordinator.bundleCountUpdater.onBeforeFinalizeFilter(listOf(bundle))
        assertThat(bundle.bundleRepository.numberOfChildren).isEqualTo(2)
    }

    @Test
    fun testTotalCount_addMultipleGroups() {
        val bundle = BundleEntry(TEST_BUNDLE_SPEC)

        val groupEntry1 = GroupEntry("groupKey1", 0L)
        groupEntry1.rawChildren.add(NotificationEntryBuilder().build())
        bundle.addChild(groupEntry1)

        val groupEntry2 = GroupEntry("groupKey2", 0L)
        groupEntry2.rawChildren.add(NotificationEntryBuilder().build())
        groupEntry2.rawChildren.add(NotificationEntryBuilder().build())
        bundle.addChild(groupEntry2)

        coordinator.bundleCountUpdater.onBeforeFinalizeFilter(listOf(bundle))
        assertThat(bundle.bundleRepository.numberOfChildren).isEqualTo(3)
    }

    @Test
    fun testTotalCount_addNotifAndGroup() {
        val bundle = BundleEntry(TEST_BUNDLE_SPEC)

        val groupEntry1 = GroupEntry("groupKey1", 0L)
        groupEntry1.rawChildren.add(NotificationEntryBuilder().build())
        bundle.addChild(groupEntry1)
        bundle.addChild(NotificationEntryBuilder().build())

        coordinator.bundleCountUpdater.onBeforeFinalizeFilter(listOf(bundle))
        assertThat(bundle.bundleRepository.numberOfChildren).isEqualTo(2)
    }

    @Test
    fun testTotalCount_removeNotif() {
        val bundle = BundleEntry(TEST_BUNDLE_SPEC)

        val directNotifChild = NotificationEntryBuilder().build()
        bundle.addChild(directNotifChild)

        val groupEntry1 = GroupEntry("groupKey1", 0L)
        groupEntry1.rawChildren.add(NotificationEntryBuilder().build())
        bundle.addChild(groupEntry1)

        coordinator.bundleCountUpdater.onBeforeFinalizeFilter(listOf(bundle))
        assertThat(bundle.bundleRepository.numberOfChildren).isEqualTo(2)

        bundle.removeChild(directNotifChild)

        coordinator.bundleCountUpdater.onBeforeFinalizeFilter(listOf(bundle))
        assertThat(bundle.bundleRepository.numberOfChildren).isEqualTo(1)
    }

    @Test
    fun testTotalCount_removeGroupChild() {
        val bundle = BundleEntry(TEST_BUNDLE_SPEC)
        val groupEntry1 = GroupEntry("key", 0)
        groupEntry1.rawChildren.add(NotificationEntryBuilder().build())
        bundle.addChild(groupEntry1)
        bundle.addChild(NotificationEntryBuilder().build())

        coordinator.bundleCountUpdater.onBeforeFinalizeFilter(listOf(bundle))
        assertThat(bundle.bundleRepository.numberOfChildren).isEqualTo(2)

        groupEntry1.rawChildren.clear()
        coordinator.bundleCountUpdater.onBeforeFinalizeFilter(listOf(bundle))
        assertThat(bundle.bundleRepository.numberOfChildren).isEqualTo(1)
    }

    @Test
    fun testTotalCount_clearChildren() {
        val bundle = BundleEntry(TEST_BUNDLE_SPEC)
        val groupEntry1 = GroupEntry("groupKey1", 0L)
        groupEntry1.rawChildren.add(NotificationEntryBuilder().build())
        bundle.addChild(groupEntry1)
        bundle.addChild(NotificationEntryBuilder().build())

        coordinator.bundleCountUpdater.onBeforeFinalizeFilter(listOf(bundle))
        assertThat(bundle.bundleRepository.numberOfChildren).isEqualTo(2)

        bundle.clearChildren()

        coordinator.bundleCountUpdater.onBeforeFinalizeFilter(listOf(bundle))
        assertThat(bundle.bundleRepository.numberOfChildren).isEqualTo(0)
    }

    @Test
    @EnableFlags(android.app.Flags.FLAG_NM_CONTEXTUAL_DISPLAY)
    fun testAddDynamicBundles() {
        val bundles = listOf(DynamicBundle(130, "Group Chats"), DynamicBundle(140, "Spoilers"))
        whenever(notificationManager.getDynamicBundles(any(), any())).thenReturn(bundles)

        executor.runAllReady()

        // 4 static bundles and 2 dynamic bundles
        assertThat(coordinator.bundler.bundleSpecs)
            .comparingElementsUsing<BundleSpec, Int>(
                Correspondence.transforming({ it?.bundleType }, "bundleType")
            )
            .containsExactly(1, 2, 3, 4, 130, 140)
    }

    @Test
    @EnableFlags(android.app.Flags.FLAG_NM_CONTEXTUAL_DISPLAY)
    fun testAddDynamicBundles_afterBoot() {
        val bundles =
            mutableListOf(DynamicBundle(130, "Group Chats"), DynamicBundle(140, "Spoilers"))
        whenever(notificationManager.getDynamicBundles(any(), any())).thenReturn(bundles)

        executor.runAllReady()

        val newBundle = DynamicBundle(150, "bundle!")
        bundles.add(newBundle)
        broadcastDispatcher.sendIntentToMatchingReceiversOnly(
            kosmos.applicationContext,
            Intent(ACTION_DYNAMIC_BUNDLE_MODIFIED).apply {
                putExtra(
                    EXTRA_DYNAMIC_BUNDLE_MODIFICATION_TYPE,
                    DYNAMIC_BUNDLE_MODIFICATION_TYPE_ADDED,
                )
                putExtra(EXTRA_DYNAMIC_BUNDLE, newBundle)
            },
        )

        // 4 static bundles and 3 dynamic bundles
        assertThat(coordinator.bundler.bundleSpecs)
            .comparingElementsUsing<BundleSpec, Int>(
                Correspondence.transforming({ it?.bundleType }, "bundleType")
            )
            .containsExactly(1, 2, 3, 4, 130, 140, 150)
    }

    @Test
    @EnableFlags(android.app.Flags.FLAG_NM_CONTEXTUAL_DISPLAY)
    fun testRemoveDynamicBundles() {
        val removable = DynamicBundle(140, "Spoilers")
        val bundles = mutableListOf(DynamicBundle(130, "Group Chats"), removable)
        whenever(notificationManager.getDynamicBundles(any(), any())).thenReturn(bundles)

        executor.runAllReady()

        broadcastDispatcher.sendIntentToMatchingReceiversOnly(
            kosmos.applicationContext,
            Intent(ACTION_DYNAMIC_BUNDLE_MODIFIED).apply {
                putExtra(
                    EXTRA_DYNAMIC_BUNDLE_MODIFICATION_TYPE,
                    DYNAMIC_BUNDLE_MODIFICATION_TYPE_REMOVED,
                )
                putExtra(EXTRA_DYNAMIC_BUNDLE, removable)
            },
        )

        // 4 static bundles and 1 dynamic bundle
        assertThat(coordinator.bundler.bundleSpecs)
            .comparingElementsUsing<BundleSpec, Int>(
                Correspondence.transforming({ it?.bundleType }, "bundleType")
            )
            .containsExactly(1, 2, 3, 4, 130)
    }

    private fun makeEntryOfChannelType(
        type: String,
        buildBlock: NotificationEntryBuilder.() -> Unit = {},
    ): NotificationEntry {
        val channel: NotificationChannel = NotificationChannel(type, type, 2)
        val entry =
            NotificationEntryBuilder()
                .updateRanking { it.setChannel(channel) }
                .also(buildBlock)
                .build()
        return entry
    }
}
