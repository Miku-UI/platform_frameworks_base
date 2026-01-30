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

package com.android.systemui.statusbar.notification.promoted.domain.interactor

import android.app.Notification.FLAG_FOREGROUND_SERVICE
import android.app.Notification.FLAG_ONGOING_EVENT
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.mediaprojection.data.model.MediaProjectionState
import com.android.systemui.mediaprojection.data.repository.fakeMediaProjectionRepository
import com.android.systemui.mediaprojection.taskswitcher.FakeActivityTaskManager.Companion.createTask
import com.android.systemui.screenrecord.data.model.ScreenRecordModel
import com.android.systemui.screenrecord.data.repository.screenRecordRepository
import com.android.systemui.statusbar.chips.call.ui.viewmodel.CallChipViewModelTest.Companion.createStatusBarIconViewOrNull
import com.android.systemui.statusbar.chips.notification.domain.interactor.statusBarNotificationChipsInteractor
import com.android.systemui.statusbar.core.StatusBarRootModernization
import com.android.systemui.statusbar.notification.collection.buildNotificationEntry
import com.android.systemui.statusbar.notification.collection.buildOngoingCallEntry
import com.android.systemui.statusbar.notification.collection.buildPromotedOngoingEntry
import com.android.systemui.statusbar.notification.data.model.activeNotificationModel
import com.android.systemui.statusbar.notification.data.repository.activeNotificationListRepository
import com.android.systemui.statusbar.notification.data.repository.addNotif
import com.android.systemui.statusbar.notification.domain.interactor.renderNotificationListInteractor
import com.android.systemui.statusbar.notification.promoted.PromotedNotificationUi
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentBuilder
import com.android.systemui.statusbar.phone.ongoingcall.StatusBarChipsModernization
import com.android.systemui.statusbar.phone.ongoingcall.shared.model.OngoingCallTestHelper.addOngoingCallState
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(
    PromotedNotificationUi.FLAG_NAME,
    StatusBarChipsModernization.FLAG_NAME,
    StatusBarRootModernization.FLAG_NAME,
)
class PromotedNotificationsInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.underTest by Fixture { promotedNotificationsInteractor }

    @Before
    fun setUp() {
        kosmos.statusBarNotificationChipsInteractor.start()
    }

    @Test
    fun orderedChipNotificationKeys_containsNonPromotedCalls() =
        kosmos.runTest {
            // GIVEN a non-promoted call and a promoted ongoing notification
            val callEntry = buildOngoingCallEntry(promoted = false)
            val ronEntry = buildPromotedOngoingEntry()
            val otherEntry = buildNotificationEntry(tag = "other")

            renderNotificationListInteractor.setRenderedList(
                listOf(callEntry, ronEntry, otherEntry)
            )

            val orderedChipNotificationKeys by
                collectLastValue(underTest.orderedChipNotificationKeys)

            // THEN both the call and the notif are in the list
            assertThat(orderedChipNotificationKeys).containsExactly(callEntry.key, ronEntry.key)
        }

    @Test
    fun orderedChipNotificationKeys_containsPromotedCalls_callNotFirst() =
        kosmos.runTest {
            // GIVEN a promoted call and a promoted ongoing notification
            val callEntry = buildOngoingCallEntry(promoted = true)
            val ronEntry = buildPromotedOngoingEntry()
            val otherEntry = buildNotificationEntry(tag = "other")

            renderNotificationListInteractor.setRenderedList(
                listOf(ronEntry, callEntry, otherEntry)
            )

            val orderedChipNotificationKeys by
                collectLastValue(underTest.orderedChipNotificationKeys)

            // THEN the call notif is considered a *promoted notification* chip not a *call* chip,
            // so the order should match the incoming order
            assertThat(orderedChipNotificationKeys)
                .containsExactly(ronEntry.key, callEntry.key)
                .inOrder()
        }

    @Test
    fun orderedChipNotificationKeys_noScreenRecordNotif_isEmpty() =
        kosmos.runTest {
            screenRecordRepository.screenRecordState.value = ScreenRecordModel.Recording
            fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(
                    hostPackage = "com.android.systemui.tests"
                )

            renderNotificationListInteractor.setRenderedList(emptyList())

            val orderedChipNotificationKeys by
                collectLastValue(underTest.orderedChipNotificationKeys)

            assertThat(orderedChipNotificationKeys).isEmpty()
        }

    @Test
    fun orderedChipNotificationKeys_nullHostPackageForScreenRecord_isEmpty() =
        kosmos.runTest {
            screenRecordRepository.screenRecordState.value = ScreenRecordModel.Recording
            // hostPackage would be provided through mediaProjectionState
            fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.NotProjecting

            val entry = buildNotificationEntry(tag = "record", promoted = false)
            renderNotificationListInteractor.setRenderedList(listOf(entry))

            val orderedChipNotificationKeys by
                collectLastValue(underTest.orderedChipNotificationKeys)

            assertThat(orderedChipNotificationKeys).isEmpty()
        }

    @Test
    fun orderedChipNotificationKeys_containsPromotedScreenRecordNotif() =
        kosmos.runTest {
            screenRecordRepository.screenRecordState.value = ScreenRecordModel.Recording
            fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(
                    hostPackage = "com.android.systemui.tests"
                )

            val screenRecordEntry = buildNotificationEntry(tag = "record", promoted = true)
            renderNotificationListInteractor.setRenderedList(listOf(screenRecordEntry))

            val orderedChipNotificationKeys by
                collectLastValue(underTest.orderedChipNotificationKeys)

            assertThat(orderedChipNotificationKeys).containsExactly(screenRecordEntry.key).inOrder()
        }

    @Test
    fun orderedChipNotificationKeys_containsNotPromotedScreenRecordNotif_ifOngoing() =
        kosmos.runTest {
            screenRecordRepository.screenRecordState.value = ScreenRecordModel.Recording
            fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(
                    hostPackage = "com.android.systemui.tests"
                )

            val screenRecordEntry =
                buildNotificationEntry(tag = "record", promoted = false) {
                    setFlag(context, FLAG_ONGOING_EVENT, true)
                }
            renderNotificationListInteractor.setRenderedList(listOf(screenRecordEntry))

            val orderedChipNotificationKeys by
                collectLastValue(underTest.orderedChipNotificationKeys)

            assertThat(orderedChipNotificationKeys).containsExactly(screenRecordEntry.key).inOrder()
        }

    @Test
    fun orderedChipNotificationKeys_containsNotPromotedScreenRecordNotif_ifFgs() =
        kosmos.runTest {
            screenRecordRepository.screenRecordState.value = ScreenRecordModel.Recording
            fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(
                    hostPackage = "com.android.systemui.tests"
                )

            val screenRecordEntry =
                buildNotificationEntry(tag = "record", promoted = false) {
                    setFlag(context, FLAG_FOREGROUND_SERVICE, true)
                }
            renderNotificationListInteractor.setRenderedList(listOf(screenRecordEntry))

            val orderedChipNotificationKeys by
                collectLastValue(underTest.orderedChipNotificationKeys)

            assertThat(orderedChipNotificationKeys).containsExactly(screenRecordEntry.key).inOrder()
        }

    @Test
    fun orderedChipNotificationKeys_doesNotContainScreenRecordNotif_ifNotOngoingOrFgs() =
        kosmos.runTest {
            screenRecordRepository.screenRecordState.value = ScreenRecordModel.Recording
            fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(
                    hostPackage = "com.android.systemui.tests"
                )

            val screenRecordEntry =
                buildNotificationEntry(tag = "record", promoted = false) {
                    setFlag(context, FLAG_ONGOING_EVENT, false)
                    setFlag(context, FLAG_FOREGROUND_SERVICE, false)
                }
            renderNotificationListInteractor.setRenderedList(listOf(screenRecordEntry))

            val orderedChipNotificationKeys by
                collectLastValue(underTest.orderedChipNotificationKeys)

            assertThat(orderedChipNotificationKeys).isEmpty()
        }

    @Test
    fun orderedChipNotificationKeys_containsFgsScreenRecordNotif_whenNonFgsNotifExists() =
        kosmos.runTest {
            screenRecordRepository.screenRecordState.value = ScreenRecordModel.Recording
            fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(
                    hostPackage = "com.android.systemui.tests"
                )

            val fgsEntry =
                buildNotificationEntry(tag = "recordFgs", promoted = false) {
                    setFlag(context, FLAG_FOREGROUND_SERVICE, true)
                }
            val notFgsEntry =
                buildNotificationEntry(tag = "recordNotFgs", promoted = false) {
                    setFlag(context, FLAG_FOREGROUND_SERVICE, false)
                }
            renderNotificationListInteractor.setRenderedList(listOf(fgsEntry, notFgsEntry))

            val orderedChipNotificationKeys by
                collectLastValue(underTest.orderedChipNotificationKeys)

            assertThat(orderedChipNotificationKeys).containsExactly(fgsEntry.key).inOrder()
        }

    @Test
    fun orderedChipNotificationKeys_containsOngoingScreenRecordNotif_whenNonOngoingNotifExists() =
        kosmos.runTest {
            screenRecordRepository.screenRecordState.value = ScreenRecordModel.Recording
            fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(
                    hostPackage = "com.android.systemui.tests"
                )

            val ongoingEntry =
                buildNotificationEntry(tag = "recordOngoing", promoted = false) {
                    setFlag(context, FLAG_ONGOING_EVENT, true)
                }
            val notOngoingEntry =
                buildNotificationEntry(tag = "recordNotOngoing", promoted = false) {
                    setFlag(context, FLAG_ONGOING_EVENT, false)
                }
            renderNotificationListInteractor.setRenderedList(listOf(notOngoingEntry, ongoingEntry))

            val orderedChipNotificationKeys by
                collectLastValue(underTest.orderedChipNotificationKeys)

            assertThat(orderedChipNotificationKeys).containsExactly(ongoingEntry.key).inOrder()
        }

    @Test
    fun orderedChipNotificationKeys_containsFgsOngoingScreenRecordNotif_whenNonFgsOngoingNotifExists() =
        kosmos.runTest {
            screenRecordRepository.screenRecordState.value = ScreenRecordModel.Recording
            fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(
                    hostPackage = "com.android.systemui.tests"
                )

            val ongoingAndFgsEntry =
                buildNotificationEntry(tag = "recordBoth", promoted = false) {
                    setFlag(context, FLAG_FOREGROUND_SERVICE, true)
                    setFlag(context, FLAG_ONGOING_EVENT, true)
                }
            val ongoingButNotFgsEntry =
                buildNotificationEntry(tag = "recordOngoing", promoted = false) {
                    setFlag(context, FLAG_ONGOING_EVENT, true)
                    setFlag(context, FLAG_FOREGROUND_SERVICE, false)
                }
            val fgsButNotOngoingEntry =
                buildNotificationEntry(tag = "recordFgs", promoted = false) {
                    setFlag(context, FLAG_FOREGROUND_SERVICE, true)
                    setFlag(context, FLAG_ONGOING_EVENT, false)
                }
            renderNotificationListInteractor.setRenderedList(
                listOf(fgsButNotOngoingEntry, ongoingButNotFgsEntry, ongoingAndFgsEntry)
            )

            val orderedChipNotificationKeys by
                collectLastValue(underTest.orderedChipNotificationKeys)

            assertThat(orderedChipNotificationKeys)
                .containsExactly(ongoingAndFgsEntry.key)
                .inOrder()
        }

    @Test
    fun orderedChipNotificationKeys_twoEquivalentNotifsForScreenRecord_isEmpty() =
        kosmos.runTest {
            screenRecordRepository.screenRecordState.value = ScreenRecordModel.Recording
            fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(
                    hostPackage = "com.android.systemui.tests"
                )

            val entry1 =
                buildNotificationEntry(tag = "entry1", promoted = false) {
                    setFlag(context, FLAG_FOREGROUND_SERVICE, true)
                }
            val entry2 =
                buildNotificationEntry(tag = "entry2", promoted = false) {
                    setFlag(context, FLAG_FOREGROUND_SERVICE, true)
                }
            renderNotificationListInteractor.setRenderedList(listOf(entry1, entry2))

            val orderedChipNotificationKeys by
                collectLastValue(underTest.orderedChipNotificationKeys)

            assertThat(orderedChipNotificationKeys).isEmpty()
        }

    @Test
    fun orderedChipNotificationKeys_noMediProjNotif_isEmpty() =
        kosmos.runTest {
            fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.SingleTask(
                    hostPackage = "com.android.systemui.tests",
                    hostDeviceName = null,
                    createTask(taskId = 1),
                )

            renderNotificationListInteractor.setRenderedList(emptyList())

            val orderedChipNotificationKeys by
                collectLastValue(underTest.orderedChipNotificationKeys)

            assertThat(orderedChipNotificationKeys).isEmpty()
        }

    @Test
    fun orderedChipNotificationKeys_containsPromotedMediaProjNotif() =
        kosmos.runTest {
            fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.SingleTask(
                    hostPackage = "com.android.systemui.tests",
                    hostDeviceName = null,
                    createTask(taskId = 1),
                )

            val mediaProjEntry = buildNotificationEntry(tag = "proj", promoted = true)
            renderNotificationListInteractor.setRenderedList(listOf(mediaProjEntry))

            val orderedChipNotificationKeys by
                collectLastValue(underTest.orderedChipNotificationKeys)

            assertThat(orderedChipNotificationKeys).containsExactly(mediaProjEntry.key).inOrder()
        }

    @Test
    fun orderedChipNotificationKeys_containsNotPromotedMediaProjNotif_ifOngoing() =
        kosmos.runTest {
            fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.SingleTask(
                    hostPackage = "com.android.systemui.tests",
                    hostDeviceName = null,
                    createTask(taskId = 1),
                )

            val mediaProjEntry =
                buildNotificationEntry(tag = "proj", promoted = false) {
                    setFlag(context, FLAG_ONGOING_EVENT, true)
                }
            renderNotificationListInteractor.setRenderedList(listOf(mediaProjEntry))

            val orderedChipNotificationKeys by
                collectLastValue(underTest.orderedChipNotificationKeys)

            assertThat(orderedChipNotificationKeys).containsExactly(mediaProjEntry.key).inOrder()
        }

    @Test
    fun orderedChipNotificationKeys_containsNotPromotedMediaProjNotif_ifFgs() =
        kosmos.runTest {
            fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.SingleTask(
                    hostPackage = "com.android.systemui.tests",
                    hostDeviceName = null,
                    createTask(taskId = 1),
                )

            val mediaProjEntry =
                buildNotificationEntry(tag = "proj", promoted = false) {
                    setFlag(context, FLAG_FOREGROUND_SERVICE, true)
                }
            renderNotificationListInteractor.setRenderedList(listOf(mediaProjEntry))

            val orderedChipNotificationKeys by
                collectLastValue(underTest.orderedChipNotificationKeys)

            assertThat(orderedChipNotificationKeys).containsExactly(mediaProjEntry.key).inOrder()
        }

    @Test
    fun orderedChipNotificationKeys_doesNotContainMediaProjNotif_ifNotOngoingOrFgs() =
        kosmos.runTest {
            fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.SingleTask(
                    hostPackage = "com.android.systemui.tests",
                    hostDeviceName = null,
                    createTask(taskId = 1),
                )

            val mediaProjEntry =
                buildNotificationEntry(tag = "proj", promoted = false) {
                    setFlag(context, FLAG_ONGOING_EVENT, false)
                    setFlag(context, FLAG_FOREGROUND_SERVICE, false)
                }
            renderNotificationListInteractor.setRenderedList(listOf(mediaProjEntry))

            val orderedChipNotificationKeys by
                collectLastValue(underTest.orderedChipNotificationKeys)

            assertThat(orderedChipNotificationKeys).isEmpty()
        }

    @Test
    fun orderedChipNotificationKeys_containsFgsMediaProjNotif_whenNonFgsNotifExists() =
        kosmos.runTest {
            fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.SingleTask(
                    hostPackage = "com.android.systemui.tests",
                    hostDeviceName = null,
                    createTask(taskId = 1),
                )

            val fgsEntry =
                buildNotificationEntry(tag = "projFgs", promoted = false) {
                    setFlag(context, FLAG_FOREGROUND_SERVICE, true)
                }
            val notFgsEntry =
                buildNotificationEntry(tag = "projNotFgs", promoted = false) {
                    setFlag(context, FLAG_FOREGROUND_SERVICE, false)
                }
            renderNotificationListInteractor.setRenderedList(listOf(fgsEntry, notFgsEntry))

            val orderedChipNotificationKeys by
                collectLastValue(underTest.orderedChipNotificationKeys)

            assertThat(orderedChipNotificationKeys).containsExactly(fgsEntry.key).inOrder()
        }

    @Test
    fun orderedChipNotificationKeys_containsOngoingMediaProjNotif_whenNonOngoingNotifExists() =
        kosmos.runTest {
            fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.SingleTask(
                    hostPackage = "com.android.systemui.tests",
                    hostDeviceName = null,
                    createTask(taskId = 1),
                )

            val ongoingEntry =
                buildNotificationEntry(tag = "projOngoing", promoted = false) {
                    setFlag(context, FLAG_ONGOING_EVENT, true)
                }
            val notOngoingEntry =
                buildNotificationEntry(tag = "projNotOngoing", promoted = false) {
                    setFlag(context, FLAG_ONGOING_EVENT, false)
                }
            renderNotificationListInteractor.setRenderedList(listOf(notOngoingEntry, ongoingEntry))

            val orderedChipNotificationKeys by
                collectLastValue(underTest.orderedChipNotificationKeys)

            assertThat(orderedChipNotificationKeys).containsExactly(ongoingEntry.key).inOrder()
        }

    @Test
    fun orderedChipNotificationKeys_containsFgsOngoingMediaProjNotif_whenNonFgsOngoingNotifExists() =
        kosmos.runTest {
            fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.SingleTask(
                    hostPackage = "com.android.systemui.tests",
                    hostDeviceName = null,
                    createTask(taskId = 1),
                )

            val ongoingAndFgsEntry =
                buildNotificationEntry(tag = "projBoth", promoted = false) {
                    setFlag(context, FLAG_FOREGROUND_SERVICE, true)
                    setFlag(context, FLAG_ONGOING_EVENT, true)
                }
            val ongoingButNotFgsEntry =
                buildNotificationEntry(tag = "projOngoing", promoted = false) {
                    setFlag(context, FLAG_ONGOING_EVENT, true)
                    setFlag(context, FLAG_FOREGROUND_SERVICE, false)
                }
            val fgsButNotOngoingEntry =
                buildNotificationEntry(tag = "projFgs", promoted = false) {
                    setFlag(context, FLAG_FOREGROUND_SERVICE, true)
                    setFlag(context, FLAG_ONGOING_EVENT, false)
                }
            renderNotificationListInteractor.setRenderedList(
                listOf(fgsButNotOngoingEntry, ongoingButNotFgsEntry, ongoingAndFgsEntry)
            )

            val orderedChipNotificationKeys by
                collectLastValue(underTest.orderedChipNotificationKeys)

            assertThat(orderedChipNotificationKeys)
                .containsExactly(ongoingAndFgsEntry.key)
                .inOrder()
        }

    @Test
    fun orderedChipNotificationKeys_twoEquivalentNotifsForMediaProj_isEmpty() =
        kosmos.runTest {
            fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.SingleTask(
                    hostPackage = "com.android.systemui.tests",
                    hostDeviceName = null,
                    createTask(taskId = 1),
                )

            val entry1 =
                buildNotificationEntry(tag = "entry1", promoted = false) {
                    setFlag(context, FLAG_FOREGROUND_SERVICE, true)
                }
            val entry2 =
                buildNotificationEntry(tag = "entry2", promoted = false) {
                    setFlag(context, FLAG_FOREGROUND_SERVICE, true)
                }
            renderNotificationListInteractor.setRenderedList(listOf(entry1, entry2))

            val orderedChipNotificationKeys by
                collectLastValue(underTest.orderedChipNotificationKeys)

            assertThat(orderedChipNotificationKeys).isEmpty()
        }

    @Test
    fun orderedChipNotificationKeys_maintainsPromotedNotifOrder() =
        kosmos.runTest {
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = "notif1",
                    statusBarChipIcon = createStatusBarIconViewOrNull(),
                    promotedContent = PromotedNotificationContentBuilder("notif1").build(),
                )
            )
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = "notif2",
                    statusBarChipIcon = createStatusBarIconViewOrNull(),
                    promotedContent = PromotedNotificationContentBuilder("notif2").build(),
                )
            )

            val orderedChipNotificationKeys by
                collectLastValue(underTest.orderedChipNotificationKeys)

            assertThat(orderedChipNotificationKeys).containsExactly("notif1", "notif2").inOrder()
        }

    // The ranking between different chips should stay consistent between
    // PromotedNotificationsInteractor and OngoingActivityChipsViewModel.
    // See OngoingActivityChipsWithNotifsViewModelTest#chips_screenRecordAndCallAndPromotedNotifs
    // test for the right ranking.
    @Test
    fun orderedChipNotificationKeys_rankingIsCorrect() =
        kosmos.runTest {
            // Screen record
            screenRecordRepository.screenRecordState.value = ScreenRecordModel.Recording
            fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.SingleTask(
                    hostPackage = "screen.record.package",
                    hostDeviceName = null,
                    createTask(taskId = 1),
                )
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = "screenRecordKey",
                    packageName = "screen.record.package",
                    isOngoingEvent = true,
                )
            )
            // Call
            addOngoingCallState(key = "callKey")
            // Other promoted notifs
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = "notif1",
                    statusBarChipIcon = createStatusBarIconViewOrNull(),
                    promotedContent = PromotedNotificationContentBuilder("notif1").build(),
                )
            )
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = "notif2",
                    statusBarChipIcon = createStatusBarIconViewOrNull(),
                    promotedContent = PromotedNotificationContentBuilder("notif2").build(),
                )
            )

            val orderedChipNotificationKeys by
                collectLastValue(underTest.orderedChipNotificationKeys)

            assertThat(orderedChipNotificationKeys)
                .containsExactly("screenRecordKey", "callKey", "notif1", "notif2")
                .inOrder()
        }

    @Test
    fun topPromotedNotificationContent_skipsNonPromotedCalls() =
        kosmos.runTest {
            // GIVEN a non-promoted call and a promoted ongoing notification
            val callEntry = buildOngoingCallEntry(promoted = false)
            val ronEntry = buildPromotedOngoingEntry()
            val otherEntry = buildNotificationEntry(tag = "other")

            renderNotificationListInteractor.setRenderedList(
                listOf(callEntry, ronEntry, otherEntry)
            )

            val topPromotedNotificationContent by
                collectLastValue(underTest.aodPromotedNotification)

            // THEN the ron is first because the call has no content
            assertThat(topPromotedNotificationContent?.key).isEqualTo(ronEntry.key)
        }

    @Test
    fun topPromotedNotificationContent_includesPromotedCalls() =
        kosmos.runTest {
            // GIVEN a promoted call and a promoted ongoing notification
            val callEntry = buildOngoingCallEntry(promoted = true)
            val ronEntry = buildPromotedOngoingEntry()
            val otherEntry = buildNotificationEntry(tag = "other")

            renderNotificationListInteractor.setRenderedList(
                listOf(callEntry, ronEntry, otherEntry)
            )

            val topPromotedNotificationContent by
                collectLastValue(underTest.aodPromotedNotification)

            // THEN the call is the top notification
            assertThat(topPromotedNotificationContent?.key).isEqualTo(callEntry.key)
        }

    @Test
    fun topPromotedNotificationContent_nullWithNoPromotedNotifications() =
        kosmos.runTest {
            // GIVEN a a non-promoted call and no promoted ongoing entry
            val callEntry = buildOngoingCallEntry(promoted = false)
            val otherEntry = buildNotificationEntry(tag = "other")

            renderNotificationListInteractor.setRenderedList(listOf(callEntry, otherEntry))

            val topPromotedNotificationContent by
                collectLastValue(underTest.aodPromotedNotification)

            // THEN there is no top promoted notification
            assertThat(topPromotedNotificationContent).isNull()
        }
}
