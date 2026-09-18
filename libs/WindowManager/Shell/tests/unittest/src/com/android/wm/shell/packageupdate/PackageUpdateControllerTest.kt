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

package com.android.wm.shell.packageupdate

import android.app.ActivityManager.RunningTaskInfo
import android.app.ApplicationPackageManager
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.view.Display.DEFAULT_DISPLAY
import android.view.WindowManager
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_OPEN
import android.window.WindowContainerTransaction
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_CONTINUE_PACKAGE_UPDATE
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_PENDING_INTENT
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_REMOVE_TASK
import androidx.test.filters.SmallTest
import com.android.testing.wm.util.MockToken
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.UserProfileContexts
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.windowdecor.DesktopModeWindowDecorViewModel
import com.android.wm.shell.windowdecor.common.WindowDecorTaskResourceLoader
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.Optional
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isA
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.whenever

/** Tests for [PackageUpdateController] */
@SmallTest
@RunWith(AndroidTestingRunner::class)
@EnableFlags(com.android.window.flags.Flags.FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
class PackageUpdateControllerTest : ShellTestCase() {
    private val transitions = mock<Transitions>()
    private val shellTaskOrganizer = mock<ShellTaskOrganizer>()
    private val shellInit = mock<ShellInit>()
    private val userProfileContexts = mock<UserProfileContexts>()
    private val taskResourceLoader = mock<WindowDecorTaskResourceLoader>()
    private val viewModel = mock<DesktopModeWindowDecorViewModel>()
    private val transitionHandler = mock<PackageUpdateTransitionHandler>()
    private val packageManager = mock<ApplicationPackageManager>()
    private val testScope = TestScope()
    private val testDispatcher = StandardTestDispatcher(testScope.testScheduler)
    private lateinit var packageUpdateController: PackageUpdateController

    @Before
    fun setUp() {
        packageUpdateController =
            PackageUpdateController(
                transitions,
                shellTaskOrganizer,
                shellInit,
                userProfileContexts,
                taskResourceLoader,
                Optional.of(viewModel),
                transitionHandler,
                packageManager,
                testScope,
            )

        whenever(userProfileContexts[anyInt()]).thenReturn(context)
        whenever(userProfileContexts.getOrCreate(anyInt())).thenReturn(context)
        whenever(viewModel.hasWindowDecoration(anyInt())).thenReturn(true)
        whenever(packageManager.resolveActivityAsUser(any(), anyInt(), anyInt())).thenReturn(mock())

        taskResourceLoader.stub {
            onBlocking { getVeilIcon(any()) }.thenReturn(mock<Bitmap>())
            onBlocking { getNameAndHeaderIcon(any()) }.thenReturn(Pair("appName", mock<Bitmap>()))
        }
    }

    @Test
    fun onPackageUpdateRequested_launchesPlaceholder() {
        runTest(testDispatcher) {
            val task = createTaskInfo(1)

            packageUpdateController.onPackageUpdateRequested(listOf(task))
            testScope.testScheduler.advanceUntilIdle()

            val wct = getLatestWct(type = TRANSIT_OPEN)
            assertThat(wct.hierarchyOps.map { it.type })
                .containsExactly(
                    HIERARCHY_OP_TYPE_PENDING_INTENT,
                    HIERARCHY_OP_TYPE_CONTINUE_PACKAGE_UPDATE,
                )
            wct.assertPendingIntent(createPuActivityIntent())
            wct.assertContinuePackageUpdate(task)
        }
    }

    @Test
    fun onPackageUpdateFinished_launchesBaseIntent() {
        val task = createTaskInfo(1)
        val originalIntent = task.baseIntent
        originalIntent.action = Intent.ACTION_VIEW
        originalIntent.addCategory(Intent.CATEGORY_DEFAULT)
        originalIntent.putExtra("test_key", "test_value")
        originalIntent.setDataAndType(Uri.parse("content://test"), "text/plain")

        packageUpdateController.onPackageUpdateRequested(listOf(task))

        packageUpdateController.onPackageUpdateFinished(listOf(task))

        val wct = getLatestWct(type = TRANSIT_CHANGE)
        assertThat(wct.hierarchyOps.map { it.type })
            .containsExactly(HIERARCHY_OP_TYPE_PENDING_INTENT)
        wct.assertPendingIntent(originalIntent)

        // Verify that the launched intent is a fresh intent with copied attributes
        val launchedIntent = wct.getLaunchedIntent()

        assertThat(launchedIntent).isNotNull()
        assertThat(launchedIntent?.action).isEqualTo(originalIntent.action)
        assertThat(launchedIntent?.categories).containsExactly(Intent.CATEGORY_DEFAULT)
        assertThat(launchedIntent?.getStringExtra("test_key")).isEqualTo("test_value")
        assertThat(launchedIntent?.data).isEqualTo(originalIntent.data)
        assertThat(launchedIntent?.type).isEqualTo(originalIntent.type)
        assertThat(launchedIntent?.flags?.and(Intent.FLAG_ACTIVITY_CLEAR_TASK))
            .isEqualTo(Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

    @Test
    fun onPackageUpdateFinished_launchesBaseIntent_withDataOnly() {
        val task = createTaskInfo(1)
        task.baseIntent.setData(Uri.parse("content://test"))

        packageUpdateController.onPackageUpdateRequested(listOf(task))

        packageUpdateController.onPackageUpdateFinished(listOf(task))

        val wct = getLatestWct(type = TRANSIT_CHANGE)
        val launchedIntent = wct.getLaunchedIntent()

        assertThat(launchedIntent?.data).isEqualTo(task.baseIntent.data)
        assertThat(launchedIntent?.type).isNull()
    }

    @Test
    fun onPackageUpdateFinished_launchesBaseIntent_withTypeOnly() {
        val task = createTaskInfo(1)
        task.baseIntent.setType("text/plain")

        packageUpdateController.onPackageUpdateRequested(listOf(task))

        packageUpdateController.onPackageUpdateFinished(listOf(task))

        val wct = getLatestWct(type = TRANSIT_CHANGE)
        val launchedIntent = wct.getLaunchedIntent()

        assertThat(launchedIntent?.data).isNull()
        assertThat(launchedIntent?.type).isEqualTo(task.baseIntent.type)
    }

    @Test
    fun onPackageUpdateFinished_activityNotResolved_removesTask() {
        whenever(packageManager.resolveActivityAsUser(any(), anyInt(), anyInt())).thenReturn(null)
        val task = createTaskInfo(1)
        packageUpdateController.onPackageUpdateRequested(listOf(task))

        packageUpdateController.onPackageUpdateFinished(listOf(task))

        val wct = getLatestWct(type = TRANSIT_CHANGE)
        assertThat(wct.hierarchyOps.map { it.type }).containsExactly(HIERARCHY_OP_TYPE_REMOVE_TASK)
        wct.assertRemoveTask(task, removeFromRecents = true)
    }

    @Test
    fun onTaskAppeared_setsHandlePackageUpdate() {
        val task = createTaskInfo(1)

        packageUpdateController.onTaskAppeared(task)

        val wct = getLatestWctThroughTransaction()
        wct.assertHandlePackageUpdate(task, true)
    }

    @Test
    fun onTaskVanished_unsetsHandlePackageUpdate() {
        val task = createTaskInfo(1)

        packageUpdateController.onTaskVanished(task)

        val wct = getLatestWctThroughTransaction()
        wct.assertHandlePackageUpdate(task, false)
    }

    private fun createTaskInfo(
        id: Int,
        windowingMode: Int = WINDOWING_MODE_FULLSCREEN,
        visible: Boolean = true,
    ) =
        RunningTaskInfo().apply {
            taskId = id
            displayId = DEFAULT_DISPLAY
            configuration.windowConfiguration.windowingMode = windowingMode
            token = MockToken().token()
            isVisible = visible
            baseIntent = Intent().apply { component = ComponentName("package", "component.name") }
        }

    private fun getLatestWct(
        @WindowManager.TransitionType type: Int = TRANSIT_OPEN
    ): WindowContainerTransaction {
        val arg = ArgumentCaptor.forClass(WindowContainerTransaction::class.java)
        verify(transitions)
            .startTransition(eq(type), arg.capture(), isA(transitionHandler::class.java))
        return arg.value
    }

    private fun getLatestWctThroughTransaction(): WindowContainerTransaction {
        val arg = ArgumentCaptor.forClass(WindowContainerTransaction::class.java)
        verify(shellTaskOrganizer).applyTransaction(arg.capture())
        return arg.value
    }

    private fun WindowContainerTransaction.assertHandlePackageUpdate(
        task: RunningTaskInfo,
        shouldHandlePackageUpdate: Boolean,
    ) {
        val change = changes[task.token.asBinder()]

        assertWithMessage("Expected WCT to contain change for task ${task.taskId}")
            .that(change)
            .isNotNull()

        assertWithMessage(
                "Wrong handlePackageUpdate value for task ${task.taskId} expected " +
                    "$shouldHandlePackageUpdate but found ${change?.handlePackageUpdate}"
            )
            .that(change?.handlePackageUpdate)
            .isEqualTo(shouldHandlePackageUpdate)
    }

    private fun WindowContainerTransaction.assertPendingIntent(intent: Intent) {
        assertHop("Type=PENDING_INTENT with component=${intent.component}") { hop ->
            hop.type == HIERARCHY_OP_TYPE_PENDING_INTENT &&
                hop.pendingIntent?.intent?.component == intent.component
        }
    }

    private fun WindowContainerTransaction.getLaunchedIntent(): Intent? {
        val hop = hierarchyOps.firstOrNull { it.type == HIERARCHY_OP_TYPE_PENDING_INTENT }
        return hop?.pendingIntent?.intent
    }

    private fun WindowContainerTransaction.assertContinuePackageUpdate(task: RunningTaskInfo) {
        assertHop("Type=CONTINUE_UPDATE for taskId=${task.taskId}") { hop ->
            hop.type == HIERARCHY_OP_TYPE_CONTINUE_PACKAGE_UPDATE &&
                hop.container == task.token.asBinder()
        }
    }

    private fun WindowContainerTransaction.assertRemoveTask(
        task: RunningTaskInfo,
        removeFromRecents: Boolean,
    ) {
        assertHop("Type=REMOVE_TASK for taskId=${task.taskId}") { hop ->
            hop.type == HIERARCHY_OP_TYPE_REMOVE_TASK &&
                hop.container == task.token.asBinder() &&
                hop.removeFromRecents == removeFromRecents
        }
    }

    private fun WindowContainerTransaction.assertHop(
        description: String,
        predicate: (WindowContainerTransaction.HierarchyOp) -> Boolean,
    ) {
        val match = hierarchyOps.find(predicate)
        val actualOps = hierarchyOps.joinToString(",") { op -> "$op" }
        assertWithMessage("Failed to find hop: $description.\nActual Hops:\n$actualOps")
            .that(match)
            .isNotNull()
    }

    private fun createPuActivityIntent(): Intent {
        return Intent(mContext, PackageUpdateActivity::class.java)
    }
}
