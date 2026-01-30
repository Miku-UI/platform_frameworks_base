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

package com.android.systemui.statusbar.chips.ui.viewmodel

import android.content.DialogInterface
import android.content.packageManager
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.view.View
import android.view.ViewRootImpl
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.animation.Expandable
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.ContentDescription.Companion.loadContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.display.data.repository.displayStateRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.mediaprojection.data.model.MediaProjectionState
import com.android.systemui.mediaprojection.data.repository.fakeMediaProjectionRepository
import com.android.systemui.mediaprojection.taskswitcher.FakeActivityTaskManager.Companion.createTask
import com.android.systemui.res.R
import com.android.systemui.screenrecord.data.model.ScreenRecordModel
import com.android.systemui.screenrecord.data.repository.screenRecordRepository
import com.android.systemui.statusbar.chips.StatusBarChipToHunAnimation
import com.android.systemui.statusbar.chips.call.ui.viewmodel.CallChipViewModel
import com.android.systemui.statusbar.chips.call.ui.viewmodel.CallChipViewModelTest.Companion.createStatusBarIconViewOrNull
import com.android.systemui.statusbar.chips.mediaprojection.domain.interactor.MediaProjectionChipInteractorTest.Companion.NORMAL_PACKAGE
import com.android.systemui.statusbar.chips.mediaprojection.domain.interactor.MediaProjectionChipInteractorTest.Companion.setUpPackageManagerForMediaProjection
import com.android.systemui.statusbar.chips.notification.domain.interactor.statusBarNotificationChipsInteractor
import com.android.systemui.statusbar.chips.notification.ui.viewmodel.NotifChipsViewModelTest.Companion.assertIsNotifChip
import com.android.systemui.statusbar.chips.screenrecord.ui.viewmodel.ScreenRecordChipViewModel
import com.android.systemui.statusbar.chips.sharetoapp.ui.viewmodel.ShareToAppChipViewModel
import com.android.systemui.statusbar.chips.ui.model.MultipleOngoingActivityChipsModel
import com.android.systemui.statusbar.chips.ui.model.MultipleOngoingActivityChipsModelLegacy
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.chips.ui.view.ChipBackgroundContainer
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipsViewModelTest.Companion.assertIsCallChip
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipsViewModelTest.Companion.assertIsScreenRecordChip
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipsViewModelTest.Companion.assertIsShareToAppChip
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipsViewModelTest.Companion.getStopActionFromDialog
import com.android.systemui.statusbar.notification.data.model.activeNotificationModel
import com.android.systemui.statusbar.notification.data.repository.ActiveNotificationsStore
import com.android.systemui.statusbar.notification.data.repository.activeNotificationListRepository
import com.android.systemui.statusbar.notification.data.repository.addNotif
import com.android.systemui.statusbar.notification.data.repository.addNotifs
import com.android.systemui.statusbar.notification.data.repository.removeNotif
import com.android.systemui.statusbar.notification.promoted.PromotedNotificationUi
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentBuilder
import com.android.systemui.statusbar.notification.shared.ActiveNotificationModel
import com.android.systemui.statusbar.notification.shared.CallType
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.statusbar.phone.ongoingcall.DisableChipsModernization
import com.android.systemui.statusbar.phone.ongoingcall.EnableChipsModernization
import com.android.systemui.statusbar.phone.ongoingcall.shared.model.OngoingCallTestHelper
import com.android.systemui.statusbar.phone.ongoingcall.shared.model.OngoingCallTestHelper.addOngoingCallState
import com.android.systemui.statusbar.phone.ongoingcall.shared.model.OngoingCallTestHelper.removeOngoingCallState
import com.android.systemui.testKosmos
import com.android.systemui.util.time.fakeSystemClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

// TODO(b/273205603): add tests for Active chips with hidden=true once actually used.
/** Tests for [OngoingActivityChipsViewModel] when the [PromotedNotificationUi] flag is enabled. */
@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(PromotedNotificationUi.FLAG_NAME)
class OngoingActivityChipsWithNotifsViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val systemClock = kosmos.fakeSystemClock

    private val screenRecordState = kosmos.screenRecordRepository.screenRecordState
    private val mediaProjectionState = kosmos.fakeMediaProjectionRepository.mediaProjectionState
    private val activeNotificationListRepository = kosmos.activeNotificationListRepository

    private val mockSystemUIDialog = mock<SystemUIDialog>()
    private val chipBackgroundView =
        mock<ChipBackgroundContainer> { on { context } doReturn context }
    private val chipView =
        mock<View> {
            on {
                requireViewById<ChipBackgroundContainer>(R.id.ongoing_activity_chip_background)
            } doReturn chipBackgroundView
            on { context } doReturn context
        }
    private val viewRootImpl = mock<ViewRootImpl> { on { view } doReturn chipView }
    private val dialogTransitionController =
        mock<DialogTransitionAnimator.Controller> { on { viewRoot } doReturn viewRootImpl }
    private val mockExpandable: Expandable =
        mock<Expandable> {
            on { dialogTransitionController(any()) } doReturn dialogTransitionController
        }

    private val Kosmos.underTest by Kosmos.Fixture { ongoingActivityChipsViewModel }

    @Before
    fun setUp() {
        setUpPackageManagerForMediaProjection(kosmos)
        kosmos.statusBarNotificationChipsInteractor.start()
        val icon =
            BitmapDrawable(
                context.resources,
                Bitmap.createBitmap(/* width= */ 100, /* height= */ 100, Bitmap.Config.ARGB_8888),
            )
        whenever(kosmos.packageManager.getApplicationIcon(any<String>())).thenReturn(icon)
    }

    // Even though the `primaryChip` flow isn't used when the notifs flag is on, still test that the
    // flow has the right behavior to verify that we don't break any existing functionality.

    @Test
    fun primaryChip_allHidden_hidden() =
        kosmos.runTest {
            screenRecordState.value = ScreenRecordModel.DoingNothing
            mediaProjectionState.value = MediaProjectionState.NotProjecting
            removeOngoingCallState(key = "call")

            val latest by collectLastValue(underTest.primaryChip)

            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Inactive::class.java)
        }

    @DisableChipsModernization
    @Test
    fun chipsLegacy_allHidden_bothPrimaryAndSecondaryHidden() =
        kosmos.runTest {
            screenRecordState.value = ScreenRecordModel.DoingNothing
            mediaProjectionState.value = MediaProjectionState.NotProjecting
            removeOngoingCallState(key = "call")

            val latest by collectLastValue(underTest.chipsLegacy)
            val unused by collectLastValue(underTest.chips)

            assertThat(latest!!.primary).isInstanceOf(OngoingActivityChipModel.Inactive::class.java)
            assertThat(latest!!.secondary)
                .isInstanceOf(OngoingActivityChipModel.Inactive::class.java)
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModel())
        }

    @EnableChipsModernization
    @Test
    fun chips_allInactive() =
        kosmos.runTest {
            screenRecordState.value = ScreenRecordModel.DoingNothing
            mediaProjectionState.value = MediaProjectionState.NotProjecting
            setNotifs(emptyList())

            val latest by collectLastValue(underTest.chips)
            val unused by collectLastValue(underTest.chipsLegacy)

            assertThat(latest!!.active).isEmpty()
            assertThat(latest!!.overflow).isEmpty()
            assertThat(latest!!.inactive.size).isEqualTo(4)
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModelLegacy())
        }

    @Test
    fun visibleChipsWithBounds_allInactive() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.visibleChipsWithBounds)

            screenRecordState.value = ScreenRecordModel.DoingNothing
            mediaProjectionState.value = MediaProjectionState.NotProjecting
            setNotifs(emptyList())

            assertThat(latest).isEmpty()
        }

    @Test
    fun primaryChip_screenRecordShow_restHidden_screenRecordShown() =
        kosmos.runTest {
            screenRecordState.value = ScreenRecordModel.Recording
            mediaProjectionState.value = MediaProjectionState.NotProjecting
            removeOngoingCallState(key = "call")

            val latest by collectLastValue(underTest.primaryChip)

            assertIsScreenRecordChip(latest)
        }

    @DisableChipsModernization
    @Test
    fun chipsLegacy_screenRecordShow_restHidden_primaryIsScreenRecordSecondaryIsHidden() =
        kosmos.runTest {
            screenRecordState.value = ScreenRecordModel.Recording
            mediaProjectionState.value = MediaProjectionState.NotProjecting
            removeOngoingCallState(key = "call")

            val latest by collectLastValue(underTest.chipsLegacy)
            val unused by collectLastValue(underTest.chips)

            assertIsScreenRecordChip(latest!!.primary)
            assertThat(latest!!.secondary)
                .isInstanceOf(OngoingActivityChipModel.Inactive::class.java)
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModel())
        }

    @EnableChipsModernization
    @Test
    fun chips_screenRecordActive_restInactive() =
        kosmos.runTest {
            screenRecordState.value = ScreenRecordModel.Recording
            mediaProjectionState.value = MediaProjectionState.NotProjecting
            setNotifs(emptyList())

            val latest by collectLastValue(underTest.chips)
            val unused by collectLastValue(underTest.chipsLegacy)

            assertThat(latest!!.active.size).isEqualTo(1)
            assertIsScreenRecordChip(latest!!.active[0])
            assertThat(latest!!.overflow).isEmpty()
            assertThat(latest!!.inactive.size).isEqualTo(3)
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModelLegacy())
        }

    @Test
    fun primaryChip_screenRecordShowAndCallShow_screenRecordShown() =
        kosmos.runTest {
            screenRecordState.value = ScreenRecordModel.Recording
            addOngoingCallState("call", isAppVisible = false)

            val latest by collectLastValue(underTest.primaryChip)

            assertIsScreenRecordChip(latest)
        }

    @DisableChipsModernization
    @Test
    fun chipsLegacy_screenRecordShowAndCallShow_primaryIsScreenRecordSecondaryIsCall() =
        kosmos.runTest {
            val callNotificationKey = "call"
            screenRecordState.value = ScreenRecordModel.Recording
            addOngoingCallState(callNotificationKey, isAppVisible = false)

            val latest by collectLastValue(underTest.chipsLegacy)
            val unused by collectLastValue(underTest.chips)

            assertIsScreenRecordChip(latest!!.primary)
            assertIsCallChip(latest!!.secondary, callNotificationKey, context)
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModel())
        }

    @Test
    @DisableFlags(StatusBarChipToHunAnimation.FLAG_NAME)
    fun visibleChipsWithBounds_screenRecordShowAndCallShow_animFlagOff_hasBothKeys() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.visibleChipsWithBounds)

            val callNotificationKey = "call"
            screenRecordState.value = ScreenRecordModel.Recording
            addOngoingCallState(callNotificationKey)

            assertThat(latest!!.map { it.key })
                .containsExactly(
                    ScreenRecordChipViewModel.KEY,
                    "${CallChipViewModel.KEY_PREFIX}$callNotificationKey",
                )
                .inOrder()
        }

    @Test
    @EnableFlags(StatusBarChipToHunAnimation.FLAG_NAME)
    fun visibleChipsWithBounds_screenRecordShowAndCallShow_animFlagOn_noBoundsSet_isEmpty() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.visibleChipsWithBounds)

            val callNotificationKey = "call"
            screenRecordState.value = ScreenRecordModel.Recording
            addOngoingCallState(callNotificationKey)

            assertThat(latest).isEmpty()
        }

    @Test
    @EnableFlags(StatusBarChipToHunAnimation.FLAG_NAME)
    fun visibleChipsWithBounds_screenRecordShowAndCallShow_animFlagOn_boundsSetForOneChip_hasOnlyOneKey() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.visibleChipsWithBounds)

            val callNotificationKey = "call"
            val callKeyForChip = "${CallChipViewModel.KEY_PREFIX}$callNotificationKey"
            screenRecordState.value = ScreenRecordModel.Recording
            addOngoingCallState(callNotificationKey)

            underTest.onChipBoundsChanged(callKeyForChip, RectF(1f, 2f, 3f, 4f))

            assertThat(latest!![callKeyForChip]).isEqualTo(RectF(1f, 2f, 3f, 4f))
            assertThat(latest).doesNotContainKey(ScreenRecordChipViewModel.KEY)
        }

    @Test
    @EnableFlags(StatusBarChipToHunAnimation.FLAG_NAME)
    fun visibleChipsWithBounds_screenRecordShowAndCallShow_animFlagOn_boundsUpdated_hasUpdatedBounds() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.visibleChipsWithBounds)

            val callNotificationKey = "call"
            val callKeyForChip = "${CallChipViewModel.KEY_PREFIX}$callNotificationKey"
            addOngoingCallState(callNotificationKey)

            underTest.onChipBoundsChanged(callKeyForChip, RectF(1f, 2f, 3f, 4f))
            assertThat(latest!![callKeyForChip]).isEqualTo(RectF(1f, 2f, 3f, 4f))

            underTest.onChipBoundsChanged(callKeyForChip, RectF(10f, 20f, 30f, 40f))
            assertThat(latest!![callKeyForChip]).isEqualTo(RectF(10f, 20f, 30f, 40f))
        }

    @Test
    @EnableFlags(StatusBarChipToHunAnimation.FLAG_NAME)
    fun visibleChipsWithBounds_screenRecordShowAndCallShow_animFlagOn_boundsSet_hasBothKeysAndBounds() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.visibleChipsWithBounds)

            val callNotificationKey = "call"
            val callKeyForChip = "${CallChipViewModel.KEY_PREFIX}$callNotificationKey"
            screenRecordState.value = ScreenRecordModel.Recording
            addOngoingCallState(callNotificationKey)

            underTest.onChipBoundsChanged(callKeyForChip, RectF(1f, 2f, 3f, 4f))
            underTest.onChipBoundsChanged(ScreenRecordChipViewModel.KEY, RectF(5f, 6f, 7f, 8f))

            assertThat(latest!![callKeyForChip]).isEqualTo(RectF(1f, 2f, 3f, 4f))
            assertThat(latest!![ScreenRecordChipViewModel.KEY]).isEqualTo(RectF(5f, 6f, 7f, 8f))
        }

    @EnableChipsModernization
    @Test
    fun chips_screenRecordAndCallActive_inThatOrder() =
        kosmos.runTest {
            val callNotificationKey = "call"
            screenRecordState.value = ScreenRecordModel.Recording
            addOngoingCallState(callNotificationKey)

            val latest by collectLastValue(underTest.chips)
            val unused by collectLastValue(underTest.chipsLegacy)

            assertThat(latest!!.active.size).isEqualTo(2)
            assertIsScreenRecordChip(latest!!.active[0])
            assertIsCallChip(latest!!.active[1], callNotificationKey, context)
            assertThat(latest!!.overflow).isEmpty()
            assertThat(latest!!.inactive.size).isEqualTo(2)
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModelLegacy())
        }

    @DisableChipsModernization
    @Test
    fun chipsLegacy_oneChip_notSquished() =
        kosmos.runTest {
            addOngoingCallState(isAppVisible = false)

            val latest by collectLastValue(underTest.chipsLegacy)

            // The call chip isn't squished (squished chips would be icon only)
            assertThat((latest!!.primary as OngoingActivityChipModel.Active).content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
        }

    @EnableChipsModernization
    @Test
    fun chips_oneChip_notSquished() =
        kosmos.runTest {
            addOngoingCallState()

            val latest by collectLastValue(underTest.chips)

            // The call chip isn't squished (squished chips would be icon only)
            assertThat(latest!!.active[0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
        }

    @DisableChipsModernization
    @Test
    fun chipsLegacy_twoTimerChips_isSmallPortrait_bothSquished() =
        kosmos.runTest {
            screenRecordState.value = ScreenRecordModel.Recording
            addOngoingCallState(key = "call", isAppVisible = false)

            val latest by collectLastValue(underTest.chipsLegacy)

            // Squished chips are icon only
            assertThat((latest!!.primary as OngoingActivityChipModel.Active).content)
                .isInstanceOf(OngoingActivityChipModel.Content.IconOnly::class.java)
            assertThat((latest!!.secondary as OngoingActivityChipModel.Active).content)
                .isInstanceOf(OngoingActivityChipModel.Content.IconOnly::class.java)
        }

    @EnableChipsModernization
    @Test
    fun chips_twoTimerChips_isSmallPortrait_bothSquished() =
        kosmos.runTest {
            screenRecordState.value = ScreenRecordModel.Recording
            addOngoingCallState(key = "call")

            val latest by collectLastValue(underTest.chips)

            // Squished chips are icon only
            assertThat(latest!!.active[0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.IconOnly::class.java)
            assertThat(latest!!.active[1].content)
                .isInstanceOf(OngoingActivityChipModel.Content.IconOnly::class.java)
        }

    @EnableChipsModernization
    @Test
    fun chips_threeChips_isSmallPortrait_allSquished() =
        kosmos.runTest {
            screenRecordState.value = ScreenRecordModel.Recording
            addOngoingCallState(key = "call")

            val promotedContentBuilder =
                PromotedNotificationContentBuilder("notif").applyToShared {
                    this.shortCriticalText = "Some text here"
                }
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = "notif",
                    packageName = "notif",
                    statusBarChipIcon = createStatusBarIconViewOrNull(),
                    promotedContent = promotedContentBuilder.build(),
                )
            )

            val latest by collectLastValue(underTest.chips)

            // Squished chips are icon only
            assertThat(latest!!.active[0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.IconOnly::class.java)
            assertThat(latest!!.active[1].content)
                .isInstanceOf(OngoingActivityChipModel.Content.IconOnly::class.java)
            assertThat(latest!!.active[2].content)
                .isInstanceOf(OngoingActivityChipModel.Content.IconOnly::class.java)
        }

    @DisableChipsModernization
    @Test
    fun chipsLegacy_countdownChipAndTimerChip_countdownNotSquished_butTimerSquished() =
        kosmos.runTest {
            screenRecordState.value = ScreenRecordModel.Starting(millisUntilStarted = 2000)
            addOngoingCallState(key = "call", isAppVisible = false)

            val latest by collectLastValue(underTest.chipsLegacy)

            // The screen record countdown isn't squished to icon-only
            assertThat((latest!!.primary as OngoingActivityChipModel.Active).content)
                .isInstanceOf(OngoingActivityChipModel.Content.Countdown::class.java)
            // But the call chip *is* squished
            assertThat((latest!!.secondary as OngoingActivityChipModel.Active).content)
                .isInstanceOf(OngoingActivityChipModel.Content.IconOnly::class.java)
        }

    @EnableChipsModernization
    @Test
    fun chips_countdownChipAndTimerChip_countdownNotSquished_butTimerSquished() =
        kosmos.runTest {
            screenRecordState.value = ScreenRecordModel.Starting(millisUntilStarted = 2000)
            addOngoingCallState(key = "call")

            val latest by collectLastValue(underTest.chips)

            // The screen record countdown isn't squished to icon-only
            assertThat(latest!!.active[0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.Countdown::class.java)
            // But the call chip *is* squished
            assertThat(latest!!.active[1].content)
                .isInstanceOf(OngoingActivityChipModel.Content.IconOnly::class.java)
        }

    @DisableChipsModernization
    @Test
    fun chipsLegacy_numberOfChipsChanges_chipsGetSquishedAndUnsquished() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chipsLegacy)

            // WHEN there's only one chip
            screenRecordState.value = ScreenRecordModel.Recording
            removeOngoingCallState(key = "call")

            // The screen record isn't squished because it's the only one
            assertThat((latest!!.primary as OngoingActivityChipModel.Active).content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
            assertThat(latest!!.secondary)
                .isInstanceOf(OngoingActivityChipModel.Inactive::class.java)

            // WHEN there's 2 chips
            addOngoingCallState(key = "call", isAppVisible = false)

            // THEN they both become squished
            assertThat((latest!!.primary as OngoingActivityChipModel.Active).content)
                .isInstanceOf(OngoingActivityChipModel.Content.IconOnly::class.java)
            // But the call chip *is* squished
            assertThat((latest!!.secondary as OngoingActivityChipModel.Active).content)
                .isInstanceOf(OngoingActivityChipModel.Content.IconOnly::class.java)

            // WHEN we go back down to 1 chip
            screenRecordState.value = ScreenRecordModel.DoingNothing

            // THEN the remaining chip unsquishes
            assertThat((latest!!.primary as OngoingActivityChipModel.Active).content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
            assertThat(latest!!.secondary)
                .isInstanceOf(OngoingActivityChipModel.Inactive::class.java)
        }

    @EnableChipsModernization
    @Test
    fun chips_numberOfChipsChanges_chipsGetSquishedAndUnsquished() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)

            // WHEN there's only one chip
            screenRecordState.value = ScreenRecordModel.Recording
            removeOngoingCallState(key = "call")

            // The screen record isn't squished because it's the only one
            assertThat(latest!!.active[0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)

            // WHEN there's 2 chips
            addOngoingCallState(key = "call")

            // THEN they both become squished
            assertThat(latest!!.active[0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.IconOnly::class.java)
            // But the call chip *is* squished
            assertThat(latest!!.active[1].content)
                .isInstanceOf(OngoingActivityChipModel.Content.IconOnly::class.java)

            // WHEN we go back down to 1 chip
            screenRecordState.value = ScreenRecordModel.DoingNothing

            // THEN the remaining chip unsquishes
            assertThat(latest!!.active[0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
        }

    @DisableChipsModernization
    @Test
    fun chipsLegacy_twoChips_isWideScreen_notSquished() =
        kosmos.runTest {
            screenRecordState.value = ScreenRecordModel.Recording
            addOngoingCallState(key = "call", isAppVisible = false)

            // WHEN we're on a wide screen
            kosmos.displayStateRepository.setIsWideScreen(true)

            val latest by collectLastValue(underTest.chipsLegacy)

            // THEN the chips aren't squished (squished chips would be icon only)
            assertThat((latest!!.primary as OngoingActivityChipModel.Active).content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
            assertThat((latest!!.secondary as OngoingActivityChipModel.Active).content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
        }

    @EnableChipsModernization
    @Test
    fun chips_twoChips_isWideScreen_notSquished() =
        kosmos.runTest {
            screenRecordState.value = ScreenRecordModel.Recording
            addOngoingCallState(key = "call")

            // WHEN we're on a wide screen
            kosmos.displayStateRepository.setIsWideScreen(true)

            val latest by collectLastValue(underTest.chips)

            // THEN the chips aren't squished (squished chips would be icon only)
            assertThat(latest!!.active[0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
            assertThat(latest!!.active[1].content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
        }

    @Test
    fun primaryChip_screenRecordShowAndShareToAppShow_screenRecordShown() =
        kosmos.runTest {
            screenRecordState.value = ScreenRecordModel.Recording
            mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(NORMAL_PACKAGE)
            removeOngoingCallState(key = "call")

            val latest by collectLastValue(underTest.primaryChip)

            assertIsScreenRecordChip(latest)
        }

    @DisableChipsModernization
    @Test
    fun chipsLegacy_screenRecordShowAndShareToAppShow_primaryIsScreenRecordSecondaryIsHidden() =
        kosmos.runTest {
            screenRecordState.value = ScreenRecordModel.Recording
            mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(NORMAL_PACKAGE)
            removeOngoingCallState(key = "call")

            val latest by collectLastValue(underTest.chipsLegacy)
            val unused by collectLastValue(underTest.chips)

            assertIsScreenRecordChip(latest!!.primary)
            // Even though share-to-app is active, we suppress it because this share-to-app is
            // represented by screen record being active. See b/296461748.
            assertThat(latest!!.secondary)
                .isInstanceOf(OngoingActivityChipModel.Inactive::class.java)
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModel())
        }

    @EnableChipsModernization
    @Test
    fun chips_screenRecordAndShareToApp_screenRecordIsActiveShareToAppIsInOverflow() =
        kosmos.runTest {
            screenRecordState.value = ScreenRecordModel.Recording
            mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(NORMAL_PACKAGE)
            setNotifs(emptyList())

            val latest by collectLastValue(underTest.chips)
            val unused by collectLastValue(underTest.chipsLegacy)

            assertThat(latest!!.active.size).isEqualTo(1)
            assertIsScreenRecordChip(latest!!.active[0])
            // Even though share-to-app is active, we suppress it because this share-to-app is
            // represented by screen record being active. See b/296461748.
            assertThat(latest!!.overflow.size).isEqualTo(1)
            assertIsShareToAppChip(latest!!.overflow[0])
            assertThat(latest!!.inactive.size).isEqualTo(2)
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModelLegacy())
        }

    @Test
    fun primaryChip_shareToAppShowAndCallShow_shareToAppShown() =
        kosmos.runTest {
            screenRecordState.value = ScreenRecordModel.DoingNothing
            mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(NORMAL_PACKAGE)
            addOngoingCallState(key = "call", isAppVisible = false)

            val latest by collectLastValue(underTest.primaryChip)

            assertIsShareToAppChip(latest)
        }

    @DisableChipsModernization
    @Test
    fun chipsLegacy_shareToAppShowAndCallShow_primaryIsShareToAppSecondaryIsCall() =
        kosmos.runTest {
            val callNotificationKey = "call"
            screenRecordState.value = ScreenRecordModel.DoingNothing
            mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(NORMAL_PACKAGE)
            addOngoingCallState(key = "call", isAppVisible = false)

            val latest by collectLastValue(underTest.chipsLegacy)
            val unused by collectLastValue(underTest.chips)

            assertIsShareToAppChip(latest!!.primary)
            assertIsCallChip(latest!!.secondary, callNotificationKey, context)
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModel())
        }

    @EnableChipsModernization
    @Test
    fun chips_shareToAppAndCallActive() =
        kosmos.runTest {
            val callNotificationKey = "call"
            screenRecordState.value = ScreenRecordModel.DoingNothing
            mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(NORMAL_PACKAGE)
            addOngoingCallState(key = callNotificationKey)

            val latest by collectLastValue(underTest.chips)
            val unused by collectLastValue(underTest.chipsLegacy)

            assertThat(latest!!.active.size).isEqualTo(2)
            assertIsShareToAppChip(latest!!.active[0])
            assertIsCallChip(latest!!.active[1], callNotificationKey, context)
            assertThat(latest!!.overflow).isEmpty()
            assertThat(latest!!.inactive.size).isEqualTo(2)
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModelLegacy())
        }

    @Test
    fun primaryChip_onlyCallShown_callShown() =
        kosmos.runTest {
            screenRecordState.value = ScreenRecordModel.DoingNothing
            // MediaProjection covers both share-to-app and cast-to-other-device
            mediaProjectionState.value = MediaProjectionState.NotProjecting

            val callNotificationKey = "call"
            addOngoingCallState(key = callNotificationKey, isAppVisible = false)

            val latest by collectLastValue(underTest.primaryChip)

            assertIsCallChip(latest, callNotificationKey, context)
        }

    @DisableChipsModernization
    @Test
    fun chipsLegacy_onlyCallShown_primaryIsCallSecondaryIsHidden() =
        kosmos.runTest {
            val callNotificationKey = "call"
            screenRecordState.value = ScreenRecordModel.DoingNothing
            // MediaProjection covers both share-to-app and cast-to-other-device
            mediaProjectionState.value = MediaProjectionState.NotProjecting

            addOngoingCallState(key = callNotificationKey, isAppVisible = false)

            val latest by collectLastValue(underTest.chipsLegacy)
            val unused by collectLastValue(underTest.chips)

            assertIsCallChip(latest!!.primary, callNotificationKey, context)
            assertThat(latest!!.secondary)
                .isInstanceOf(OngoingActivityChipModel.Inactive::class.java)
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModel())
        }

    @EnableChipsModernization
    @Test
    fun chips_callActive_restInactive() =
        kosmos.runTest {
            val callNotificationKey = "call"
            screenRecordState.value = ScreenRecordModel.DoingNothing
            // MediaProjection covers both share-to-app and cast-to-other-device
            mediaProjectionState.value = MediaProjectionState.NotProjecting
            addOngoingCallState(key = callNotificationKey)

            val latest by collectLastValue(underTest.chips)
            val unused by collectLastValue(underTest.chipsLegacy)

            assertThat(latest!!.active.size).isEqualTo(1)
            assertIsCallChip(latest!!.active[0], callNotificationKey, context)
            assertThat(latest!!.overflow).isEmpty()
            assertThat(latest!!.inactive.size).isEqualTo(3)
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModelLegacy())
        }

    @DisableChipsModernization
    @Test
    fun chipsLegacy_singlePromotedNotif_primaryIsNotifSecondaryIsHidden() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chipsLegacy)
            val unused by collectLastValue(underTest.chips)

            val icon = createStatusBarIconViewOrNull()
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        packageName = "notif",
                        statusBarChipIcon = icon,
                        promotedContent = PromotedNotificationContentBuilder("notif").build(),
                    )
                )
            )

            assertIsNotifChip(latest!!.primary, context, icon, "notif")
            assertThat(latest!!.secondary)
                .isInstanceOf(OngoingActivityChipModel.Inactive::class.java)
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModel())
        }

    @EnableChipsModernization
    @Test
    fun chips_singlePromotedNotif() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)
            val unused by collectLastValue(underTest.chipsLegacy)

            val icon = createStatusBarIconViewOrNull()
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        packageName = "notif",
                        statusBarChipIcon = icon,
                        promotedContent = PromotedNotificationContentBuilder("notif").build(),
                    )
                )
            )

            assertThat(latest!!.active.size).isEqualTo(1)
            assertIsNotifChip(latest!!.active[0], context, icon, "notif")
            assertThat(latest!!.overflow).isEmpty()
            assertThat(latest!!.inactive.size).isEqualTo(4)
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModelLegacy())
        }

    @DisableChipsModernization
    @Test
    fun chipsLegacy_twoPromotedNotifs_primaryAndSecondaryAreNotifsInOrder() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chipsLegacy)
            val unused by collectLastValue(underTest.chips)

            val firstIcon = createStatusBarIconViewOrNull()
            val secondIcon = createStatusBarIconViewOrNull()
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "firstNotif",
                        packageName = "firstNotif",
                        statusBarChipIcon = firstIcon,
                        promotedContent = PromotedNotificationContentBuilder("firstNotif").build(),
                    ),
                    activeNotificationModel(
                        key = "secondNotif",
                        packageName = "secondNotif",
                        statusBarChipIcon = secondIcon,
                        promotedContent = PromotedNotificationContentBuilder("secondNotif").build(),
                    ),
                )
            )

            assertIsNotifChip(latest!!.primary, context, firstIcon, "firstNotif")
            assertIsNotifChip(latest!!.secondary, context, secondIcon, "secondNotif")
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModel())
        }

    @EnableChipsModernization
    @Test
    fun chips_twoPromotedNotifs_bothActiveInOrder() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)
            val unused by collectLastValue(underTest.chipsLegacy)

            val firstIcon = createStatusBarIconViewOrNull()
            val secondIcon = createStatusBarIconViewOrNull()
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "firstNotif",
                        packageName = "firstNotif",
                        statusBarChipIcon = firstIcon,
                        promotedContent = PromotedNotificationContentBuilder("firstNotif").build(),
                    ),
                    activeNotificationModel(
                        key = "secondNotif",
                        packageName = "secondNotif",
                        statusBarChipIcon = secondIcon,
                        promotedContent = PromotedNotificationContentBuilder("secondNotif").build(),
                    ),
                )
            )

            assertThat(latest!!.active.size).isEqualTo(2)
            assertIsNotifChip(latest!!.active[0], context, firstIcon, "firstNotif")
            assertIsNotifChip(latest!!.active[1], context, secondIcon, "secondNotif")
            assertThat(latest!!.overflow).isEmpty()
            assertThat(latest!!.inactive.size).isEqualTo(4)
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModelLegacy())
        }

    @DisableChipsModernization
    @Test
    fun chipsLegacy_threePromotedNotifs_topTwoShown() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chipsLegacy)
            val unused by collectLastValue(underTest.chips)

            val firstIcon = createStatusBarIconViewOrNull()
            val secondIcon = createStatusBarIconViewOrNull()
            val thirdIcon = createStatusBarIconViewOrNull()
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "firstNotif",
                        packageName = "firstNotif",
                        statusBarChipIcon = firstIcon,
                        promotedContent = PromotedNotificationContentBuilder("firstNotif").build(),
                    ),
                    activeNotificationModel(
                        key = "secondNotif",
                        packageName = "secondNotif",
                        statusBarChipIcon = secondIcon,
                        promotedContent = PromotedNotificationContentBuilder("secondNotif").build(),
                    ),
                    activeNotificationModel(
                        key = "thirdNotif",
                        packageName = "thirdNotif",
                        statusBarChipIcon = thirdIcon,
                        promotedContent = PromotedNotificationContentBuilder("thirdNotif").build(),
                    ),
                )
            )

            assertIsNotifChip(latest!!.primary, context, firstIcon, "firstNotif")
            assertIsNotifChip(latest!!.secondary, context, secondIcon, "secondNotif")
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModel())
        }

    @EnableChipsModernization
    @Test
    fun chips_fourPromotedNotifs_topThreeActiveFourthInOverflow() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)
            val unused by collectLastValue(underTest.chipsLegacy)

            val firstIcon = createStatusBarIconViewOrNull()
            val secondIcon = createStatusBarIconViewOrNull()
            val thirdIcon = createStatusBarIconViewOrNull()
            val fourthIcon = createStatusBarIconViewOrNull()
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "firstNotif",
                        packageName = "firstNotif",
                        statusBarChipIcon = firstIcon,
                        promotedContent = PromotedNotificationContentBuilder("firstNotif").build(),
                    ),
                    activeNotificationModel(
                        key = "secondNotif",
                        packageName = "secondNotif",
                        statusBarChipIcon = secondIcon,
                        promotedContent = PromotedNotificationContentBuilder("secondNotif").build(),
                    ),
                    activeNotificationModel(
                        key = "thirdNotif",
                        packageName = "thirdNotif",
                        statusBarChipIcon = thirdIcon,
                        promotedContent = PromotedNotificationContentBuilder("thirdNotif").build(),
                    ),
                    activeNotificationModel(
                        key = "fourthNotif",
                        packageName = "fourthNotif",
                        statusBarChipIcon = fourthIcon,
                        promotedContent = PromotedNotificationContentBuilder("fourthNotif").build(),
                    ),
                )
            )

            assertThat(latest!!.active.size).isEqualTo(3)
            assertIsNotifChip(latest!!.active[0], context, firstIcon, "firstNotif")
            assertIsNotifChip(latest!!.active[1], context, secondIcon, "secondNotif")
            assertIsNotifChip(latest!!.active[2], context, thirdIcon, "thirdNotif")
            assertThat(latest!!.overflow.size).isEqualTo(1)
            assertIsNotifChip(latest!!.overflow[0], context, fourthIcon, "fourthNotif")
            assertThat(latest!!.inactive.size).isEqualTo(4)
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModelLegacy())
        }

    @Test
    @DisableChipsModernization
    @DisableFlags(StatusBarChipToHunAnimation.FLAG_NAME)
    fun visibleChipsWithBounds_chipsModOff_animFlagOff_threePromotedNotifs_topTwoInList() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.visibleChipsWithBounds)

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "firstNotif",
                        packageName = "firstNotif",
                        statusBarChipIcon = createStatusBarIconViewOrNull(),
                        promotedContent = PromotedNotificationContentBuilder("firstNotif").build(),
                    ),
                    activeNotificationModel(
                        key = "secondNotif",
                        packageName = "secondNotif",
                        statusBarChipIcon = createStatusBarIconViewOrNull(),
                        promotedContent = PromotedNotificationContentBuilder("secondNotif").build(),
                    ),
                    activeNotificationModel(
                        key = "thirdNotif",
                        packageName = "thirdNotif",
                        statusBarChipIcon = createStatusBarIconViewOrNull(),
                        promotedContent = PromotedNotificationContentBuilder("thirdNotif").build(),
                    ),
                )
            )

            assertThat(latest!!.keys).containsExactly("firstNotif", "secondNotif")
        }

    @Test
    @EnableChipsModernization
    @DisableFlags(StatusBarChipToHunAnimation.FLAG_NAME)
    fun visibleChipsWithBounds_chipsModOn_animFlagOff_fourPromotedNotifs_topThreeInList() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.visibleChipsWithBounds)

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "firstNotif",
                        packageName = "firstNotif",
                        statusBarChipIcon = createStatusBarIconViewOrNull(),
                        promotedContent = PromotedNotificationContentBuilder("firstNotif").build(),
                    ),
                    activeNotificationModel(
                        key = "secondNotif",
                        packageName = "secondNotif",
                        statusBarChipIcon = createStatusBarIconViewOrNull(),
                        promotedContent = PromotedNotificationContentBuilder("secondNotif").build(),
                    ),
                    activeNotificationModel(
                        key = "thirdNotif",
                        packageName = "thirdNotif",
                        statusBarChipIcon = createStatusBarIconViewOrNull(),
                        promotedContent = PromotedNotificationContentBuilder("thirdNotif").build(),
                    ),
                    activeNotificationModel(
                        key = "fourthNotif",
                        packageName = "fourthNotif",
                        statusBarChipIcon = createStatusBarIconViewOrNull(),
                        promotedContent = PromotedNotificationContentBuilder("fourthNotif").build(),
                    ),
                )
            )

            assertThat(latest!!.keys).containsExactly("firstNotif", "secondNotif", "thirdNotif")
        }

    @Test
    @EnableChipsModernization
    @EnableFlags(StatusBarChipToHunAnimation.FLAG_NAME)
    fun visibleChipsWithBounds_chipsModOn_animFlagOn_fourPromotedNotifs_topThreeInListWithBounds() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.visibleChipsWithBounds)

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "firstNotif",
                        packageName = "firstNotif",
                        statusBarChipIcon = createStatusBarIconViewOrNull(),
                        promotedContent = PromotedNotificationContentBuilder("firstNotif").build(),
                    ),
                    activeNotificationModel(
                        key = "secondNotif",
                        packageName = "secondNotif",
                        statusBarChipIcon = createStatusBarIconViewOrNull(),
                        promotedContent = PromotedNotificationContentBuilder("secondNotif").build(),
                    ),
                    activeNotificationModel(
                        key = "thirdNotif",
                        packageName = "thirdNotif",
                        statusBarChipIcon = createStatusBarIconViewOrNull(),
                        promotedContent = PromotedNotificationContentBuilder("thirdNotif").build(),
                    ),
                    activeNotificationModel(
                        key = "fourthNotif",
                        packageName = "fourthNotif",
                        statusBarChipIcon = createStatusBarIconViewOrNull(),
                        promotedContent = PromotedNotificationContentBuilder("fourthNotif").build(),
                    ),
                )
            )

            underTest.onChipBoundsChanged("firstNotif", RectF(1f, 1f, 1f, 1f))
            underTest.onChipBoundsChanged("secondNotif", RectF(2f, 2f, 2f, 2f))
            underTest.onChipBoundsChanged("thirdNotif", RectF(3f, 3f, 3f, 3f))
            underTest.onChipBoundsChanged("fourthNotif", RectF(4f, 4f, 4f, 4f))

            assertThat(latest!!["firstNotif"]).isEqualTo(RectF(1f, 1f, 1f, 1f))
            assertThat(latest!!["secondNotif"]).isEqualTo(RectF(2f, 2f, 2f, 2f))
            assertThat(latest!!["thirdNotif"]).isEqualTo(RectF(3f, 3f, 3f, 3f))
            assertThat(latest).doesNotContainKey("fourthNotif")
        }

    @Test
    @EnableChipsModernization
    fun chips_callNotifDidNotRequestPromotion_showsCallChip() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)

            val key = "call-notPromoted"
            addOngoingCallState(
                key = key,
                statusBarChipIconView = createStatusBarIconViewOrNull(),
                requestedPromotion = false,
                promotedContent = OngoingCallTestHelper.PromotedContentInput.OverrideToNull,
            )

            assertIsCallChip(latest!!.active[0], key, context)
        }

    @Test
    @DisableChipsModernization
    fun chipsLegacy_callNotifDidNotRequestPromotion_showsCallChip() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chipsLegacy)

            val key = "call-notPromoted"
            val icon = createStatusBarIconViewOrNull()
            // When ChipsModernization is disabled, activeNotificationListRepository isn't the
            // source of truth for call notifs, so we have to set the information in 2 places.
            addOngoingCallState(
                key = key,
                statusBarChipIconView = icon,
                requestedPromotion = false,
                promotedContent = OngoingCallTestHelper.PromotedContentInput.OverrideToNull,
            )
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = key,
                    statusBarChipIcon = icon,
                    callType = CallType.Ongoing,
                    requestedPromotion = false,
                    promotedContent = null,
                )
            )

            assertIsCallChip(latest!!.primary, key, context)
        }

    @Test
    @EnableChipsModernization
    fun chips_callNotifRequestedPromotionAndIsPromoted_showsNotifChip() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)

            val key = "call-requestedPromoted-andPromoted"
            val icon = createStatusBarIconViewOrNull()
            addOngoingCallState(
                key = key,
                statusBarChipIconView = icon,
                requestedPromotion = true,
                promotedContent =
                    OngoingCallTestHelper.PromotedContentInput.OverrideToValue(
                        PromotedNotificationContentBuilder(key).build()
                    ),
            )

            assertIsNotifChip(latest!!.active[0], context, icon, key)
        }

    @Test
    @DisableChipsModernization
    fun chipsLegacy_callNotifRequestedPromotionAndIsPromoted_showsNotifChip() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chipsLegacy)

            val key = "call-requestedPromoted-andPromoted"
            val icon = createStatusBarIconViewOrNull()
            val promotedContent = PromotedNotificationContentBuilder(key).build()
            // When ChipsModernization is disabled, activeNotificationListRepository isn't the
            // source of truth for call notifs, so we have to set the information in 2 places.
            addOngoingCallState(
                key = key,
                statusBarChipIconView = icon,
                requestedPromotion = true,
                promotedContent =
                    OngoingCallTestHelper.PromotedContentInput.OverrideToValue(promotedContent),
            )
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = key,
                    statusBarChipIcon = icon,
                    callType = CallType.Ongoing,
                    requestedPromotion = true,
                    promotedContent = promotedContent,
                )
            )

            assertIsNotifChip(latest!!.primary, context, icon, key)
        }

    @Test
    @EnableChipsModernization
    fun chips_callNotifRequestedPromotionButNotPromoted_noChipsShown() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)

            val key = "call-requestedPromoted-butNotPromoted"
            val icon = createStatusBarIconViewOrNull()
            addOngoingCallState(
                key = key,
                statusBarChipIconView = icon,
                requestedPromotion = true,
                // Content will be null if notif wasn't actually promoted
                promotedContent = OngoingCallTestHelper.PromotedContentInput.OverrideToNull,
            )

            assertThat(latest!!.active).isEmpty()
        }

    @Test
    @DisableChipsModernization
    fun chipsLegacy_callNotifRequestedPromotionButNotPromoted_noChipsShown() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chipsLegacy)

            val key = "call-requestedPromoted-butNotPromoted"
            val icon = createStatusBarIconViewOrNull()
            // When ChipsModernization is disabled, activeNotificationListRepository isn't the
            // source of truth for call notifs, so we have to set the information in 2 places.
            addOngoingCallState(
                key = key,
                statusBarChipIconView = icon,
                requestedPromotion = true,
                // Content will be null if notif wasn't actually promoted
                promotedContent = OngoingCallTestHelper.PromotedContentInput.OverrideToNull,
            )
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = key,
                    statusBarChipIcon = icon,
                    callType = CallType.Ongoing,
                    requestedPromotion = true,
                    promotedContent = null,
                )
            )

            assertThat(latest!!.primary).isInstanceOf(OngoingActivityChipModel.Inactive::class.java)
        }

    @DisableChipsModernization
    @Test
    fun chipsLegacy_callAndPromotedNotifs_primaryIsCallSecondaryIsNotif() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chipsLegacy)
            val unused by collectLastValue(underTest.chips)

            val callNotificationKey = "call"
            addOngoingCallState(callNotificationKey, isAppVisible = false)

            val firstIcon = createStatusBarIconViewOrNull()
            activeNotificationListRepository.addNotifs(
                listOf(
                    activeNotificationModel(
                        key = "firstNotif",
                        packageName = "firstNotif",
                        statusBarChipIcon = firstIcon,
                        promotedContent = PromotedNotificationContentBuilder("firstNotif").build(),
                    ),
                    activeNotificationModel(
                        key = "secondNotif",
                        packageName = "secondNotif",
                        statusBarChipIcon = createStatusBarIconViewOrNull(),
                        promotedContent = PromotedNotificationContentBuilder("secondNotif").build(),
                    ),
                )
            )

            assertIsCallChip(latest!!.primary, callNotificationKey, context)
            assertIsNotifChip(latest!!.secondary, context, firstIcon, "firstNotif")
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModel())
        }

    @EnableChipsModernization
    @Test
    fun chips_callAndPromotedNotifs_callAndFirstTwoNotifsActive_thirdNotifInOverflow() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)
            val unused by collectLastValue(underTest.chipsLegacy)

            val callNotificationKey = "call"
            val firstIcon = createStatusBarIconViewOrNull()
            val secondIcon = createStatusBarIconViewOrNull()
            val thirdIcon = createStatusBarIconViewOrNull()
            addOngoingCallState(key = callNotificationKey)
            activeNotificationListRepository.addNotifs(
                listOf(
                    activeNotificationModel(
                        key = "firstNotif",
                        packageName = "firstNotif",
                        statusBarChipIcon = firstIcon,
                        promotedContent = PromotedNotificationContentBuilder("firstNotif").build(),
                    ),
                    activeNotificationModel(
                        key = "secondNotif",
                        packageName = "secondNotif",
                        statusBarChipIcon = secondIcon,
                        promotedContent = PromotedNotificationContentBuilder("secondNotif").build(),
                    ),
                    activeNotificationModel(
                        key = "thirdNotif",
                        packageName = "thirdNotif",
                        statusBarChipIcon = thirdIcon,
                        promotedContent = PromotedNotificationContentBuilder("thirdNotif").build(),
                    ),
                )
            )

            assertThat(latest!!.active.size).isEqualTo(3)
            assertIsCallChip(latest!!.active[0], callNotificationKey, context)
            assertIsNotifChip(latest!!.active[1], context, firstIcon, "firstNotif")
            assertIsNotifChip(latest!!.active[2], context, secondIcon, "secondNotif")
            assertThat(latest!!.overflow.size).isEqualTo(1)
            assertIsNotifChip(latest!!.overflow[0], context, thirdIcon, "thirdNotif")
            assertThat(latest!!.inactive.size).isEqualTo(3)
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModelLegacy())
        }

    @DisableChipsModernization
    @Test
    fun chipsLegacy_screenRecordAndCallAndPromotedNotif_notifNotShown() =
        kosmos.runTest {
            val callNotificationKey = "call"
            val latest by collectLastValue(underTest.chipsLegacy)
            val unused by collectLastValue(underTest.chips)

            addOngoingCallState(callNotificationKey, isAppVisible = false)
            screenRecordState.value = ScreenRecordModel.Recording
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = "notif",
                    statusBarChipIcon = createStatusBarIconViewOrNull(),
                    promotedContent = PromotedNotificationContentBuilder("notif").build(),
                )
            )

            assertIsScreenRecordChip(latest!!.primary)
            assertIsCallChip(latest!!.secondary, callNotificationKey, context)
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModel())
        }

    @Test
    @DisableChipsModernization
    @DisableFlags(StatusBarChipToHunAnimation.FLAG_NAME)
    fun visibleChipsWithBounds_chipsModOff_animFlagOff_screenRecordAndCallAndPromotedNotifs_topTwoInList() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.visibleChipsWithBounds)

            val callNotificationKey = "call"
            addOngoingCallState(callNotificationKey)
            screenRecordState.value = ScreenRecordModel.Recording
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = "notif1",
                    packageName = "notif1",
                    statusBarChipIcon = createStatusBarIconViewOrNull(),
                    promotedContent = PromotedNotificationContentBuilder("notif1").build(),
                )
            )
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = "notif2",
                    packageName = "notif2",
                    statusBarChipIcon = createStatusBarIconViewOrNull(),
                    promotedContent = PromotedNotificationContentBuilder("notif2").build(),
                )
            )

            assertThat(latest!!.map { it.key })
                .containsExactly(
                    ScreenRecordChipViewModel.KEY,
                    "${CallChipViewModel.KEY_PREFIX}$callNotificationKey",
                )
                .inOrder()
        }

    @Test
    @EnableChipsModernization
    @DisableFlags(StatusBarChipToHunAnimation.FLAG_NAME)
    fun visibleChipsWithBounds_chipsModOn_animFlagOff_screenRecordAndCallAndPromotedNotifs_topThreeInList() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.visibleChipsWithBounds)

            val callNotificationKey = "call"
            addOngoingCallState(callNotificationKey)
            screenRecordState.value = ScreenRecordModel.Recording
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = "notif1",
                    packageName = "notif1",
                    statusBarChipIcon = createStatusBarIconViewOrNull(),
                    promotedContent = PromotedNotificationContentBuilder("notif1").build(),
                )
            )
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = "notif2",
                    packageName = "notif2",
                    statusBarChipIcon = createStatusBarIconViewOrNull(),
                    promotedContent = PromotedNotificationContentBuilder("notif2").build(),
                )
            )

            assertThat(latest!!.map { it.key })
                .containsExactly(
                    ScreenRecordChipViewModel.KEY,
                    "${CallChipViewModel.KEY_PREFIX}$callNotificationKey",
                    "notif1",
                )
                .inOrder()
        }

    @Test
    @EnableChipsModernization
    @EnableFlags(StatusBarChipToHunAnimation.FLAG_NAME)
    fun visibleChipsWithBounds_chipsModOn_animFlagOn_screenRecordAndCallAndPromotedNotifs_topThreeInListWithBounds() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.visibleChipsWithBounds)

            val callNotificationKey = "call"
            val callKeyForChip = "${CallChipViewModel.KEY_PREFIX}$callNotificationKey"
            addOngoingCallState(callNotificationKey)
            screenRecordState.value = ScreenRecordModel.Recording
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = "notif1",
                    packageName = "notif1",
                    statusBarChipIcon = createStatusBarIconViewOrNull(),
                    promotedContent = PromotedNotificationContentBuilder("notif1").build(),
                )
            )
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = "notif2",
                    packageName = "notif2",
                    statusBarChipIcon = createStatusBarIconViewOrNull(),
                    promotedContent = PromotedNotificationContentBuilder("notif2").build(),
                )
            )

            underTest.onChipBoundsChanged(ScreenRecordChipViewModel.KEY, RectF(1f, 1f, 1f, 1f))
            underTest.onChipBoundsChanged(callKeyForChip, RectF(2f, 2f, 2f, 2f))
            underTest.onChipBoundsChanged("notif1", RectF(3f, 3f, 3f, 3f))
            underTest.onChipBoundsChanged("notif2", RectF(4f, 4f, 4f, 4f))

            assertThat(latest!![ScreenRecordChipViewModel.KEY]).isEqualTo(RectF(1f, 1f, 1f, 1f))
            assertThat(latest!![callKeyForChip]).isEqualTo(RectF(2f, 2f, 2f, 2f))
            assertThat(latest!!["notif1"]).isEqualTo(RectF(3f, 3f, 3f, 3f))
            assertThat(latest).doesNotContainKey("notif2")
        }

    // The ranking between different chips should stay consistent between
    // OngoingActivityChipsViewModel and PromotedNotificationsInteractor.
    // Make sure to also change
    // PromotedNotificationsInteractorTest#orderedChipNotificationKeys_rankingIsCorrect
    // if you change this test.
    @EnableChipsModernization
    @Test
    fun chips_screenRecordAndCallAndPromotedNotifs_secondNotifInOverflow() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)
            val unused by collectLastValue(underTest.chipsLegacy)

            val callNotificationKey = "call"
            val notifIcon = createStatusBarIconViewOrNull()
            screenRecordState.value = ScreenRecordModel.Recording
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = "notif",
                    packageName = "notif",
                    statusBarChipIcon = notifIcon,
                    promotedContent = PromotedNotificationContentBuilder("notif").build(),
                )
            )
            addOngoingCallState(key = callNotificationKey)

            // This is the overflow notif
            val notifIcon2 = createStatusBarIconViewOrNull()
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = "notif2",
                    packageName = "notif2",
                    statusBarChipIcon = notifIcon2,
                    promotedContent = PromotedNotificationContentBuilder("notif2").build(),
                )
            )

            assertThat(latest!!.active.size).isEqualTo(3)
            assertIsScreenRecordChip(latest!!.active[0])
            assertIsCallChip(latest!!.active[1], callNotificationKey, context)
            assertIsNotifChip(latest!!.active[2], context, notifIcon, "notif")
            assertThat(latest!!.overflow.size).isEqualTo(1)
            assertIsNotifChip(latest!!.overflow[0], context, notifIcon2, "notif2")
            assertThat(latest!!.inactive.size).isEqualTo(2)
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModelLegacy())
        }

    @Test
    fun primaryChip_higherPriorityChipAdded_lowerPriorityChipReplaced() =
        kosmos.runTest {
            val callNotificationKey = "call"
            // Start with just the lowest priority chip shown
            val notifIcon = createStatusBarIconViewOrNull()
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        packageName = "notif",
                        statusBarChipIcon = notifIcon,
                        promotedContent = PromotedNotificationContentBuilder("notif").build(),
                    )
                )
            )
            // And everything else hidden
            removeOngoingCallState(key = callNotificationKey)
            mediaProjectionState.value = MediaProjectionState.NotProjecting
            screenRecordState.value = ScreenRecordModel.DoingNothing

            val latest by collectLastValue(underTest.primaryChip)

            assertIsNotifChip(latest, context, notifIcon, "notif")

            // WHEN the higher priority call chip is added
            addOngoingCallState(callNotificationKey, isAppVisible = false)

            // THEN the higher priority call chip is used
            assertIsCallChip(latest, callNotificationKey, context)

            // WHEN the higher priority media projection chip is added
            mediaProjectionState.value =
                MediaProjectionState.Projecting.SingleTask(
                    NORMAL_PACKAGE,
                    hostDeviceName = null,
                    createTask(taskId = 1),
                )

            // THEN the higher priority media projection chip is used
            assertIsShareToAppChip(latest)

            // WHEN the higher priority screen record chip is added
            screenRecordState.value = ScreenRecordModel.Recording

            // THEN the higher priority screen record chip is used
            assertIsScreenRecordChip(latest)
        }

    @Test
    fun primaryChip_highestPriorityChipRemoved_showsNextPriorityChip() =
        kosmos.runTest {
            val callNotificationKey = "call"
            // WHEN all chips are active
            screenRecordState.value = ScreenRecordModel.Recording
            mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(NORMAL_PACKAGE)
            addOngoingCallState(callNotificationKey, isAppVisible = false)
            val notifIcon = createStatusBarIconViewOrNull()
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = "notif",
                    packageName = "notif",
                    statusBarChipIcon = notifIcon,
                    promotedContent = PromotedNotificationContentBuilder("notif").build(),
                )
            )

            val latest by collectLastValue(underTest.primaryChip)

            // THEN the highest priority screen record is used
            assertIsScreenRecordChip(latest)

            // WHEN the higher priority screen record is removed
            screenRecordState.value = ScreenRecordModel.DoingNothing

            // THEN the lower priority media projection is used
            assertIsShareToAppChip(latest)

            // WHEN the higher priority media projection is removed
            mediaProjectionState.value = MediaProjectionState.NotProjecting

            // THEN the lower priority call is used
            assertIsCallChip(latest, callNotificationKey, context)

            // WHEN the higher priority call is removed
            removeOngoingCallState(key = callNotificationKey)

            // THEN the lower priority notif is used
            assertIsNotifChip(latest, context, notifIcon, "notif")
        }

    @DisableChipsModernization
    @Test
    fun chipsLegacy_movesChipsAroundAccordingToPriority() =
        kosmos.runTest {
            val callNotificationKey = "call"
            // Start with just the lowest priority chip shown
            val notifIcon = createStatusBarIconViewOrNull()
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = "notif",
                    packageName = "notif",
                    statusBarChipIcon = notifIcon,
                    promotedContent = PromotedNotificationContentBuilder("notif").build(),
                )
            )
            // And everything else hidden
            removeOngoingCallState(key = callNotificationKey)
            mediaProjectionState.value = MediaProjectionState.NotProjecting
            screenRecordState.value = ScreenRecordModel.DoingNothing

            val latest by collectLastValue(underTest.chipsLegacy)
            val unused by collectLastValue(underTest.chips)

            assertIsNotifChip(latest!!.primary, context, notifIcon, "notif")
            assertThat(latest!!.secondary)
                .isInstanceOf(OngoingActivityChipModel.Inactive::class.java)
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModel())

            // WHEN the higher priority call chip is added
            addOngoingCallState(callNotificationKey, isAppVisible = false)

            // THEN the higher priority call chip is used as primary and notif is demoted to
            // secondary
            assertIsCallChip(latest!!.primary, callNotificationKey, context)
            assertIsNotifChip(latest!!.secondary, context, notifIcon, "notif")
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModel())

            // WHEN the higher priority media projection chip is added
            mediaProjectionState.value =
                MediaProjectionState.Projecting.SingleTask(
                    NORMAL_PACKAGE,
                    hostDeviceName = null,
                    createTask(taskId = 1),
                )

            // THEN the higher priority media projection chip is used as primary and call is demoted
            // to secondary (and notif is dropped altogether)
            assertIsShareToAppChip(latest!!.primary)
            assertIsCallChip(latest!!.secondary, callNotificationKey, context)
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModel())

            // WHEN the higher priority screen record chip is added
            screenRecordState.value = ScreenRecordModel.Recording

            // THEN the higher priority screen record chip is used
            assertIsScreenRecordChip(latest!!.primary)
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModel())

            // WHEN screen record and call is dropped
            screenRecordState.value = ScreenRecordModel.DoingNothing
            removeOngoingCallState(key = callNotificationKey)

            // THEN media projection and notif remain
            assertIsShareToAppChip(latest!!.primary)
            assertIsNotifChip(latest!!.secondary, context, notifIcon, "notif")
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModel())

            // WHEN media projection is dropped
            mediaProjectionState.value = MediaProjectionState.NotProjecting

            // THEN notif is promoted to primary
            assertIsNotifChip(latest!!.primary, context, notifIcon, "notif")
            assertThat(latest!!.secondary)
                .isInstanceOf(OngoingActivityChipModel.Inactive::class.java)
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModel())
        }

    @EnableChipsModernization
    @Test
    fun chips_movesChipsAroundAccordingToPriority() =
        kosmos.runTest {
            systemClock.setCurrentTimeMillis(10_000)
            val callNotificationKey = "call"
            // Start with just the lowest priority chip active
            val notif1Icon = createStatusBarIconViewOrNull()
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif1",
                        packageName = "notif1",
                        statusBarChipIcon = notif1Icon,
                        promotedContent = PromotedNotificationContentBuilder("notif1").build(),
                    )
                )
            )
            // And everything else hidden
            mediaProjectionState.value = MediaProjectionState.NotProjecting
            screenRecordState.value = ScreenRecordModel.DoingNothing

            val latest by collectLastValue(underTest.chips)
            val unused by collectLastValue(underTest.chipsLegacy)

            assertThat(latest!!.active.size).isEqualTo(1)
            assertIsNotifChip(latest!!.active[0], context, notif1Icon, "notif1")
            assertThat(latest!!.overflow).isEmpty()
            assertThat(latest!!.inactive.size).isEqualTo(4)
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModelLegacy())

            // WHEN the higher priority call chip is added
            addOngoingCallState(key = callNotificationKey)

            // THEN the higher priority call chip and notif1 are active in that order
            assertThat(latest!!.active.size).isEqualTo(2)
            assertIsCallChip(latest!!.active[0], callNotificationKey, context)
            assertIsNotifChip(latest!!.active[1], context, notif1Icon, "notif1")
            assertThat(latest!!.overflow).isEmpty()
            assertThat(latest!!.inactive.size).isEqualTo(3)
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModelLegacy())

            // WHEN the higher priority media projection chip is added
            mediaProjectionState.value =
                MediaProjectionState.Projecting.SingleTask(
                    NORMAL_PACKAGE,
                    hostDeviceName = null,
                    createTask(taskId = 1),
                )

            // THEN media projection, then call, then notif1 are active
            assertThat(latest!!.active.size).isEqualTo(3)
            assertIsShareToAppChip(latest!!.active[0])
            assertIsCallChip(latest!!.active[1], callNotificationKey, context)
            assertIsNotifChip(latest!!.active[2], context, notif1Icon, "notif1")
            assertThat(latest!!.overflow).isEmpty()
            assertThat(latest!!.inactive.size).isEqualTo(2)
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModelLegacy())

            // WHEN the screen record chip is added, which replaces media projection
            screenRecordState.value = ScreenRecordModel.Recording
            // AND another notification is added
            systemClock.advanceTime(2_000)
            val notif2Icon = createStatusBarIconViewOrNull()
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = "notif2",
                    packageName = "notif2",
                    statusBarChipIcon = notif2Icon,
                    promotedContent = PromotedNotificationContentBuilder("notif2").build(),
                )
            )

            // THEN screen record, then call, then notif2 are active
            assertThat(latest!!.active.size).isEqualTo(3)
            assertIsScreenRecordChip(latest!!.active[0])
            assertIsCallChip(latest!!.active[1], callNotificationKey, context)
            assertIsNotifChip(latest!!.active[2], context, notif2Icon, "notif2")

            // AND notif1 and media projection is demoted in overflow
            assertThat(latest!!.overflow.size).isEqualTo(2)
            assertIsShareToAppChip(latest!!.overflow[0])
            assertIsNotifChip(latest!!.overflow[1], context, notif1Icon, "notif1")
            assertThat(latest!!.inactive.size).isEqualTo(1)
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModelLegacy())

            // WHEN screen record and call are dropped
            screenRecordState.value = ScreenRecordModel.DoingNothing
            removeOngoingCallState(callNotificationKey)

            // THEN media projection, notif2, and notif1 remain
            assertThat(latest!!.active.size).isEqualTo(3)
            assertIsShareToAppChip(latest!!.active[0])
            assertIsNotifChip(latest!!.active[1], context, notif2Icon, "notif2")
            assertIsNotifChip(latest!!.active[2], context, notif1Icon, "notif1")
            assertThat(latest!!.overflow).isEmpty()
            assertThat(latest!!.inactive.size).isEqualTo(3)
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModelLegacy())

            // WHEN media projection is dropped
            mediaProjectionState.value = MediaProjectionState.NotProjecting
            // AND notif2 is dropped
            systemClock.advanceTime(2_000)
            activeNotificationListRepository.removeNotif("notif2")

            // THEN only notif1 is active
            assertThat(latest!!.active.size).isEqualTo(1)
            assertIsNotifChip(latest!!.active[0], context, notif1Icon, "notif1")
            assertThat(latest!!.overflow).isEmpty()
            assertThat(latest!!.inactive.size).isEqualTo(4)
            assertThat(unused).isEqualTo(MultipleOngoingActivityChipsModelLegacy())
        }

    /** Regression test for b/347726238. */
    @Test
    fun primaryChip_timerDoesNotResetAfterSubscribersRestart() =
        kosmos.runTest {
            var latest: OngoingActivityChipModel? = null

            val job1 = underTest.primaryChip.onEach { latest = it }.launchIn(testScope)

            // Start a chip with a timer
            systemClock.setElapsedRealtime(1234)
            screenRecordState.value = ScreenRecordModel.Recording

            runCurrent()

            assertThat(
                    ((latest as OngoingActivityChipModel.Active).content
                            as OngoingActivityChipModel.Content.Timer)
                        .startTimeMs
                )
                .isEqualTo(1234)

            // Stop subscribing to the chip flow
            job1.cancel()

            // Let time pass
            systemClock.setElapsedRealtime(5678)

            // WHEN we re-subscribe to the chip flow
            val job2 = underTest.primaryChip.onEach { latest = it }.launchIn(testScope)

            runCurrent()

            // THEN the old start time is still used
            assertThat(
                    ((latest as OngoingActivityChipModel.Active).content
                            as OngoingActivityChipModel.Content.Timer)
                        .startTimeMs
                )
                .isEqualTo(1234)

            job2.cancel()
        }

    /** Regression test for b/347726238. */
    @DisableChipsModernization
    @Test
    fun chipsLegacy_timerDoesNotResetAfterSubscribersRestart() =
        kosmos.runTest {
            var latest: MultipleOngoingActivityChipsModelLegacy? = null

            val job1 = underTest.chipsLegacy.onEach { latest = it }.launchIn(testScope)

            // Start a chip with a timer
            systemClock.setElapsedRealtime(1234)
            screenRecordState.value = ScreenRecordModel.Recording

            runCurrent()

            assertThat(
                    ((latest!!.primary as OngoingActivityChipModel.Active).content
                            as OngoingActivityChipModel.Content.Timer)
                        .startTimeMs
                )
                .isEqualTo(1234)

            // Stop subscribing to the chip flow
            job1.cancel()

            // Let time pass
            systemClock.setElapsedRealtime(5678)

            // WHEN we re-subscribe to the chip flow
            val job2 = underTest.chipsLegacy.onEach { latest = it }.launchIn(testScope)

            runCurrent()

            // THEN the old start time is still used
            assertThat(
                    ((latest!!.primary as OngoingActivityChipModel.Active).content
                            as OngoingActivityChipModel.Content.Timer)
                        .startTimeMs
                )
                .isEqualTo(1234)

            job2.cancel()
        }

    /** Regression test for b/347726238. */
    @EnableChipsModernization
    @Test
    fun chips_timerDoesNotResetAfterSubscribersRestart() =
        kosmos.runTest {
            var latest: MultipleOngoingActivityChipsModel? = null

            val job1 = underTest.chips.onEach { latest = it }.launchIn(testScope)

            // Start a chip with a timer
            systemClock.setElapsedRealtime(1234)
            screenRecordState.value = ScreenRecordModel.Recording

            runCurrent()

            assertThat(
                    (latest!!.active[0].content as OngoingActivityChipModel.Content.Timer)
                        .startTimeMs
                )
                .isEqualTo(1234)

            // Stop subscribing to the chip flow
            job1.cancel()

            // Let time pass
            systemClock.setElapsedRealtime(5678)

            // WHEN we re-subscribe to the chip flow
            val job2 = underTest.chips.onEach { latest = it }.launchIn(testScope)

            runCurrent()

            // THEN the old start time is still used
            assertThat(
                    (latest!!.active[0].content as OngoingActivityChipModel.Content.Timer)
                        .startTimeMs
                )
                .isEqualTo(1234)

            job2.cancel()
        }

    @Test
    @Ignore("b/364653005") // We'll need to re-do the animation story when we implement RON chips
    fun primaryChip_screenRecordStoppedViaDialog_chipHiddenWithoutAnimation() =
        kosmos.runTest {
            screenRecordState.value = ScreenRecordModel.Recording
            mediaProjectionState.value = MediaProjectionState.NotProjecting
            removeOngoingCallState(key = "call")

            val latest by collectLastValue(underTest.primaryChip)

            assertIsScreenRecordChip(latest)

            // WHEN screen record gets stopped via dialog
            val dialogStopAction =
                getStopActionFromDialog(
                    latest,
                    chipView,
                    mockExpandable,
                    mockSystemUIDialog,
                    kosmos,
                )
            dialogStopAction.onClick(mock<DialogInterface>(), 0)

            // THEN the chip is immediately hidden with no animation
            assertThat(latest).isEqualTo(OngoingActivityChipModel.Inactive(shouldAnimate = false))
        }

    @Test
    fun primaryChip_projectionStoppedViaDialog_chipHiddenWithoutAnimation() =
        kosmos.runTest {
            mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(NORMAL_PACKAGE)
            screenRecordState.value = ScreenRecordModel.DoingNothing
            removeOngoingCallState(key = "call")

            val latest by collectLastValue(underTest.primaryChip)

            assertIsShareToAppChip(latest)

            // WHEN media projection gets stopped via dialog
            val dialogStopAction =
                getStopActionFromDialog(
                    latest,
                    chipView,
                    mockExpandable,
                    mockSystemUIDialog,
                    kosmos,
                )
            dialogStopAction.onClick(mock<DialogInterface>(), 0)

            // THEN the chip is immediately hidden with no animation
            assertThat(latest).isEqualTo(OngoingActivityChipModel.Inactive(shouldAnimate = false))
        }

    @Test
    @EnableChipsModernization
    fun chips_singleRefiner_hidesSpecificChip() =
        kosmos.runTest {
            chipsRefinerSet.add(RemoveChipRefiner(ShareToAppChipViewModel.KEY))

            val latestChips by collectLastValue(underTest.chips)

            val callNotificationKey = "call"
            addOngoingCallState(key = callNotificationKey, isAppVisible = false)
            mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(NORMAL_PACKAGE)

            val activeChips = latestChips?.active

            assertThat(activeChips?.size).isEqualTo(1)
            assertThat(activeChips?.any { it.key == ShareToAppChipViewModel.KEY }).isFalse()
            assertThat(activeChips?.any { it.key.startsWith(CallChipViewModel.KEY_PREFIX) })
                .isTrue()

            val inactiveChips = latestChips?.inactive
            assertThat(
                    inactiveChips?.any {
                        it == OngoingActivityChipModel.Inactive(shouldAnimate = false)
                    }
                )
                .isTrue()
        }

    @Test
    @EnableChipsModernization
    fun chips_multipleRefiners_bothApplied() =
        kosmos.runTest {
            chipsRefinerSet.add(RemoveChipRefiner(ShareToAppChipViewModel.KEY))
            val changeFirstChipIconRefiner = ChangeFirstChipIconRefiner()
            chipsRefinerSet.add(changeFirstChipIconRefiner)

            val latestChips by collectLastValue(underTest.chips)

            val callNotificationKey = "callChip"
            addOngoingCallState(key = callNotificationKey, isAppVisible = false)
            mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(NORMAL_PACKAGE)
            screenRecordState.value = ScreenRecordModel.DoingNothing

            val activeChips = latestChips?.active
            assertThat(activeChips).isNotNull()
            assertThat(activeChips).isNotEmpty()

            assertThat(activeChips?.any { it.key == ShareToAppChipViewModel.KEY }).isFalse()

            val firstChip = activeChips?.firstOrNull()
            assertThat(firstChip!!.key).startsWith(CallChipViewModel.KEY_PREFIX)

            val icon = firstChip.icon
            assertThat(icon)
                .isInstanceOf(OngoingActivityChipModel.ChipIcon.SingleColorIcon::class.java)
            val singleColorIcon = icon as OngoingActivityChipModel.ChipIcon.SingleColorIcon
            val resourceIcon = singleColorIcon.impl as Icon.Resource
            assertThat(resourceIcon.resId).isEqualTo(changeFirstChipIconRefiner.newIconRes)
            assertThat(resourceIcon.contentDescription?.loadContentDescription(context))
                .isEqualTo(changeFirstChipIconRefiner.newIconDesc)
        }

    companion object {
        class RemoveChipRefiner(private val keyToRemove: String) : OngoingActivityChipsRefiner {
            override fun transform(
                input: MultipleOngoingActivityChipsModel
            ): MultipleOngoingActivityChipsModel {
                val chipWasInActive = input.active.any { it.key == keyToRemove }
                val chipWasInOverflow = input.overflow.any { it.key == keyToRemove }

                val newActive = input.active.filterNot { it.key == keyToRemove }
                val newOverflow = input.overflow.filterNot { it.key == keyToRemove }

                val currentInactive = input.inactive.toMutableList()
                if (chipWasInActive || chipWasInOverflow) {
                    // Add an Inactive model if the chip key was found in active or overflow lists.
                    // This signifies that the refiner made this chip inactive.
                    currentInactive.add(OngoingActivityChipModel.Inactive(shouldAnimate = false))
                }

                return input.copy(
                    active = newActive,
                    overflow = newOverflow,
                    inactive = currentInactive.toList(),
                )
            }
        }

        class ChangeFirstChipIconRefiner : OngoingActivityChipsRefiner {
            val newIconRes = android.R.drawable.ic_dialog_info // A standard Android resource
            val newIconDesc = "Test Changed Icon"

            override fun transform(
                input: MultipleOngoingActivityChipsModel
            ): MultipleOngoingActivityChipsModel {
                if (input.active.isNotEmpty()) {
                    val firstChip = input.active.first()
                    val modifiedFirstChip =
                        firstChip.copy(
                            icon =
                                OngoingActivityChipModel.ChipIcon.SingleColorIcon(
                                    Icon.Resource(
                                        newIconRes,
                                        ContentDescription.Loaded(newIconDesc),
                                    )
                                )
                        )
                    val newActive = listOf(modifiedFirstChip) + input.active.drop(1)
                    return input.copy(active = newActive)
                }
                return input
            }
        }
    }

    private fun setNotifs(notifs: List<ActiveNotificationModel>) {
        activeNotificationListRepository.activeNotifications.value =
            ActiveNotificationsStore.Builder()
                .apply { notifs.forEach { addIndividualNotif(it) } }
                .build()
    }
}
