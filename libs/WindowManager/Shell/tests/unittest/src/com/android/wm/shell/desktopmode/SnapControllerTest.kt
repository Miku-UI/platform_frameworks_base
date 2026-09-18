/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may not use this file except in compliance with the License.
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

package com.android.wm.shell.desktopmode

import android.app.ActivityManager
import android.graphics.Rect
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.windowdecor.tiling.SnapEventHandler
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock

/**
 * Tests for [com.android.wm.shell.desktopmode.SnapController]
 *
 * Usage: atest WMShellUnitTests:SnapControllerTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class SnapControllerTest : ShellTestCase() {

    private lateinit var desktopTasksLimiter: DesktopTasksLimiter
    private lateinit var snapEventHandler: SnapEventHandler

    private lateinit var snapController: SnapController

    @Before
    fun setUp() {
        snapEventHandler = mock<SnapEventHandler>()
        snapController = SnapController()
        snapController.start(snapEventHandler)
    }

    @Test
    fun testSnapToHalfScreen() {
        val taskInfo = ActivityManager.RunningTaskInfo()
        val bounds = Rect()
        val position = DesktopTasksController.SnapPosition.LEFT
        snapController.snapToHalfScreen(taskInfo, bounds, position)
        verify(snapEventHandler).snapToHalfScreen(taskInfo, bounds, position)
    }

    @Test
    fun testSnapPersistedTaskToHalfScreen() {
        val taskInfo = ActivityManager.RunningTaskInfo()
        val bounds = Rect()
        val position = DesktopTasksController.SnapPosition.LEFT
        snapController.snapPersistedTaskToHalfScreen(taskInfo, bounds, position)
        verify(snapEventHandler).snapPersistedTaskToHalfScreen(taskInfo, bounds, position)
    }

    @Test
    fun testRemoveTaskIfTiled() {
        val displayId = 1
        val taskId = 2
        snapController.removeTaskIfTiled(displayId, taskId)
        verify(snapEventHandler).removeTaskIfTiled(displayId, taskId)
    }

    @Test
    fun testOnUserChange() {
        val userId = 3
        snapController.onUserChange(userId)
        verify(snapEventHandler).onUserChange(userId)
    }

    @Test
    fun testOnRecentsAnimationEndedToSameDesk() {
        snapController.onRecentsAnimationEndedToSameDesk()
        verify(snapEventHandler).onRecentsAnimationEndedToSameDesk()
    }

    @Test
    fun testMoveTaskToFrontIfTiled() {
        val taskInfo = ActivityManager.RunningTaskInfo()
        snapController.moveTaskToFrontIfTiled(taskInfo)
        verify(snapEventHandler).moveTaskToFrontIfTiled(taskInfo)
    }

    @Test
    fun testGetLeftSnapBoundsIfTiled() {
        val displayId = 4
        snapController.getLeftSnapBoundsIfTiled(displayId)
        verify(snapEventHandler).getLeftSnapBoundsIfTiled(displayId)
    }

    @Test
    fun testGetRightSnapBoundsIfTiled() {
        val displayId = 5
        snapController.getRightSnapBoundsIfTiled(displayId)
        verify(snapEventHandler).getRightSnapBoundsIfTiled(displayId)
    }

    @Test
    fun testOnDeskDeactivated() {
        val deskId = 6
        snapController.onDeskDeactivated(deskId)
        verify(snapEventHandler).onDeskDeactivated(deskId)
    }

    @Test
    fun testOnDisplayDisconnected() {
        val displayId = 7
        snapController.onDisplayDisconnected(displayId)
        verify(snapEventHandler).onDisplayDisconnected(displayId)
    }

    @Test
    fun testOnDeskActivated() {
        val deskId = 8
        val displayId = 9
        snapController.onDeskActivated(deskId, displayId)
        verify(snapEventHandler).onDeskActivated(deskId, displayId)
    }

    @Test
    fun testOnDeskRemoved() {
        val deskId = 10
        snapController.onDeskRemoved(deskId)
        verify(snapEventHandler).onDeskRemoved(deskId)
    }

    @Test
    fun testNotifyTilingOfExplodedViewReorder() {
        val deskId = 11
        val topTaskId = 12
        snapController.notifyTilingOfExplodedViewReorder(deskId, topTaskId)
        verify(snapEventHandler).notifyTilingOfExplodedViewReorder(deskId, topTaskId)
    }

    @Test
    fun testGetDividerBounds() {
        val deskId = 13
        snapController.getDividerBounds(deskId)
        verify(snapEventHandler).getDividerBounds(deskId)
    }

    @Test
    fun testOnDisplayLayoutChange() {
        val displayId = 14
        val config = null
        val oldStableBounds = Rect()
        val newToOldDpiRatio = 1.0
        snapController.onDisplayLayoutChange(displayId, config, oldStableBounds, newToOldDpiRatio)
        verify(snapEventHandler)
            .onDisplayLayoutChange(displayId, config, oldStableBounds, newToOldDpiRatio)
    }

    @Test
    fun testOnTaskLaunchStarted() {
        snapController.onTaskLaunchStarted()
        verify(snapEventHandler).onTaskLaunchStarted()
    }
}
