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
package com.android.systemui.statusbar.notification.headsup

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.plugins.statusbar.fakeStatusBarStateController
import com.android.systemui.shade.data.repository.fakeShadeDisplaysRepository
import com.android.systemui.shade.domain.interactor.panelExpansionInteractor
import com.android.systemui.shade.domain.interactor.shadeDisplaysInteractor
import com.android.systemui.shade.shadeViewController
import com.android.systemui.statusbar.StatusBarState
import com.android.systemui.statusbar.notification.stack.notificationStackScrollLayoutController
import com.android.systemui.statusbar.notificationRemoteInputManager
import com.android.systemui.statusbar.notificationShadeWindowController
import com.android.systemui.statusbar.phone.keyguardBypassController
import com.android.systemui.statusbar.window.fakeStatusBarWindowControllerStore
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.launch
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class StatusBarHeadsUpChangeListenerTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()

    private val Kosmos.underTest by
        Kosmos.Fixture {
            StatusBarHeadsUpChangeListener(
                notificationShadeWindowController,
                fakeStatusBarWindowControllerStore,
                shadeViewController,
                panelExpansionInteractor,
                notificationStackScrollLayoutController,
                keyguardBypassController,
                mockHeadsUpManager,
                fakeStatusBarStateController,
                notificationRemoteInputManager,
                shadeDisplaysInteractor,
                applicationCoroutineScope,
            )
        }

    @Before
    fun start() {
        kosmos.apply { testScope.launch { underTest.start() } }
    }

    @Test
    @DisableFlags(Flags.FLAG_SCENE_CONTAINER)
    fun onHeadsUpPinnedModeChanged_isPinned_setsStatusBarForcedVisibleOnCurrentDisplay() =
        kosmos.runTest {
            fakeShadeDisplaysRepository.setDisplayId(MAIN_TEST_DISPLAY_ID)

            underTest.onHeadsUpPinnedModeChanged(true)

            assertThat(
                    fakeStatusBarWindowControllerStore
                        .forDisplay(MAIN_TEST_DISPLAY_ID)
                        .isForcedVisible
                )
                .isTrue()
        }

    @Test
    @DisableFlags(Flags.FLAG_SCENE_CONTAINER)
    fun onDisplayChange_hunPinned_setsStatusBarForcedVisibleOnNewDisplay() =
        kosmos.runTest {
            whenever(mockHeadsUpManager.hasPinnedHeadsUp()).thenReturn(true)

            fakeShadeDisplaysRepository.setDisplayId(MAIN_TEST_DISPLAY_ID)
            fakeShadeDisplaysRepository.setDisplayId(SECONDARY_TEST_DISPLAY_ID)

            assertThat(
                    fakeStatusBarWindowControllerStore
                        .forDisplay(SECONDARY_TEST_DISPLAY_ID)
                        .isForcedVisible
                )
                .isTrue()
        }

    @Test
    @DisableFlags(Flags.FLAG_SCENE_CONTAINER)
    fun onDisplayChange_hunPinned_setsStatusBarNotForcedVisibleOnPreviousDisplay() =
        kosmos.runTest {
            whenever(mockHeadsUpManager.hasPinnedHeadsUp()).thenReturn(true)

            fakeShadeDisplaysRepository.setDisplayId(MAIN_TEST_DISPLAY_ID)
            fakeShadeDisplaysRepository.setDisplayId(SECONDARY_TEST_DISPLAY_ID)

            assertThat(
                    fakeStatusBarWindowControllerStore
                        .forDisplay(MAIN_TEST_DISPLAY_ID)
                        .isForcedVisible
                )
                .isFalse()
        }

    @Test
    @EnableFlags(Flags.FLAG_SCENE_CONTAINER)
    fun onHeadsUpPinnedModeChanged_isPinned_sceneContainerFlagOn_doesNotSetStatusBarForcedVisible() =
        kosmos.runTest {
            fakeShadeDisplaysRepository.setDisplayId(MAIN_TEST_DISPLAY_ID)

            underTest.onHeadsUpPinnedModeChanged(true)

            assertThat(
                    fakeStatusBarWindowControllerStore
                        .forDisplay(MAIN_TEST_DISPLAY_ID)
                        .isForcedVisible
                )
                .isFalse()
        }

    @Test
    @EnableFlags(Flags.FLAG_SCENE_CONTAINER)
    fun onHeadsUpPinnedModeChanged_isNotPinned_sceneContainerFlagOn_doesNotSetStatusBarForcedVisible() =
        kosmos.runTest {
            fakeShadeDisplaysRepository.setDisplayId(MAIN_TEST_DISPLAY_ID)

            underTest.onHeadsUpPinnedModeChanged(true)

            assertThat(
                    fakeStatusBarWindowControllerStore
                        .forDisplay(MAIN_TEST_DISPLAY_ID)
                        .isForcedVisible
                )
                .isFalse()
        }

    @Test
    @DisableFlags(Flags.FLAG_SCENE_CONTAINER)
    fun onHeadsUpPinnedModeChanged_isNotPinned_setsStatusBarNotForcedVisibleOnCurrentDisplay() =
        kosmos.runTest {
            whenever(keyguardBypassController.bypassEnabled).thenReturn(true)
            fakeStatusBarStateController.state = StatusBarState.KEYGUARD
            fakeShadeDisplaysRepository.setDisplayId(MAIN_TEST_DISPLAY_ID)

            underTest.onHeadsUpPinnedModeChanged(true)
            underTest.onHeadsUpPinnedModeChanged(false)

            assertThat(
                    fakeStatusBarWindowControllerStore
                        .forDisplay(MAIN_TEST_DISPLAY_ID)
                        .isForcedVisible
                )
                .isFalse()
        }

    @Test
    @DisableFlags(Flags.FLAG_SCENE_CONTAINER)
    fun onDisplayChange_noHunPinned_oldDisplayRemainsForcedVisible() =
        kosmos.runTest {
            whenever(mockHeadsUpManager.hasPinnedHeadsUp()).thenReturn(false)
            fakeShadeDisplaysRepository.setDisplayId(MAIN_TEST_DISPLAY_ID)
            underTest.onHeadsUpPinnedModeChanged(true)

            fakeShadeDisplaysRepository.setDisplayId(SECONDARY_TEST_DISPLAY_ID)

            assertThat(
                    fakeStatusBarWindowControllerStore
                        .forDisplay(MAIN_TEST_DISPLAY_ID)
                        .isForcedVisible
                )
                .isTrue()
        }

    @Test
    @DisableFlags(Flags.FLAG_SCENE_CONTAINER)
    fun onDisplayChange_noHunPinned_newDisplayRemainsNotForcedVisible() =
        kosmos.runTest {
            whenever(mockHeadsUpManager.hasPinnedHeadsUp()).thenReturn(false)

            fakeShadeDisplaysRepository.setDisplayId(MAIN_TEST_DISPLAY_ID)
            fakeShadeDisplaysRepository.setDisplayId(SECONDARY_TEST_DISPLAY_ID)

            assertThat(
                    fakeStatusBarWindowControllerStore
                        .forDisplay(SECONDARY_TEST_DISPLAY_ID)
                        .isForcedVisible
                )
                .isFalse()
        }

    private companion object {
        const val MAIN_TEST_DISPLAY_ID = 123
        const val SECONDARY_TEST_DISPLAY_ID = 321
    }
}
