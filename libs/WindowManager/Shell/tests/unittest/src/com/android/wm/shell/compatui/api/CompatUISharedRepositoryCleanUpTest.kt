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

package com.android.wm.shell.compatui.api

import android.content.res.Configuration
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.compatui.letterbox.asMode
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.util.testTaskVanishedListener
import java.util.function.Consumer
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/**
 * Tests for [CompatUISharedRepositoryCleanUp].
 *
 * Build/Install/Run: atest WMShellUnitTests:CompatUISharedRepositoryCleanUpTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class CompatUISharedRepositoryCleanUpTest : ShellTestCase() {

    @Test
    fun `VanishedListener is registered at init`() {
        runTestScenario { r ->
            r.invokeShellInit()
            r.checkTaskVanishedListenerIsRegistered(expected = true)
        }
    }

    @Test
    fun `When Task vanishes the related item it is removed from repository`() {
        runTestScenario { r ->
            r.useShareComponentRepository { repo ->
                repo.insert(key = 10, CompatUISharedState(taskConfiguration = Configuration()))
            }
            testTaskVanishedListener(r.getCompatUISharedRepositoryCleanUp()) {
                runningTaskInfo { ti -> ti.taskId = 10 }
                validateOnTaskVanished { r.validateItem(10) { item -> assertNull(item) } }
            }
        }
    }

    @Test
    fun `Only information for the vanishing Task are removed`() {
        runTestScenario { r ->
            r.useShareComponentRepository { repo ->
                repo.insert(key = 20, CompatUISharedState(taskConfiguration = Configuration()))
            }
            testTaskVanishedListener(r.getCompatUISharedRepositoryCleanUp()) {
                runningTaskInfo { ti -> ti.taskId = 10 }
                validateOnTaskVanished { r.validateItem(20) { item -> assertNotNull(item) } }
            }
        }
    }

    /** Runs a test scenario providing a Robot. */
    fun runTestScenario(consumer: Consumer<LetterboxTaskListenerAdapterRobotTest>) {
        val robot = LetterboxTaskListenerAdapterRobotTest()
        consumer.accept(robot)
    }

    class LetterboxTaskListenerAdapterRobotTest {

        private val executor: ShellExecutor
        private val shellInit: ShellInit
        private val shellTaskOrganizer: ShellTaskOrganizer
        private val sharedRepositoryCleanUp: CompatUISharedRepositoryCleanUp
        private val sharedStateRepository: CompatUISharedStateRepository

        init {
            executor = mock<ShellExecutor>()
            shellInit = ShellInit(executor)
            shellTaskOrganizer = mock<ShellTaskOrganizer>()
            sharedStateRepository = CompatUISharedStateRepository()
            sharedRepositoryCleanUp =
                CompatUISharedRepositoryCleanUp(
                    shellInit,
                    shellTaskOrganizer,
                    sharedStateRepository,
                )
        }

        fun getCompatUISharedRepositoryCleanUp(): () -> CompatUISharedRepositoryCleanUp = {
            sharedRepositoryCleanUp
        }

        fun invokeShellInit() = shellInit.init()

        fun useShareComponentRepository(consumer: (CompatUISharedStateRepository) -> Unit) {
            consumer(sharedStateRepository)
        }

        fun checkTaskVanishedListenerIsRegistered(expected: Boolean) {
            verify(shellTaskOrganizer, expected.asMode()).addTaskVanishedListener(any())
        }

        fun validateItem(taskId: Int, consumer: (CompatUISharedState?) -> Unit) {
            consumer(sharedStateRepository.find(taskId))
        }
    }
}
