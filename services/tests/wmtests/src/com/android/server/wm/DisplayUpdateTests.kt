/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.server.wm

import android.os.IBinder
import android.os.Message
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.Presubmit
import android.view.Display
import android.view.WindowManager
import android.view.WindowManager.RemoveContentMode
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn
import com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn
import com.android.server.testutils.OffsettableClock
import com.android.server.testutils.TestHandler
import com.android.server.testutils.TestHandler.MsgInfo
import com.google.common.base.Objects
import com.google.common.truth.Correspondence
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.util.function.Predicate
import android.view.SurfaceControl
import org.mockito.ArgumentMatchers.anyInt
import java.util.function.Consumer

/**
 * Tests for applying display updates coming from DM to WM
 *
 * Build/Install/Run:
 * atest WmTests:DisplayUpdateTests
 */
@SmallTest
@Presubmit
@EnableFlags(com.android.window.flags.Flags.FLAG_SYNCED_DISPLAY_MODE_UPDATES)
@RunWith(WindowTestRunner::class)
class DisplayUpdateTests : WindowTestsBase() {

    private lateinit var displayManager: TestDisplayManager
    private lateinit var testPlayer: TestTransitionPlayer

    private lateinit var secondaryDisplay: DisplayContent
    private var secondaryDisplayId: Int = Display.INVALID_DISPLAY

    private lateinit var anotherSecondaryDisplay: DisplayContent
    private var anotherSecondaryDisplayId: Int = Display.INVALID_DISPLAY

    private val screenUnblocker: Message = mock()
    private val secondaryScreenUnblocker: Message = mock()

    private val unblockerClock: OffsettableClock = OffsettableClock.Stopped()
    private val unblockerTestHandler: TestHandler =
        spy(TestHandler( /* callback= */ null, unblockerClock))

    private val wmHandlerClock: OffsettableClock = OffsettableClock.Stopped()
    private val wmTestHandler: TestHandler = TestHandler(/* callback= */ null, wmHandlerClock)

    @Before
    fun before() {
        mRootWindowContainer.mTransitionController.mHandler = wmTestHandler
        displayManager = mSystemServicesTestRule.testDisplayManager
        testPlayer = registerTestTransitionPlayer()
        displayManager.setReturnDisplaysFromWm(false)

        spyOn(mWm.mDisplayWindowSettings)
        mRootWindowContainer.mDisplayUnblocker!!.mHandler = unblockerTestHandler

        // Default TestHandler's implementation of removeCallbacks only supports
        // removeMessages(msgId), so let's add this callback-based removeCallbacks implementation
        // using mockito, since it can't be added to the test handler due to removeCallbacks
        // method being final public API
        doAnswer({ invocation: InvocationOnMock ->
            unblockerTestHandler.removeIf(Predicate { msgInfo: MsgInfo ->
                Objects.equal(invocation.getArgument(0),
                    msgInfo.message.callback)
            })
            null
        }).whenever(unblockerTestHandler).removeCallbacks(any())

        whenever(screenUnblocker.target).thenReturn(unblockerTestHandler)
        whenever(secondaryScreenUnblocker.target).thenReturn(unblockerTestHandler)

        // Mock that displays have content
        spyOn(mRootWindowContainer)
        doReturn(true).whenever(mRootWindowContainer)
            .handleNotObscuredLocked(any(), anyBoolean(), anyBoolean())

        // Create secondary displays in addition to the default one for testing
        displayManager.updateDisplays {
            secondaryDisplayId = add().displayInfo.displayId
            anotherSecondaryDisplayId = add().displayInfo.displayId
        }

        // Add windows to the displays, so that mLastHasContent could be 'true'
        addActivityToDisplay(mDisplayContent)

        secondaryDisplay = mRootWindowContainer.getDisplayContent(secondaryDisplayId)
        addActivityToDisplay(secondaryDisplay)

        anotherSecondaryDisplay = mRootWindowContainer.getDisplayContent(anotherSecondaryDisplayId)
        addActivityToDisplay(anotherSecondaryDisplay)

        testPlayer.flush()
        advanceWmHandler()

        // Perform surface placement, so DisplayContent#mLastHasContent is updated
        mRootWindowContainer.performSurfacePlacement()
    }

    @Test
    fun testDisplayAdded_addsDisplayContent() {
        val newDisplayId = addDisplayInDisplayManager()

        assertThat(mRootWindowContainer.displays)
            .comparingElementsUsing(idCorrespondence)
            .contains(newDisplayId)
    }

    @Test
    fun testDisplayAdded_requestsTransitionToAddDisplay() {
        val newDisplayId = addDisplayInDisplayManager()

        assertThat(testPlayer.mLastTransit).isNotNull()
        assertThat(testPlayer.mLastTransit.mParticipants.filterIsInstance<DisplayContent>())
            .comparingElementsUsing(idCorrespondence)
            .contains(newDisplayId)

        val displayChange = testPlayer.mLastTransit.mChanges
            .get(mRootWindowContainer.getDisplayContent(newDisplayId))
        assertThat(displayChange).isNotNull()
        assertThat(displayChange!!.mExistenceChanged).isTrue()
    }

    @Test
    fun testDisplaySizeChanged_updatesDisplayContent() {
        displayManager.updateDisplays {
            change(secondaryDisplayId) {
                info.logicalWidth = 123
            }
        }

        assertThat(secondaryDisplay.displayInfo.logicalWidth).isEqualTo(123)
    }

    @Test
    fun testDisplaySizeChanged_requestsTransitionToChangeDisplay() {
        displayManager.updateDisplays {
            change(secondaryDisplayId) {
                info.logicalWidth = 123
            }
        }

        assertThat(testPlayer.mLastTransit).isNotNull()
        assertThat(testPlayer.mLastTransit.mParticipants.filterIsInstance<DisplayContent>())
            .comparingElementsUsing(idCorrespondence)
            .contains(secondaryDisplayId)
    }

    @Test
    fun testOnlyDisplayRenderRateChanged_doesNotRequestTransition() {
        displayManager.updateDisplays {
            change(secondaryDisplayId) {
                info.renderFrameRate = 123.0f
            }
        }

        val display = mRootWindowContainer.getDisplayContent(secondaryDisplayId)
        assertThat(display.displayInfo.renderFrameRate).isEqualTo(123.0f)
        assertThat(testPlayer.mLastTransit).isNull()
    }

    @Test
    fun testDisplayRemoved_removeContentDestroyMode_removesDisplayContent() {
        givenDisplayRemoveMode(secondaryDisplay, WindowManager.REMOVE_CONTENT_MODE_DESTROY)
        displayManager.updateDisplays {
            remove(secondaryDisplayId)
        }

        assertThat(secondaryDisplay.isRemoving).isTrue()
    }

    @Test
    fun testDisplayRemoved_removeContentMoveToPrimaryMode_keepsDisplayContent() {
        givenDisplayRemoveMode(secondaryDisplay, WindowManager.REMOVE_CONTENT_MODE_MOVE_TO_PRIMARY)
        displayManager.updateDisplays {
            remove(secondaryDisplayId)
        }

        // DisplayContent is kept by DisplayUpdater, the actual removal should be handled later
        // when applying the transition's WCT
        assertThat(secondaryDisplay.isRemoving).isFalse()
    }

    @Test
    fun testDisplayRemoved_requestsTransitionToRemoveDisplay() {
        displayManager.updateDisplays {
            remove(secondaryDisplayId)
        }

        assertThat(testPlayer.mLastTransit).isNotNull()
        assertThat(testPlayer.mLastTransit.mParticipants.filterIsInstance<DisplayContent>())
            .comparingElementsUsing(idCorrespondence)
            .contains(secondaryDisplayId)

        val displayChange = testPlayer.mLastTransit.mChanges.get(secondaryDisplay)
        assertThat(displayChange).isNotNull()
        assertThat(displayChange!!.mExistenceChanged).isTrue()
    }

    @Test
    fun testDisplayAddedRemovedChangedTogether_requestsTransitionWithAllChanges() {
        var addedDisplayId = Display.INVALID_DISPLAY
        displayManager.updateDisplays {
            addedDisplayId = add().displayInfo.displayId
            remove(secondaryDisplayId)
            change(anotherSecondaryDisplayId) {
                info.logicalWidth = 123
            }
        }

        assertThat(testPlayer.mLastTransit).isNotNull()
        assertThat(testPlayer.mLastTransit.mParticipants.filterIsInstance<DisplayContent>())
            .comparingElementsUsing(idCorrespondence)
            .containsAtLeast(addedDisplayId, secondaryDisplayId, anotherSecondaryDisplayId)
    }

    @Test
    fun testDisplayAddedRemovedChangedTogether_allDisplayContentsUpdated() {
        givenDisplayRemoveMode(secondaryDisplay, WindowManager.REMOVE_CONTENT_MODE_DESTROY)
        var addedDisplayId = Display.INVALID_DISPLAY
        displayManager.updateDisplays {
            addedDisplayId = add().displayInfo.displayId
            remove(secondaryDisplayId)
            change(anotherSecondaryDisplayId) {
                info.logicalWidth = 123
            }
        }

        val addedDisplay = mRootWindowContainer.getDisplayContent(addedDisplayId)
        assertThat(addedDisplay).isNotNull()
        assertThat(anotherSecondaryDisplay.displayInfo.logicalWidth).isEqualTo(123)
        assertThat(secondaryDisplay.isRemoving).isTrue()
    }

    @Test
    fun testTwoDisplaysChangedTogether_requestsTransitionWithBothChanges() {
        displayManager.updateDisplays {
            change(secondaryDisplayId) {
                info.logicalWidth = 123
            }
            change(anotherSecondaryDisplayId) {
                info.logicalWidth = 456
            }
        }

        assertThat(testPlayer.mLastTransit).isNotNull()
        assertThat(testPlayer.mLastTransit.mParticipants.filterIsInstance<DisplayContent>())
            .comparingElementsUsing(idCorrespondence)
            .containsAtLeast(secondaryDisplayId, anotherSecondaryDisplayId)
    }

    @Test
    fun testTwoDisplaysChangedTogether_updatesBothDisplayContents() {
        displayManager.updateDisplays {
            change(secondaryDisplayId) {
                info.logicalWidth = 123
            }
            change(anotherSecondaryDisplayId) {
                info.logicalWidth = 456
            }
        }

        assertThat(secondaryDisplay.displayInfo.logicalWidth).isEqualTo(123)
        assertThat(anotherSecondaryDisplay.displayInfo.logicalWidth).isEqualTo(456)
    }

    @Test
    @DisableFlags(com.android.window.flags.Flags.FLAG_ENSURE_WALLPAPER_DRAWN_ON_DISPLAY_SWITCH)
    fun testDefaultDisplaySwitching_wallpaperFlagDisabled_transitionIsReady() {
        notifyDefaultDisplaySwitching(switching = true)

        performDefaultPhysicalDisplaySwitch()

        // Transition should be ready as there are no extra conditions to be met
        val transition: Transition = testPlayer.mLastTransit
        assertThat(transition.allReady()).isTrue()
    }

    @Test
    @EnableFlags(com.android.window.flags.Flags.FLAG_ENSURE_WALLPAPER_DRAWN_ON_DISPLAY_SWITCH)
    fun testDefaultDisplaySwitching_keyguardIsNotDrawn_transitionIsNotReady() {
        notifyDefaultDisplaySwitching(switching = true)

        performDefaultPhysicalDisplaySwitch()

        // Transition should not be ready as keyguard is not drawn yet
        val transition: Transition = testPlayer.mLastTransit
        assertThat(transition.allReady()).isFalse()
    }

    @Test
    @EnableFlags(com.android.window.flags.Flags.FLAG_ENSURE_WALLPAPER_DRAWN_ON_DISPLAY_SWITCH)
    fun testDisplaySwitching_keyguardDrawn_transitionIsNotReady() {
        notifyDefaultDisplaySwitching(switching = true)
        performDefaultPhysicalDisplaySwitch()

        signalKeyguardIsDrawn()

        // Transition should be ready as keyguard is drawn
        val transition: Transition = testPlayer.mLastTransit
        assertThat(transition.allReady()).isTrue()
    }

    @Test
    @EnableFlags(com.android.window.flags.Flags.FLAG_ENSURE_WALLPAPER_DRAWN_ON_DISPLAY_SWITCH)
    fun testDisplaySwitching_keyguardDrawnBeforeDisplaySwitching_transitionIsNotReady() {
        signalKeyguardIsDrawn()
        notifyDefaultDisplaySwitching(switching = true)

        performDefaultPhysicalDisplaySwitch()

        // Transition should not be ready as keyguard wasn't drawn for the new display switch
        val transition: Transition = testPlayer.mLastTransit
        assertThat(transition.allReady()).isFalse()
    }

    @Test
    @EnableFlags(com.android.window.flags.Flags.FLAG_ENSURE_WALLPAPER_DRAWN_ON_DISPLAY_SWITCH)
    fun testDisplaySwitching_keyguardWasNotDrawn_secondSwitchingIsNormal_transitionIsReady() {
        performDefaultPhysicalDisplaySwitch(newUniqueId = "new1", newWidth = 100, newHeight = 200)
        notifyDefaultDisplaySwitching(switching = false)

        // Here we finished the display switching, but keyguard drawn callback
        // was not invoked. Let's verify that we still make the second display change transition
        // ready after keyguard has drawn.
        notifyDefaultDisplaySwitching(switching = true)
        performDefaultPhysicalDisplaySwitch(newUniqueId = "new2", newWidth = 300, newHeight = 400)
        signalKeyguardIsDrawn()

        // Transition should be ready as keyguard drawing is completed for the last display change
        val transition: Transition = testPlayer.mLastTransit
        assertThat(transition.allReady()).isTrue()
    }

    @Test
    fun testTwoDisplayUpdates_transitionStarted_displayUpdated() {
        // Perform two back-to-back display updates, so that the second one is performed
        // while WM is 'busy' (collecting the first display change)
        notifyDefaultDisplaySwitching(switching = true)
        performDefaultPhysicalDisplaySwitch(newUniqueId = "new1", newWidth = 100, newHeight = 200)

        // Second transition will be queued as the first is still collecting
        notifyDefaultDisplaySwitching(switching = true)
        performDefaultPhysicalDisplaySwitch(newUniqueId = "new2", newWidth = 300, newHeight = 400)

        // Assert that the first display switch started collecting
        assertThat(testPlayer.mLastTransit).isNotNull()
        assertThat(mDisplayContent.displayInfo.uniqueId).isEqualTo("new1")

        // Finish collecting the first transition and proceed with the second update
        // Also wait for the handlers to be idle to ensure that the second queued transition started
        // collecting (onStartCollect callback is posted to mH handler)
        testPlayer.start()
        advanceWmHandler()

        assertThat(mDisplayContent.displayInfo.uniqueId).isEqualTo("new2")
    }

    @Test
    fun testWaitForTransition_displaySwitching_waitsForTransitionToBeStarted() {
        notifyDefaultDisplaySwitching(switching = true)
        val willWait = mRootWindowContainer.mDisplayUnblocker!!
            .waitForDefaultDisplayTransition(screenUnblocker)
        assertThat(willWait).isTrue()

        performDefaultPhysicalDisplaySwitch()

        // Verify that screen is not unblocked yet as the start transaction hasn't been presented
        verify(screenUnblocker, never()).sendToTarget()

        val transition: Transition = testPlayer.mLastTransit
        transition.invokePresentedListenersForTest()

        // Verify that screen is unblocked as start transaction of the transition
        // has been completed
        verify(screenUnblocker).sendToTarget()
    }

    @Test
    fun testDisplaySwitching_requestsTransitionWithDisplayLevelFlag() {
        notifyDefaultDisplaySwitching(switching = true)

        performDefaultPhysicalDisplaySwitch()

        val transition: Transition = testPlayer.mLastTransit
        assertThat(transition.flags and WindowManager.TRANSIT_FLAG_DISPLAY_LEVEL_TRANSITION)
            .isNotEqualTo(0)
    }

    @Test
    fun testWaitForTransition_displayNotSwitching_doesNotWait() {
        notifyDefaultDisplaySwitching(switching = false)

        val willWait = mRootWindowContainer.mDisplayUnblocker!!
            .waitForDefaultDisplayTransition(screenUnblocker)

        assertThat(willWait).isFalse()
        verify(screenUnblocker, never()).sendToTarget()
    }

    @Test
    fun testDefaultAndSecondaryDisplayUpdateAtTheSameTime_bothDisplaysAreUnblocked() {
        val defaultDisplayWindow = newWindowBuilder(
            "DefaultDisplayWindow",
            WindowManager.LayoutParams.TYPE_BASE_APPLICATION
        ).setDisplay(mDisplayContent).build()
        val secondaryDisplayWindow = newWindowBuilder(
            "SecondaryDisplayWindow",
            WindowManager.LayoutParams.TYPE_BASE_APPLICATION
        ).setDisplay(secondaryDisplay).build()
        makeWindowVisibleAndNotDrawn(defaultDisplayWindow, secondaryDisplayWindow)

        // We notify display as switching only for the default display as we do not support
        // non-default display switching events in the display policy
        notifyDefaultDisplaySwitching(switching = true)

        mWm.mInternal.waitForAllWindowsDrawn(screenUnblocker,
            /* timeout= */ Int.MAX_VALUE.toLong(), Display.INVALID_DISPLAY)
        mWm.mInternal.waitForAllWindowsDrawn(secondaryScreenUnblocker,
            /* timeout= */ Int.MAX_VALUE.toLong(), secondaryDisplayId)

        // Perform display update for both displays at the same time
        displayManager.updateDisplays {
            change(Display.DEFAULT_DISPLAY) {
                info.uniqueId = "new_default_display_unique_id"
                info.logicalWidth = 100
                info.logicalHeight = 200
            }
            change(secondaryDisplayId) {
                info.uniqueId = "new_secondary_display_unique_id"
                info.logicalWidth = 300
                info.logicalHeight = 400
            }
        }

        // Verify that screens are not unblocked yet
        verify(screenUnblocker, never()).sendToTarget()
        verify(secondaryScreenUnblocker, never()).sendToTarget()

        // Make all secondary display windows drawn
        secondaryDisplayWindow.mWinAnimator.mDrawState = WindowStateAnimator.HAS_DRAWN
        mWm.mRoot.performSurfacePlacement()

        // Verify that only secondary screen is unblocked as it uses
        // the legacy waitForAllWindowsDrawn path
        verify(screenUnblocker, never()).sendToTarget()
        verify(secondaryScreenUnblocker).sendToTarget()

        // Simulate that start transaction is presented
        testPlayer.startTransition()
        testPlayer.mLastTransit.invokePresentedListenersForTest()

        // Verify that the default screen unblocker is sent only after start transaction
        // of the Shell transition is presented
        verify(screenUnblocker).sendToTarget()
    }

    @Test
    fun test_displaySwitch_timeoutWaitingForTransition_unblocksScreen() {
        // Trigger the first display switch
        notifyDefaultDisplaySwitching(switching = true)
        mWm.mInternal.waitForAllWindowsDrawn(screenUnblocker,
            /* timeout= */ Int.MAX_VALUE.toLong(), Display.INVALID_DISPLAY)
        performDefaultPhysicalDisplaySwitch()

        // Verify that screen is not unblocked yet
        verify(screenUnblocker, never()).sendToTarget()

        // Advance time past the timeout
        unblockerClock.fastForward((2000 + 1).toLong())
        unblockerTestHandler.timeAdvance()

        // Verify that the screen is unblocked
        verify(screenUnblocker).sendToTarget()
    }

    @Test
    fun test_displaySwitchArrivesWhileAnotherOneIsNotFinished_unblocksOnlyAfterSecondDisplaySwitch() {
        // Trigger the first display switch
        notifyDefaultDisplaySwitching(switching = true)
        mWm.mInternal.waitForAllWindowsDrawn(screenUnblocker, /* timeout= */ Int.MAX_VALUE.toLong(),
            Display.INVALID_DISPLAY)
        performDefaultPhysicalDisplaySwitch(newUniqueId = "new1", newWidth = 100, newHeight = 200)

        // Trigger the second display switch, transition will be queued
        // as the first one is still collecting
        notifyDefaultDisplaySwitching(switching = true)
        mWm.mInternal.waitForAllWindowsDrawn(screenUnblocker, /* timeout= */ Int.MAX_VALUE.toLong(),
            Display.INVALID_DISPLAY)
        performDefaultPhysicalDisplaySwitch(newUniqueId = "new2", newWidth = 300, newHeight = 400)

        // Make the first display switch finished collecting, but do not invoke that its start
        // transaction is presented yet, then advance WM handler to ensure onStartCallback is
        // invoked since the callback is posted to mH handler
        val firstTransition = testPlayer.mLastTransit
        val firstTransitionPresentedListeners =
            captureTransactionPresentedListeners(firstTransition)
        testPlayer.start()
        advanceWmHandler()

        // Verify that second (queued) transition is requested now
        assertThat(firstTransition).isNotEqualTo(testPlayer.mLastTransit)

        // Verify that screen is not unblocked yet
        verify(screenUnblocker, never()).sendToTarget()

        // Fire presented listener for the first transition
        firstTransitionPresentedListeners.run()

        // Verify that the screen is still not unblocked, as we already have the second
        // transition collecting and this 'presented' callback doesn't match with it
        verify(screenUnblocker, never()).sendToTarget()

        // Let's notify that the second display switch started and its start transaction
        // is now presented
        val secondTransitionPresentedListeners =
            captureTransactionPresentedListeners(testPlayer.mLastTransit)
        testPlayer.start()
        secondTransitionPresentedListeners.run()

        // The second display switch is complete, so the display should be unblocked now
        verify(screenUnblocker).sendToTarget()
    }

    @Test
    fun test_displaySwitchWhileAnotherOneIsNotFinished_timeoutPasses_unblocksScreenOnlyAfterSecondTimeout() {
        // Trigger the first display switch
        notifyDefaultDisplaySwitching(switching = true)
        mWm.mInternal.waitForAllWindowsDrawn(
            screenUnblocker,  /* timeout= */
            Int.MAX_VALUE.toLong(), Display.INVALID_DISPLAY
        )
        performDefaultPhysicalDisplaySwitch(newUniqueId = "new1", newWidth = 100, newHeight = 200)

        // Advance time just before the timeout
        unblockerClock.fastForward(1500)
        unblockerTestHandler.timeAdvance()

        // Trigger the second display switch
        notifyDefaultDisplaySwitching(switching = true)
        mWm.mInternal.waitForAllWindowsDrawn(
            screenUnblocker,  /* timeout= */
            Int.MAX_VALUE.toLong(), Display.INVALID_DISPLAY
        )
        performDefaultPhysicalDisplaySwitch(newUniqueId = "new2", newWidth = 300, newHeight = 400)

        // Advance time further, so in total time passed since the first display change
        // is greater than the timeout
        unblockerClock.fastForward(1000)
        unblockerTestHandler.timeAdvance()

        // Verify that screen is not unblocked yet as the second display switch should have
        // reset the timeout
        verify(screenUnblocker, never()).sendToTarget()

        // Advance time further, so we hit the timeout since the second display change
        unblockerClock.fastForward((1000 + 1).toLong())
        unblockerTestHandler.timeAdvance()

        // We hit the timeout since the last display change, so we should unblock the screen
        verify(screenUnblocker).sendToTarget()
    }

    private fun performDefaultPhysicalDisplaySwitch(newUniqueId: String = "new_unique_id",
                                            newWidth: Int = 100, newHeight: Int = 200) {
        displayManager.updateDisplays {
            change(Display.DEFAULT_DISPLAY) {
                info.uniqueId = newUniqueId
                info.logicalWidth = newWidth
                info.logicalHeight = newHeight
            }
        }
    }

    private fun signalKeyguardIsDrawn() {
        // waitForTransition call signals that keyguard is drawn
        mRootWindowContainer.mDisplayUnblocker!!.waitForDefaultDisplayTransition(screenUnblocker)
        unblockerTestHandler.flush()
    }

    private fun notifyDefaultDisplaySwitching(switching: Boolean = true) {
        mRootWindowContainer.mDisplayUnblocker!!.onDefaultDisplaySwitching(switching)
    }

    private fun captureTransactionPresentedListeners(transition: Transition): Runnable {
        spyOn(transition)

        val capturedListeners = arrayListOf<Consumer<SurfaceControl.TransactionStats>>()
        doAnswer { invocation ->
            val transaction = invocation.getArgument<SurfaceControl.Transaction>(1)
            spyOn(transaction)
            doAnswer { invocation ->
                val listener = invocation.getArgument<Consumer<SurfaceControl.TransactionStats>>(1)
                capturedListeners.add(listener)
                invocation.callRealMethod()
            }.whenever(transaction).addTransactionCompletedListener(any(), any())

            invocation.callRealMethod()
        }.whenever(transition).onTransactionReady(anyInt(), any())

        return Runnable {
            val stats = mock<SurfaceControl.TransactionStats>()
            whenever(stats.presentFence).thenReturn(mock())
            assertThat(capturedListeners).isNotEmpty()
            capturedListeners.forEach { it.accept(stats) }
        }
    }

    private fun addDisplayInDisplayManager(): Int {
        var newDisplayId: Int = Display.INVALID_DISPLAY
        displayManager.updateDisplays {
            newDisplayId = add().displayInfo.displayId
        }
        return newDisplayId
    }

    private val RootWindowContainer.displays: List<DisplayContent>
        get() {
            val result = ArrayList<DisplayContent>()
            forAllDisplays { display ->
                result.add(display)
            }
            return result
        }

    private fun givenDisplayRemoveMode(
        displayContent: DisplayContent,
        @RemoveContentMode mode: Int
    ) {
        doReturn(mode).whenever(mWm.mDisplayWindowSettings)
            .getRemoveContentModeLocked(eq(displayContent))
    }

    private fun addActivityToDisplay(display: DisplayContent) {
        val task = createTask(display)
        val act = createActivityRecord(task)
        val win = createWindowState(
            WindowManager.LayoutParams(WindowManager.LayoutParams.TYPE_BASE_APPLICATION),
            act
        )
        act.addWindow(win)
        act.setVisibleRequested(true)
    }

    private fun advanceWmHandler() {
        wmTestHandler.timeAdvance()
    }

    private companion object {
        val idCorrespondence: Correspondence<DisplayContent, Int> =
            Correspondence.transforming<DisplayContent, Int>(
                { it.displayInfo.displayId },
                "has ID of"
            )
    }
}
