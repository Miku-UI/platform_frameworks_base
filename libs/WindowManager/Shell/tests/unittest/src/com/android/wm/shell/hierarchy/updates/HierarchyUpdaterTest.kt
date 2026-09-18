/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.wm.shell.hierarchy.updates

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.UserInfo
import android.graphics.Rect
import android.hardware.devicestate.DeviceStateManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.IBinder
import android.platform.test.annotations.EnableFlags
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import android.view.InsetsSource
import android.view.InsetsState
import android.view.Surface
import android.view.SurfaceControl
import android.view.WindowInsets.Type.navigationBars
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_OPEN
import android.view.WindowManager.TRANSIT_TO_BACK
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.window.ActivityTransitionInfo
import android.window.DisplayAreaOrganizer.FEATURE_DEFAULT_TASK_CONTAINER
import android.window.TaskAppearedInfo
import android.window.TransitionInfo
import android.window.TransitionInfo.FLAG_IS_DISPLAY
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_MODES
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestSyncExecutor
import com.android.wm.shell.common.DisplayInsetsController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.hierarchy.ContainerHierarchy
import com.android.wm.shell.hierarchy.containers.Container
import com.android.wm.shell.hierarchy.containers.StubContainer
import com.android.wm.shell.hierarchy.modes.FormFactorModes
import com.android.wm.shell.hierarchy.modes.Mode
import com.android.wm.shell.hierarchy.modes.StubMode
import com.android.wm.shell.hierarchy.properties.DeviceState
import com.android.wm.shell.hierarchy.properties.DisplayAreaContainerProperties
import com.android.wm.shell.hierarchy.properties.DisplayContainerProperties
import com.android.wm.shell.hierarchy.properties.TaskContainerProperties
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_INSETS
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_IS_FOLDED
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_KEYGUARD
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_USER
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_USER_PROFILES
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.AnimationPlan
import com.android.wm.shell.transition.Transitions
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify

/**
 * Tests for [HierarchyUpdater].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:ContainerHierarchyUpdaterTest
 */
@EnableFlags(FLAG_ENABLE_SHELL_MODES)
@SmallTest
@RunWith(AndroidJUnit4::class)
class HierarchyUpdaterTest : ShellTestCase() {

    private val shellController = mock<ShellController>()
    private val shellTaskOrganizer = mock<ShellTaskOrganizer>()
    private val transitions = mock<Transitions>()
    private val displayInsetsController = mock<DisplayInsetsController>()
    private val deviceStateManager = mock<DeviceStateManager>()
    private val formFactorModes = mock<FormFactorModes>()
    private val shellInit = mock<ShellInit>()
    private val testExecutor = TestSyncExecutor()

    // Create a test hierarchy
    private val display =
        StubContainer(
            WindowContainerToken.createProxy("Display"),
            DisplayContainerProperties(DEFAULT_DISPLAY)
        ).apply {
            leash = mock<SurfaceControl>()
        }
    private val displayArea =
        StubContainer(
            WindowContainerToken.createProxy("DisplayArea"),
            DisplayAreaContainerProperties(FEATURE_DEFAULT_TASK_CONTAINER)
        ).apply {
            leash = mock<SurfaceControl>()
        }
    private val child1 =
        StubContainer(
            WindowContainerToken.createProxy("Child1"),
            TaskContainerProperties(ActivityManager.RunningTaskInfo().apply {
                taskId = 1
            })
        )
    private val child1Mode = spy(StubMode("Child1Mode"))
    private val child2 =
        StubContainer(
            WindowContainerToken.createProxy("Child2"),
            TaskContainerProperties(ActivityManager.RunningTaskInfo().apply {
                taskId = 2
            })
        )
    private val child2Mode = spy(StubMode("Child2Mode"))
    private val hierarchy = ContainerHierarchy().apply {
        root.mode = StubMode("RootMode")
        display.parent = root
        displayArea.parent = display
        child1.parent = displayArea
        child1.mode = child1Mode
        child2.parent = child1
        child2.mode = child2Mode
    }

    private lateinit var updater: HierarchyUpdater

    @Before
    fun setup() {
        updater =
            HierarchyUpdater(
                context,
                shellController,
                shellTaskOrganizer,
                transitions,
                displayInsetsController,
                deviceStateManager,
                hierarchy,
                formFactorModes,
                shellInit,
                testExecutor,
                mock<Handler>(),
            )
    }

    @Test
    fun testOpenTransition() {
        // Create an open transition
        val wct = WindowContainerToken.createProxy("test")
        val change = TransitionInfo.Change(wct, mock<SurfaceControl>()).apply {
            mode = TRANSIT_OPEN
            flags = FLAG_IS_DISPLAY
        }
        val info = TransitionInfo(TRANSIT_OPEN, 0).apply {
            addChange(change)
        }

        // Notify the transition
        updater.handleTransition(
            mock<IBinder>(),
            mock<AnimationPlan>(),
            info,
            mock<SurfaceControl.Transaction>(),
        )

        // Ensure that a container was added
        assertThat(hierarchy.getContainer(wct)).isNotNull()
    }

    @Test
    fun testCloseTransition() {
        // Add existing container in hierarchy
        val wct = WindowContainerToken.createProxy("test")
        val container = StubContainer(wct)
        container.parent = hierarchy.root

        // Create a close transition
        val change = TransitionInfo.Change(wct, mock<SurfaceControl>()).apply {
            mode = TRANSIT_CLOSE
        }
        val info = TransitionInfo(TRANSIT_CLOSE, 0).apply {
            addChange(change)
        }

        // Notify the transition
        updater.handleTransition(
            mock<IBinder>(),
            mock<AnimationPlan>(),
            info,
            mock<SurfaceControl.Transaction>(),
        )

        // Ensure that a container was removed
        assertThat(hierarchy.getContainer(wct)).isNull()
        assertThat(container.parent).isNull()
    }

    @Test
    fun testToFrontTransition() {
        // Add two containers in hierarchy in order (1, 2)
        val token1 = WindowContainerToken.createProxy("test")
        val container1 = StubContainer(token1)
        val token2 = WindowContainerToken.createProxy("test")
        val container2 = StubContainer(token2)
        container1.parent = hierarchy.root
        container2.parent = hierarchy.root

        // Create a to-front transition
        val change = TransitionInfo.Change(token1, mock<SurfaceControl>()).apply {
            mode = TRANSIT_TO_FRONT
            flags = FLAG_IS_DISPLAY
        }
        val info = TransitionInfo(TRANSIT_TO_FRONT, 0).apply {
            addChange(change)
        }

        // Notify the transition
        updater.handleTransition(
            mock<IBinder>(),
            mock<AnimationPlan>(),
            info,
            mock<SurfaceControl.Transaction>(),
        )

        // Verify 1 moved to top
        assertThat(hierarchy.root.children.indexOf(container1)).isGreaterThan(
            hierarchy.root.children.indexOf(
                container2
            )
        )
    }

    @Test
    fun testToBackTransition() {
        // Add two containers in hierarchy in order (1, 2)
        val token1 = WindowContainerToken.createProxy("test")
        val container1 = StubContainer(token1)
        val token2 = WindowContainerToken.createProxy("test")
        val container2 = StubContainer(token2)
        container1.parent = hierarchy.root
        container2.parent = hierarchy.root

        // Create a to-back transition
        val change = TransitionInfo.Change(token2, mock<SurfaceControl>()).apply {
            mode = TRANSIT_TO_BACK
            flags = FLAG_IS_DISPLAY
        }
        val info = TransitionInfo(TRANSIT_TO_BACK, 0).apply {
            addChange(change)
        }

        // Notify the transition
        updater.handleTransition(
            mock<IBinder>(),
            mock<AnimationPlan>(),
            info,
            mock<SurfaceControl.Transaction>(),
        )

        // Verify 2 moved to back
        assertThat(hierarchy.root.children.indexOf(container2)).isLessThan(
            hierarchy.root.children.indexOf(
                container1
            )
        )
    }

    @Test
    fun testDisplayChangeInvalidatesContext() {
        val mockDisplayManager = mock<DisplayManager>()
        mockDisplayManager.stub {
            on { getDisplay(any()) } doAnswer {
                mock<Display>()
            }
        }
        val baseContext = mock<Context>()
        baseContext.stub {
            on { getSystemService(eq(DisplayManager::class.java)) } doAnswer {
                mockDisplayManager
            }
            on { createDisplayContext(any()) } doAnswer {
                mock<Context>()
            }
        }

        // Create a separate display container since we don't create display contexts for the
        // default display
        val otherDisplay =
            Container(
                WindowContainerToken.createProxy("Display2"),
                DisplayContainerProperties(12345)
            ).apply {
                parent = hierarchy.root
                leash = mock<SurfaceControl>()
            }

        // Get the old context instance
        val displayProps = otherDisplay.displayProps()
        val preChangeDisplayContext = displayProps.getDisplayContext(baseContext)
        // Just verify it's cached
        assertThat(displayProps.getDisplayContext(baseContext)).isEqualTo(preChangeDisplayContext)

        // Trigger a default display change
        val info = TransitionInfo(TRANSIT_CHANGE, 0).apply {
            val change = TransitionInfo.Change(otherDisplay.token, otherDisplay.leash).apply {
                mode = TRANSIT_CHANGE
                setEndAbsBounds(Rect(0, 0, 500, 500))
                setRotation(Surface.ROTATION_0, Surface.ROTATION_90)
            }
            addChange(change)
        }
        updater.handleTransition(
            mock<IBinder>(),
            mock<AnimationPlan>(),
            info,
            mock<SurfaceControl.Transaction>(),
        )

        // Verify that the context has changed
        assertThat(displayProps.getDisplayContext(baseContext))
            .isNotEqualTo(preChangeDisplayContext)
    }

    @Test
    fun testUpdateChildOfParentPropsChanging() {
        // Update child1
        val change = TransitionInfo.Change(child1.token, mock<SurfaceControl>()).apply {
            mode = TRANSIT_CHANGE
            setEndAbsBounds(Rect(0, 0, 500, 500))
        }
        child1.updateFromWindowChange(hierarchy.root, change)

        // Verify that the children have changed
        for (child in child1.children) {
            assertThat((child as StubContainer).parentConfigChanged).isTrue()
        }
    }

    @Test
    fun testNotifyMode_onChildAdded() {
        // Create a new child
        val newChild =
            Container(
                WindowContainerToken.createProxy("new child"),
                TaskContainerProperties(ActivityManager.RunningTaskInfo().apply {
                    taskId = 1234
                })
            )

        // Add it to the hierarchy and notify
        val snapshot = HierarchySnapshot(hierarchy.toContainerList())
        newChild.parent = child2
        updater.notifyModes(Mode.UpdateContext(), snapshot)

        // Verify the container was attached to the associated ancestor mode
        verify(child2Mode).containersChanged(any(), any(), any(), any(), any(), anyOrNull())
        assertThat(child2Mode.enteringContainers).contains(newChild)
        assertThat(child2Mode.changedContainers).contains(child2)
        verify(child1Mode, never()).containersChanged(
            any(),
            any(),
            any(),
            any(),
            any(),
            anyOrNull()
        )
    }

    @Test
    fun testNotifyMode_onChildRemoved() {
        // Create a new child
        val newChild =
            Container(
                WindowContainerToken.createProxy("new child"),
                TaskContainerProperties(ActivityManager.RunningTaskInfo().apply {
                    taskId = 1234
                })
            )

        // Add it to the hierarchy and notify
        val snapshot1 = HierarchySnapshot(hierarchy.toContainerList())
        newChild.parent = child2
        updater.notifyModes(Mode.UpdateContext(), snapshot1)

        reset(child1Mode)
        reset(child2Mode)

        // Remove it from the hierarchy and notify
        val snapshot2 = HierarchySnapshot(hierarchy.toContainerList())
        newChild.parent = null
        updater.notifyModes(Mode.UpdateContext(), snapshot2)

        // Verify the container was detached from the associated ancestor mode
        verify(child2Mode).containersRemoved(any(), any(), any(), any(), anyOrNull())
        assertThat(child2Mode.removedContainers).contains(newChild)
        verify(child1Mode, never()).containersRemoved(any(), any(), any(), any(), anyOrNull())
    }

    @Test
    fun testNotifyMode_onChildReparented() {
        // Create a new child
        val newChild =
            Container(
                WindowContainerToken.createProxy("new child"),
                TaskContainerProperties(ActivityManager.RunningTaskInfo().apply {
                    taskId = 1234
                })
            )

        // Add it to the hierarchy under child1 and notify
        val snapshot1 = HierarchySnapshot(hierarchy.toContainerList())
        newChild.parent = child1
        updater.notifyModes(Mode.UpdateContext(), snapshot1)

        reset(child1Mode)
        reset(child2Mode)

        // Move it the child under child2 and notify
        val snapshot2 = HierarchySnapshot(hierarchy.toContainerList())
        newChild.parent = child2
        updater.notifyModes(Mode.UpdateContext(), snapshot2)

        // Verify the container was detached from the previous mode, and attached to the new mode
        verify(child2Mode).containersChanged(any(), any(), any(), any(), any(), anyOrNull())
        assertThat(child2Mode.enteringContainers).contains(newChild)
        assertThat(child2Mode.changedContainers).contains(child2)
        assertThat(child2Mode.changedContainers).doesNotContain(newChild)
        verify(child1Mode).containersRemoved(any(), any(), any(), any(), anyOrNull())
        assertThat(child1Mode.leavingContainers).contains(newChild)
        assertThat(child1Mode.removedContainers).isEmpty()
    }

    @Test
    fun testNotifyMode_onAncestorContainerChange() {
        // Create containers
        val wct1 = WindowContainerToken.createProxy("test")
        val child1 = StubContainer(wct1)
        child1.parent = hierarchy.root

        val wct2 = WindowContainerToken.createProxy("test")
        val child2 = StubContainer(wct2)
        val mode2 = StubMode()
        child2.parent = child1
        child2.mode = mode2

        // Create a transition that updates focus (by starting a new task)
        val taskId = 10
        val taskInfo = ActivityManager.RunningTaskInfo().apply {
            this.taskId = taskId
            this.isFocused = true
            this.displayId = DEFAULT_DISPLAY
        }
        val wct = WindowContainerToken.createProxy("test")
        val change = TransitionInfo.Change(wct, mock<SurfaceControl>()).apply {
            mode = TRANSIT_OPEN
            this.taskInfo = taskInfo
        }
        val info = TransitionInfo(TRANSIT_OPEN, 0).apply {
            addChange(change)
        }

        // Notify the transition
        updater.handleTransition(
            mock<IBinder>(),
            mock<AnimationPlan>(),
            info,
            mock<SurfaceControl.Transaction>(),
        )

        // Verify that child2 mode is updated (because ancestor changed)
        assertThat(mode2.globalStateSnapshot).isNotNull()
    }

    @Test
    fun testNotifyMode_onModeContainerChange() {
        // Create containers
        val wct1 = WindowContainerToken.createProxy("test")
        val child1 = StubContainer(wct1)
        val mode1 = StubMode()
        child1.parent = hierarchy.root
        child1.mode = mode1

        // Trigger a change on child1
        val snapshot = HierarchySnapshot(hierarchy.toContainerList())
        child1.setBounds(Rect(0, 0, 50, 50))
        updater.notifyModes(Mode.UpdateContext(), snapshot)

        // Verify that child1 mode is updated (because an ancestor changed)
        assertThat(mode1.changedContainers).contains(child1)
    }

    @Test
    fun testNotifyMode_onDescendantContainerChange() {
        // Create containers
        val wct1 = WindowContainerToken.createProxy("test")
        val child1 = StubContainer(wct1)
        val mode1 = StubMode()
        child1.parent = hierarchy.root
        child1.mode = mode1

        val wct2 = WindowContainerToken.createProxy("test")
        val child2 = StubContainer(wct2)
        child2.parent = child1

        // Trigger a change on child2
        val snapshot = HierarchySnapshot(hierarchy.toContainerList())
        child2.setBounds(Rect(0, 0, 50, 50))
        updater.notifyModes(Mode.UpdateContext(), snapshot)

        // Verify that child1 mode is updated (because a descendant changed)
        assertThat(mode1.changedContainers).contains(child2)
    }

    @Test
    fun testHandleCreateRootTask() {
        // Create a new root task
        val wct = WindowContainerToken.createProxy("test")
        val taskInfo = ActivityManager.RunningTaskInfo().apply {
            token = wct
        }
        val appearedInfo = TaskAppearedInfo(taskInfo, mock<SurfaceControl>())
        updater.handleCreateRootTask(appearedInfo, "test")

        // Ensure that a task container was added
        val container = hierarchy.getContainer(wct)
        assertThat(container).isNotNull()
        assertThat(container!!.props).isInstanceOf(TaskContainerProperties::class.java)
        assertThat(container.parent).isEqualTo(displayArea)
    }

    @Test
    fun testHandleRemoveRootTask() {
        // Add existing container in hierarchy
        val wct = WindowContainerToken.createProxy("test")
        val container = StubContainer(wct)
        container.parent = hierarchy.root

        updater.handleRemoveRootTask(wct)

        // Ensure that a container was removed
        assertThat(hierarchy.getContainer(wct)).isNull()
        assertThat(container.parent).isNull()
    }

    @Test
    fun testCreateTransientContainersInHierarchy() {
        val taskInfo = ActivityManager.RunningTaskInfo().apply {
            taskId = 1234
        }
        val task =
            Container(
                WindowContainerToken.createProxy("test"),
                TaskContainerProperties(taskInfo),
            )
        task.parent = display

        // Create a transition with a wallpaper and activity change
        val activityToken = WindowContainerToken.createProxy("test")
        val activityChange = TransitionInfo.Change(activityToken, mock<SurfaceControl>()).apply {
            mode = TRANSIT_OPEN
            activityTransitionInfo =
                ActivityTransitionInfo(
                    mock<ComponentName>(),
                    task.taskProps().taskId
                )
            parent = task.token
        }
        val info = TransitionInfo(TRANSIT_OPEN, 0).apply {
            addChange(activityChange)
        }

        var hookCalled = false
        updater.updaterTestHook = object : HierarchyUpdater.UpdaterTestHook {
            override fun onHierarchyUpdated() {
                // Reset once hit
                updater.updaterTestHook = null
                hookCalled = true
                // Verify transient containers exist
                val activity = hierarchy.getContainer(activityToken)
                assertThat(activity).isNotNull()
                assertThat(activity!!.isActivity()).isTrue()
            }
        }

        // Have the transition complete immediately after the update
        transitions.stub {
            on { runOnIdle(any()) } doAnswer {
                val callback = it.getArgument<Runnable>(0)
                callback.run()
            }
        }

        // Notify the transition
        updater.handleTransition(
            mock<IBinder>(),
            mock<AnimationPlan>(),
            info,
            mock<SurfaceControl.Transaction>(),
        )

        // Verify the hook above was called and the transitions ran
        assertThat(hookCalled).isTrue()

        // Verify transient containers are removed
        assertThat(hierarchy.getContainer(activityToken)).isNull()
    }

    @Test
    fun testUpdateFocusedTask() {
        // Create a transition that updates focus
        val taskId = 10
        val taskInfo = ActivityManager.RunningTaskInfo().apply {
            this.taskId = taskId
            this.isFocused = true
            this.displayId = DEFAULT_DISPLAY
        }
        val wct = WindowContainerToken.createProxy("test")
        val change = TransitionInfo.Change(wct, mock<SurfaceControl>()).apply {
            mode = TRANSIT_OPEN
            this.taskInfo = taskInfo
        }
        val info = TransitionInfo(TRANSIT_OPEN, 0).apply {
            addChange(change)
        }

        // Notify the transition
        updater.handleTransition(
            mock<IBinder>(),
            mock<AnimationPlan>(),
            info,
            mock<SurfaceControl.Transaction>(),
        )

        // Verify the root container's focus state is updated
        val rootProps = hierarchy.root.rootProps()
        assertThat(rootProps.focusState.globallyFocusedTaskId).isEqualTo(taskId)
    }

    @Test
    fun testRequestUpdateForDisplayChange() {
        // Create two displays in the hierarchy with children that have modes
        val otherDisplay =
            Container(
                WindowContainerToken.createProxy("Display2"),
                DisplayContainerProperties(12345)
            )
        otherDisplay.parent = hierarchy.root
        val otherDisplayChild = StubContainer(WindowContainerToken.createProxy("OtherDispChild"))
        val otherDisplayChildMode = StubMode()
        otherDisplayChild.parent = otherDisplay
        otherDisplayChild.mode = otherDisplayChildMode

        // Trigger a default display change
        val wct = WindowContainerTransaction()
        val displayLayout = DisplayLayout().apply {
            rotateTo(mContext.resources, Surface.ROTATION_90)
        }
        updater.updateDisplay(DEFAULT_DISPLAY, displayLayout, wct)

        // Verify that the root & default display modes are notified, but not others
        assertThat((hierarchy.root.mode as StubMode).requestedDisplayChanges).isNotEmpty()
        assertThat(child1Mode.requestedDisplayChanges).isNotEmpty()
        assertThat(otherDisplayChildMode.requestedDisplayChanges).isEmpty()
    }

    @Test
    fun testUpdateFromTaskInfoChange() {
        // Trigger a task info change
        val taskInfo = ActivityManager.RunningTaskInfo().apply {
            token = child1.token
            taskDescription = ActivityManager.TaskDescription().apply {
                label = "test"
            }
        }
        updater.handleTaskInfoChanged(taskInfo)

        // Verify that child1 mode is updated
        assertThat(child1Mode.changedContainers).contains(child1)
    }

    @Test
    fun testUpdateFromInsetsChange() {
        // Create new insets
        val newInsets = InsetsState().apply {
            val id = InsetsSource.createId(null, 1, navigationBars())
            val source = InsetsSource(id, navigationBars()).apply {
                isVisible = true
            }
            this.addSource(source)
        }

        // Trigger the update
        updater.handleDisplayInsetsChanged(DEFAULT_DISPLAY, newInsets)

        // Verify that the display container props have the latest insets
        assertThat(display.displayProps().insetsState.insetsState).isEqualTo(
            newInsets
        )
        // Verify that the children modes are updated when the display changes
        assertGlobalStateChangeReportedToChildrenModes(display, CHANGED_INSETS)
    }

    @Test
    fun testUpdateFromUserChange() {
        val newUserId = 1234
        // Trigger the update
        updater.handleUserChanged(newUserId)

        // Verify that the display container props have the latest user
        assertThat(hierarchy.root.rootProps().userState.currentUserId).isEqualTo(newUserId)

        // Verify that the children modes are updated when the user changes
        assertGlobalStateChangeReportedToChildrenModes(hierarchy.root, CHANGED_USER)
    }

    @Test
    fun testUpdateFromUserProfilesChange() {
        val profiles = listOf(mock<UserInfo>(), mock<UserInfo>())
        // Trigger the update
        updater.handleUserProfilesChanged(profiles)

        // Verify that the display container props have the latest user profiles
        assertThat(hierarchy.root.rootProps().userState.currentUserProfiles).isEqualTo(profiles)

        // Verify that the children modes are updated when the user profile changes
        assertGlobalStateChangeReportedToChildrenModes(hierarchy.root, CHANGED_USER_PROFILES)
    }

    @Test
    fun testUpdateFromKeyguardChange() {
        // Trigger the update
        updater.handleKeyguardVisibilityChanged(DeviceState.KeyguardState.Occluded)

        // Verify that the display container props have the latest keyguard state
        assertThat(hierarchy.root.rootProps().deviceState.keyguardState)
            .isEqualTo(DeviceState.KeyguardState.Occluded)

        // Verify that the children modes are updated when the keyguard state changes
        assertGlobalStateChangeReportedToChildrenModes(hierarchy.root, CHANGED_KEYGUARD)
    }

    @Test
    fun testUpdateFromFoldStateChange() {
        // Trigger the update
        updater.foldStateListener = mock<DeviceStateManager.FoldStateListener>()
        updater.foldStateListener.stub {
            on { folded } doAnswer {
                true
            }
        }
        updater.handleDeviceStateChanged(mock<android.hardware.devicestate.DeviceState>())

        // Verify that the display container props have the latest folded state
        assertThat(hierarchy.root.rootProps().deviceState.isFolded).isTrue()

        // Verify that the children modes are updated when the folded state changes
        assertGlobalStateChangeReportedToChildrenModes(hierarchy.root, CHANGED_IS_FOLDED)
    }

    /**
     * Asserts that a global state has changed and that child modes should be notified.
     */
    private fun assertGlobalStateChangeReportedToChildrenModes(
        expectedContainer: Container,
        expectedChangeFlag: Int,
    ) {
        assertThat(child1Mode.globalStateSnapshot).isNotNull()
        assertThat(child1Mode.changedContainers).isEmpty()
        assertThat(child2Mode.globalStateSnapshot).isNotNull()
        assertThat(child2Mode.changedContainers).isEmpty()

        val expectedContainerChgs = child1Mode.globalStateSnapshot!!.getChanges(expectedContainer)
        assertThat(expectedContainerChgs[expectedChangeFlag]).isTrue()
    }
}