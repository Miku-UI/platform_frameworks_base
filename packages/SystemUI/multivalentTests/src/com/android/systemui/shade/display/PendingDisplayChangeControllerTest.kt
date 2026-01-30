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

package com.android.systemui.shade.display

import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.policy.IKeyguardService.SCREEN_TURNING_ON_REASON_DISPLAY_SWITCH
import com.android.internal.policy.IKeyguardService.SCREEN_TURNING_ON_REASON_UNKNOWN
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.verifyCurrent
import com.android.systemui.power.data.repository.fakePowerRepository
import com.android.systemui.power.domain.interactor.powerInteractor
import com.android.systemui.power.shared.model.ScreenPowerState
import com.android.systemui.statusbar.notificationShadeWindowController
import com.android.systemui.testKosmos
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(com.android.window.flags.Flags.FLAG_ENSURE_WALLPAPER_DRAWN_ON_DISPLAY_SWITCH)
class PendingDisplayChangeControllerTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val powerRepository = kosmos.fakePowerRepository

    private lateinit var underTest: PendingDisplayChangeController

    @Test
    fun onScreenTurningOnWithDisplaySwitching_notifiesShadeWindowController() =
        kosmos.runTest {
            createAndStartController()
            underTest.onScreenTurningOn(SCREEN_TURNING_ON_REASON_DISPLAY_SWITCH, mock<Runnable>())

            verifyCurrent(notificationShadeWindowController).setPendingDisplayChange(true)
        }

    @Test
    fun onScreenTurningOnWithDisplaySwitching_notifiesScreenTurningOnComplete() =
        kosmos.runTest {
            createAndStartController()
            val onScreenTurningOnComplete = mock<Runnable>()
            underTest.onScreenTurningOn(SCREEN_TURNING_ON_REASON_DISPLAY_SWITCH, onScreenTurningOnComplete)

            verifyCurrent(onScreenTurningOnComplete).run()
        }

    @Test
    fun onScreenTurningWithoutDisplaySwitching_notifiesScreenTurningOnComplete() =
        kosmos.runTest {
            createAndStartController()
            val onScreenTurningOnComplete = mock<Runnable>()
            underTest.onScreenTurningOn(SCREEN_TURNING_ON_REASON_UNKNOWN, onScreenTurningOnComplete)

            verifyCurrent(onScreenTurningOnComplete).run()
        }

    @Test
    fun onScreenTurningWithoutDisplaySwitching_doesNotSetPendingScreenState() =
        kosmos.runTest {
            createAndStartController()
            val onScreenTurningOnComplete = mock<Runnable>()
            underTest.onScreenTurningOn(SCREEN_TURNING_ON_REASON_UNKNOWN, onScreenTurningOnComplete)

            runCurrent()

            verify(notificationShadeWindowController, never()).setPendingDisplayChange(anyBoolean())
        }

    @Test
    fun onScreenTurningOnWithDisplaySwitching_screenTurnedOn_resetsPendingDisplayChange() =
        kosmos.runTest {
            createAndStartController()
            underTest.onScreenTurningOn(SCREEN_TURNING_ON_REASON_DISPLAY_SWITCH, mock<Runnable>())
            runCurrent()
            clearInvocations(notificationShadeWindowController)

            powerRepository.setScreenPowerState(ScreenPowerState.SCREEN_ON)

            verifyCurrent(notificationShadeWindowController).setPendingDisplayChange(false)
        }

    @Test
    fun onScreenTurningOnWithDisplaySwitching_screenTurningOnTimeout_resetsPendingDisplayChange() =
        kosmos.runTest {
            createAndStartController()
            underTest.onScreenTurningOn(SCREEN_TURNING_ON_REASON_DISPLAY_SWITCH, mock<Runnable>())
            runCurrent()
            clearInvocations(notificationShadeWindowController)

            testScope.advanceTimeBy(15.seconds)

            verify(notificationShadeWindowController).setPendingDisplayChange(false)
        }

    @Test
    fun onScreenTurningOnWithDisplaySwitching_beforeTurningOnTimeout_noPendingDisplayChange() =
        kosmos.runTest {
            createAndStartController()
            underTest.onScreenTurningOn(SCREEN_TURNING_ON_REASON_DISPLAY_SWITCH, mock<Runnable>())
            runCurrent()
            clearInvocations(notificationShadeWindowController)

            testScope.advanceTimeBy(100.milliseconds)

            verifyNoInteractions(notificationShadeWindowController)
        }

    @Test
    fun onScreenTurningOnWithDisplaySwitching_previousOneIsStillInProgress_processesBoth() =
        kosmos.runTest {
            createAndStartController()
            val onScreenTurningOnComplete1 = mock<Runnable>()
            underTest.onScreenTurningOn(SCREEN_TURNING_ON_REASON_DISPLAY_SWITCH,
                onScreenTurningOnComplete1)
            runCurrent()

            val onScreenTurningOnComplete2 = mock<Runnable>()
            underTest.onScreenTurningOn(SCREEN_TURNING_ON_REASON_DISPLAY_SWITCH,
                onScreenTurningOnComplete2)
            runCurrent()

            powerRepository.setScreenPowerState(ScreenPowerState.SCREEN_ON)

            verifyCurrent(onScreenTurningOnComplete1).run()
            verifyCurrent(onScreenTurningOnComplete2).run()

            inOrder(notificationShadeWindowController) {
                verify(notificationShadeWindowController).setPendingDisplayChange(true)
                verify(notificationShadeWindowController).setPendingDisplayChange(false)
                verify(notificationShadeWindowController).setPendingDisplayChange(true)
                verify(notificationShadeWindowController).setPendingDisplayChange(false)
            }
        }

    private fun Kosmos.createAndStartController() {
        underTest = PendingDisplayChangeController(
            testScope.backgroundScope,
            testScope.backgroundScope,
            { notificationShadeWindowController },
            powerInteractor
        ).also { it.start() }
        runCurrent()
    }
}