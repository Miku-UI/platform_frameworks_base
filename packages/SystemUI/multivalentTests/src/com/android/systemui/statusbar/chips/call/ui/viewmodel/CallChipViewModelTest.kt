/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.chips.call.ui.viewmodel

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import androidx.test.filters.SmallTest
import com.android.internal.logging.InstanceId
import com.android.systemui.SysuiTestCase
import com.android.systemui.activity.data.repository.activityManagerRepository
import com.android.systemui.activity.data.repository.fake
import com.android.systemui.animation.ActivityTransitionAnimator
import com.android.systemui.animation.Expandable
import com.android.systemui.common.shared.model.ContentDescription.Companion.loadContentDescription
import com.android.systemui.keyguard.data.repository.FakeKeyguardTransitionRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.statusbar.chips.StatusBarChipsReturnAnimations
import com.android.systemui.statusbar.chips.notification.domain.interactor.statusBarNotificationChipsInteractor
import com.android.systemui.statusbar.chips.ui.model.Chronometer
import com.android.systemui.statusbar.chips.ui.model.ColorsModel
import com.android.systemui.statusbar.chips.ui.model.EventTime
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.chips.ui.view.ChipBackgroundContainer
import com.android.systemui.statusbar.notification.data.repository.UnconfinedFakeHeadsUpRowRepository
import com.android.systemui.statusbar.notification.headsup.PinnedStatus
import com.android.systemui.statusbar.notification.stack.data.repository.headsUpNotificationRepository
import com.android.systemui.statusbar.phone.ongoingcall.shared.model.OngoingCallTestHelper
import com.android.systemui.statusbar.phone.ongoingcall.shared.model.OngoingCallTestHelper.addOngoingCallState
import com.android.systemui.statusbar.phone.ongoingcall.shared.model.OngoingCallTestHelper.callPromotedContentBuilder
import com.android.systemui.statusbar.phone.ongoingcall.shared.model.OngoingCallTestHelper.removeOngoingCallState
import com.android.systemui.testKosmos
import com.android.systemui.util.time.fakeSystemClock
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class CallChipViewModelTest(flags: FlagsParameterization) : SysuiTestCase() {
    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    private val kosmos =
        testKosmos().useUnconfinedTestDispatcher().apply {
            // Don't be in lockscreen so that HUNs are allowed
            fakeKeyguardTransitionRepository =
                FakeKeyguardTransitionRepository(initInLockscreen = false, testScope = testScope)
        }

    private val chipBackgroundView = mock<ChipBackgroundContainer>()
    private val mockExpandable: Expandable =
        mock<Expandable>().apply { whenever(dialogTransitionController(any())).thenReturn(mock()) }

    private val Kosmos.underTest by Kosmos.Fixture { callChipViewModel }

    @Test
    fun chip_noCall_isHidden() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            removeOngoingCallState("testKey")

            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Inactive::class.java)
        }

    @Test
    fun chip_inCall_hasKeyWithPrefix() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState(startTimeMs = 0, isAppVisible = false, key = NOTIFICATION_KEY)

            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).key)
                .startsWith(CallChipViewModel.KEY_PREFIX)
            assertThat((latest as OngoingActivityChipModel.Active).key).contains(NOTIFICATION_KEY)
        }

    @Test
    fun chip_inCall_notificationKeyHasNoPrefix() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState(startTimeMs = 0, isAppVisible = false, key = NOTIFICATION_KEY)

            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).notificationKey)
                .isEqualTo(NOTIFICATION_KEY)
        }

    @Test
    fun chip_inCall_callDidNotRequestPromotion_callChipIsShown() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            val instanceId = InstanceId.fakeInstanceId(10)
            addOngoingCallState(
                startTimeMs = 0,
                isAppVisible = false,
                instanceId = instanceId,
                requestedPromotion = false,
                promotedContent = OngoingCallTestHelper.PromotedContentInput.OverrideToNull,
            )

            assertThat((latest as OngoingActivityChipModel.Active).content)
                .isInstanceOf(OngoingActivityChipModel.Content.IconOnly::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).instanceId).isEqualTo(instanceId)
        }

    @Test
    fun chip_inCall_callRequestedPromotion_andIsPromoted_noCallChip() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            val instanceId = InstanceId.fakeInstanceId(10)
            addOngoingCallState(
                startTimeMs = 0,
                isAppVisible = false,
                instanceId = instanceId,
                requestedPromotion = true,
                promotedContent =
                    OngoingCallTestHelper.PromotedContentInput.OverrideToValue(
                        callPromotedContentBuilder().build()
                    ),
            )

            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Inactive::class.java)
        }

    /** See b/414830065. */
    @Test
    fun chip_inCall_callRequestedPromotion_butNotPromoted_noCallChip() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            val instanceId = InstanceId.fakeInstanceId(10)
            addOngoingCallState(
                startTimeMs = 0,
                isAppVisible = false,
                instanceId = instanceId,
                requestedPromotion = true,
                // This is null if notif isn't actually promoted,
                promotedContent = OngoingCallTestHelper.PromotedContentInput.OverrideToNull,
            )

            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Inactive::class.java)
        }

    @Test
    fun chip_inCall_negativeStartTime_isShownAsIconOnly_withData() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            val instanceId = InstanceId.fakeInstanceId(10)
            addOngoingCallState(
                startTimeMs = -2,
                isAppVisible = false,
                instanceId = instanceId,
                packageName = PACKAGE_NAME,
            )

            assertThat((latest as OngoingActivityChipModel.Active).content)
                .isInstanceOf(OngoingActivityChipModel.Content.IconOnly::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isFalse()
            assertThat((latest as OngoingActivityChipModel.Active).isImportantForPrivacy).isFalse()
            assertThat((latest as OngoingActivityChipModel.Active).instanceId).isEqualTo(instanceId)
            assertThat((latest as OngoingActivityChipModel.Active).managingPackageName)
                .isEqualTo(PACKAGE_NAME)
        }

    @Test
    fun chip_inCall_noHun_chipHasTime() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState(startTimeMs = 4_000)

            headsUpNotificationRepository.setNotifications(emptyList())

            assertThat((latest as OngoingActivityChipModel.Active).content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
        }

    @Test
    fun chip_inCall_hunPinnedBySystem_chipHasTime() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState()

            headsUpNotificationRepository.setNotifications(
                UnconfinedFakeHeadsUpRowRepository(
                    key = "systemNotif",
                    pinnedStatus = MutableStateFlow(PinnedStatus.PinnedBySystem),
                )
            )

            assertThat((latest as OngoingActivityChipModel.Active).content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
        }

    @Test
    fun chip_inCall_hunPinnedByUser_forDifferentChip_chipHasTime() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState(key = "thisNotif")

            headsUpNotificationRepository.setNotifications(
                UnconfinedFakeHeadsUpRowRepository(
                    key = "otherNotifPinnedByUser",
                    pinnedStatus = MutableStateFlow(PinnedStatus.PinnedByUser),
                )
            )

            assertThat((latest as OngoingActivityChipModel.Active).content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
        }

    @Test
    fun chip_inCall_hunPinnedByUser_forThisChip_chipDoesNotHaveTime() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState(key = "thisNotif")

            headsUpNotificationRepository.setNotifications(
                UnconfinedFakeHeadsUpRowRepository(
                    key = "thisNotif",
                    pinnedStatus = MutableStateFlow(PinnedStatus.PinnedByUser),
                )
            )

            assertThat((latest as OngoingActivityChipModel.Active).content)
                .isInstanceOf(OngoingActivityChipModel.Content.IconOnly::class.java)
        }

    @Test
    fun chip_twoCallNotifs_earlierIsUsed() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            val instanceIdOld = InstanceId.fakeInstanceId(3)
            addOngoingCallState(
                key = "earlierNotif",
                startTimeMs = 3_000,
                instanceId = instanceIdOld,
            )
            val instanceIdNew = InstanceId.fakeInstanceId(6)
            addOngoingCallState(key = "laterNotif", startTimeMs = 6_000, instanceId = instanceIdNew)

            assertThat((latest as OngoingActivityChipModel.Active).content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).key).contains("earlierNotif")
            assertThat((latest as OngoingActivityChipModel.Active).instanceId)
                .isEqualTo(instanceIdOld)
        }

    @Test
    @DisableFlags(StatusBarChipsReturnAnimations.FLAG_NAME)
    fun chipLegacy_inCallWithVisibleApp_zeroStartTime_isHiddenAsIconOnly() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState(startTimeMs = 0, isAppVisible = true)

            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            val activeChip = latest as OngoingActivityChipModel.Active

            assertThat(activeChip.isHidden).isTrue()
            assertThat(activeChip.content)
                .isInstanceOf(OngoingActivityChipModel.Content.IconOnly::class.java)
        }

    @Test
    @EnableFlags(StatusBarChipsReturnAnimations.FLAG_NAME)
    fun chipWithReturnAnimation_inCallWithVisibleApp_zeroStartTime_isHiddenAsIconOnly() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState(startTimeMs = 0, isAppVisible = true)

            assertThat((latest as OngoingActivityChipModel.Active).content)
                .isInstanceOf(OngoingActivityChipModel.Content.IconOnly::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isTrue()
            assertThat((latest as OngoingActivityChipModel.Active).isImportantForPrivacy).isFalse()
        }

    @Test
    @EnableFlags(StatusBarChipsReturnAnimations.FLAG_NAME)
    fun chipWithReturnAnimation_inCallWithVisibleApp_negativeStartTime_isHiddenAsIconOnly() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState(startTimeMs = -2, isAppVisible = true)

            assertThat((latest as OngoingActivityChipModel.Active).content)
                .isInstanceOf(OngoingActivityChipModel.Content.IconOnly::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isTrue()
            assertThat((latest as OngoingActivityChipModel.Active).isImportantForPrivacy).isFalse()
        }

    @Test
    @DisableFlags(StatusBarChipsReturnAnimations.FLAG_NAME)
    fun chipLegacy_inCallWithVisibleApp_negativeStartTime_isHiddenAsIconOnly() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState(startTimeMs = -2, isAppVisible = true)

            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            val activeChip = latest as OngoingActivityChipModel.Active

            assertThat(activeChip.isHidden).isTrue()
            assertThat(activeChip.content)
                .isInstanceOf(OngoingActivityChipModel.Content.IconOnly::class.java)
        }

    @Test
    @EnableFlags(StatusBarChipsReturnAnimations.FLAG_NAME)
    fun chipWithReturnAnimation_inCallWithVisibleApp_positiveStartTime_isHiddenAsTimer() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState(startTimeMs = 345, isAppVisible = true)

            assertThat((latest as OngoingActivityChipModel.Active).content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isTrue()
            assertThat((latest as OngoingActivityChipModel.Active).isImportantForPrivacy).isFalse()
        }

    @Test
    @DisableFlags(StatusBarChipsReturnAnimations.FLAG_NAME)
    fun chipLegacy_inCallWithVisibleApp_positiveStartTime_isHiddenAsTimer() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState(startTimeMs = 345, isAppVisible = true)

            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            val activeChip = latest as OngoingActivityChipModel.Active

            assertThat(activeChip.isHidden).isTrue()
            assertThat(activeChip.content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
        }

    @Test
    fun chip_inCall_startTimeConvertedToElapsedRealtime() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            kosmos.fakeSystemClock.setCurrentTimeMillis(3000)
            kosmos.fakeSystemClock.setElapsedRealtime(400_000)

            addOngoingCallState(startTimeMs = 1000)

            // The OngoingCallModel start time is relative to currentTimeMillis, so this call
            // started 2000ms ago (1000 - 3000). The OngoingActivityChipModel start time needs to be
            // relative to elapsedRealtime, so it should be 2000ms before the elapsed realtime set
            // on the clock.
            assertThat(
                    ((latest as OngoingActivityChipModel.Active).content
                            as OngoingActivityChipModel.Content.Timer)
                        .value
                )
                .isEqualTo(Chronometer.Running(EventTime.ElapsedRealtime(398_000)))
        }

    @Test
    fun chip_positiveStartTime_iconIsNotifIcon() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            val notifKey = "testNotifKey"
            addOngoingCallState(startTimeMs = 1000, statusBarChipIconView = null, key = notifKey)

            assertThat((latest as OngoingActivityChipModel.Active).icon)
                .isInstanceOf(
                    OngoingActivityChipModel.ChipIcon.StatusBarNotificationIcon::class.java
                )
            val actualNotifKey =
                (((latest as OngoingActivityChipModel.Active).icon)
                        as OngoingActivityChipModel.ChipIcon.StatusBarNotificationIcon)
                    .notificationKey
            assertThat(actualNotifKey).isEqualTo(notifKey)
        }

    @Test
    fun chip_zeroStartTime_iconIsNotifKeyIcon_withContentDescription() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState(
                key = "notifKey",
                statusBarChipIconView = null,
                appName = "Fake app name",
            )

            assertThat((latest as OngoingActivityChipModel.Active).icon)
                .isInstanceOf(
                    OngoingActivityChipModel.ChipIcon.StatusBarNotificationIcon::class.java
                )
            val actualIcon =
                (latest as OngoingActivityChipModel.Active).icon
                    as OngoingActivityChipModel.ChipIcon.StatusBarNotificationIcon
            assertThat(actualIcon.notificationKey).isEqualTo("notifKey")
            assertThat(actualIcon.contentDescription.loadContentDescription(context))
                .contains("Ongoing call")
            assertThat(actualIcon.contentDescription.loadContentDescription(context))
                .contains("Fake app name")
        }

    @Test
    fun chip_notifIconFlagOn_butNullNotifIcon_iconIsNotifKeyIcon_withContentDescription() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState(
                key = "notifKey",
                statusBarChipIconView = null,
                appName = "Fake app name",
            )

            assertThat((latest as OngoingActivityChipModel.Active).icon)
                .isInstanceOf(
                    OngoingActivityChipModel.ChipIcon.StatusBarNotificationIcon::class.java
                )
            val actualIcon =
                (latest as OngoingActivityChipModel.Active).icon
                    as OngoingActivityChipModel.ChipIcon.StatusBarNotificationIcon
            assertThat(actualIcon.notificationKey).isEqualTo("notifKey")
            assertThat(actualIcon.contentDescription.loadContentDescription(context))
                .contains("Ongoing call")
            assertThat(actualIcon.contentDescription.loadContentDescription(context))
                .contains("Fake app name")
        }

    @Test
    fun chip_positiveStartTime_colorsAreAccentThemed() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState(startTimeMs = 1000)

            assertThat((latest as OngoingActivityChipModel.Active).colors)
                .isEqualTo(ColorsModel.AccentThemed)
        }

    @Test
    fun chip_zeroStartTime_colorsAreAccentThemed() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState(startTimeMs = 0)

            assertThat((latest as OngoingActivityChipModel.Active).colors)
                .isEqualTo(ColorsModel.AccentThemed)
        }

    @Test
    fun chip_requestedPromotionChanges_modelUpdates() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState(requestedPromotion = false)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)

            addOngoingCallState(requestedPromotion = true)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Inactive::class.java)

            addOngoingCallState(requestedPromotion = false)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
        }

    @Test
    fun chip_resetsCorrectly() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)
            kosmos.fakeSystemClock.setCurrentTimeMillis(3000)
            kosmos.fakeSystemClock.setElapsedRealtime(400_000)

            // Start a call
            addOngoingCallState(key = "testKey", startTimeMs = 1000)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat(
                    ((latest as OngoingActivityChipModel.Active).content
                            as OngoingActivityChipModel.Content.Timer)
                        .value
                )
                .isEqualTo(Chronometer.Running(EventTime.ElapsedRealtime(398_000)))

            // End the call
            removeOngoingCallState(key = "testKey")
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Inactive::class.java)

            // Let 100_000ms elapse
            kosmos.fakeSystemClock.setCurrentTimeMillis(103_000)
            kosmos.fakeSystemClock.setElapsedRealtime(500_000)

            // Start a new call, which started 1000ms ago
            addOngoingCallState(key = "testKey", startTimeMs = 102_000)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat(
                    ((latest as OngoingActivityChipModel.Active).content
                            as OngoingActivityChipModel.Content.Timer)
                        .value
                )
                .isEqualTo(Chronometer.Running(EventTime.ElapsedRealtime(499_000)))
        }

    @Test
    fun chip_inCall_nullIntent_chipsModFlagOn_clickingChipNotifiesInteractor() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)
            val latestChipTapKey by
                collectLastValue(
                    statusBarNotificationChipsInteractor.promotedNotificationChipTapEvent
                )

            addOngoingCallState(key = "fakeCallKey", startTimeMs = 1000, contentIntent = null)

            val clickBehavior = (latest as OngoingActivityChipModel.Active).clickBehavior
            assertThat(clickBehavior)
                .isInstanceOf(
                    OngoingActivityChipModel.ClickBehavior.ShowHeadsUpNotification::class.java
                )

            (clickBehavior as OngoingActivityChipModel.ClickBehavior.ShowHeadsUpNotification)
                .onClick()

            assertThat(latestChipTapKey).isEqualTo("fakeCallKey")
        }

    @Test
    fun chip_inCall_positiveStartTime_validIntent_clickingChipNotifiesInteractor() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)
            val latestChipTapKey by
                collectLastValue(
                    statusBarNotificationChipsInteractor.promotedNotificationChipTapEvent
                )

            val pendingIntent = mock<PendingIntent>()
            addOngoingCallState(
                key = "fakeCallKey",
                startTimeMs = 1000,
                contentIntent = pendingIntent,
            )

            val clickBehavior = (latest as OngoingActivityChipModel.Active).clickBehavior
            assertThat(clickBehavior)
                .isInstanceOf(
                    OngoingActivityChipModel.ClickBehavior.ShowHeadsUpNotification::class.java
                )
            (clickBehavior as OngoingActivityChipModel.ClickBehavior.ShowHeadsUpNotification)
                .onClick()

            assertThat(latestChipTapKey).isEqualTo("fakeCallKey")
        }

    @Test
    fun chip_inCall_zeroStartTime_validIntent_clickingChipNotifiesInteractor() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)
            val latestChipTapKey by
                collectLastValue(
                    statusBarNotificationChipsInteractor.promotedNotificationChipTapEvent
                )

            val pendingIntent = mock<PendingIntent>()
            addOngoingCallState(key = "fakeCallKey", startTimeMs = 0, contentIntent = pendingIntent)

            val clickBehavior = (latest as OngoingActivityChipModel.Active).clickBehavior
            assertThat(clickBehavior)
                .isInstanceOf(
                    OngoingActivityChipModel.ClickBehavior.ShowHeadsUpNotification::class.java
                )
            (clickBehavior as OngoingActivityChipModel.ClickBehavior.ShowHeadsUpNotification)
                .onClick()

            assertThat(latestChipTapKey).isEqualTo("fakeCallKey")
        }

    @Test
    fun chip_inCall_noHun_clickBehaviorIsShowHun() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState()

            headsUpNotificationRepository.setNotifications(emptyList())

            assertThat((latest as OngoingActivityChipModel.Active).clickBehavior)
                .isInstanceOf(
                    OngoingActivityChipModel.ClickBehavior.ShowHeadsUpNotification::class.java
                )
        }

    @Test
    fun chip_inCall_hunPinnedBySystem_clickBehaviorIsShowHun() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState()

            headsUpNotificationRepository.setNotifications(
                UnconfinedFakeHeadsUpRowRepository(
                    key = "systemNotif",
                    pinnedStatus = MutableStateFlow(PinnedStatus.PinnedBySystem),
                )
            )

            assertThat((latest as OngoingActivityChipModel.Active).clickBehavior)
                .isInstanceOf(
                    OngoingActivityChipModel.ClickBehavior.ShowHeadsUpNotification::class.java
                )
        }

    @Test
    fun chip_inCall_hunPinnedByUser_forDifferentChip_clickBehaviorIsShowHun() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState(key = "thisNotif")

            headsUpNotificationRepository.setNotifications(
                UnconfinedFakeHeadsUpRowRepository(
                    key = "otherNotifPinnedByUser",
                    pinnedStatus = MutableStateFlow(PinnedStatus.PinnedByUser),
                )
            )

            assertThat((latest as OngoingActivityChipModel.Active).clickBehavior)
                .isInstanceOf(
                    OngoingActivityChipModel.ClickBehavior.ShowHeadsUpNotification::class.java
                )
        }

    @Test
    fun chip_inCall_hunPinnedByUser_forThisChip_clickBehaviorIsHideHun() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState(key = "thisNotif")

            headsUpNotificationRepository.setNotifications(
                UnconfinedFakeHeadsUpRowRepository(
                    key = "thisNotif",
                    pinnedStatus = MutableStateFlow(PinnedStatus.PinnedByUser),
                )
            )

            assertThat((latest as OngoingActivityChipModel.Active).clickBehavior)
                .isInstanceOf(
                    OngoingActivityChipModel.ClickBehavior.HideHeadsUpNotification::class.java
                )
        }

    // We don't have any custom launch animation, we only have the return animation.
    @Test
    @EnableFlags(StatusBarChipsReturnAnimations.FLAG_NAME)
    fun chipWithReturnAnimation_updatesCorrectly_withStateAndTransitionState() =
        kosmos.runTest {
            val pendingIntent = mock<PendingIntent>()
            val intent = mock<Intent>()
            whenever(pendingIntent.intent).thenReturn(intent)
            val component = mock<ComponentName>()
            whenever(intent.component).thenReturn(component)

            val expandable = mock<Expandable>()
            val activityController = mock<ActivityTransitionAnimator.Controller>()
            whenever(
                    expandable.activityTransitionController(
                        anyOrNull(),
                        anyOrNull(),
                        any(),
                        anyOrNull(),
                        any(),
                    )
                )
                .thenReturn(activityController)

            val latest by collectLastValue(underTest.chip)

            // Start off with no call.
            removeOngoingCallState(key = NOTIFICATION_KEY)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Inactive::class.java)
            assertThat(latest!!.transitionManager!!.controllerFactory).isNull()

            // Call starts [NoCall -> InCall(isAppVisible=true), NoTransition].
            addOngoingCallState(
                key = NOTIFICATION_KEY,
                startTimeMs = 345,
                contentIntent = pendingIntent,
                uid = NOTIFICATION_UID,
                isAppVisible = true,
            )
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isTrue()
            assertThat(latest!!.transitionManager!!.hideChipForTransition).isFalse()
            val factory = latest!!.transitionManager!!.controllerFactory
            assertThat(factory!!.component).isEqualTo(component)

            // Request a return transition [InCall(isAppVisible=true), NoTransition ->
            // ReturnRequested].
            factory.onCompose(expandable)
            var controller = factory.createController(forLaunch = false)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isFalse()
            assertThat(latest!!.transitionManager!!.controllerFactory).isEqualTo(factory)
            assertThat(latest!!.transitionManager!!.hideChipForTransition).isTrue()

            // Start the return transition [InCall(isAppVisible=true), ReturnRequested ->
            // Returning].
            controller.onTransitionAnimationStart(isExpandingFullyAbove = false)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isFalse()
            assertThat(latest!!.transitionManager!!.controllerFactory).isEqualTo(factory)
            assertThat(latest!!.transitionManager!!.hideChipForTransition).isFalse()

            // End the return transition [InCall(isAppVisible=true), Returning -> NoTransition].
            controller.onTransitionAnimationEnd(isExpandingFullyAbove = false)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isFalse()
            assertThat(latest!!.transitionManager!!.controllerFactory).isEqualTo(factory)
            assertThat(latest!!.transitionManager!!.hideChipForTransition).isFalse()

            // Settle the return transition [InCall(isAppVisible=true) ->
            // InCall(isAppVisible=false), NoTransition].
            kosmos.activityManagerRepository.fake.setIsAppVisible(NOTIFICATION_UID, false)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isFalse()
            assertThat(latest!!.transitionManager!!.controllerFactory).isEqualTo(factory)
            assertThat(latest!!.transitionManager!!.hideChipForTransition).isFalse()

            // End the call.
            removeOngoingCallState(key = NOTIFICATION_KEY)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Inactive::class.java)
            assertThat(latest!!.transitionManager!!.controllerFactory).isNull()

            // End the call with the app hidden [InCall(isAppVisible=false) -> NoCall,
            // NoTransition].
            addOngoingCallState(
                key = NOTIFICATION_KEY,
                startTimeMs = 345,
                contentIntent = pendingIntent,
                isAppVisible = false,
            )
            removeOngoingCallState(key = NOTIFICATION_KEY)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Inactive::class.java)
            assertThat(latest!!.transitionManager!!.controllerFactory).isNull()
        }

    @Test
    @EnableFlags(StatusBarChipsReturnAnimations.FLAG_NAME)
    fun chipWithReturnAnimation_updatesCorrectly_whenAppIsLaunchedAndClosedWithoutAnimation() =
        kosmos.runTest {
            val pendingIntent = mock<PendingIntent>()
            val intent = mock<Intent>()
            whenever(pendingIntent.intent).thenReturn(intent)
            val component = mock<ComponentName>()
            whenever(intent.component).thenReturn(component)

            val expandable = mock<Expandable>()
            val activityController = mock<ActivityTransitionAnimator.Controller>()
            whenever(
                    expandable.activityTransitionController(
                        anyOrNull(),
                        anyOrNull(),
                        any(),
                        anyOrNull(),
                        any(),
                    )
                )
                .thenReturn(activityController)

            val latest by collectLastValue(underTest.chip)

            // Start off with a call with visible app.
            addOngoingCallState(
                key = NOTIFICATION_KEY,
                startTimeMs = 345,
                contentIntent = pendingIntent,
                uid = NOTIFICATION_UID,
                isAppVisible = true,
            )
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isTrue()
            assertThat(latest!!.transitionManager!!.hideChipForTransition).isFalse()
            val factory = latest!!.transitionManager!!.controllerFactory
            assertThat(factory!!.component).isEqualTo(component)

            // Close the app without a return transition (e.g. from gesture nav)
            // [InCall(isAppVisible=true) -> InCall(isAppVisible=false), NoTransition].
            kosmos.activityManagerRepository.fake.setIsAppVisible(NOTIFICATION_UID, false)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isFalse()
            assertThat(latest!!.transitionManager!!.controllerFactory).isEqualTo(factory)
            assertThat(latest!!.transitionManager!!.hideChipForTransition).isFalse()

            // Launch the app from another source (e.g. the app icon) [InCall(isAppVisible=true) ->
            // InCall(isAppVisible=false), NoTransition].
            kosmos.activityManagerRepository.fake.setIsAppVisible(NOTIFICATION_UID, true)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isTrue()
            assertThat(latest!!.transitionManager!!.controllerFactory).isEqualTo(factory)
            assertThat(latest!!.transitionManager!!.hideChipForTransition).isFalse()
        }

    @Test
    @DisableFlags(StatusBarChipsReturnAnimations.FLAG_NAME)
    fun chipLegacy_hasNoTransitionAnimationInformation() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            // NoCall
            removeOngoingCallState(key = NOTIFICATION_KEY)
            assertThat(latest!!.transitionManager).isNull()

            // InCall with visible app
            addOngoingCallState(
                key = NOTIFICATION_KEY,
                startTimeMs = 345,
                uid = NOTIFICATION_UID,
                isAppVisible = true,
            )
            assertThat(latest!!.transitionManager).isNull()

            // InCall with hidden app
            kosmos.activityManagerRepository.fake.setIsAppVisible(NOTIFICATION_UID, false)
            assertThat(latest!!.transitionManager).isNull()
        }

    @Test
    @EnableFlags(StatusBarChipsReturnAnimations.FLAG_NAME)
    fun chipWithReturnAnimation_chipDataChangesMidTransition() =
        kosmos.runTest {
            val pendingIntent = mock<PendingIntent>()
            val intent = mock<Intent>()
            whenever(pendingIntent.intent).thenReturn(intent)
            val component = mock<ComponentName>()
            whenever(intent.component).thenReturn(component)

            val expandable = mock<Expandable>()
            val activityController = mock<ActivityTransitionAnimator.Controller>()
            whenever(
                    expandable.activityTransitionController(
                        anyOrNull(),
                        anyOrNull(),
                        any(),
                        anyOrNull(),
                        any(),
                    )
                )
                .thenReturn(activityController)

            val latest by collectLastValue(underTest.chip)

            // Start with the app visible and trigger a return animation.
            addOngoingCallState(
                key = NOTIFICATION_KEY,
                startTimeMs = 345,
                contentIntent = pendingIntent,
                uid = NOTIFICATION_UID,
                isAppVisible = true,
            )
            var factory = latest!!.transitionManager!!.controllerFactory!!
            factory.onCompose(expandable)
            var controller = factory.createController(forLaunch = false)
            controller.onTransitionAnimationStart(isExpandingFullyAbove = false)
            assertThat((latest as OngoingActivityChipModel.Active).content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isFalse()

            // The chip changes state.
            addOngoingCallState(
                key = NOTIFICATION_KEY,
                startTimeMs = 0,
                contentIntent = pendingIntent,
                uid = NOTIFICATION_UID,
                isAppVisible = true,
            )
            assertThat((latest as OngoingActivityChipModel.Active).content)
                .isInstanceOf(OngoingActivityChipModel.Content.IconOnly::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isFalse()

            // Reset the state and trigger a launch animation.
            controller.onTransitionAnimationEnd(isExpandingFullyAbove = false)
            addOngoingCallState(
                key = NOTIFICATION_KEY,
                startTimeMs = 345,
                contentIntent = pendingIntent,
                uid = NOTIFICATION_UID,
                isAppVisible = true,
            )
            factory = latest!!.transitionManager!!.controllerFactory!!
            factory.onCompose(expandable)
            controller = factory.createController(forLaunch = true)
            controller.onTransitionAnimationStart(isExpandingFullyAbove = false)
            assertThat((latest as OngoingActivityChipModel.Active).content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isFalse()

            // The chip changes state.
            addOngoingCallState(
                key = NOTIFICATION_KEY,
                startTimeMs = -2,
                contentIntent = pendingIntent,
                uid = NOTIFICATION_UID,
                isAppVisible = true,
            )
            assertThat((latest as OngoingActivityChipModel.Active).content)
                .isInstanceOf(OngoingActivityChipModel.Content.IconOnly::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isFalse()
        }

    @Test
    @EnableFlags(StatusBarChipsReturnAnimations.FLAG_NAME)
    fun chipWithReturnAnimation_chipDisappearsMidTransition() =
        kosmos.runTest {
            val pendingIntent = mock<PendingIntent>()
            val intent = mock<Intent>()
            whenever(pendingIntent.intent).thenReturn(intent)
            val component = mock<ComponentName>()
            whenever(intent.component).thenReturn(component)

            val expandable = mock<Expandable>()
            val activityController = mock<ActivityTransitionAnimator.Controller>()
            whenever(
                    expandable.activityTransitionController(
                        anyOrNull(),
                        anyOrNull(),
                        any(),
                        anyOrNull(),
                        any(),
                    )
                )
                .thenReturn(activityController)

            val latest by collectLastValue(underTest.chip)

            // Start with the app visible and trigger a return animation.
            addOngoingCallState(
                key = NOTIFICATION_KEY,
                startTimeMs = 345,
                contentIntent = pendingIntent,
                uid = NOTIFICATION_UID,
                isAppVisible = true,
            )
            var factory = latest!!.transitionManager!!.controllerFactory!!
            factory.onCompose(expandable)
            var controller = factory.createController(forLaunch = false)
            controller.onTransitionAnimationStart(isExpandingFullyAbove = false)
            assertThat((latest as OngoingActivityChipModel.Active).content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isFalse()

            // The chip disappears.
            removeOngoingCallState(key = NOTIFICATION_KEY)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Inactive::class.java)

            // Reset the state and trigger a launch animation.
            controller.onTransitionAnimationEnd(isExpandingFullyAbove = false)
            addOngoingCallState(
                key = NOTIFICATION_KEY,
                startTimeMs = 345,
                contentIntent = pendingIntent,
                uid = NOTIFICATION_UID,
                isAppVisible = true,
            )
            factory = latest!!.transitionManager!!.controllerFactory!!
            factory.onCompose(expandable)
            controller = factory.createController(forLaunch = true)
            controller.onTransitionAnimationStart(isExpandingFullyAbove = false)
            assertThat((latest as OngoingActivityChipModel.Active).content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isFalse()

            // The chip disappears.
            removeOngoingCallState(key = NOTIFICATION_KEY)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Inactive::class.java)
        }

    companion object {
        private const val NOTIFICATION_KEY = "testKey"
        private const val NOTIFICATION_UID = 12345
        private const val PACKAGE_NAME = "testApp.package.name"
        @get:Parameters(name = "{0}")
        @JvmStatic
        val flags: List<FlagsParameterization>
            get() = buildList {
                addAll(
                    FlagsParameterization.allCombinationsOf(
                        StatusBarChipsReturnAnimations.FLAG_NAME
                    )
                )
            }
    }
}
