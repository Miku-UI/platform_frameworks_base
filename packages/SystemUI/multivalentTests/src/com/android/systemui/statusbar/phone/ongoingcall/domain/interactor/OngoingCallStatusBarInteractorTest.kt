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

package com.android.systemui.statusbar.phone.ongoingcall.domain.interactor

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.activity.data.repository.activityManagerRepository
import com.android.systemui.activity.data.repository.fake
import com.android.systemui.ambient.statusbar.shared.flag.OngoingActivityChipsOnDream
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.statusbar.data.repository.fakeStatusBarModePerDisplayRepository
import com.android.systemui.statusbar.gesture.swipeStatusBarAwayGestureHandler
import com.android.systemui.statusbar.phone.ongoingcall.shared.model.OngoingCallModel
import com.android.systemui.statusbar.phone.ongoingcall.shared.model.OngoingCallTestHelper.addOngoingCallState
import com.android.systemui.statusbar.phone.ongoingcall.shared.model.OngoingCallTestHelper.removeOngoingCallState
import com.android.systemui.statusbar.window.fakeStatusBarWindowControllerStore
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class OngoingCallStatusBarInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest by Kosmos.Fixture { kosmos.ongoingCallStatusBarInteractor }
    private val statusBarModeRepository = kosmos.fakeStatusBarModePerDisplayRepository

    @Before
    fun setUp() {
        kosmos.underTest.start()
    }

    @Test
    fun ongoingCallNotification_setsRequiresStatusBarVisibleTrue() =
        kosmos.runTest {
            val isStatusBarRequired by collectLastValue(underTest.isStatusBarRequiredForOngoingCall)
            val requiresStatusBarVisibleInRepository by
                collectLastValue(statusBarModeRepository.ongoingProcessRequiresStatusBarVisible)
            val requiresStatusBarVisibleInWindowController by
                collectLastValue(
                    fakeStatusBarWindowControllerStore.defaultDisplay
                        .ongoingProcessRequiresStatusBarVisible
                )
            addOngoingCallState()

            assertThat(isStatusBarRequired).isTrue()
            assertThat(requiresStatusBarVisibleInRepository).isTrue()
            assertThat(requiresStatusBarVisibleInWindowController).isTrue()
        }

    @Test
    fun notificationRemoved_setsRequiresStatusBarVisibleFalse() =
        kosmos.runTest {
            val isStatusBarRequired by collectLastValue(underTest.isStatusBarRequiredForOngoingCall)
            val requiresStatusBarVisibleInRepository by
                collectLastValue(statusBarModeRepository.ongoingProcessRequiresStatusBarVisible)
            val requiresStatusBarVisibleInWindowController by
                collectLastValue(
                    fakeStatusBarWindowControllerStore.defaultDisplay
                        .ongoingProcessRequiresStatusBarVisible
                )

            addOngoingCallState(key = "testKey")

            removeOngoingCallState(key = "testKey")

            assertThat(isStatusBarRequired).isFalse()
            assertThat(requiresStatusBarVisibleInRepository).isFalse()
            assertThat(requiresStatusBarVisibleInWindowController).isFalse()
        }

    @Test
    fun ongoingCallNotification_appBecomesVisible_setsRequiresStatusBarVisibleFalse() =
        kosmos.runTest {
            val ongoingCallState by collectLastValue(ongoingCallInteractor.ongoingCallState)

            val requiresStatusBarVisibleInRepository by
                collectLastValue(statusBarModeRepository.ongoingProcessRequiresStatusBarVisible)
            val requiresStatusBarVisibleInWindowController by
                collectLastValue(
                    fakeStatusBarWindowControllerStore.defaultDisplay
                        .ongoingProcessRequiresStatusBarVisible
                )

            addOngoingCallState(uid = UID, isAppVisible = false)

            assertThat(ongoingCallState).isInstanceOf(OngoingCallModel.InCall::class.java)
            assertThat((ongoingCallState as OngoingCallModel.InCall).isAppVisible).isFalse()
            assertThat(requiresStatusBarVisibleInRepository).isTrue()
            assertThat(requiresStatusBarVisibleInWindowController).isTrue()

            kosmos.activityManagerRepository.fake.setIsAppVisible(UID, true)

            assertThat(ongoingCallState).isInstanceOf(OngoingCallModel.InCall::class.java)
            assertThat((ongoingCallState as OngoingCallModel.InCall).isAppVisible).isTrue()
            assertThat(requiresStatusBarVisibleInRepository).isFalse()
            assertThat(requiresStatusBarVisibleInWindowController).isFalse()
        }

    @Test
    fun gestureHandler_inCall_notFullscreen_doesNotListen() =
        kosmos.runTest {
            val ongoingCallState by collectLastValue(ongoingCallInteractor.ongoingCallState)

            clearInvocations(kosmos.swipeStatusBarAwayGestureHandler)
            // Set up notification but not in fullscreen
            statusBarModeRepository.isInFullscreenMode.value = false
            addOngoingCallState()

            assertThat(ongoingCallState).isInstanceOf(OngoingCallModel.InCall::class.java)
            assertThat((ongoingCallState as OngoingCallModel.InCall).isAppVisible).isFalse()
            verify(kosmos.swipeStatusBarAwayGestureHandler, never())
                .addOnGestureDetectedCallback(any(), any())
        }

    @Test
    fun gestureHandler_inCall_fullscreen_addsListener() =
        kosmos.runTest {
            val isGestureListeningEnabled by collectLastValue(underTest.isGestureListeningEnabled)

            // Set up notification and fullscreen mode
            statusBarModeRepository.isInFullscreenMode.value = true
            addOngoingCallState()

            assertThat(isGestureListeningEnabled).isTrue()
            verify(kosmos.swipeStatusBarAwayGestureHandler)
                .addOnGestureDetectedCallback(any(), any())
        }

    @Test
    fun gestureHandler_inCall_fullscreen_chipSwiped_removesListener() =
        kosmos.runTest {
            val swipeAwayState by collectLastValue(underTest.isChipSwipedAway)

            // Set up notification and fullscreen mode
            statusBarModeRepository.isInFullscreenMode.value = true
            addOngoingCallState()

            clearInvocations(kosmos.swipeStatusBarAwayGestureHandler)

            underTest.onStatusBarSwiped()

            assertThat(swipeAwayState).isTrue()
            verify(kosmos.swipeStatusBarAwayGestureHandler).removeOnGestureDetectedCallback(any())
        }

    @Test
    fun chipSwipedAway_setsRequiresStatusBarVisibleFalse() =
        kosmos.runTest {
            val isStatusBarRequiredForOngoingCall by
                collectLastValue(underTest.isStatusBarRequiredForOngoingCall)
            val requiresStatusBarVisibleInRepository by
                collectLastValue(statusBarModeRepository.ongoingProcessRequiresStatusBarVisible)
            val requiresStatusBarVisibleInWindowController by
                collectLastValue(
                    fakeStatusBarWindowControllerStore.defaultDisplay
                        .ongoingProcessRequiresStatusBarVisible
                )

            // Start with an ongoing call (which should set status bar required)
            addOngoingCallState()

            assertThat(isStatusBarRequiredForOngoingCall).isTrue()
            assertThat(requiresStatusBarVisibleInRepository).isTrue()
            assertThat(requiresStatusBarVisibleInWindowController).isTrue()

            // Swipe away the chip
            underTest.onStatusBarSwiped()

            // Verify status bar is no longer required
            assertThat(requiresStatusBarVisibleInRepository).isFalse()
            assertThat(requiresStatusBarVisibleInWindowController).isFalse()
        }

    @DisableFlags(OngoingActivityChipsOnDream.FLAG_NAME)
    @Test
    fun ongoingCallNotificationAndDreaming_flagDisabled_setsRequiresStatusBarVisibleTrue() =
        kosmos.runTest {
            val isStatusBarRequired by collectLastValue(underTest.isStatusBarRequiredForOngoingCall)
            val requiresStatusBarVisibleInRepository by
                collectLastValue(statusBarModeRepository.ongoingProcessRequiresStatusBarVisible)
            val requiresStatusBarVisibleInWindowController by
                collectLastValue(
                    fakeStatusBarWindowControllerStore.defaultDisplay
                        .ongoingProcessRequiresStatusBarVisible
                )
            addOngoingCallState()

            assertThat(isStatusBarRequired).isTrue()
            assertThat(requiresStatusBarVisibleInRepository).isTrue()
            assertThat(requiresStatusBarVisibleInWindowController).isTrue()

            kosmos.fakeKeyguardRepository.setDreamingWithOverlay(true)

            assertThat(isStatusBarRequired).isTrue()
            assertThat(requiresStatusBarVisibleInRepository).isTrue()
            assertThat(requiresStatusBarVisibleInWindowController).isTrue()
        }

    @EnableFlags(OngoingActivityChipsOnDream.FLAG_NAME)
    @Test
    fun ongoingCallNotificationAndDreaming_flagEnabled_setsRequiresStatusBarVisibleFalse() =
        kosmos.runTest {
            val isStatusBarRequired by collectLastValue(underTest.isStatusBarRequiredForOngoingCall)
            val requiresStatusBarVisibleInRepository by
                collectLastValue(statusBarModeRepository.ongoingProcessRequiresStatusBarVisible)
            val requiresStatusBarVisibleInWindowController by
                collectLastValue(
                    fakeStatusBarWindowControllerStore.defaultDisplay
                        .ongoingProcessRequiresStatusBarVisible
                )
            addOngoingCallState()

            assertThat(isStatusBarRequired).isTrue()
            assertThat(requiresStatusBarVisibleInRepository).isTrue()
            assertThat(requiresStatusBarVisibleInWindowController).isTrue()

            kosmos.fakeKeyguardRepository.setDreamingWithOverlay(true)

            assertThat(isStatusBarRequired).isFalse()
            assertThat(requiresStatusBarVisibleInRepository).isFalse()
            assertThat(requiresStatusBarVisibleInWindowController).isFalse()
        }

    companion object {
        private const val UID = 885
    }
}
