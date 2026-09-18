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

package com.android.wm.shell.compatui.impl

import android.app.ActivityManager
import android.content.res.Configuration
import android.testing.AndroidTestingRunner
import android.view.View
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.SyncTransactionQueue
import com.android.wm.shell.compatui.api.CompatUIComponentRepository
import com.android.wm.shell.compatui.api.CompatUIComponentState
import com.android.wm.shell.compatui.api.CompatUIInfo
import com.android.wm.shell.compatui.api.CompatUIRepository
import com.android.wm.shell.compatui.api.CompatUISharedState
import com.android.wm.shell.compatui.api.CompatUISharedStateRepository
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

/**
 * Tests for {@link DefaultCompatUIHandler}.
 *
 * Build/Install/Run: atest WMShellUnitTests:DefaultCompatUIHandlerTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class DefaultCompatUIHandlerTest : ShellTestCase() {

    @JvmField @Rule val compatUIHandlerRule: CompatUIHandlerRule = CompatUIHandlerRule()

    lateinit var compatUIRepository: CompatUIRepository
    lateinit var compatUIHandler: DefaultCompatUIHandler
    lateinit var compatUICompatUIRepository: CompatUIComponentRepository
    lateinit var sharedStateRepository: CompatUISharedStateRepository
    lateinit var fakeIdGenerator: FakeCompatUIComponentIdGenerator
    lateinit var syncQueue: SyncTransactionQueue
    lateinit var displayController: DisplayController
    lateinit var shellExecutor: TestShellExecutor
    lateinit var componentFactory: FakeCompatUIComponentFactory

    @Before
    fun setUp() {
        shellExecutor = TestShellExecutor()
        compatUIRepository = CompatUIRepository()
        compatUICompatUIRepository = CompatUIComponentRepository()
        sharedStateRepository = CompatUISharedStateRepository()
        fakeIdGenerator = FakeCompatUIComponentIdGenerator("compId")
        syncQueue = mock<SyncTransactionQueue>()
        displayController = mock<DisplayController>()
        componentFactory =
            FakeCompatUIComponentFactory(
                mContext,
                syncQueue,
                displayController,
                compatUICompatUIRepository,
                sharedStateRepository,
            )
        compatUIHandler =
            DefaultCompatUIHandler(
                compatUIRepository,
                compatUICompatUIRepository,
                sharedStateRepository,
                fakeIdGenerator,
                componentFactory,
                shellExecutor,
            )
        prepareSharedStateRepository()
    }

    @Test
    fun `when creationReturn is false no state is stored`() {
        // We add a spec to the repository
        val fakeLifecycle =
            FakeCompatUILifecyclePredicates(creationReturn = false, removalReturn = false)
        val fakeCompatUILayout = FakeCompatUILayout(viewBuilderReturn = View(mContext))
        val fakeCompatUISpec =
            FakeCompatUISpec(name = "one", lifecycle = fakeLifecycle, layout = fakeCompatUILayout)
                .getSpec()
        compatUIRepository.registerSpec(fakeCompatUISpec)

        val generatedId = fakeIdGenerator.generatedComponentId

        sendCompatUIInfo(testCompatUIInfo())

        fakeIdGenerator.assertGenerateInvocations(1)
        fakeLifecycle.assertCreationInvocation(1)
        fakeLifecycle.assertRemovalInvocation(0)
        fakeLifecycle.assertInitialStateInvocation(0)
        compatUICompatUIRepository.assertHasStateFor(generatedId, expected = false)
        compatUICompatUIRepository.assertHasComponentFor(generatedId, expected = false)

        sendCompatUIInfo(testCompatUIInfo())
        fakeLifecycle.assertCreationInvocation(2)
        fakeLifecycle.assertRemovalInvocation(0)
        fakeLifecycle.assertInitialStateInvocation(0)
        compatUICompatUIRepository.assertHasStateFor(generatedId, expected = false)
        compatUICompatUIRepository.assertHasComponentFor(generatedId, expected = false)
    }

    @Test
    fun `when creationReturn is true and no state is created no state is stored`() {
        // We add a spec to the repository
        val fakeLifecycle =
            FakeCompatUILifecyclePredicates(creationReturn = true, removalReturn = false)
        val fakeCompatUILayout = FakeCompatUILayout(viewBuilderReturn = View(mContext))
        val fakeCompatUISpec =
            FakeCompatUISpec(name = "one", lifecycle = fakeLifecycle, layout = fakeCompatUILayout)
                .getSpec()
        compatUIRepository.registerSpec(fakeCompatUISpec)

        val generatedId = fakeIdGenerator.generatedComponentId

        sendCompatUIInfo(testCompatUIInfo())

        fakeLifecycle.assertCreationInvocation(1)
        fakeLifecycle.assertRemovalInvocation(0)
        fakeLifecycle.assertInitialStateInvocation(1)
        compatUICompatUIRepository.assertHasStateFor(generatedId, expected = false)
        compatUICompatUIRepository.assertHasComponentFor(generatedId, expected = true)

        sendCompatUIInfo(testCompatUIInfo())

        fakeLifecycle.assertCreationInvocation(1)
        fakeLifecycle.assertRemovalInvocation(1)
        fakeLifecycle.assertInitialStateInvocation(1)
        compatUICompatUIRepository.assertHasStateFor(generatedId, expected = false)
        compatUICompatUIRepository.assertHasComponentFor(generatedId, expected = true)
    }

    @Test
    fun `when creationReturn is true and state is created state is stored`() {
        val fakeComponentState = object : CompatUIComponentState {}
        // We add a spec to the repository
        val fakeLifecycle =
            FakeCompatUILifecyclePredicates(
                creationReturn = true,
                removalReturn = false,
                initialState = { _, _ -> fakeComponentState },
            )
        val fakeCompatUILayout = FakeCompatUILayout(viewBuilderReturn = View(mContext))
        val fakeCompatUISpec =
            FakeCompatUISpec(name = "one", lifecycle = fakeLifecycle, layout = fakeCompatUILayout)
                .getSpec()
        compatUIRepository.registerSpec(fakeCompatUISpec)

        val generatedId = fakeIdGenerator.generatedComponentId

        sendCompatUIInfo(testCompatUIInfo())

        fakeLifecycle.assertCreationInvocation(1)
        fakeLifecycle.assertRemovalInvocation(0)
        fakeLifecycle.assertInitialStateInvocation(1)
        compatUICompatUIRepository.assertHasStateEqualsTo(generatedId, fakeComponentState)
        compatUICompatUIRepository.assertHasComponentFor(generatedId, expected = true)

        sendCompatUIInfo(testCompatUIInfo())

        fakeLifecycle.assertCreationInvocation(1)
        fakeLifecycle.assertRemovalInvocation(1)
        fakeLifecycle.assertInitialStateInvocation(1)
        compatUICompatUIRepository.assertHasStateEqualsTo(generatedId, fakeComponentState)
        compatUICompatUIRepository.assertHasComponentFor(generatedId, expected = true)
    }

    @Test
    fun `when lifecycle is complete and state is created state is stored and removed`() {

        val fakeComponentState = object : CompatUIComponentState {}
        // We add a spec to the repository
        val fakeLifecycle =
            FakeCompatUILifecyclePredicates(
                creationReturn = true,
                removalReturn = true,
                initialState = { _, _ -> fakeComponentState },
            )
        val fakeCompatUILayout = FakeCompatUILayout(viewBuilderReturn = View(mContext))
        val fakeCompatUISpec =
            FakeCompatUISpec(name = "one", lifecycle = fakeLifecycle, layout = fakeCompatUILayout)
                .getSpec()
        compatUIRepository.registerSpec(fakeCompatUISpec)

        val generatedId = fakeIdGenerator.generatedComponentId

        sendCompatUIInfo(testCompatUIInfo())

        fakeLifecycle.assertCreationInvocation(1)
        fakeLifecycle.assertRemovalInvocation(0)
        fakeLifecycle.assertOnRemovalInvocation(0)
        fakeLifecycle.assertInitialStateInvocation(1)
        compatUICompatUIRepository.assertHasStateEqualsTo(generatedId, fakeComponentState)
        compatUICompatUIRepository.assertHasComponentFor(generatedId, expected = true)

        sendCompatUIInfo(testCompatUIInfo())

        fakeLifecycle.assertCreationInvocation(1)
        fakeLifecycle.assertRemovalInvocation(1)
        fakeLifecycle.assertOnRemovalInvocation(1)
        fakeLifecycle.assertInitialStateInvocation(1)
        compatUICompatUIRepository.assertHasStateFor(generatedId, expected = false)
        compatUICompatUIRepository.assertHasComponentFor(generatedId, expected = false)
    }

    @Test
    fun `idGenerator is invoked every time a component is created`() {
        // We add a spec to the repository
        val fakeLifecycle =
            FakeCompatUILifecyclePredicates(creationReturn = true, removalReturn = true)
        val fakeCompatUILayout = FakeCompatUILayout(viewBuilderReturn = View(mContext))
        val fakeCompatUISpec = FakeCompatUISpec("one", fakeLifecycle, fakeCompatUILayout).getSpec()
        compatUIRepository.registerSpec(fakeCompatUISpec)
        // Component creation
        fakeIdGenerator.assertGenerateInvocations(0)
        sendCompatUIInfo(testCompatUIInfo())

        fakeIdGenerator.assertGenerateInvocations(1)

        sendCompatUIInfo(testCompatUIInfo())
        fakeIdGenerator.assertGenerateInvocations(2)
    }

    @Test
    fun `viewBuilder and viewBinder invoked if component is created and released when destroyed`() {
        // We add a spec to the repository
        val fakeLifecycle =
            FakeCompatUILifecyclePredicates(creationReturn = true, removalReturn = true)
        val fakeCompatUILayout = FakeCompatUILayout(viewBuilderReturn = View(mContext))
        val fakeCompatUISpec = FakeCompatUISpec("one", fakeLifecycle, fakeCompatUILayout).getSpec()
        compatUIRepository.registerSpec(fakeCompatUISpec)

        sendCompatUIInfo(testCompatUIInfo())

        componentFactory.assertInvocations(1)
        fakeCompatUILayout.assertViewBuilderInvocation(1)
        fakeCompatUILayout.assertViewBinderInvocation(1)
        fakeCompatUILayout.assertViewReleaserInvocation(0)

        sendCompatUIInfo(testCompatUIInfo())

        componentFactory.assertInvocations(1)
        fakeCompatUILayout.assertViewBuilderInvocation(1)
        fakeCompatUILayout.assertViewBinderInvocation(1)
        fakeCompatUILayout.assertViewReleaserInvocation(1)
    }

    private fun testCompatUIInfo(): CompatUIInfo {
        val taskInfo = ActivityManager.RunningTaskInfo()
        taskInfo.taskId = 1
        return CompatUIInfo(taskInfo, null)
    }

    private fun prepareSharedStateRepository(taskId: Int = 1) {
        sharedStateRepository.insert(
            taskId,
            CompatUISharedState(taskId = taskId, taskConfiguration = Configuration()),
            overrideIfPresent = true,
        )
    }

    private fun sendCompatUIInfo(input: CompatUIInfo) {
        compatUIHandlerRule.postBlocking {
            compatUIHandler.onCompatInfoChanged(input)
            shellExecutor.flushAll()
        }
    }
}
