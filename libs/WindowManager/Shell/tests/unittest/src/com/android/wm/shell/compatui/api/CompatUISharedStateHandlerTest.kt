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
import android.graphics.Rect
import android.testing.AndroidTestingRunner
import android.view.View
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.util.testCompatUIHandler
import java.util.Locale
import java.util.function.Consumer
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests for [CompatUISharedStateHandler].
 *
 * Build/Install/Run: atest WMShellUnitTests:CompatUISharedStateHandlerTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class CompatUISharedStateHandlerTest : ShellTestCase() {

    @Test
    fun `when onCompatInfoChanged is invoked CompatUISharedStateRepository is updated`() {
        runTestScenario { r ->
            testCompatUIHandler(r.getSharedStateHandlerFactory()) {
                compatUIInfo { runningTaskInfo { ti -> ti.taskId = 10 } }
                validateOnCompatInfoChanged {
                    r.useRepository { repo -> assertNotNull(repo.find(key = 10)) }
                }
            }
        }
    }

    @Test
    fun `when onCompatInfoChanged is invoked stableBounds are calculated for the task`() {
        runTestScenario { r ->
            testCompatUIHandler(r.getSharedStateHandlerFactory()) {
                compatUIInfo {
                    runningTaskInfo { ti ->
                        ti.taskId = 10
                        ti.displayId = 3
                    }
                }
                r.setUpStableBoundsForDisplay(displayId = 3, stableBounds = Rect(1, 2, 3, 4))
                validateOnCompatInfoChanged {
                    r.useRepository { repo ->
                        assertNotNull(repo.find(key = 10))
                        assertEquals(true, repo.find(key = 10)?.areParentBoundsChanged)
                        assertEquals(Rect(1, 2, 3, 4), repo.find(key = 10)?.stableBounds)
                    }
                }
            }
        }
    }

    @Test
    fun `when onCompatInfoChanged shared properties are available in shared state`() {
        runTestScenario { r ->
            val testConfiguration = Configuration()
            val testTaskBounds = Rect(1, 2, 3, 4)
            testConfiguration.windowConfiguration.bounds.set(testTaskBounds)
            testConfiguration.setLayoutDirection(Locale.UK)
            testCompatUIHandler(r.getSharedStateHandlerFactory()) {
                compatUIInfo {
                    runningTaskInfo { ti ->
                        ti.taskId = 10
                        ti.configuration.setTo(testConfiguration)
                    }
                }

                validateOnCompatInfoChanged {
                    r.useRepository { repo ->
                        assertNotNull(repo.find(key = 10))
                        assertEquals(testConfiguration, repo.find(key = 10)?.taskConfiguration)
                        assertEquals(testTaskBounds, repo.find(key = 10)?.taskBoundsFn())
                        assertEquals(
                            View.LAYOUT_DIRECTION_LTR,
                            repo.find(key = 10)?.layoutDirectionFn(),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `when onCompatInfoChanged areParentBoundsChanged is true when stableBounds change`() {
        runTestScenario { r ->
            testCompatUIHandler(r.getSharedStateHandlerFactory()) {
                compatUIInfo {
                    runningTaskInfo { ti ->
                        ti.taskId = 10
                        ti.displayId = 3
                    }
                }
                r.setUpStableBoundsForDisplay(displayId = 3, stableBounds = Rect(1, 2, 3, 4))
                validateOnCompatInfoChanged {
                    r.useRepository { repo ->
                        assertNotNull(repo.find(key = 10))
                        assertEquals(true, repo.find(key = 10)?.areParentBoundsChanged)
                        assertEquals(Rect(1, 2, 3, 4), repo.find(key = 10)?.stableBounds)
                    }
                }
                r.setUpStableBoundsForDisplay(displayId = 3, stableBounds = Rect(1, 2, 3, 4))
                validateOnCompatInfoChanged {
                    r.useRepository { repo ->
                        assertNotNull(repo.find(key = 10))
                        assertEquals(false, repo.find(key = 10)?.areParentBoundsChanged)
                        assertEquals(Rect(1, 2, 3, 4), repo.find(key = 10)?.stableBounds)
                    }
                }
                r.setUpStableBoundsForDisplay(displayId = 3, stableBounds = Rect(5, 6, 7, 8))
                validateOnCompatInfoChanged {
                    r.useRepository { repo ->
                        assertNotNull(repo.find(key = 10))
                        assertEquals(true, repo.find(key = 10)?.areParentBoundsChanged)
                        assertEquals(Rect(5, 6, 7, 8), repo.find(key = 10)?.stableBounds)
                    }
                }
            }
        }
    }

    /** Runs a test scenario providing a Robot. */
    fun runTestScenario(consumer: Consumer<CompatUISharedStateHandlerRobotTest>) {
        val robot = CompatUISharedStateHandlerRobotTest()
        consumer.accept(robot)
    }

    class CompatUISharedStateHandlerRobotTest {

        private val sharedStateHandler: CompatUISharedStateHandler
        private val sharedStateRepository: CompatUISharedStateRepository
        private val displayController: DisplayController
        private val displayLayout: DisplayLayout

        init {
            displayController = mock<DisplayController>()
            displayLayout = mock<DisplayLayout>()
            sharedStateRepository = CompatUISharedStateRepository()
            sharedStateHandler =
                CompatUISharedStateHandler(sharedStateRepository, displayController)
        }

        fun getSharedStateHandlerFactory(): () -> CompatUISharedStateHandler = {
            sharedStateHandler
        }

        fun useRepository(consumer: (CompatUISharedStateRepository) -> Unit) {
            consumer(sharedStateRepository)
        }

        fun setUpStableBoundsForDisplay(displayId: Int = 1, stableBounds: Rect) {
            doReturn(displayLayout).`when`(displayController).getDisplayLayout(any())
            doAnswer { invocation ->
                    val rectArg = invocation.getArgument<Rect>(0)
                    rectArg.set(stableBounds)
                    null
                }
                .whenever(displayLayout)
                .getStableBounds(any())
        }
    }
}
