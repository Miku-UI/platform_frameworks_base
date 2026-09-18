/*
 * Copyright (C) 2020 The Android Open Source Project
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
import android.app.Notification.GROUP_ALERT_ALL
import android.app.Notification.GROUP_ALERT_SUMMARY
import android.app.NotificationChannel
import android.app.NotificationChannel.SYSTEM_RESERVED_IDS
import android.app.NotificationManager.IMPORTANCE_LOW
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.runTest
import com.android.systemui.log.logcatLogBuffer
import com.android.systemui.statusbar.NotificationRemoteInputManager
import com.android.systemui.statusbar.SbnBuilder
import com.android.systemui.statusbar.chips.notification.domain.interactor.statusBarNotificationChipsInteractor
import com.android.systemui.statusbar.chips.uievents.statusBarChipsUiEventLogger
import com.android.systemui.statusbar.notification.collection.GroupEntryBuilder
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.NotificationEntryBuilder
import com.android.systemui.statusbar.notification.collection.buildChildNotificationEntry
import com.android.systemui.statusbar.notification.collection.buildSummaryNotificationEntry
import com.android.systemui.statusbar.notification.collection.listbuilder.OnBeforeFinalizeFilterListener
import com.android.systemui.statusbar.notification.collection.listbuilder.OnBeforeTransformGroupsListener
import com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.NotifPromoter
import com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.NotifSectioner
import com.android.systemui.statusbar.notification.collection.makeClassifiedConversation
import com.android.systemui.statusbar.notification.collection.notifCollection
import com.android.systemui.statusbar.notification.collection.notifPipeline
import com.android.systemui.statusbar.notification.collection.notifcollection.NotifCollectionListener
import com.android.systemui.statusbar.notification.collection.notifcollection.NotifLifetimeExtender
import com.android.systemui.statusbar.notification.collection.notifcollection.NotifLifetimeExtender.OnEndLifetimeExtensionCallback
import com.android.systemui.statusbar.notification.collection.notifcollection.UpdateSource
import com.android.systemui.statusbar.notification.collection.provider.LaunchFullScreenIntentProvider
import com.android.systemui.statusbar.notification.headsup.OnHeadsUpChangedListener
import com.android.systemui.statusbar.notification.headsup.headsUpManager
import com.android.systemui.statusbar.notification.headsup.mockHeadsUpManager
import com.android.systemui.statusbar.notification.interruption.HeadsUpViewBinder
import com.android.systemui.statusbar.notification.interruption.NotificationInterruptStateProvider.FullScreenIntentDecision
import com.android.systemui.statusbar.notification.interruption.VisualInterruptionDecisionLogger
import com.android.systemui.statusbar.notification.interruption.VisualInterruptionDecisionProvider
import com.android.systemui.statusbar.notification.row.mockNotificationActionClickManager
import com.android.systemui.statusbar.notification.shared.LaunchNewFsiOnUpdate
import com.android.systemui.testKosmos
import com.android.systemui.util.concurrency.FakeExecutor
import com.android.systemui.util.mockito.withArgCaptor
import com.android.systemui.util.time.FakeSystemClock
import java.util.function.Consumer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.clearInvocations
import org.mockito.BDDMockito.given
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy

@SmallTest
@RunWith(AndroidJUnit4::class)
class HeadsUpCoordinatorTest : SysuiTestCase() {
    private val kosmos = testKosmos().apply { headsUpManager = mockHeadsUpManager }

    private val Kosmos.coordinator: HeadsUpCoordinator by Fixture {
        HeadsUpCoordinator(
            applicationCoroutineScope,
            HeadsUpCoordinatorLogger(logcatLogBuffer(), verbose = true),
            interruptLogger,
            systemClock,
            notifCollection,
            headsUpManager,
            headsUpViewBinder,
            visualInterruptionDecisionProvider,
            remoteInputManager,
            mockNotificationActionClickManager,
            launchFullScreenIntentProvider,
            statusBarNotificationChipsInteractor,
            statusBarChipsUiEventLogger,
            mock(),
            executor,
        )
    }

    // captured listeners and pluggables:
    private lateinit var collectionListener: NotifCollectionListener
    private lateinit var notifPromoter: NotifPromoter
    private lateinit var notifLifetimeExtender: NotifLifetimeExtender
    private lateinit var beforeTransformGroupsListener: OnBeforeTransformGroupsListener
    private lateinit var beforeFinalizeFilterListener: OnBeforeFinalizeFilterListener
    private lateinit var onHeadsUpChangedListener: OnHeadsUpChangedListener
    private lateinit var notifSectioner: NotifSectioner
    private lateinit var actionPressListener: Consumer<NotificationEntry>

    private val interruptLogger = spy(VisualInterruptionDecisionLogger(logcatLogBuffer()))
    private val headsUpViewBinder: HeadsUpViewBinder = mock()
    private val visualInterruptionDecisionProvider: VisualInterruptionDecisionProvider = mock()
    private val remoteInputManager: NotificationRemoteInputManager = mock()
    private val endLifetimeExtension: OnEndLifetimeExtensionCallback = mock()
    private val launchFullScreenIntentProvider: LaunchFullScreenIntentProvider = mock()

    private val Kosmos.entry: NotificationEntry by Fixture { NotificationEntryBuilder().build() }
    // Same summary we can use for either set of children
    private val Kosmos.groupSummary: NotificationEntry by Fixture {
        buildSummaryNotificationEntry {
            modifyNotification(context).setGroupAlertBehavior(GROUP_ALERT_ALL).setWhen(500)
        }
    }
    // One set of children with GROUP_ALERT_SUMMARY
    private val Kosmos.groupPriority: NotificationEntry by Fixture {
        buildChildNotificationEntry() {
            modifyNotification(context).setGroupAlertBehavior(GROUP_ALERT_SUMMARY).setWhen(400)
            updateSbn { setTag("priority") }
        }
    }
    private val Kosmos.groupSibling1: NotificationEntry by Fixture {
        buildChildNotificationEntry() {
            modifyNotification(context).setGroupAlertBehavior(GROUP_ALERT_SUMMARY).setWhen(300)
        }
    }
    private val Kosmos.groupSibling2: NotificationEntry by Fixture {
        buildChildNotificationEntry() {
            modifyNotification(context).setGroupAlertBehavior(GROUP_ALERT_SUMMARY).setWhen(200)
        }
    }
    // Another set of children with GROUP_ALERT_ALL
    private val Kosmos.groupChild1: NotificationEntry by Fixture {
        buildChildNotificationEntry() {
            modifyNotification(context).setGroupAlertBehavior(GROUP_ALERT_ALL).setWhen(350)
        }
    }
    private val Kosmos.groupChild2: NotificationEntry by Fixture {
        buildChildNotificationEntry() {
            modifyNotification(context).setGroupAlertBehavior(GROUP_ALERT_ALL).setWhen(250)
        }
    }
    private val Kosmos.groupChild3: NotificationEntry by Fixture {
        buildChildNotificationEntry() {
            modifyNotification(context).setGroupAlertBehavior(GROUP_ALERT_ALL).setWhen(150)
        }
    }
    private val Kosmos.systemClock: FakeSystemClock by Fixture { FakeSystemClock() }
    private val Kosmos.executor: FakeExecutor by Fixture { FakeExecutor(systemClock) }
    private val huns: ArrayList<NotificationEntry> = ArrayList()

    @Before
    fun setUp() =
        kosmos.run {
            coordinator.attach(notifPipeline)

            // capture arguments:
            collectionListener = withArgCaptor {
                verify(notifPipeline).addCollectionListener(capture())
            }
            notifPromoter = withArgCaptor { verify(notifPipeline).addPromoter(capture()) }
            notifLifetimeExtender = withArgCaptor {
                verify(notifPipeline).addNotificationLifetimeExtender(capture())
            }
            beforeTransformGroupsListener = withArgCaptor {
                verify(notifPipeline).addOnBeforeTransformGroupsListener(capture())
            }
            beforeFinalizeFilterListener = withArgCaptor {
                verify(notifPipeline).addOnBeforeFinalizeFilterListener(capture())
            }
            onHeadsUpChangedListener = withArgCaptor {
                verify(headsUpManager).addListener(capture())
            }
            actionPressListener = withArgCaptor {
                verify(kosmos.mockNotificationActionClickManager).addActionClickListener(capture())
            }
            given(headsUpManager.allEntries).willAnswer { huns.stream() }
            given(headsUpManager.isHeadsUpEntry(anyString())).willAnswer { invocation ->
                val key = invocation.getArgument<String>(0)
                huns.any { entry -> entry.key == key }
            }
            given(headsUpManager.canRemoveImmediately(anyString())).willAnswer { invocation ->
                val key = invocation.getArgument<String>(0)
                !huns.any { entry -> entry.key == key }
            }
            whenever(headsUpManager.getEarliestRemovalTime(anyString())).thenReturn(1000L)
            notifSectioner = coordinator.sectioner
            notifLifetimeExtender.setCallback(endLifetimeExtension)

            // Set the default HUN decision
            setDefaultShouldHeadsUp(false)

            // Set the default FSI decision
            setDefaultShouldFullScreen(FullScreenIntentDecision.NO_FULL_SCREEN_INTENT)
        }

    @Test
    fun testCancelStickyNotification() =
        kosmos.runTest {
            whenever(headsUpManager.isSticky(anyString())).thenReturn(true)
            addHUN(entry)
            whenever(headsUpManager.canRemoveImmediately(anyString())).thenReturn(false, true)
            whenever(headsUpManager.getEarliestRemovalTime(anyString())).thenReturn(1000L, 0L)
            assertTrue(notifLifetimeExtender.maybeExtendLifetime(entry, 0))
            executor.advanceClockToLast()
            executor.runAllReady()
            verify(headsUpManager, times(0)).removeNotification(anyString(), eq(false), anyString())
            verify(headsUpManager, times(1)).removeNotification(anyString(), eq(true), anyString())
        }

    @Test
    fun testCancelAndReAddStickyNotification() =
        kosmos.runTest {
            whenever(headsUpManager.isSticky(anyString())).thenReturn(true)
            addHUN(entry)
            whenever(headsUpManager.canRemoveImmediately(anyString()))
                .thenReturn(false, true, false)
            whenever(headsUpManager.getEarliestRemovalTime(anyString())).thenReturn(1000L)
            assertTrue(notifLifetimeExtender.maybeExtendLifetime(entry, 0))
            addHUN(entry)
            assertFalse(notifLifetimeExtender.maybeExtendLifetime(entry, 0))
            executor.advanceClockToLast()
            executor.runAllReady()
            assertTrue(notifLifetimeExtender.maybeExtendLifetime(entry, 0))
            verify(headsUpManager, times(0)).removeNotification(anyString(), eq(false), anyString())
            verify(headsUpManager, times(0)).removeNotification(anyString(), eq(true), anyString())
        }

    @Test
    fun hunNotRemovedWhenExtensionCancelled() =
        kosmos.runTest {
            whenever(headsUpManager.isSticky(anyString())).thenReturn(true)
            addHUN(entry)
            whenever(headsUpManager.canRemoveImmediately(anyString())).thenReturn(false)
            whenever(headsUpManager.getEarliestRemovalTime(anyString())).thenReturn(1000L)
            assertTrue(notifLifetimeExtender.maybeExtendLifetime(entry, 0))
            notifLifetimeExtender.cancelLifetimeExtension(entry)
            executor.advanceClockToLast()
            executor.runAllReady()
            verify(headsUpManager, never()).removeNotification(anyString(), any(), anyString())
        }

    @Test
    fun actionPressCancelsExistingLifetimeExtension() =
        kosmos.runTest {
            whenever(headsUpManager.isSticky(anyString())).thenReturn(true)
            addHUN(entry)

            whenever(headsUpManager.canRemoveImmediately(anyString())).thenReturn(false)
            whenever(headsUpManager.getEarliestRemovalTime(anyString())).thenReturn(1000L)
            assertTrue(notifLifetimeExtender.maybeExtendLifetime(entry, /* reason= */ 0))

            actionPressListener.accept(entry)
            executor.runAllReady()
            verify(endLifetimeExtension, times(1))
                .onEndLifetimeExtension(notifLifetimeExtender, entry)

            collectionListener.onEntryRemoved(entry, /* reason= */ 0)
            verify(headsUpManager, times(1)).removeNotification(eq(entry.key), any(), anyString())
        }

    @Test
    fun actionPressPreventsFutureLifetimeExtension() =
        kosmos.runTest {
            whenever(headsUpManager.isSticky(anyString())).thenReturn(true)
            addHUN(entry)

            actionPressListener.accept(entry)
            verify(headsUpManager, times(1)).setUserActionMayIndirectlyRemove(entry.key)

            whenever(headsUpManager.canRemoveImmediately(anyString())).thenReturn(true)
            assertFalse(notifLifetimeExtender.maybeExtendLifetime(entry, 0))

            collectionListener.onEntryRemoved(entry, /* reason= */ 0)
            verify(headsUpManager, times(1)).removeNotification(eq(entry.key), any(), anyString())
        }

    @Test
    fun testCancelUpdatedStickyNotification() =
        kosmos.runTest {
            whenever(headsUpManager.isSticky(anyString())).thenReturn(true)
            addHUN(entry)
            whenever(headsUpManager.getEarliestRemovalTime(anyString())).thenReturn(1000L, 500L)
            assertTrue(notifLifetimeExtender.maybeExtendLifetime(entry, 0))
            addHUN(entry)
            executor.advanceClockToLast()
            executor.runAllReady()
            verify(headsUpManager, never()).removeNotification(anyString(), eq(false), anyString())
            verify(headsUpManager, never()).removeNotification(anyString(), eq(true), anyString())
        }

    @Test
    fun testCancelNotification() =
        kosmos.runTest {
            whenever(headsUpManager.isSticky(anyString())).thenReturn(false)
            addHUN(entry)
            whenever(headsUpManager.getEarliestRemovalTime(anyString())).thenReturn(1000L, 500L)
            assertTrue(notifLifetimeExtender.maybeExtendLifetime(entry, 0))
            executor.advanceClockToLast()
            executor.runAllReady()
            verify(headsUpManager, times(1)).removeNotification(anyString(), eq(false), anyString())
            verify(headsUpManager, never()).removeNotification(anyString(), eq(true), anyString())
        }

    @Test
    fun testOnEntryAdded_shouldFullScreen() =
        kosmos.runTest {
            setShouldFullScreen(entry, FullScreenIntentDecision.FSI_KEYGUARD_SHOWING)
            collectionListener.onEntryAdded(entry)
            verify(launchFullScreenIntentProvider).launchFullScreenIntent(entry)
        }

    @Test
    fun testOnEntryAdded_shouldNotFullScreen() =
        kosmos.runTest {
            setShouldFullScreen(entry, FullScreenIntentDecision.NO_FULL_SCREEN_INTENT)
            collectionListener.onEntryAdded(entry)
            verify(launchFullScreenIntentProvider, never()).launchFullScreenIntent(any())
        }

    @Test
    fun testPromotesAddedHUN() =
        kosmos.runTest {
            // GIVEN the current entry should heads up
            setShouldHeadsUp(entry, true)

            // WHEN the notification is added but not yet binding
            collectionListener.onEntryAdded(entry)
            verify(headsUpViewBinder, never()).bindHeadsUpView(eq(entry), any(), any())

            // THEN only promote mEntry
            assertTrue(notifPromoter.shouldPromoteToTopLevel(entry))
        }

    @Test
    fun testPromotesBindingHUN() =
        kosmos.runTest {
            // GIVEN the current entry should heads up
            setShouldHeadsUp(entry, true)

            // WHEN the notification started binding on the previous run
            collectionListener.onEntryAdded(entry)
            beforeTransformGroupsListener.onBeforeTransformGroups(listOf(entry))
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(listOf(entry))
            verify(headsUpViewBinder).bindHeadsUpView(eq(entry), eq(false), any())

            // THEN only promote mEntry
            assertTrue(notifPromoter.shouldPromoteToTopLevel(entry))
        }

    @Test
    fun testPromotesCurrentHUN() =
        kosmos.runTest {
            // GIVEN the current HUN is set to mEntry
            addHUN(entry)

            // THEN only promote the current HUN, mEntry
            assertTrue(notifPromoter.shouldPromoteToTopLevel(entry))
            val testPackage2 = NotificationEntryBuilder().setPkg("test-package2").build()
            assertFalse(notifPromoter.shouldPromoteToTopLevel(testPackage2))
        }

    @Test
    fun testHeadsUpSectioner_accepts_currentHUN() =
        kosmos.runTest {
            // GIVEN the current HUN is set to mEntry
            addHUN(entry)

            // THEN only section the current HUN, mEntry
            assertTrue(notifSectioner.isInSection(entry))
            assertFalse(
                notifSectioner.isInSection(
                    NotificationEntryBuilder().setPkg("test-package").build()
                )
            )
        }

    @Test
    fun testHeadsUpSectioner_rejects_classifiedConversation() =
        kosmos.runTest {
            for (id in SYSTEM_RESERVED_IDS) {
                assertFalse(notifSectioner.isInSection(kosmos.makeClassifiedConversation(id)))
            }
        }

    @Test
    fun testLifetimeExtendsCurrentHUN() =
        kosmos.runTest {
            // GIVEN there is a HUN, mEntry
            addHUN(entry)

            // THEN only the current HUN, mEntry, should be lifetimeExtended
            assertTrue(notifLifetimeExtender.maybeExtendLifetime(entry, /* cancellationReason */ 0))
            assertFalse(
                notifLifetimeExtender.maybeExtendLifetime(
                    NotificationEntryBuilder().setPkg("test-package").build(),
                    /* reason= */ 0,
                )
            )
        }

    @Test
    fun testShowHUNOnInflationFinished() =
        kosmos.runTest {
            // WHEN a notification should HUN and its inflation is finished
            setShouldHeadsUp(entry, true)

            collectionListener.onEntryAdded(entry)
            beforeTransformGroupsListener.onBeforeTransformGroups(listOf(entry))
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(listOf(entry))

            finishBind(entry)

            // THEN we tell the HeadsUpManager to show the notification
            verify(headsUpManager).showNotification(entry)
        }

    @Test
    fun testNoHUNOnInflationFinished() =
        kosmos.runTest {
            // WHEN a notification shouldn't HUN and its inflation is finished
            setShouldHeadsUp(entry, false)
            collectionListener.onEntryAdded(entry)
            beforeTransformGroupsListener.onBeforeTransformGroups(listOf(entry))
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(listOf(entry))

            // THEN we never bind the heads up view or tell HeadsUpManager to show the notification
            verify(headsUpViewBinder, never()).bindHeadsUpView(eq(entry), any(), any())
            verify(headsUpManager, never()).showNotification(eq(entry), any())
        }

    @Test
    fun testOnEntryUpdated_toAlert() =
        kosmos.runTest {
            // GIVEN that an entry is posted that should not heads up
            setShouldHeadsUp(entry, false)
            collectionListener.onEntryAdded(entry)

            // WHEN it's updated to heads up
            setShouldHeadsUp(entry)
            collectionListener.onEntryUpdated(entry)
            beforeTransformGroupsListener.onBeforeTransformGroups(listOf(entry))
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(listOf(entry))

            // THEN the notification alerts
            finishBind(entry)
            verify(headsUpManager).showNotification(entry)
        }

    @Test
    fun testOnEntryUpdated_toNotAlert() =
        kosmos.runTest {
            // GIVEN that an entry is posted that should heads up
            setShouldHeadsUp(entry)
            collectionListener.onEntryAdded(entry)

            // WHEN it's updated to not heads up
            setShouldHeadsUp(entry, false)
            collectionListener.onEntryUpdated(entry)
            beforeTransformGroupsListener.onBeforeTransformGroups(listOf(entry))
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(listOf(entry))

            // THEN the notification is never bound or shown
            verify(headsUpViewBinder, never()).bindHeadsUpView(any(), any(), any())
            verify(headsUpManager, never()).showNotification(any(), any())
        }

    @Test
    @EnableFlags(android.service.notification.Flags.FLAG_NOTIFICATION_SILENT_FLAG)
    fun testOnEntryUpdated_toSilentByApp_removesHun() =
        kosmos.runTest {
            // GIVEN that an entry is posted that should heads up
            setShouldHeadsUp(entry)
            addHUN(entry)

            // WHEN it's updated to silent by the app, and has been visible long enough to remove.
            entry.sbn.notification.flags = entry.sbn.notification.flags or Notification.FLAG_SILENT
            whenever(headsUpManager.canRemoveImmediately(anyString())).thenReturn(true)
            setShouldHeadsUp(entry, false)
            collectionListener.onEntryUpdated(entry)
            beforeTransformGroupsListener.onBeforeTransformGroups(listOf(entry))
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(listOf(entry))

            // Ensure deferred mutations can run
            executor.advanceClockToLast()
            executor.runAllReady()

            // THEN the notification is never bound and is removed
            verify(headsUpViewBinder, never()).bindHeadsUpView(any(), any(), any())
            verify(headsUpManager, never()).showNotification(any(), any())
            verify(headsUpManager).removeNotification(eq(entry.key), any(), any())
        }

    @Test
    @EnableFlags(android.service.notification.Flags.FLAG_NOTIFICATION_SILENT_FLAG)
    fun testOnEntryUpdated_toSilentBySystem_doesNotRemoveHun() =
        kosmos.runTest {
            // GIVEN that an entry is posted that should heads up
            setShouldHeadsUp(entry)
            addHUN(entry)

            // WHEN it's updated to silent by system, and has been visible long enough to remove.
            entry.sbn.notification.flags = entry.sbn.notification.flags or Notification.FLAG_SILENT
            whenever(headsUpManager.canRemoveImmediately(anyString())).thenReturn(true)
            setShouldHeadsUp(entry, false)
            collectionListener.onEntryUpdated(entry, UpdateSource.SystemServer)
            beforeTransformGroupsListener.onBeforeTransformGroups(listOf(entry))
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(listOf(entry))

            // Ensure deferred mutations can run
            executor.advanceClockToLast()
            executor.runAllReady()

            // THEN the notification is never bound or removed
            verify(headsUpViewBinder, never()).bindHeadsUpView(any(), any(), any())
            verify(headsUpManager, never()).showNotification(any(), any())
            verify(headsUpManager, never()).removeNotification(any(), any(), any())
        }

    @Test
    fun testOnEntryRemovedRemovesHeadsUpNotification() =
        kosmos.runTest {
            // GIVEN the current HUN is mEntry
            addHUN(entry)

            // WHEN mEntry is removed from the notification collection
            collectionListener.onEntryRemoved(entry, /* cancellation reason */ 0)
            whenever(remoteInputManager.isSpinning(any())).thenReturn(false)

            // THEN heads up manager should remove the entry
            verify(headsUpManager).removeNotification(eq(entry.key), eq(false), anyString())
        }

    private fun Kosmos.addHUN(entry: NotificationEntry) {
        huns.add(entry)
        whenever(headsUpManager.getTopEntry()).thenReturn(entry)
        onHeadsUpChangedListener.onHeadsUpStateChanged(entry, true)
        notifLifetimeExtender.cancelLifetimeExtension(entry)
    }

    @Test
    fun onPromotedNotificationChipTapped_hasNotifEntry_shownAsHUN() =
        kosmos.runTest {
            whenever(notifCollection.getEntry(entry.key)).thenReturn(entry)

            statusBarNotificationChipsInteractor.onPromotedNotificationChipTapped(entry.key)
            executor.advanceClockToLast()
            executor.runAllReady()
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(listOf(entry))

            finishBind(entry)
            verify(headsUpManager).showNotification(entry, isFromUserOpenAction = true)
        }

    @Test
    fun onPromotedNotificationChipTapped_noNotifEntry_noHUN() =
        kosmos.runTest {
            whenever(notifCollection.getEntry(entry.key)).thenReturn(null)

            statusBarNotificationChipsInteractor.onPromotedNotificationChipTapped(entry.key)
            executor.advanceClockToLast()
            executor.runAllReady()
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(listOf(entry))

            verify(headsUpViewBinder, never()).bindHeadsUpView(eq(entry), any(), any())
            verify(headsUpManager, never()).showNotification(eq(entry), any())
        }

    @Test
    fun onPromotedNotificationChipTapped_shownAsHUNEvenIfEntryShouldNot() =
        kosmos.runTest {
            whenever(notifCollection.getEntry(entry.key)).thenReturn(entry)

            // First, add the entry as shouldn't HUN
            setShouldHeadsUp(entry, false)
            collectionListener.onEntryAdded(entry)
            beforeTransformGroupsListener.onBeforeTransformGroups(listOf(entry))
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(listOf(entry))

            // WHEN that entry becomes a promoted notification and is tapped
            statusBarNotificationChipsInteractor.onPromotedNotificationChipTapped(entry.key)
            executor.advanceClockToLast()
            executor.runAllReady()
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(listOf(entry))

            // THEN it's still shown as heads up
            finishBind(entry)
            verify(headsUpManager).showNotification(entry, isFromUserOpenAction = true)
        }

    @Test
    fun onPromotedNotificationChipTapped_atSameTimeAsOnAdded_promotedShownAsHUN() =
        kosmos.runTest {
            // First, the promoted notification appears as not heads up
            val promotedEntry = NotificationEntryBuilder().setPkg("promotedPackage").build()
            whenever(notifCollection.getEntry(promotedEntry.key)).thenReturn(promotedEntry)
            setShouldHeadsUp(promotedEntry, false)

            collectionListener.onEntryAdded(promotedEntry)
            beforeTransformGroupsListener.onBeforeTransformGroups(listOf(promotedEntry))
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(listOf(promotedEntry))

            verify(headsUpViewBinder, never()).bindHeadsUpView(eq(promotedEntry), any(), any())
            verify(headsUpManager, never()).showNotification(eq(promotedEntry), any())

            // Then a new notification comes in that should be heads up
            setShouldHeadsUp(entry, false)
            whenever(notifCollection.getEntry(entry.key)).thenReturn(entry)
            collectionListener.onEntryAdded(entry)

            // At the same time, the promoted notification chip is tapped
            statusBarNotificationChipsInteractor.onPromotedNotificationChipTapped(promotedEntry.key)
            executor.advanceClockToLast()
            executor.runAllReady()

            // WHEN we finalize the pipeline
            beforeTransformGroupsListener.onBeforeTransformGroups(listOf(promotedEntry, entry))
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(listOf(promotedEntry, entry))

            // THEN the promoted entry is shown as a HUN, *not* the new entry
            finishBind(promotedEntry)
            verify(headsUpManager).showNotification(promotedEntry, isFromUserOpenAction = true)

            verify(headsUpViewBinder, never()).bindHeadsUpView(eq(entry), any(), any())
            verify(headsUpManager, never()).showNotification(eq(entry), any())
        }

    @Test
    fun onPromotedNotificationChipTapped_chipTappedTwice_hunHiddenOnSecondTapImmediately() =
        kosmos.runTest {
            whenever(notifCollection.getEntry(entry.key)).thenReturn(entry)

            // WHEN chip tapped first
            statusBarNotificationChipsInteractor.onPromotedNotificationChipTapped(entry.key)
            executor.advanceClockToLast()
            executor.runAllReady()
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(listOf(entry))

            // THEN HUN is shown
            finishBind(entry)
            verify(headsUpManager).showNotification(entry, isFromUserOpenAction = true)
            addHUN(entry)

            // WHEN chip is tapped again
            statusBarNotificationChipsInteractor.onPromotedNotificationChipTapped(entry.key)
            executor.advanceClockToLast()
            executor.runAllReady()
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(listOf(entry))

            // THEN HUN is hidden and it's hidden immediately
            verify(headsUpManager)
                .removeNotification(eq(entry.key), /* releaseImmediately= */ eq(true), any())
        }

    @Test
    fun testTransferIsolatedChildAlert_withGroupAlertSummary() =
        kosmos.runTest {
            setShouldHeadsUp(groupSummary)
            setAllNotifs(listOf(groupSummary, groupSibling1))

            collectionListener.onEntryAdded(groupSummary)
            collectionListener.onEntryAdded(groupSibling1)
            beforeTransformGroupsListener.onBeforeTransformGroups(listOf(groupSibling1))
            verify(headsUpViewBinder, never()).bindHeadsUpView(any(), any(), any())
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(listOf(groupSibling1))

            verify(headsUpViewBinder, never()).bindHeadsUpView(eq(groupSummary), any(), any())
            finishBind(groupSibling1)

            verify(headsUpManager, never()).showNotification(groupSummary)
            verify(headsUpManager).showNotification(groupSibling1)

            // In addition make sure we have explicitly marked the summary as having interrupted due
            // to the alert being transferred
            assertTrue(groupSummary.hasInterrupted())
        }

    @Test
    fun testTransferIsolatedChildAlert_withGroupAlertAll() =
        kosmos.runTest {
            setShouldHeadsUp(groupSummary)
            setAllNotifs(listOf(groupSummary, groupChild1))

            collectionListener.onEntryAdded(groupSummary)
            collectionListener.onEntryAdded(groupChild1)
            beforeTransformGroupsListener.onBeforeTransformGroups(listOf(groupChild1))
            verify(headsUpViewBinder, never()).bindHeadsUpView(any(), any(), any())
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(listOf(groupChild1))

            verify(headsUpViewBinder, never()).bindHeadsUpView(eq(groupSummary), any(), any())
            finishBind(groupChild1)

            verify(headsUpManager, never()).showNotification(groupSummary)
            verify(headsUpManager).showNotification(groupChild1)
            assertTrue(groupSummary.hasInterrupted())
        }

    @Test
    fun testTransferTwoIsolatedChildAlert_withGroupAlertSummary() =
        kosmos.runTest {
            // WHEN a notification should HUN and its inflation is finished
            setShouldHeadsUp(groupSummary)
            setAllNotifs(listOf(groupSummary, groupSibling1, groupSibling2, groupPriority))

            collectionListener.onEntryAdded(groupSummary)
            collectionListener.onEntryAdded(groupSibling1)
            collectionListener.onEntryAdded(groupSibling2)
            val entryList = listOf(groupSibling1, groupSibling2)
            beforeTransformGroupsListener.onBeforeTransformGroups(entryList)
            verify(headsUpViewBinder, never()).bindHeadsUpView(any(), any(), any())
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(entryList)

            verify(headsUpViewBinder, never()).bindHeadsUpView(eq(groupSummary), any(), any())
            finishBind(groupSibling1)
            verify(headsUpViewBinder, never()).bindHeadsUpView(eq(groupSibling2), any(), any())

            // THEN we tell the HeadsUpManager to show the notification
            verify(headsUpManager, never()).showNotification(groupSummary)
            verify(headsUpManager).showNotification(groupSibling1)
            verify(headsUpManager, never()).showNotification(groupSibling2)
            assertTrue(groupSummary.hasInterrupted())
        }

    @Test
    fun testTransferTwoIsolatedChildAlert_withGroupAlertAll() =
        kosmos.runTest {
            // WHEN a notification should HUN and its inflation is finished
            setShouldHeadsUp(groupSummary)
            setAllNotifs(listOf(groupSummary, groupChild1, groupChild2, groupPriority))

            collectionListener.onEntryAdded(groupSummary)
            collectionListener.onEntryAdded(groupChild1)
            collectionListener.onEntryAdded(groupChild2)
            val entryList = listOf(groupChild1, groupChild2)
            beforeTransformGroupsListener.onBeforeTransformGroups(entryList)
            verify(headsUpViewBinder, never()).bindHeadsUpView(any(), any(), any())
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(entryList)

            verify(headsUpViewBinder, never()).bindHeadsUpView(eq(groupSummary), any(), any())
            finishBind(groupChild1)
            verify(headsUpViewBinder, never()).bindHeadsUpView(eq(groupChild2), any(), any())

            // THEN we tell the HeadsUpManager to show the notification
            verify(headsUpManager, never()).showNotification(groupSummary)
            verify(headsUpManager).showNotification(groupChild1)
            verify(headsUpManager, never()).showNotification(groupChild2)
            assertTrue(groupSummary.hasInterrupted())
        }

    @Test
    fun testTransferToPriorityOnAddWithTwoSiblings() =
        kosmos.runTest {
            // WHEN a notification should HUN and its inflation is finished
            setShouldHeadsUp(groupSummary)
            setAllNotifs(listOf(groupSummary, groupSibling1, groupSibling2, groupPriority))

            collectionListener.onEntryAdded(groupSummary)
            collectionListener.onEntryAdded(groupPriority)
            collectionListener.onEntryAdded(groupSibling1)
            collectionListener.onEntryAdded(groupSibling2)

            val beforeTransformGroup =
                GroupEntryBuilder()
                    .setSummary(groupSummary)
                    .setChildren(listOf(groupSibling1, groupPriority, groupSibling2))
                    .build()
            // beforeTransformGroupsListener.onBeforeTransformGroups(listOf(beforeTransformGroup))
            // verify(headsUpViewBinder, never()).bindHeadsUpView(any(), any(), any())

            val afterTransformGroup =
                GroupEntryBuilder()
                    .setSummary(groupSummary)
                    .setChildren(listOf(groupSibling1, groupSibling2))
                    .build()
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(
                listOf(groupPriority, afterTransformGroup)
            )

            verify(headsUpViewBinder, never()).bindHeadsUpView(eq(groupSummary), any(), any())
            finishBind(groupPriority)
            verify(headsUpViewBinder, never()).bindHeadsUpView(eq(groupSibling1), any(), any())
            verify(headsUpViewBinder, never()).bindHeadsUpView(eq(groupSibling2), any(), any())

            // THEN we tell the HeadsUpManager to show the notification
            verify(headsUpManager, never()).showNotification(groupSummary)
            verify(headsUpManager).showNotification(groupPriority)
            verify(headsUpManager, never()).showNotification(groupSibling1)
            verify(headsUpManager, never()).showNotification(groupSibling2)
            assertTrue(groupSummary.hasInterrupted())
        }

    @Test
    fun testTransferToPriorityOnUpdateWithTwoSiblings() =
        kosmos.runTest {
            setShouldHeadsUp(groupSummary)
            setAllNotifs(listOf(groupSummary, groupSibling1, groupSibling2, groupPriority))

            collectionListener.onEntryUpdated(groupSummary)
            collectionListener.onEntryUpdated(groupPriority)
            collectionListener.onEntryUpdated(groupSibling1)
            collectionListener.onEntryUpdated(groupSibling2)

            val beforeTransformGroup =
                GroupEntryBuilder()
                    .setSummary(groupSummary)
                    .setChildren(listOf(groupSibling1, groupPriority, groupSibling2))
                    .build()
            beforeTransformGroupsListener.onBeforeTransformGroups(listOf(beforeTransformGroup))
            verify(headsUpViewBinder, never()).bindHeadsUpView(any(), any(), any())

            val afterTransformGroup =
                GroupEntryBuilder()
                    .setSummary(groupSummary)
                    .setChildren(listOf(groupSibling1, groupSibling2))
                    .build()
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(
                listOf(groupPriority, afterTransformGroup)
            )

            verify(headsUpViewBinder, never()).bindHeadsUpView(eq(groupSummary), any(), any())
            finishBind(groupPriority)
            verify(headsUpViewBinder, never()).bindHeadsUpView(eq(groupSibling1), any(), any())
            verify(headsUpViewBinder, never()).bindHeadsUpView(eq(groupSibling2), any(), any())

            verify(headsUpManager, never()).showNotification(groupSummary)
            verify(headsUpManager).showNotification(groupPriority)
            verify(headsUpManager, never()).showNotification(groupSibling1)
            verify(headsUpManager, never()).showNotification(groupSibling2)
            assertTrue(groupSummary.hasInterrupted())
        }

    @Test
    fun testTransferToPriorityOnUpdateWithTwoNonUpdatedSiblings() =
        kosmos.runTest {
            setShouldHeadsUp(groupSummary)
            setAllNotifs(listOf(groupSummary, groupSibling1, groupSibling2, groupPriority))

            collectionListener.onEntryUpdated(groupSummary)
            collectionListener.onEntryUpdated(groupPriority)

            val beforeTransformGroup =
                GroupEntryBuilder()
                    .setSummary(groupSummary)
                    .setChildren(listOf(groupSibling1, groupPriority, groupSibling2))
                    .build()
            beforeTransformGroupsListener.onBeforeTransformGroups(listOf(beforeTransformGroup))
            verify(headsUpViewBinder, never()).bindHeadsUpView(any(), any(), any())

            val afterTransformGroup =
                GroupEntryBuilder()
                    .setSummary(groupSummary)
                    .setChildren(listOf(groupSibling1, groupSibling2))
                    .build()
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(
                listOf(groupPriority, afterTransformGroup)
            )

            verify(headsUpViewBinder, never()).bindHeadsUpView(eq(groupSummary), any(), any())
            finishBind(groupPriority)
            verify(headsUpViewBinder, never()).bindHeadsUpView(eq(groupSibling1), any(), any())
            verify(headsUpViewBinder, never()).bindHeadsUpView(eq(groupSibling2), any(), any())

            verify(headsUpManager, never()).showNotification(groupSummary)
            verify(headsUpManager).showNotification(groupPriority)
            verify(headsUpManager, never()).showNotification(groupSibling1)
            verify(headsUpManager, never()).showNotification(groupSibling2)
            assertTrue(groupSummary.hasInterrupted())
        }

    @Test
    fun testNoTransferToPriorityOnUpdateOfTwoSiblings() =
        kosmos.runTest {
            setShouldHeadsUp(groupSummary)
            setAllNotifs(listOf(groupSummary, groupSibling1, groupSibling2, groupPriority))

            collectionListener.onEntryUpdated(groupSummary)
            collectionListener.onEntryUpdated(groupSibling1)
            collectionListener.onEntryUpdated(groupSibling2)

            val beforeTransformGroup =
                GroupEntryBuilder()
                    .setSummary(groupSummary)
                    .setChildren(listOf(groupSibling1, groupPriority, groupSibling2))
                    .build()
            beforeTransformGroupsListener.onBeforeTransformGroups(listOf(beforeTransformGroup))
            verify(headsUpViewBinder, never()).bindHeadsUpView(any(), any(), any())

            val afterTransformGroup =
                GroupEntryBuilder()
                    .setSummary(groupSummary)
                    .setChildren(listOf(groupSibling1, groupSibling2))
                    .build()
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(
                listOf(groupPriority, afterTransformGroup)
            )

            finishBind(groupSummary)
            verify(headsUpViewBinder, never()).bindHeadsUpView(eq(groupPriority), any(), any())
            verify(headsUpViewBinder, never()).bindHeadsUpView(eq(groupSibling1), any(), any())
            verify(headsUpViewBinder, never()).bindHeadsUpView(eq(groupSibling2), any(), any())

            verify(headsUpManager).showNotification(groupSummary)
            verify(headsUpManager, never()).showNotification(groupPriority)
            verify(headsUpManager, never()).showNotification(groupSibling1)
            verify(headsUpManager, never()).showNotification(groupSibling2)
        }

    @Test
    fun testNoTransferTwoChildAlert_withGroupAlertSummary() =
        kosmos.runTest {
            setShouldHeadsUp(groupSummary)
            setAllNotifs(listOf(groupSummary, groupSibling1, groupSibling2))

            collectionListener.onEntryAdded(groupSummary)
            collectionListener.onEntryAdded(groupSibling1)
            collectionListener.onEntryAdded(groupSibling2)
            val groupEntry =
                GroupEntryBuilder()
                    .setSummary(groupSummary)
                    .setChildren(listOf(groupSibling1, groupSibling2))
                    .build()
            beforeTransformGroupsListener.onBeforeTransformGroups(listOf(groupEntry))
            verify(headsUpViewBinder, never()).bindHeadsUpView(any(), any(), any())
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(listOf(groupEntry))

            finishBind(groupSummary)
            verify(headsUpViewBinder, never()).bindHeadsUpView(eq(groupSibling1), any(), any())
            verify(headsUpViewBinder, never()).bindHeadsUpView(eq(groupSibling2), any(), any())

            verify(headsUpManager).showNotification(groupSummary)
            verify(headsUpManager, never()).showNotification(groupSibling1)
            verify(headsUpManager, never()).showNotification(groupSibling2)
        }

    @Test
    fun testNoTransferTwoChildAlert_withGroupAlertAll() =
        kosmos.runTest {
            setShouldHeadsUp(groupSummary)
            setAllNotifs(listOf(groupSummary, groupChild1, groupChild2))

            collectionListener.onEntryAdded(groupSummary)
            collectionListener.onEntryAdded(groupChild1)
            collectionListener.onEntryAdded(groupChild2)
            val groupEntry =
                GroupEntryBuilder()
                    .setSummary(groupSummary)
                    .setChildren(listOf(groupChild1, groupChild2))
                    .build()
            beforeTransformGroupsListener.onBeforeTransformGroups(listOf(groupEntry))
            verify(headsUpViewBinder, never()).bindHeadsUpView(any(), any(), any())
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(listOf(groupEntry))

            finishBind(groupSummary)
            verify(headsUpViewBinder, never()).bindHeadsUpView(eq(groupChild1), any(), any())
            verify(headsUpViewBinder, never()).bindHeadsUpView(eq(groupChild2), any(), any())

            verify(headsUpManager).showNotification(groupSummary)
            verify(headsUpManager, never()).showNotification(groupChild1)
            verify(headsUpManager, never()).showNotification(groupChild2)
        }

    @Test
    fun testNoTransfer_groupSummaryNotAlerting() =
        kosmos.runTest {
            // When we have a group where the summary should not alert and exactly one child should
            // alert, we should never mark the group summary as interrupted (because it doesn't).
            setShouldHeadsUp(groupSummary, false)
            setShouldHeadsUp(groupChild1, true)
            setShouldHeadsUp(groupChild2, false)

            collectionListener.onEntryAdded(groupSummary)
            collectionListener.onEntryAdded(groupChild1)
            collectionListener.onEntryAdded(groupChild2)
            val groupEntry =
                GroupEntryBuilder()
                    .setSummary(groupSummary)
                    .setChildren(listOf(groupChild1, groupChild2))
                    .build()
            beforeTransformGroupsListener.onBeforeTransformGroups(listOf(groupEntry))
            verify(headsUpViewBinder, never()).bindHeadsUpView(any(), any(), any())
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(listOf(groupEntry))

            verify(headsUpViewBinder, never()).bindHeadsUpView(eq(groupSummary), any(), any())
            finishBind(groupChild1)
            verify(headsUpViewBinder, never()).bindHeadsUpView(eq(groupChild2), any(), any())

            verify(headsUpManager, never()).showNotification(groupSummary)
            verify(headsUpManager).showNotification(groupChild1)
            verify(headsUpManager, never()).showNotification(groupChild2)
            assertFalse(groupSummary.hasInterrupted())
        }

    private fun Kosmos.helpTestNoTransferToBundleChildForChannel(channelId: String) {
        // Set up for normal alert transfer from summary to child
        // but here child is classified so it should not happen
        val bundleChild =
            buildChildNotificationEntry() {
                modifyNotification(context).setGroupAlertBehavior(GROUP_ALERT_SUMMARY)
                setChannel(NotificationChannel(channelId, channelId, IMPORTANCE_LOW))
            }
        setShouldHeadsUp(bundleChild, true)
        setShouldHeadsUp(groupSummary, true)
        setAllNotifs(listOf(groupSummary, bundleChild))

        collectionListener.onEntryAdded(groupSummary)
        collectionListener.onEntryAdded(bundleChild)

        beforeTransformGroupsListener.onBeforeTransformGroups(listOf(groupSummary, bundleChild))
        beforeFinalizeFilterListener.onBeforeFinalizeFilter(listOf(groupSummary, bundleChild))

        verify(headsUpViewBinder, never()).bindHeadsUpView(eq(groupSummary), any(), any())
        verify(headsUpViewBinder, never()).bindHeadsUpView(eq(bundleChild), any(), any())

        verify(headsUpManager, never()).showNotification(groupSummary)
        verify(headsUpManager, never()).showNotification(bundleChild)

        // Capture last param
        val decision =
            withArgCaptor<VisualInterruptionDecisionProvider.Decision> {
                verify(interruptLogger).logDecision(any(), any(), capture())
            }
        assertFalse(decision.shouldInterrupt)
        assertEquals(decision.logReason, "disqualified-transfer-target")

        // Must clear invocations, otherwise these calls get stored for the next call from the same
        // test, which complains that there are more invocations than expected
        clearInvocations(interruptLogger)
    }

    @Test
    fun testNoTransfer_toBundleChild() =
        kosmos.runTest {
            for (id in SYSTEM_RESERVED_IDS) {
                helpTestNoTransferToBundleChildForChannel(id)
            }
        }

    @Test
    fun testOnRankingApplied_newEntryShouldAlert() =
        kosmos.runTest {
            // GIVEN that mEntry has never interrupted in the past, and now should
            // and is new enough to do so
            assertFalse(entry.hasInterrupted())
            coordinator.setUpdateTime(entry, systemClock.currentTimeMillis())
            setShouldHeadsUp(entry)
            setAllNotifs(listOf(entry))

            // WHEN a ranking applied update occurs
            collectionListener.onRankingApplied()
            beforeTransformGroupsListener.onBeforeTransformGroups(listOf(entry))
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(listOf(entry))

            // THEN the notification is shown
            finishBind(entry)
            verify(headsUpManager).showNotification(entry, isFromUserOpenAction = false)
        }

    @Test
    fun testOnRankingApplied_alreadyAlertedEntryShouldNotAlertAgain() =
        kosmos.runTest {
            // GIVEN that mEntry has alerted in the past, even if it's new
            entry.setInterruption()
            coordinator.setUpdateTime(entry, systemClock.currentTimeMillis())
            setShouldHeadsUp(entry)
            setAllNotifs(listOf(entry))

            // WHEN a ranking applied update occurs
            collectionListener.onRankingApplied()
            beforeTransformGroupsListener.onBeforeTransformGroups(listOf(entry))
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(listOf(entry))

            // THEN the notification is never bound or shown
            verify(headsUpViewBinder, never()).bindHeadsUpView(any(), any(), any())
            verify(headsUpManager, never()).showNotification(any(), any())
        }

    @Test
    fun testOnRankingApplied_entryUpdatedToHun() =
        kosmos.runTest {
            // GIVEN that mEntry is added in a state where it should not HUN
            setShouldHeadsUp(entry, false)
            collectionListener.onEntryAdded(entry)

            // and it is then updated such that it should now HUN
            setShouldHeadsUp(entry)
            setAllNotifs(listOf(entry))

            // WHEN a ranking applied update occurs
            collectionListener.onRankingApplied()
            beforeTransformGroupsListener.onBeforeTransformGroups(listOf(entry))
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(listOf(entry))

            // THEN the notification is shown
            finishBind(entry)
            verify(headsUpManager).showNotification(entry, isFromUserOpenAction = false)
        }

    @Test
    fun testOnRankingApplied_entryUpdatedButTooOld() =
        kosmos.runTest {
            // GIVEN that mEntry is added in a state where it should not HUN
            setShouldHeadsUp(entry, false)
            collectionListener.onEntryAdded(entry)

            // and it was actually added 10s ago
            coordinator.setUpdateTime(entry, systemClock.currentTimeMillis() - 10000)

            // WHEN it is updated to HUN and then a ranking update occurs
            setShouldHeadsUp(entry)
            setAllNotifs(listOf(entry))
            collectionListener.onRankingApplied()
            beforeTransformGroupsListener.onBeforeTransformGroups(listOf(entry))
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(listOf(entry))

            // THEN the notification is never bound or shown
            verify(headsUpViewBinder, never()).bindHeadsUpView(any(), any(), any())
            verify(headsUpManager, never()).showNotification(any(), any())
        }

    @Test
    fun onEntryAdded_whenLaunchingFSI_doesLogDecision() =
        kosmos.runTest {
            // GIVEN A new notification can FSI
            setShouldFullScreen(entry, FullScreenIntentDecision.FSI_DEVICE_NOT_INTERACTIVE)
            collectionListener.onEntryAdded(entry)

            verify(launchFullScreenIntentProvider).launchFullScreenIntent(entry)
            verifyLoggedFullScreenIntentDecision(
                entry,
                FullScreenIntentDecision.FSI_DEVICE_NOT_INTERACTIVE,
            )
        }

    @Test
    fun onEntryAdded_whenNotLaunchingFSI_doesLogDecision() =
        kosmos.runTest {
            // GIVEN A new notification can't FSI
            setShouldFullScreen(entry, FullScreenIntentDecision.NO_FULL_SCREEN_INTENT)
            collectionListener.onEntryAdded(entry)

            verify(launchFullScreenIntentProvider, never()).launchFullScreenIntent(any())
            verifyLoggedFullScreenIntentDecision(
                entry,
                FullScreenIntentDecision.NO_FULL_SCREEN_INTENT,
            )
        }

    @Test
    fun onEntryAdded_whenNotLaunchingFSIBecauseOfDnd_doesLogDecision() =
        kosmos.runTest {
            // GIVEN A new notification can't FSI because of DND
            setShouldFullScreen(entry, FullScreenIntentDecision.NO_FSI_SUPPRESSED_ONLY_BY_DND)
            collectionListener.onEntryAdded(entry)

            verify(launchFullScreenIntentProvider, never()).launchFullScreenIntent(any())
            verifyLoggedFullScreenIntentDecision(
                entry,
                FullScreenIntentDecision.NO_FSI_SUPPRESSED_ONLY_BY_DND,
            )
        }

    @Test
    fun testOnRankingApplied_updateToFullScreen() =
        kosmos.runTest {
            // GIVEN that mEntry was previously suppressed from full-screen only by DND
            setShouldFullScreen(entry, FullScreenIntentDecision.NO_FSI_SUPPRESSED_ONLY_BY_DND)
            collectionListener.onEntryAdded(entry)

            // at this point, it should not have full screened, but should have logged
            verify(launchFullScreenIntentProvider, never()).launchFullScreenIntent(any())
            verifyLoggedFullScreenIntentDecision(
                entry,
                FullScreenIntentDecision.NO_FSI_SUPPRESSED_ONLY_BY_DND,
            )
            clearInterruptionProviderInvocations()

            // and it is then updated to allow full screen AND HUN
            setShouldFullScreen(entry, FullScreenIntentDecision.FSI_DEVICE_NOT_INTERACTIVE)
            setShouldHeadsUp(entry)
            setAllNotifs(listOf(entry))
            collectionListener.onRankingApplied()
            beforeTransformGroupsListener.onBeforeTransformGroups(listOf(entry))
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(listOf(entry))

            // THEN it should full screen and log but it should NOT HUN
            verify(launchFullScreenIntentProvider).launchFullScreenIntent(entry)
            verify(headsUpViewBinder, never()).bindHeadsUpView(any(), any(), any())
            verify(headsUpManager, never()).showNotification(any(), any())
            verifyLoggedFullScreenIntentDecision(
                entry,
                FullScreenIntentDecision.FSI_DEVICE_NOT_INTERACTIVE,
            )
            clearInterruptionProviderInvocations()

            // WHEN ranking updates again and the pipeline reruns
            clearInvocations(launchFullScreenIntentProvider)
            collectionListener.onRankingApplied()
            beforeTransformGroupsListener.onBeforeTransformGroups(listOf(entry))
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(listOf(entry))

            // VERIFY that the FSI does not launch again or log
            verify(launchFullScreenIntentProvider, never()).launchFullScreenIntent(any())
            verifyNoFullScreenIntentDecisionLogged()
        }

    @Test
    fun testOnRankingApplied_withOnlyDndSuppressionAllowsFsiLater() =
        kosmos.runTest {
            // GIVEN that mEntry was previously suppressed from full-screen only by DND
            setShouldFullScreen(entry, FullScreenIntentDecision.NO_FSI_SUPPRESSED_ONLY_BY_DND)
            collectionListener.onEntryAdded(entry)

            // at this point, it should not have full screened, but should have logged
            verify(launchFullScreenIntentProvider, never()).launchFullScreenIntent(any())
            verifyLoggedFullScreenIntentDecision(
                entry,
                FullScreenIntentDecision.NO_FSI_SUPPRESSED_ONLY_BY_DND,
            )
            clearInterruptionProviderInvocations()

            // ranking is applied with only DND blocking FSI
            setShouldFullScreen(entry, FullScreenIntentDecision.NO_FSI_SUPPRESSED_ONLY_BY_DND)
            collectionListener.onRankingApplied()
            beforeTransformGroupsListener.onBeforeTransformGroups(listOf(entry))
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(listOf(entry))

            // THEN it should still not yet full screen or HUN
            verify(launchFullScreenIntentProvider, never()).launchFullScreenIntent(any())
            verify(headsUpViewBinder, never()).bindHeadsUpView(any(), any(), any())
            verify(headsUpManager, never()).showNotification(any(), any())

            // Same decision as before; is not logged
            verifyNoFullScreenIntentDecisionLogged()
            clearInterruptionProviderInvocations()

            // and it is then updated to allow full screen AND HUN
            setShouldFullScreen(entry, FullScreenIntentDecision.FSI_DEVICE_NOT_INTERACTIVE)
            setShouldHeadsUp(entry)
            setAllNotifs(listOf(entry))
            collectionListener.onRankingApplied()
            beforeTransformGroupsListener.onBeforeTransformGroups(listOf(entry))
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(listOf(entry))

            // THEN it should full screen and log but it should NOT HUN
            verify(launchFullScreenIntentProvider).launchFullScreenIntent(entry)
            verify(headsUpViewBinder, never()).bindHeadsUpView(any(), any(), any())
            verify(headsUpManager, never()).showNotification(any(), any())
            verifyLoggedFullScreenIntentDecision(
                entry,
                FullScreenIntentDecision.FSI_DEVICE_NOT_INTERACTIVE,
            )
            clearInterruptionProviderInvocations()
        }

    @Test
    fun testOnRankingApplied_newNonFullScreenAnswerInvalidatesCandidate() =
        kosmos.runTest {
            // GIVEN that mEntry was previously suppressed from full-screen only by DND
            setAllNotifs(listOf(entry))
            setShouldFullScreen(entry, FullScreenIntentDecision.NO_FSI_SUPPRESSED_ONLY_BY_DND)
            collectionListener.onEntryAdded(entry)

            // at this point, it should not have full screened
            verify(launchFullScreenIntentProvider, never()).launchFullScreenIntent(entry)

            // now some other condition blocks FSI in addition to DND
            setShouldFullScreen(entry, FullScreenIntentDecision.NO_FSI_SUPPRESSED_BY_DND)
            collectionListener.onRankingApplied()
            beforeTransformGroupsListener.onBeforeTransformGroups(listOf(entry))
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(listOf(entry))

            // THEN it should NOT full screen or HUN
            verify(launchFullScreenIntentProvider, never()).launchFullScreenIntent(any())
            verify(headsUpViewBinder, never()).bindHeadsUpView(any(), any(), any())
            verify(headsUpManager, never()).showNotification(any(), any())

            // NOW the DND logic changes and FSI and HUN are available
            clearInvocations(launchFullScreenIntentProvider)
            setShouldFullScreen(entry, FullScreenIntentDecision.FSI_DEVICE_NOT_INTERACTIVE)
            setShouldHeadsUp(entry)
            collectionListener.onRankingApplied()
            beforeTransformGroupsListener.onBeforeTransformGroups(listOf(entry))
            beforeFinalizeFilterListener.onBeforeFinalizeFilter(listOf(entry))

            // VERIFY that the FSI didn't happen, but that we do HUN
            verify(launchFullScreenIntentProvider, never()).launchFullScreenIntent(any())
            finishBind(entry)
            verify(headsUpManager).showNotification(entry, isFromUserOpenAction = false)
        }

    @Test
    fun testOnRankingApplied_noFSIWhenAlsoSuppressedForOtherReasons() =
        kosmos.runTest {
            // GIVEN that mEntry is suppressed by DND (functionally), but not *only* DND
            setShouldFullScreen(entry, FullScreenIntentDecision.NO_FSI_SUPPRESSED_BY_DND)
            collectionListener.onEntryAdded(entry)

            // and it is updated to full screen later
            setShouldFullScreen(entry, FullScreenIntentDecision.FSI_DEVICE_NOT_INTERACTIVE)
            collectionListener.onRankingApplied()

            // THEN it should still not full screen because something else was blocking it before
            verify(launchFullScreenIntentProvider, never()).launchFullScreenIntent(entry)
        }

    @Test
    fun testOnRankingApplied_noFSIWhenTooOld() =
        kosmos.runTest {
            // GIVEN that mEntry is suppressed only by DND
            setShouldFullScreen(entry, FullScreenIntentDecision.NO_FSI_SUPPRESSED_ONLY_BY_DND)
            collectionListener.onEntryAdded(entry)

            // but it's >10s old
            coordinator.addForFSIReconsideration(entry, systemClock.currentTimeMillis() - 10000)

            // and it is updated to full screen later
            setShouldFullScreen(entry, FullScreenIntentDecision.FSI_KEYGUARD_SHOWING)
            collectionListener.onRankingApplied()

            // THEN it should still not full screen because it's too old
            verify(launchFullScreenIntentProvider, never()).launchFullScreenIntent(entry)
        }

    @Test
    @EnableFlags(LaunchNewFsiOnUpdate.FLAG_NAME)
    fun onEntryUpdated_withExistingUnlaunchedFsi_doesNotLaunch() =
        kosmos.runTest {
            // GIVEN A new notification with FSI that was not launched.
            updateEntryWithFullScreenIntent()
            setShouldFullScreen(entry, FullScreenIntentDecision.NO_FSI_NOT_IMPORTANT_ENOUGH)
            collectionListener.onEntryAdded(entry)

            // CHECK not launched on add
            verify(launchFullScreenIntentProvider, never()).launchFullScreenIntent(entry)

            // WHEN the notification now qualifies to launch its FSI
            updateEntryWithFullScreenIntent() // Set a new SBN, also with an FSI.
            setShouldFullScreen(entry, FullScreenIntentDecision.FSI_DEVICE_NOT_INTERACTIVE)
            collectionListener.onEntryUpdated(entry)

            // VERIFY that the FSI is not launched.
            // An FSI needs to be NEWLY ADDED for it to be launched on update.
            verify(launchFullScreenIntentProvider, never()).launchFullScreenIntent(entry)
        }

    @Test
    @EnableFlags(LaunchNewFsiOnUpdate.FLAG_NAME)
    fun onEntryUpdated_withNewFsi_doesLaunch() =
        kosmos.runTest {
            // GIVEN A new notification without an FSI.
            setShouldFullScreen(entry, FullScreenIntentDecision.NO_FULL_SCREEN_INTENT)
            collectionListener.onEntryAdded(entry)

            // CHECK not launched on add
            verify(launchFullScreenIntentProvider, never()).launchFullScreenIntent(entry)

            // WHEN the notification is updated to have FSI
            updateEntryWithFullScreenIntent()
            setShouldFullScreen(entry, FullScreenIntentDecision.FSI_DEVICE_NOT_INTERACTIVE)
            collectionListener.onEntryUpdated(entry)

            // VERIFY that the FSI is launched
            verify(launchFullScreenIntentProvider).launchFullScreenIntent(entry)
        }

    @Test
    @DisableFlags(LaunchNewFsiOnUpdate.FLAG_NAME)
    fun onEntryUpdated_withNewFsi_doesNotLaunch_withFlagDisabled() =
        kosmos.runTest {
            // GIVEN A new notification without an FSI.
            setShouldFullScreen(entry, FullScreenIntentDecision.NO_FULL_SCREEN_INTENT)
            collectionListener.onEntryAdded(entry)

            // CHECK not launched on add
            verify(launchFullScreenIntentProvider, never()).launchFullScreenIntent(entry)

            // WHEN the notification is updated to have FSI
            updateEntryWithFullScreenIntent()
            setShouldFullScreen(entry, FullScreenIntentDecision.FSI_DEVICE_NOT_INTERACTIVE)
            collectionListener.onEntryUpdated(entry)

            // VERIFY that the FSI is not launched
            verify(launchFullScreenIntentProvider, never()).launchFullScreenIntent(entry)
        }

    @Test
    @EnableFlags(LaunchNewFsiOnUpdate.FLAG_NAME)
    fun onEntryUpdated_withNewFsiBlockedOnlyByDnd_isCandidateForFSIReconsideration() =
        kosmos.runTest {
            // GIVEN A new notification without an FSI.
            setShouldFullScreen(entry, FullScreenIntentDecision.NO_FULL_SCREEN_INTENT)
            collectionListener.onEntryAdded(entry)

            // CHECK not launched on add
            verify(launchFullScreenIntentProvider, never()).launchFullScreenIntent(entry)
            assertFalse(coordinator.isCandidateForFSIReconsideration(entry))

            // WHEN the notification is updated to have FSI
            updateEntryWithFullScreenIntent()
            setShouldFullScreen(entry, FullScreenIntentDecision.NO_FSI_SUPPRESSED_ONLY_BY_DND)
            collectionListener.onEntryUpdated(entry)

            // VERIFY that the FSI is not launched, but is a candidate for FSI reconsideration
            verify(launchFullScreenIntentProvider, never()).launchFullScreenIntent(entry)
            assertTrue(coordinator.isCandidateForFSIReconsideration(entry))
        }

    @Test
    fun onEntryCleanUp_removesFromLifetimeExtensionMapWithoutCancellingTask() =
        kosmos.runTest {
            // GIVEN a sticky HUN that is being lifetime extended
            whenever(headsUpManager.canRemoveImmediately(entry.key)).thenReturn(false)
            whenever(headsUpManager.isSticky(anyString())).thenReturn(true)
            whenever(headsUpManager.getEarliestRemovalTime(anyString())).thenReturn(1000L)
            assertTrue(notifLifetimeExtender.maybeExtendLifetime(entry, /* reason= */ 0))

            // GIVEN the entry IS in the extension map and there is 1 pending task
            assertTrue(coordinator.mNotifsExtendingLifetime.containsKey(entry))
            assertEquals(1, executor.numPending())

            // WHEN the entry is cleaned up
            collectionListener.onEntryCleanUp(entry)

            // VERIFY the entry is GONE from the map (no memory leak)
            assertFalse(coordinator.mNotifsExtendingLifetime.containsKey(entry))

            // VERIFY the removal task is still pending
            assertEquals(1, executor.numPending())
        }

    private fun Kosmos.setAllNotifs(entries: List<NotificationEntry>) {
        // defining this as a function ensures that the entries are created (and any fixtures are
        // resolved) before `whenever` is called, rather than between `whenever` and `thenReturn`,
        // which confuses Mockito.
        whenever(notifPipeline.allNotifs).thenReturn(entries)
    }

    private fun Kosmos.updateEntryWithFullScreenIntent() {
        entry.sbn = SbnBuilder().build().apply { notification.fullScreenIntent = mock() }
    }

    private fun Kosmos.setDefaultShouldHeadsUp(should: Boolean) {
        whenever(visualInterruptionDecisionProvider.makeAndLogHeadsUpDecision(any()))
            .thenReturn(DecisionImpl(should))
        whenever(visualInterruptionDecisionProvider.makeUnloggedHeadsUpDecision(any()))
            .thenReturn(DecisionImpl(should))
    }

    private fun Kosmos.setShouldHeadsUp(entry: NotificationEntry, should: Boolean = true) {
        whenever(visualInterruptionDecisionProvider.makeAndLogHeadsUpDecision(entry))
            .thenReturn(DecisionImpl(should))
        whenever(visualInterruptionDecisionProvider.makeUnloggedHeadsUpDecision(entry))
            .thenReturn(DecisionImpl(should))
    }

    private fun Kosmos.setDefaultShouldFullScreen(originalDecision: FullScreenIntentDecision) {
        val provider = visualInterruptionDecisionProvider
        whenever(provider.makeUnloggedFullScreenIntentDecision(any())).thenAnswer {
            val entry: NotificationEntry = it.getArgument(0)
            FullScreenIntentDecisionImpl(entry, originalDecision)
        }
    }

    private fun Kosmos.setShouldFullScreen(
        entry: NotificationEntry,
        originalDecision: FullScreenIntentDecision,
    ) {
        whenever(visualInterruptionDecisionProvider.makeUnloggedFullScreenIntentDecision(entry))
            .thenAnswer { FullScreenIntentDecisionImpl(entry, originalDecision) }
    }

    private fun Kosmos.verifyLoggedFullScreenIntentDecision(
        entry: NotificationEntry,
        originalDecision: FullScreenIntentDecision,
    ) {
        val decision = withArgCaptor {
            verify(visualInterruptionDecisionProvider).logFullScreenIntentDecision(capture())
        }
        check(decision is FullScreenIntentDecisionImpl)
        assertEquals(entry, decision.originalEntry)
        assertEquals(originalDecision, decision.originalDecision)
    }

    private fun Kosmos.verifyNoFullScreenIntentDecisionLogged() {
        verify(visualInterruptionDecisionProvider, never()).logFullScreenIntentDecision(any())
    }

    private fun Kosmos.clearInterruptionProviderInvocations() {
        clearInvocations(visualInterruptionDecisionProvider)
    }

    private data class DecisionImpl(
        override val shouldInterrupt: Boolean,
        override val logReason: String = "unknown",
    ) : VisualInterruptionDecisionProvider.Decision

    private class FullScreenIntentDecisionImpl(
        val originalEntry: NotificationEntry,
        val originalDecision: FullScreenIntentDecision,
    ) : VisualInterruptionDecisionProvider.FullScreenIntentDecision {
        override val shouldInterrupt = originalDecision.shouldLaunch
        override val wouldInterruptWithoutDnd =
            originalDecision == FullScreenIntentDecision.NO_FSI_SUPPRESSED_ONLY_BY_DND
        override val logReason = originalDecision.name
    }

    private fun Kosmos.finishBind(entry: NotificationEntry) {
        verify(headsUpManager, never()).showNotification(eq(entry), any())
        val isFromUserOpenActionCaptor = argumentCaptor<Boolean>()
        withArgCaptor<HeadsUpViewBinder.HeadsUpBindCallback> {
                verify(headsUpViewBinder)
                    .bindHeadsUpView(eq(entry), isFromUserOpenActionCaptor.capture(), capture())
            }
            .onHeadsUpBindFinished(entry, isFromUserOpenActionCaptor.firstValue)
    }
}
