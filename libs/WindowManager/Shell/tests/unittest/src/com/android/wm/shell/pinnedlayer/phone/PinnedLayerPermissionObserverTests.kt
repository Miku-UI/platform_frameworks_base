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

package com.android.wm.shell.pinnedlayer.phone

import android.app.AppOpsManager
import android.app.TaskInfo
import android.os.Binder
import android.os.IBinder
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_CLOSE
import android.window.TransitionInfo
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_REMOVE_TASK
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.window.flags.Flags
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.common.MultiDisplayDragMoveIndicatorController
import com.android.wm.shell.desktopmode.FakeShellDesktopState
import com.android.wm.shell.desktopmode.WindowDragTransitionHandler
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerController.UnpinStrategy
import com.android.wm.shell.shared.TransactionPool
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests the [PinnedLayerPermissionObserver] together with [PinnedLayerController] to ensure proper
 * communication between these two components.
 *
 * Build/Install/Run: atest WMShellUnitTests:PinnedLayerPermissionObserverTests
 */
@SmallTest
@TestableLooper.RunWithLooper
@EnableFlags(Flags.FLAG_ENABLE_INTERACTIVE_PICTURE_IN_PICTURE)
@RunWith(AndroidJUnit4::class)
class PinnedLayerPermissionObserverTests : ShellTestCase() {

    @Mock private lateinit var shellInit: ShellInit
    @Mock private lateinit var transitions: Transitions
    @Mock private lateinit var presentationController: PinnedLayerPresentationController
    @Mock private lateinit var windowDragTransitionHandler: WindowDragTransitionHandler
    @Mock
    private lateinit var pinnedWindowRepositionAnimationHandler:
        PinnedWindowRepositionAnimationHandler
    @Mock private lateinit var transactionPool: TransactionPool
    @Mock private lateinit var rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer
    @Mock
    private lateinit var multiDisplayDragMoveIndicatorController:
        MultiDisplayDragMoveIndicatorController
    @Mock private lateinit var shellTaskOrganizer: ShellTaskOrganizer

    private val uid = Binder.getCallingUid()
    private val shellExecutor = ObservedTestShellExecutor()
    private lateinit var pinnedLayerController: PinnedLayerController
    private lateinit var pinnedLayerPermissionObserver: PinnedLayerPermissionObserver
    private lateinit var appOpsManager: AppOpsManager
    private lateinit var desktopState: FakeDesktopState
    private lateinit var shellDesktopState: FakeShellDesktopState

    private val windowRepositionAnimator = mock<PinnedWindowRepositionAnimator>()

    @Before
    fun setup() {
        appOpsManager = context.getSystemService(AppOpsManager::class.java)

        desktopState = FakeDesktopState()
        desktopState.canEnterDesktopMode = true
        shellDesktopState = FakeShellDesktopState(desktopState)
        shellDesktopState.canBeWindowDropTarget = true

        pinnedLayerController =
            PinnedLayerController(
                shellInit,
                transitions,
                shellDesktopState,
                rootTaskDisplayAreaOrganizer,
                shellTaskOrganizer,
                presentationController,
                windowDragTransitionHandler,
                pinnedWindowRepositionAnimationHandler,
                windowRepositionAnimator,
                transactionPool,
                multiDisplayDragMoveIndicatorController,
            )
        pinnedLayerPermissionObserver =
            PinnedLayerPermissionObserver(context, shellExecutor, pinnedLayerController)
        runWithShellPermissionIdentity {
            appOpsManager.setMode(
                AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                uid,
                context.packageName,
                AppOpsManager.MODE_ALLOWED,
            )
        }
    }

    @Test
    fun listeners_whenTaskIsPinned_areNotified() {
        val taskInfo = createTaskInfo()
        val listener = mock<PinnedLayerController.PinnedTasksListener>()

        pinnedLayerController.addPinnedTasksListener(listener)
        pinTask(taskInfo)

        verify(listener).onPinnedTasksAdded(eq(taskInfo))
    }

    @Test
    fun listeners_whenTaskIsUnpinned_areNotified() {
        val taskInfo = createTaskInfo()
        val listener = mock<PinnedLayerController.PinnedTasksListener>()
        pinTask(taskInfo)

        pinnedLayerController.addPinnedTasksListener(listener)
        unpinTask(taskInfo)

        verify(listener).onPinnedTasksRemoved(eq(taskInfo))
    }

    @Test
    fun permissionObserver_whenPipIsDisabled_closesTask() {
        val token = mock<IBinder>()
        val taskInfo = createTaskInfo() { setToken(wctMock(token)) }
        pinTask(taskInfo) // to start observing for permission changes
        verify(transitions, never()).startTransition(eq(TRANSIT_CLOSE), any(), any())

        shellExecutor.latch = CountDownLatch(1)
        runWithShellPermissionIdentity {
            appOpsManager.setMode(
                AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                uid,
                context.packageName,
                AppOpsManager.MODE_ERRORED,
            )
        }
        // Wait for the permission change to be processed. ShellExecutor#execute is a good signal
        // that the permission change was received by the observer.
        shellExecutor.latch!!.await(1, TimeUnit.SECONDS)
        shellExecutor.flushAll()

        val captor = argumentCaptor<WindowContainerTransaction>()
        verify(transitions).startTransition(eq(TRANSIT_CLOSE), captor.capture(), anyOrNull())
        assertContainsCloseHierOpFor(captor.firstValue, token)
    }

    private fun assertContainsCloseHierOpFor(wct: WindowContainerTransaction, token: IBinder) {
        val found =
            wct.hierarchyOps.any { op ->
                op.type == HIERARCHY_OP_TYPE_REMOVE_TASK && op.container == token
            }
        assertTrue(found, "Expected to find a HIERARCHY_OP_TYPE_REMOVE_TASK for provided token")
    }

    private fun pinTask(taskInfo: TaskInfo) {
        val transition = mock<IBinder>()
        val wct = pinnedLayerController.pinTask(transition, taskInfo, /* remoteCallback= */ null)
        assumeTrue(
            "Controller returned a null WCT, meaning the pin layer is not available on this device.",
            wct != null,
        )
        pinnedLayerController.onTransitionReady(
            transition,
            TransitionInfo(TRANSIT_CHANGE, /* flags= */ 0),
            mock(),
            mock(),
        )
    }

    private fun unpinTask(taskInfo: TaskInfo) {
        val transition = mock<IBinder>()
        val wct = pinnedLayerController.unpinTask(transition, taskInfo, UnpinStrategy.CLOSE)
        pinnedLayerController.onTransitionReady(
            transition,
            TransitionInfo(TRANSIT_CLOSE, /* flags= */ 0),
            mock(),
            mock(),
        )
    }

    private fun createTaskInfo(customizer: TestRunningTaskInfoBuilder.() -> Unit = {}): TaskInfo =
        TestRunningTaskInfoBuilder()
            .setTaskId(TASK_ID)
            .setUserId(context.userId)
            .setUid(uid)
            .setBaseActivity(InstrumentationRegistry.getInstrumentation().componentName)
            .apply(customizer)
            .build()

    private fun wctMock(token: IBinder): WindowContainerToken {
        val wct = mock<WindowContainerToken>()
        whenever(wct.asBinder()).thenReturn(token)
        return wct
    }

    private fun <R> runWithShellPermissionIdentity(block: () -> R): R {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation()
        uiAutomation.adoptShellPermissionIdentity()
        try {
            return block()
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }

    private class ObservedTestShellExecutor : TestShellExecutor() {
        var latch: CountDownLatch? = null

        override fun execute(command: Runnable) {
            super.execute(command)
            latch?.countDown()
        }
    }

    companion object {
        private const val TASK_ID = 1
    }
}
