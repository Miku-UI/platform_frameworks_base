/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.service.chooser

import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_CHOOSER
import android.graphics.Rect
import android.os.DeadObjectException
import android.os.IBinder
import android.os.RemoteException
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.service.chooser.ChooserSession.StateListener
import android.service.chooser.Flags.FLAG_INTERACTIVE_CHOOSER
import android.service.chooser.Flags.FLAG_INTERACTIVE_CHOOSER_DEFAULT_BOUNDS
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import org.junit.Rule
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify

class ChooserSessionTest {
    @get:Rule val mSetFlagsRule: SetFlagsRule = SetFlagsRule()

    private val context = mock<Context>()
    private val chooserController =
        mock<IChooserController.Stub> { on { asBinder() } doReturn this.mock }

    @EnableFlags(FLAG_INTERACTIVE_CHOOSER)
    @Test
    fun test_chooserControllerRegistered_sessionStateUpdated() {
        val (session, controllerCallback) = prepareChooserSession()
        val stateListener =
            object : StateListener {
                override fun onStateChanged(state: Int) {
                    assertThat(session.state).isEqualTo(ChooserSession.STATE_STARTED)
                    assertThat(state).isEqualTo(ChooserSession.STATE_STARTED)
                }

                override fun onBoundsChanged(bounds: Rect) {}
            }
        session.addStateListener(ImmediateExecutor(), stateListener)

        assertThat(session.state).isEqualTo(ChooserSession.STATE_INITIALIZED)

        controllerCallback.registerChooserController(chooserController)

        assertThat(session.state).isEqualTo(ChooserSession.STATE_STARTED)
    }

    @EnableFlags(FLAG_INTERACTIVE_CHOOSER)
    @Test
    fun test_deadChooserControllerRegistered_sessionStateUpdated() {
        val (session, controllerCallback) = prepareChooserSession()
        val binder =
            mock<IBinder> { on { linkToDeath(any(), any()) } doThrow RemoteException("test") }
        chooserController.stub { on { asBinder() } doReturn binder }

        assertThat(session.state).isEqualTo(ChooserSession.STATE_INITIALIZED)

        controllerCallback.registerChooserController(chooserController)

        assertThat(session.state).isEqualTo(ChooserSession.STATE_CLOSED)
    }

    @EnableFlags(FLAG_INTERACTIVE_CHOOSER)
    @Test
    fun test_chooserConnectsToClosedSession_chooserInstructedToClose() {
        val (session, controllerCallback) = prepareChooserSession()

        assertThat(session.state).isEqualTo(ChooserSession.STATE_INITIALIZED)

        session.endSession()

        controllerCallback.registerChooserController(chooserController)

        verify(chooserController) { 1 * { updateIntent(null) } }
    }

    @EnableFlags(FLAG_INTERACTIVE_CHOOSER)
    @Test
    fun test_chooserBoundsChanged_boundsReported() {
        val (session, controllerCallback) = prepareChooserSession()
        val bounds = listOf(Rect(1, 2, 3, 4), Rect(5, 6, 7, 8))
        val boundsUpdates = mutableListOf<Rect>()
        val stateListener =
            object : StateListener {
                override fun onStateChanged(state: Int) {}

                override fun onBoundsChanged(bounds: Rect) {
                    assertThat(session.bounds).isEqualTo(bounds)
                    boundsUpdates.add(bounds)
                }
            }
        session.addStateListener(ImmediateExecutor(), stateListener)

        assertThat(session.bounds).isNull()

        for (b in bounds) {
            controllerCallback.onBoundsChanged(b, b)
        }

        assertThat(boundsUpdates).containsExactlyElementsIn(bounds).inOrder()
    }

    @EnableFlags(FLAG_INTERACTIVE_CHOOSER, FLAG_INTERACTIVE_CHOOSER_DEFAULT_BOUNDS)
    @Test
    fun test_chooserBoundsChanged_defaultBoundsReported() {
        val (session, controllerCallback) = prepareChooserSession()
        val boundsUpdates = mutableListOf<Rect>()
        val restingBoundsUpdates = mutableListOf<Rect?>()
        val stateListener =
            object : StateListener {
                override fun onStateChanged(state: Int) {}

                override fun onBoundsChanged(bounds: Rect) {
                    boundsUpdates.add(bounds)
                    restingBoundsUpdates.add(session.initialRestingBounds)
                }
            }
        session.addStateListener(ImmediateExecutor(), stateListener)

        assertThat(session.bounds).isNull()

        val collapsed = Rect(1, 8, 3, 10)
        val default = Rect(1, 6, 3, 10)
        val defaultUpdated = Rect(1, 5, 3, 10)
        val expanded = Rect(1, 4, 3, 10)
        controllerCallback.onBoundsChanged(collapsed, default)
        controllerCallback.onBoundsChanged(default, default)
        controllerCallback.onBoundsChanged(expanded, default)
        controllerCallback.onBoundsChanged(expanded, defaultUpdated)

        assertWithMessage("onBoundsChanged(Rect) should not be called")
            .that(boundsUpdates)
            .hasSize(4)
        assertThat(restingBoundsUpdates).hasSize(4)
        assertThat(boundsUpdates[0]).isEqualTo(collapsed)
        assertThat(restingBoundsUpdates[0]).isEqualTo(default)
        assertThat(boundsUpdates[1]).isEqualTo(default)
        assertThat(restingBoundsUpdates[1]).isEqualTo(default)
        assertThat(boundsUpdates[2]).isEqualTo(expanded)
        assertThat(restingBoundsUpdates[2]).isEqualTo(default)
        assertThat(boundsUpdates[3]).isEqualTo(expanded)
        assertThat(restingBoundsUpdates[3]).isEqualTo(defaultUpdated)
    }

    @EnableFlags(FLAG_INTERACTIVE_CHOOSER)
    @Test
    fun test_chooserClosesSession_sessionGetsClosed() {
        val (session, controllerCallback) = prepareChooserSession()

        assertThat(session.state).isEqualTo(ChooserSession.STATE_INITIALIZED)

        controllerCallback.registerChooserController(chooserController)

        assertThat(session.state).isEqualTo(ChooserSession.STATE_STARTED)

        val invocationCounter = AtomicInteger()
        val stateListener =
            object : StateListener {
                override fun onStateChanged(state: Int) {
                    invocationCounter.incrementAndGet()
                    assertThat(session.state).isEqualTo(ChooserSession.STATE_CLOSED)
                    assertThat(state).isEqualTo(ChooserSession.STATE_CLOSED)
                }

                override fun onBoundsChanged(bounds: Rect) {
                    invocationCounter.incrementAndGet()
                }
            }
        session.addStateListener(ImmediateExecutor(), stateListener)

        controllerCallback.onClosed()

        assertThat(session.state).isEqualTo(ChooserSession.STATE_CLOSED)
        // there should not be callback invocations on a closed session
        val bounds = Rect(1, 2, 3, 4)
        controllerCallback.onBoundsChanged(bounds, bounds)
        controllerCallback.onClosed()

        assertThat(invocationCounter.get()).isEqualTo(1)
    }

    @EnableFlags(FLAG_INTERACTIVE_CHOOSER)
    @Test
    fun test_chooserProcessDies_sessionGetsClosed() {
        val (session, controllerCallback) = prepareChooserSession()

        assertThat(session.state).isEqualTo(ChooserSession.STATE_INITIALIZED)

        controllerCallback.registerChooserController(chooserController)

        assertThat(session.state).isEqualTo(ChooserSession.STATE_STARTED)

        val invocationCounter = AtomicInteger()
        val stateListener =
            object : StateListener {
                override fun onStateChanged(state: Int) {
                    invocationCounter.incrementAndGet()
                    assertThat(session.state).isEqualTo(ChooserSession.STATE_CLOSED)
                    assertThat(state).isEqualTo(ChooserSession.STATE_CLOSED)
                }

                override fun onBoundsChanged(bounds: Rect) {
                    invocationCounter.incrementAndGet()
                }
            }
        session.addStateListener(ImmediateExecutor(), stateListener)

        val linkToDeathCaptor = argumentCaptor<IBinder.DeathRecipient>()
        verify(chooserController) { 1 * { linkToDeath(linkToDeathCaptor.capture(), any()) } }
        val linkToDeath = linkToDeathCaptor.firstValue
        assertThat(linkToDeath).isNotNull()
        linkToDeath.binderDied()

        assertThat(session.state).isEqualTo(ChooserSession.STATE_CLOSED)
    }

    @EnableFlags(FLAG_INTERACTIVE_CHOOSER)
    @Test
    fun test_chooserUpdates() {
        val (session, controllerCallback) = prepareChooserSession()
        controllerCallback.registerChooserController(chooserController)
        val updatedIntent = Intent(ACTION_CHOOSER)

        session.updateIntent(updatedIntent)
        session.setMinimized(true)
        session.setTargetsEnabled(false)

        verify(chooserController) { 1 * { updateIntent(updatedIntent) } }
        verify(chooserController) { 1 * { setMinimized(true) } }
        verify(chooserController) { 1 * { setTargetsEnabled(false) } }
    }

    @EnableFlags(FLAG_INTERACTIVE_CHOOSER)
    @Test
    fun test_stateListenerUnsubscribes_stopsReceiveUpdates() {
        val (session, controllerCallback) = prepareChooserSession()
        val firstListenerBounds = ArrayList<Rect>()
        val firstListener =
            object : StateListener {
                override fun onStateChanged(p0: Int) {}

                override fun onBoundsChanged(bounds: Rect) {
                    firstListenerBounds.add(bounds)
                }
            }
        val secondListenerBounds = ArrayList<Rect>()
        val secondListener =
            object : StateListener {
                override fun onStateChanged(p0: Int) {}

                override fun onBoundsChanged(bounds: Rect) {
                    secondListenerBounds.add(bounds)
                }
            }
        val firstBounds = Rect(1, 2, 3, 4)
        val secondBounds = Rect(5, 6, 7, 8)

        session.addStateListener(ImmediateExecutor(), firstListener)
        controllerCallback.onBoundsChanged(firstBounds, firstBounds)
        session.addStateListener(ImmediateExecutor(), secondListener)
        session.removeStateListener(firstListener)
        controllerCallback.onBoundsChanged(secondBounds, secondBounds)

        assertThat(firstListenerBounds).containsExactly(firstBounds)
        assertThat(secondListenerBounds).containsExactly(secondBounds)
    }

    @EnableFlags(FLAG_INTERACTIVE_CHOOSER)
    @Test
    fun test_setMinimizedThrowsDeadObjectException_sessionGetsClosed() {
        testSessionClosedOnDeadObjectException { setMinimized(true) }
    }

    @EnableFlags(FLAG_INTERACTIVE_CHOOSER)
    @Test
    fun test_setTargetsEnabledThrowsDeadObjectException_sessionGetsClosed() {
        testSessionClosedOnDeadObjectException { setTargetsEnabled(false) }
    }

    private fun testSessionClosedOnDeadObjectException(testInvocation: ChooserSession.() -> Unit) {
        val (session, controllerCallback) = prepareChooserSession()
        chooserController.stub {
            on { setMinimized(anyBoolean()) } doThrow DeadObjectException("setMinimized")
            on { setTargetsEnabled(any()) } doThrow DeadObjectException("setTargetsEnabled")
        }
        controllerCallback.registerChooserController(chooserController)

        session.testInvocation()

        assertThat(session.state).isEqualTo(ChooserSession.STATE_CLOSED)
    }

    private fun prepareChooserSession(): Pair<ChooserSession, IChooserControllerCallback> {
        val session = ChooserManager().startSession(context, Intent(ACTION_CHOOSER))
        val intentCapture = argumentCaptor<Intent>()
        verify(context) { 1 * { startActivity(intentCapture.capture(), any()) } }
        assertThat(intentCapture.firstValue).isNotNull()
        val extras = intentCapture.firstValue.extras
        assertThat(extras).isNotNull()
        val controllerCallback = extras!!.getBinder(ChooserSession.EXTRA_CHOOSER_SESSION)
        assertThat(controllerCallback).isInstanceOf(IChooserControllerCallback::class.java)
        return session to (controllerCallback as IChooserControllerCallback)
    }

    private class ImmediateExecutor : Executor {
        override fun execute(command: Runnable) {
            command.run()
        }
    }
}
