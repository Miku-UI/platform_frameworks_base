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

package com.android.wm.shell.desktopmode.data.persistence

import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.util.ArrayMap
import android.util.ArraySet
import android.view.Display.DEFAULT_DISPLAY
import android.view.Display.INVALID_DISPLAY
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE
import com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE
import com.android.window.flags.Flags.FLAG_ENABLE_REMEMBERED_BOUNDS
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.desktopmode.data.Desk
import com.android.wm.shell.desktopmode.data.DesktopDisplay
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidTestingRunner::class)
@ExperimentalCoroutinesApi
@EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_MODE, FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE)
class DesktopPersistentRepositoryTest : ShellTestCase() {
    private val testContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var testDatastore: DataStore<DesktopPersistentRepositories>
    private lateinit var datastoreRepository: DesktopPersistentRepository
    private lateinit var datastoreScope: CoroutineScope

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        datastoreScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        testDatastore =
            DataStoreFactory.create(
                serializer =
                    DesktopPersistentRepository.Companion.DesktopPersistentRepositoriesSerializer,
                scope = datastoreScope,
            ) {
                testContext.dataStoreFile(DESKTOP_REPOSITORY_STATES_DATASTORE_TEST_FILE)
            }
        datastoreRepository = DesktopPersistentRepository(testDatastore)
    }

    @After
    fun tearDown() {
        File(ApplicationProvider.getApplicationContext<Context>().filesDir, "datastore")
            .deleteRecursively()

        datastoreScope.cancel()
    }

    @Test
    fun readRepository_returnsCorrectDesktop() {
        runTest(StandardTestDispatcher()) {
            val task = createDesktopTask(1)
            val desk = createDesktop(ArrayList(listOf(task)))
            val repositoryState =
                DesktopRepositoryState.newBuilder().putDesktop(DEFAULT_DESKTOP_ID, desk)
            val desktopPersistentRepositories =
                DesktopPersistentRepositories.newBuilder()
                    .putDesktopRepoByUser(DEFAULT_USER_ID, repositoryState.build())
                    .build()
            testDatastore.updateData { desktopPersistentRepositories }

            val actualDesktop = datastoreRepository.readDesktop(DEFAULT_USER_ID, DEFAULT_DESKTOP_ID)

            assertThat(actualDesktop).isEqualTo(desk)
        }
    }

    @Test
    fun addOrUpdateTask_addNewTaskToDesktop() {
        runTest(StandardTestDispatcher()) {
            // Create a basic repository state
            val task = createDesktopTask(1)
            val desktopPersistentRepositories = createRepositoryWithOneDesk(ArrayList(listOf(task)))
            testDatastore.updateData { desktopPersistentRepositories }
            // Create a new state to be initialized
            val visibleTasks = ArraySet(listOf(1, 2))
            val minimizedTasks = ArraySet<Int>()
            val freeformTasksInZOrder = ArrayList(listOf(2, 1))

            // Update with new state
            datastoreRepository.addOrUpdateDesktop(
                desktopId = DEFAULT_DESKTOP_ID,
                visibleTasks = visibleTasks,
                minimizedTasks = minimizedTasks,
                freeformTasksInZOrder = freeformTasksInZOrder,
                userId = DEFAULT_USER_ID,
                leftTiledTask = null,
                rightTiledTask = null,
            )

            val actualDesktop = datastoreRepository.readDesktop(DEFAULT_USER_ID, DEFAULT_DESKTOP_ID)
            assertThat(actualDesktop?.tasksByTaskIdMap).hasSize(2)
            assertThat(actualDesktop?.getZOrderedTasks(0)).isEqualTo(2)
        }
    }

    @Test
    fun addTiledTasks_addsTiledTasksToDesktop() {
        runTest(StandardTestDispatcher()) {
            // Create a basic repository state
            val task = createDesktopTask(1)
            val desktopPersistentRepositories = createRepositoryWithOneDesk(ArrayList(listOf(task)))
            testDatastore.updateData { desktopPersistentRepositories }
            // Create a new state to be initialized
            val visibleTasks = ArraySet(listOf(1, 2))
            val freeformTasksInZOrder = ArrayList(listOf(1, 2))

            // Update with new state
            datastoreRepository.addOrUpdateDesktop(
                desktopId = DEFAULT_DESKTOP_ID,
                visibleTasks = visibleTasks,
                minimizedTasks = ArraySet(),
                freeformTasksInZOrder = freeformTasksInZOrder,
                userId = DEFAULT_USER_ID,
                leftTiledTask = 1,
                rightTiledTask = null,
            )

            var actualDesktop = datastoreRepository.readDesktop(DEFAULT_USER_ID, DEFAULT_DESKTOP_ID)
            assertThat(actualDesktop?.tasksByTaskIdMap?.get(1)?.desktopTaskTilingState)
                .isEqualTo(DesktopTaskTilingState.LEFT)
            assertThat(actualDesktop?.tasksByTaskIdMap?.get(2)?.desktopTaskTilingState)
                .isEqualTo(DesktopTaskTilingState.NONE)

            // Update with new state
            datastoreRepository.addOrUpdateDesktop(
                desktopId = DEFAULT_DESKTOP_ID,
                visibleTasks = visibleTasks,
                minimizedTasks = ArraySet(),
                freeformTasksInZOrder = freeformTasksInZOrder,
                userId = DEFAULT_USER_ID,
                leftTiledTask = null,
                rightTiledTask = 2,
            )

            actualDesktop = datastoreRepository.readDesktop(DEFAULT_USER_ID, DEFAULT_DESKTOP_ID)
            assertThat(actualDesktop?.tasksByTaskIdMap?.get(1)?.desktopTaskTilingState)
                .isEqualTo(DesktopTaskTilingState.NONE)
            assertThat(actualDesktop?.tasksByTaskIdMap?.get(2)?.desktopTaskTilingState)
                .isEqualTo(DesktopTaskTilingState.RIGHT)
        }
    }

    @Test
    fun removeUsers_removesUsersData() {
        runTest(StandardTestDispatcher()) {
            val task = createDesktopTask(1)
            val desktopPersistentRepositories = createRepositoryWithOneDesk(ArrayList(listOf(task)))
            testDatastore.updateData { desktopPersistentRepositories }
            // Create a new state to be initialized
            val visibleTasks = ArraySet(listOf(1))
            val minimizedTasks = ArraySet(listOf(1))
            val freeformTasksInZOrder = ArrayList(listOf(1))
            datastoreRepository.addOrUpdateDesktop(
                desktopId = DEFAULT_DESKTOP_ID,
                visibleTasks = visibleTasks,
                minimizedTasks = minimizedTasks,
                freeformTasksInZOrder = freeformTasksInZOrder,
                userId = DEFAULT_USER_ID,
                leftTiledTask = null,
                rightTiledTask = null,
            )
            datastoreRepository.addOrUpdateDesktop(
                visibleTasks = visibleTasks,
                minimizedTasks = minimizedTasks,
                freeformTasksInZOrder = freeformTasksInZOrder,
                userId = USER_ID_2,
                leftTiledTask = null,
                rightTiledTask = null,
            )

            datastoreRepository.removeUsers(mutableListOf(USER_ID_2))

            val removedDesktopRepositoryState =
                datastoreRepository.getDesktopRepositoryState(USER_ID_2)
            assertThat(removedDesktopRepositoryState).isEqualTo(null)

            val actualDesktop = datastoreRepository.readDesktop(DEFAULT_USER_ID, DEFAULT_DESKTOP_ID)
            assertThat(actualDesktop?.tasksByTaskIdMap?.get(task.taskId)?.desktopTaskState)
                .isEqualTo(DesktopTaskState.MINIMIZED)
        }
    }

    @Test
    fun addOrUpdateTask_changeTaskStateToMinimize_taskStateIsMinimized() {
        runTest(StandardTestDispatcher()) {
            val task = createDesktopTask(1)
            val desktopPersistentRepositories = createRepositoryWithOneDesk(ArrayList(listOf(task)))
            testDatastore.updateData { desktopPersistentRepositories }
            // Create a new state to be initialized
            val visibleTasks = ArraySet(listOf(1))
            val minimizedTasks = ArraySet(listOf(1))
            val freeformTasksInZOrder = ArrayList(listOf(1))

            // Update with new state
            datastoreRepository.addOrUpdateDesktop(
                desktopId = DEFAULT_DESKTOP_ID,
                visibleTasks = visibleTasks,
                minimizedTasks = minimizedTasks,
                freeformTasksInZOrder = freeformTasksInZOrder,
                userId = DEFAULT_USER_ID,
                leftTiledTask = null,
                rightTiledTask = null,
            )

            val actualDesktop = datastoreRepository.readDesktop(DEFAULT_USER_ID, DEFAULT_DESKTOP_ID)
            assertThat(actualDesktop?.tasksByTaskIdMap?.get(task.taskId)?.desktopTaskState)
                .isEqualTo(DesktopTaskState.MINIMIZED)
        }
    }

    @Test
    fun removeTask_previouslyAddedTaskIsRemoved() {
        runTest(StandardTestDispatcher()) {
            val task1 = createDesktopTask(1)
            val task2 = createDesktopTask(2)
            val desktopPersistentRepositories =
                createRepositoryWithOneDesk(ArrayList(listOf(task1, task2)))
            testDatastore.updateData { desktopPersistentRepositories }
            // Create a new state to be initialized
            val visibleTasks = ArraySet(listOf(1))
            val minimizedTasks = ArraySet<Int>()
            val freeformTasksInZOrder = ArrayList<Int>(listOf(1))

            // Update with new state
            datastoreRepository.addOrUpdateDesktop(
                desktopId = DEFAULT_DESKTOP_ID,
                visibleTasks = visibleTasks,
                minimizedTasks = minimizedTasks,
                freeformTasksInZOrder = freeformTasksInZOrder,
                userId = DEFAULT_USER_ID,
                leftTiledTask = null,
                rightTiledTask = null,
            )

            val actualDesktop = datastoreRepository.readDesktop(DEFAULT_USER_ID, DEFAULT_DESKTOP_ID)
            assertThat(actualDesktop?.tasksByTaskIdMap).hasSize(1)
            assertThat(actualDesktop?.getZOrderedTasks(0)).isEqualTo(1)
        }
    }

    @Test
    fun addOrUpdateTask_addEmptyDesktop_noOp() {
        runTest(StandardTestDispatcher()) {
            // Create a basic repository state
            val task = createDesktopTask(1)
            val desktopPersistentRepositories = createRepositoryWithOneDesk(ArrayList(listOf(task)))
            testDatastore.updateData { desktopPersistentRepositories }
            // Create a new state to be initialized
            val visibleTasks = ArraySet<Int>()
            val minimizedTasks = ArraySet<Int>()
            val freeformTasksInZOrder = ArrayList<Int>()

            // Update with new state
            datastoreRepository.addOrUpdateDesktop(
                desktopId = DEFAULT_DESKTOP_ID,
                visibleTasks = visibleTasks,
                minimizedTasks = minimizedTasks,
                freeformTasksInZOrder = freeformTasksInZOrder,
                userId = DEFAULT_USER_ID,
                leftTiledTask = null,
                rightTiledTask = null,
            )

            val actualDesktop = datastoreRepository.readDesktop(DEFAULT_USER_ID, DEFAULT_DESKTOP_ID)
            assertThat(actualDesktop?.tasksByTaskIdMap).isNull()
        }
    }

    @Test
    fun addOrUpdateRepository_addNewTaskToDesktop() {
        runTest(StandardTestDispatcher()) {
            // Create a basic repository state
            val task = createDesktopTask(1)
            val desktopPersistentRepositories = createRepositoryWithOneDesk(ArrayList(listOf(task)))
            testDatastore.updateData { desktopPersistentRepositories }
            // Create a new state to be initialized
            val visibleTasks = ArraySet(listOf(1, 2))
            val minimizedTasks = ArraySet<Int>()
            val freeformTasksInZOrder = ArrayList(listOf(2, 1))
            val desk =
                Desk(
                    deskId = DEFAULT_DESKTOP_ID,
                    displayId = DEFAULT_DISPLAY,
                    visibleTasks = visibleTasks,
                    minimizedTasks = minimizedTasks,
                    freeformTasksInZOrder = freeformTasksInZOrder,
                    uniqueDisplayId = UNIQUE_DISPLAY_ID,
                )

            // Update with new state
            datastoreRepository.addOrUpdateRepository(
                userId = DEFAULT_USER_ID,
                desks = listOf(desk),
                activeDeskIdToUniqueDisplayId = mapOf(UNIQUE_DISPLAY_ID to DEFAULT_DESKTOP_ID),
                preservedDisplays = ArrayMap(),
                rememberedBoundsRatioByPackageName = ArrayMap(),
            )

            val actualDesktop = datastoreRepository.readDesktop(DEFAULT_USER_ID, DEFAULT_DESKTOP_ID)
            assertThat(actualDesktop?.tasksByTaskIdMap).hasSize(2)
            assertThat(actualDesktop?.getZOrderedTasks(0)).isEqualTo(2)
            assertThat(actualDesktop?.getUniqueDisplayId()).isEqualTo(UNIQUE_DISPLAY_ID)
        }
    }

    @Test
    fun addOrUpdateRepository_addDesksOnMultipleDisplays() {
        runTest(StandardTestDispatcher()) {
            // Create a basic repository state
            val task = createDesktopTask(1)
            val desktopPersistentRepositories = createRepositoryWithOneDesk(ArrayList(listOf(task)))
            testDatastore.updateData { desktopPersistentRepositories }
            // Create a new state to be initialized
            val visibleTasks = ArraySet(listOf(1, 2))
            val visibleTasks2 = ArraySet(listOf(3, 4, 5))
            val minimizedTasks = ArraySet<Int>()
            val freeformTasksInZOrder = ArrayList(listOf(2, 1))
            val freeformTasksInZOrder2 = ArrayList(listOf(4, 3, 5))
            val desk1 =
                Desk(
                    deskId = DEFAULT_DESKTOP_ID,
                    displayId = DEFAULT_DISPLAY,
                    visibleTasks = visibleTasks,
                    minimizedTasks = minimizedTasks,
                    freeformTasksInZOrder = freeformTasksInZOrder,
                    uniqueDisplayId = UNIQUE_DISPLAY_ID,
                )
            // Set up desk on external display.
            val deskId2 = DEFAULT_DESKTOP_ID + 1
            val desk2 =
                Desk(
                    deskId = deskId2,
                    displayId = DEFAULT_DISPLAY + 1,
                    visibleTasks = visibleTasks2,
                    minimizedTasks = minimizedTasks,
                    freeformTasksInZOrder = freeformTasksInZOrder2,
                    uniqueDisplayId = UNIQUE_DISPLAY_ID_2,
                )

            // Update with new state
            datastoreRepository.addOrUpdateRepository(
                userId = DEFAULT_USER_ID,
                desks = listOf(desk1, desk2),
                activeDeskIdToUniqueDisplayId =
                    mapOf(UNIQUE_DISPLAY_ID to DEFAULT_DESKTOP_ID, UNIQUE_DISPLAY_ID_2 to deskId2),
                preservedDisplays = ArrayMap(),
                rememberedBoundsRatioByPackageName = ArrayMap(),
            )

            val actualDesktop = datastoreRepository.readDesktop(DEFAULT_USER_ID, DEFAULT_DESKTOP_ID)
            assertThat(actualDesktop?.tasksByTaskIdMap).hasSize(2)
            assertThat(actualDesktop?.getZOrderedTasks(0)).isEqualTo(2)
            assertThat(actualDesktop?.getUniqueDisplayId()).isEqualTo(UNIQUE_DISPLAY_ID)
            val actualDesktop2 = datastoreRepository.readDesktop(DEFAULT_USER_ID, deskId2)
            assertThat(actualDesktop2?.tasksByTaskIdMap).hasSize(3)
            assertThat(actualDesktop2?.getZOrderedTasks(0)).isEqualTo(4)
            assertThat(actualDesktop2?.getUniqueDisplayId()).isEqualTo(UNIQUE_DISPLAY_ID_2)
        }
    }

    @Test
    fun addOrUpdateRepository_tiledTasks_addsTiledTasksToDesktop() {
        runTest(StandardTestDispatcher()) {
            // Create a basic repository state
            val task = createDesktopTask(1)
            val desktopPersistentRepositories = createRepositoryWithOneDesk(ArrayList(listOf(task)))
            testDatastore.updateData { desktopPersistentRepositories }
            // Create a new state to be initialized
            val visibleTasks = ArraySet(listOf(1, 2))
            val freeformTasksInZOrder = ArrayList(listOf(1, 2))
            val desk =
                Desk(
                    deskId = DEFAULT_DESKTOP_ID,
                    displayId = DEFAULT_DISPLAY,
                    visibleTasks = visibleTasks,
                    freeformTasksInZOrder = freeformTasksInZOrder,
                    leftTiledTaskId = 1,
                    uniqueDisplayId = UNIQUE_DISPLAY_ID,
                )

            // Update with new state
            datastoreRepository.addOrUpdateRepository(
                userId = DEFAULT_USER_ID,
                desks = listOf(desk),
                activeDeskIdToUniqueDisplayId = mapOf(UNIQUE_DISPLAY_ID to DEFAULT_DESKTOP_ID),
                preservedDisplays = ArrayMap(),
                rememberedBoundsRatioByPackageName = ArrayMap(),
            )

            var actualDesktop = datastoreRepository.readDesktop(DEFAULT_USER_ID, DEFAULT_DESKTOP_ID)
            assertThat(actualDesktop?.tasksByTaskIdMap?.get(1)?.desktopTaskTilingState)
                .isEqualTo(DesktopTaskTilingState.LEFT)
            assertThat(actualDesktop?.tasksByTaskIdMap?.get(2)?.desktopTaskTilingState)
                .isEqualTo(DesktopTaskTilingState.NONE)
            assertThat(actualDesktop?.getUniqueDisplayId()).isEqualTo(UNIQUE_DISPLAY_ID)

            desk.rightTiledTaskId = 2
            desk.leftTiledTaskId = null

            // Update with new state
            datastoreRepository.addOrUpdateRepository(
                userId = DEFAULT_USER_ID,
                desks = listOf(desk),
                activeDeskIdToUniqueDisplayId = mapOf(UNIQUE_DISPLAY_ID to DEFAULT_DESKTOP_ID),
                preservedDisplays = ArrayMap(),
                rememberedBoundsRatioByPackageName = ArrayMap(),
            )

            actualDesktop = datastoreRepository.readDesktop(DEFAULT_USER_ID, DEFAULT_DESKTOP_ID)
            assertThat(actualDesktop?.tasksByTaskIdMap?.get(1)?.desktopTaskTilingState)
                .isEqualTo(DesktopTaskTilingState.NONE)
            assertThat(actualDesktop?.tasksByTaskIdMap?.get(2)?.desktopTaskTilingState)
                .isEqualTo(DesktopTaskTilingState.RIGHT)
        }
    }

    @Test
    fun addOrUpdateRepository_changeTaskStateToMinimize_taskStateIsMinimized() {
        runTest(StandardTestDispatcher()) {
            val task = createDesktopTask(1)
            val desktopPersistentRepositories = createRepositoryWithOneDesk(ArrayList(listOf(task)))
            testDatastore.updateData { desktopPersistentRepositories }
            // Create a new state to be initialized
            val visibleTasks = ArraySet(listOf(1))
            val minimizedTasks = ArraySet(listOf(1))
            val freeformTasksInZOrder = ArrayList(listOf(1))
            val desk =
                Desk(
                    deskId = DEFAULT_DESKTOP_ID,
                    displayId = DEFAULT_DISPLAY,
                    visibleTasks = visibleTasks,
                    minimizedTasks = minimizedTasks,
                    freeformTasksInZOrder = freeformTasksInZOrder,
                    uniqueDisplayId = UNIQUE_DISPLAY_ID,
                )

            // Update with new state
            datastoreRepository.addOrUpdateRepository(
                userId = DEFAULT_USER_ID,
                desks = listOf(desk),
                activeDeskIdToUniqueDisplayId = mapOf(UNIQUE_DISPLAY_ID to DEFAULT_DESKTOP_ID),
                preservedDisplays = ArrayMap(),
                rememberedBoundsRatioByPackageName = ArrayMap(),
            )

            val actualDesktop = datastoreRepository.readDesktop(DEFAULT_USER_ID, DEFAULT_DESKTOP_ID)
            assertThat(actualDesktop?.tasksByTaskIdMap?.get(task.taskId)?.desktopTaskState)
                .isEqualTo(DesktopTaskState.MINIMIZED)
            assertThat(actualDesktop?.getUniqueDisplayId()).isEqualTo(UNIQUE_DISPLAY_ID)
        }
    }

    @Test
    fun addOrUpdateRepository_removeMultipleDesks_removesAllDesks() {
        runTest(StandardTestDispatcher()) {
            val task = createDesktopTask(1)
            val desktopPersistentRepositories = createRepositoryWithOneDesk(ArrayList(listOf(task)))
            testDatastore.updateData { desktopPersistentRepositories }
            // Create a new state to be initialized
            val visibleTasks = ArraySet(listOf(1))
            val minimizedTasks = ArraySet(listOf(1))
            val freeformTasksInZOrder = ArrayList(listOf(1))
            val desk1 =
                Desk(
                    deskId = DEFAULT_DESKTOP_ID,
                    displayId = DEFAULT_DISPLAY,
                    visibleTasks = visibleTasks,
                    minimizedTasks = minimizedTasks,
                    freeformTasksInZOrder = freeformTasksInZOrder,
                    uniqueDisplayId = UNIQUE_DISPLAY_ID,
                )
            val desk2 =
                Desk(
                    deskId = DEFAULT_DESKTOP_ID + 1,
                    displayId = DEFAULT_DISPLAY,
                    visibleTasks = visibleTasks,
                    minimizedTasks = minimizedTasks,
                    freeformTasksInZOrder = freeformTasksInZOrder,
                    uniqueDisplayId = UNIQUE_DISPLAY_ID,
                )
            val desk3 =
                Desk(
                    deskId = DEFAULT_DESKTOP_ID + 3,
                    displayId = DEFAULT_DISPLAY,
                    visibleTasks = visibleTasks,
                    minimizedTasks = minimizedTasks,
                    freeformTasksInZOrder = freeformTasksInZOrder,
                    uniqueDisplayId = UNIQUE_DISPLAY_ID,
                )
            datastoreRepository.addOrUpdateRepository(
                userId = DEFAULT_USER_ID,
                desks = listOf(desk1, desk2, desk3),
                activeDeskIdToUniqueDisplayId = mapOf(UNIQUE_DISPLAY_ID to DEFAULT_DESKTOP_ID),
                preservedDisplays = ArrayMap(),
                rememberedBoundsRatioByPackageName = ArrayMap(),
            )

            // Back to back removals
            launch {
                datastoreRepository.addOrUpdateRepository(
                    userId = DEFAULT_USER_ID,
                    desks = listOf(desk1, desk3),
                    activeDeskIdToUniqueDisplayId = mapOf(UNIQUE_DISPLAY_ID to DEFAULT_DESKTOP_ID),
                    preservedDisplays = ArrayMap(),
                    rememberedBoundsRatioByPackageName = ArrayMap(),
                )
            }
            launch {
                datastoreRepository.addOrUpdateRepository(
                    userId = DEFAULT_USER_ID,
                    desks = listOf(desk3),
                    activeDeskIdToUniqueDisplayId = mapOf(UNIQUE_DISPLAY_ID to desk3.deskId),
                    preservedDisplays = ArrayMap(),
                    rememberedBoundsRatioByPackageName = ArrayMap(),
                )
            }
            launch {
                datastoreRepository.addOrUpdateRepository(
                    userId = DEFAULT_USER_ID,
                    desks = listOf(),
                    activeDeskIdToUniqueDisplayId = mapOf(),
                    preservedDisplays = ArrayMap(),
                    rememberedBoundsRatioByPackageName = ArrayMap(),
                )
            }
            advanceUntilIdle()

            val actualDesktop = datastoreRepository.readDesktop(DEFAULT_USER_ID, DEFAULT_DESKTOP_ID)
            assertThat(actualDesktop).isNull() // Desk should no longer exist
        }
    }

    @Test
    fun addOrUpdateRepository_withEmptyDesk_emptyDeskNotPersisted() =
        runTest(StandardTestDispatcher()) {
            val task = createDesktopTask(1)
            val desktopPersistentRepositories = createRepositoryWithOneDesk(arrayListOf(task))
            testDatastore.updateData { desktopPersistentRepositories }
            val emptyDesk =
                Desk(
                    deskId = DEFAULT_DESKTOP_ID + 1,
                    displayId = DEFAULT_DISPLAY,
                    uniqueDisplayId = "empty_desk_unique_id",
                )

            datastoreRepository.addOrUpdateRepository(
                userId = DEFAULT_USER_ID,
                desks =
                    listOf(
                        Desk(
                            deskId = DEFAULT_DESKTOP_ID,
                            displayId = DEFAULT_DISPLAY,
                            visibleTasks = ArraySet(listOf(1)),
                            freeformTasksInZOrder = arrayListOf(1),
                            uniqueDisplayId = UNIQUE_DISPLAY_ID,
                        ),
                        emptyDesk,
                    ),
                activeDeskIdToUniqueDisplayId = mapOf(UNIQUE_DISPLAY_ID to DEFAULT_DESKTOP_ID),
                preservedDisplays = ArrayMap(),
                rememberedBoundsRatioByPackageName = ArrayMap(),
            )

            val persistedEmptyDesk =
                datastoreRepository.readDesktop(DEFAULT_USER_ID, emptyDesk.deskId)
            assertThat(persistedEmptyDesk).isNull()
            val persistedNonEmptyDesk =
                datastoreRepository.readDesktop(DEFAULT_USER_ID, DEFAULT_DESKTOP_ID)
            assertThat(persistedNonEmptyDesk).isNotNull()
        }

    @Test
    fun addOrUpdateRepository_withEmptyListOfDesks_clearsRepositoryForUser() =
        runTest(StandardTestDispatcher()) {
            val task = createDesktopTask(1)
            val desktopPersistentRepositories = createRepositoryWithOneDesk(arrayListOf(task))
            testDatastore.updateData { desktopPersistentRepositories }

            datastoreRepository.addOrUpdateRepository(
                userId = DEFAULT_USER_ID,
                desks = emptyList(),
                activeDeskIdToUniqueDisplayId = mapOf(),
                preservedDisplays = ArrayMap(),
                rememberedBoundsRatioByPackageName = ArrayMap(),
            )

            val desktopRepositoryState =
                datastoreRepository.getDesktopRepositoryState(DEFAULT_USER_ID)
            assertThat(desktopRepositoryState?.desktopMap).isEmpty()
        }

    @Test
    fun addOrUpdateRepository_activeDeskIsEmpty_activeDeskIdNotPersisted() =
        runTest(StandardTestDispatcher()) {
            val emptyDesk =
                Desk(
                    deskId = DEFAULT_DESKTOP_ID + 100,
                    displayId = DEFAULT_DISPLAY,
                    uniqueDisplayId = UNIQUE_DISPLAY_ID,
                    visibleTasks = ArraySet(),
                    freeformTasksInZOrder = ArrayList(),
                )

            datastoreRepository.addOrUpdateRepository(
                userId = DEFAULT_USER_ID,
                desks = listOf(emptyDesk),
                activeDeskIdToUniqueDisplayId =
                    mapOf(UNIQUE_DISPLAY_ID to DEFAULT_DESKTOP_ID + 100),
                preservedDisplays = ArrayMap(),
                rememberedBoundsRatioByPackageName = ArrayMap(),
            )

            val userState = datastoreRepository.getDesktopRepositoryState(DEFAULT_USER_ID)
            assertThat(userState?.desktopMap).doesNotContainKey(DEFAULT_DESKTOP_ID + 100)
            assertThat(userState?.activeDeskByUniqueDisplayIdMap)
                .doesNotContainKey(UNIQUE_DISPLAY_ID)
        }

    @Test
    fun addOrUpdateRepository_addsNewActiveDesk() {
        runTest(StandardTestDispatcher()) {
            // Create a basic repository state
            val task = createDesktopTask(1)
            val desktopPersistentRepositories = createRepositoryWithOneDesk(ArrayList(listOf(task)))
            testDatastore.updateData { desktopPersistentRepositories }
            // Create a new state to be initialized
            val visibleTasks = ArraySet(listOf(1, 2))
            val minimizedTasks = ArraySet<Int>()
            val freeformTasksInZOrder = ArrayList(listOf(2, 1))
            val desk =
                Desk(
                    deskId = DEFAULT_DESKTOP_ID,
                    displayId = DEFAULT_DISPLAY,
                    visibleTasks = visibleTasks,
                    minimizedTasks = minimizedTasks,
                    freeformTasksInZOrder = freeformTasksInZOrder,
                    uniqueDisplayId = UNIQUE_DISPLAY_ID,
                )

            // Update with new state
            datastoreRepository.addOrUpdateRepository(
                userId = DEFAULT_USER_ID,
                desks = listOf(desk),
                activeDeskIdToUniqueDisplayId = mapOf(UNIQUE_DISPLAY_ID to DEFAULT_DESKTOP_ID),
                preservedDisplays = ArrayMap(),
                rememberedBoundsRatioByPackageName = ArrayMap(),
            )

            val desktopState = datastoreRepository.getDesktopRepositoryState(DEFAULT_USER_ID)
            assertThat(desktopState?.activeDeskByUniqueDisplayIdMap?.values)
                .containsExactly(DEFAULT_DESKTOP_ID)
        }
    }

    @Test
    fun addOrUpdateRepository_addsNewPreservedDisplay() {
        runTest(StandardTestDispatcher()) {
            // Create a basic repository state
            val task = createDesktopTask(1)
            val desktopPersistentRepositories = createRepositoryWithOneDesk(ArrayList(listOf(task)))
            testDatastore.updateData { desktopPersistentRepositories }

            val displayToPreserve = DesktopDisplay(INVALID_DISPLAY)
            val deskToPreserve =
                Desk(
                    deskId = 2,
                    displayId = 2,
                    visibleTasks = ArraySet(listOf(3, 4, 5)),
                    minimizedTasks = ArraySet<Int>(),
                    freeformTasksInZOrder = ArrayList(listOf(3, 4, 5)),
                    uniqueDisplayId = UNIQUE_DISPLAY_ID,
                )
            deskToPreserve.activeTasks.addAll(listOf(3, 4, 5))
            displayToPreserve.orderedDesks.add(deskToPreserve)
            displayToPreserve.activeDeskId = 2
            val preservedDisplays = ArrayMap<String, DesktopDisplay>()
            preservedDisplays[UNIQUE_DISPLAY_ID] = displayToPreserve

            // Update with new state
            datastoreRepository.addOrUpdateRepository(
                userId = DEFAULT_USER_ID,
                desks = listOf(),
                activeDeskIdToUniqueDisplayId = mapOf(UNIQUE_DISPLAY_ID to DEFAULT_DESKTOP_ID),
                preservedDisplays = preservedDisplays,
                rememberedBoundsRatioByPackageName = ArrayMap(),
            )

            val desktopState = datastoreRepository.getDesktopRepositoryState(DEFAULT_USER_ID)
            val preservedDisplayFound = desktopState?.preservedDisplayByUniqueIdMap?.values?.first()
            assertThat(preservedDisplayFound?.activeDeskId).isEqualTo(2)
            val preservedDesktop = preservedDisplayFound?.preservedDesktopMap?.values?.first()
            assertThat(preservedDesktop?.tasksByTaskIdMap?.keys).containsExactly(3, 4, 5)
        }
    }

    @Test
    fun addOrUpdateRepository_storesBoundsBeforeSnapOrMaximize() {
        runTest(StandardTestDispatcher()) {
            // Create a basic repository state
            val task = createDesktopTask(1)
            val desktopPersistentRepositories = createRepositoryWithOneDesk(ArrayList(listOf(task)))
            testDatastore.updateData { desktopPersistentRepositories }

            // Create new state to be initialized
            val bounds = Rect(10, 20, 110, 120)
            val desk =
                Desk(
                    deskId = DEFAULT_DESKTOP_ID,
                    displayId = DEFAULT_DISPLAY,
                    visibleTasks = ArraySet(listOf(1)),
                    freeformTasksInZOrder = ArrayList(listOf(1)),
                    uniqueDisplayId = UNIQUE_DISPLAY_ID,
                )
            // Set bounds before snap or maximize for task 1
            desk.boundsBeforeSnapOrMaximizeByTaskId[1] = bounds

            // Update with new state
            datastoreRepository.addOrUpdateRepository(
                userId = DEFAULT_USER_ID,
                desks = listOf(desk),
                activeDeskIdToUniqueDisplayId = mapOf(UNIQUE_DISPLAY_ID to DEFAULT_DESKTOP_ID),
                preservedDisplays = ArrayMap(),
                rememberedBoundsRatioByPackageName = ArrayMap(),
            )

            val actualDesktop = datastoreRepository.readDesktop(DEFAULT_USER_ID, DEFAULT_DESKTOP_ID)
            val actualTask = actualDesktop?.tasksByTaskIdMap?.get(1)
            assertThat(actualTask?.hasBoundsBeforeSnapOrMaximize()).isTrue()
            val actualBounds = actualTask?.boundsBeforeSnapOrMaximize
            assertThat(actualBounds?.left).isEqualTo(bounds.left)
            assertThat(actualBounds?.top).isEqualTo(bounds.top)
            assertThat(actualBounds?.right).isEqualTo(bounds.right)
            assertThat(actualBounds?.bottom).isEqualTo(bounds.bottom)
        }
    }

    @Test
    fun addOrUpdateRepository_noBoundsBeforeSnapOrMaximize_isEmptyInProto() {
        runTest(StandardTestDispatcher()) {
            // Create a basic repository state
            val task = createDesktopTask(1)
            val desktopPersistentRepositories = createRepositoryWithOneDesk(ArrayList(listOf(task)))
            testDatastore.updateData { desktopPersistentRepositories }

            // Create new state to be initialized
            val desk =
                Desk(
                    deskId = DEFAULT_DESKTOP_ID,
                    displayId = DEFAULT_DISPLAY,
                    visibleTasks = ArraySet(listOf(1)),
                    freeformTasksInZOrder = ArrayList(listOf(1)),
                    uniqueDisplayId = UNIQUE_DISPLAY_ID,
                )
            // No need to set boundsBeforeSnapOrMaximizeByTaskId as it's empty by default

            // Update with new state
            datastoreRepository.addOrUpdateRepository(
                userId = DEFAULT_USER_ID,
                desks = listOf(desk),
                activeDeskIdToUniqueDisplayId = mapOf(UNIQUE_DISPLAY_ID to DEFAULT_DESKTOP_ID),
                preservedDisplays = ArrayMap(),
                rememberedBoundsRatioByPackageName = ArrayMap(),
            )

            val actualDesktop = datastoreRepository.readDesktop(DEFAULT_USER_ID, DEFAULT_DESKTOP_ID)
            val actualTask = actualDesktop?.tasksByTaskIdMap?.get(1)
            assertThat(actualTask?.hasBoundsBeforeSnapOrMaximize()).isFalse()
        }
    }

    @Test
    @EnableFlags(FLAG_ENABLE_REMEMBERED_BOUNDS)
    fun addOrUpdateRepository_addsNewRememberedBoundsRatio() {
        runTest(StandardTestDispatcher()) {
            // Create a basic repository state
            val task = createDesktopTask(1)
            val desktopPersistentRepositories = createRepositoryWithOneDesk(ArrayList(listOf(task)))
            testDatastore.updateData { desktopPersistentRepositories }

            val rememberedBounds = ArrayMap<String, RectF>()
            rememberedBounds["test.package"] = RectF(0f, 0f, 1f, 1f)

            // Update with new state
            datastoreRepository.addOrUpdateRepository(
                userId = DEFAULT_USER_ID,
                desks = listOf(),
                activeDeskIdToUniqueDisplayId = mapOf(UNIQUE_DISPLAY_ID to DEFAULT_DESKTOP_ID),
                preservedDisplays = ArrayMap(),
                rememberedBoundsRatioByPackageName = rememberedBounds,
            )

            val desktopState = datastoreRepository.getDesktopRepositoryState(DEFAULT_USER_ID)
            val rememberedBoundsFound = desktopState?.packageStateByPackageNameMap?.values?.first()
            assertThat(rememberedBoundsFound?.rememberedBoundsRatio?.left).isEqualTo(0f)
            assertThat(rememberedBoundsFound?.rememberedBoundsRatio?.top).isEqualTo(0f)
            assertThat(rememberedBoundsFound?.rememberedBoundsRatio?.right).isEqualTo(1f)
            assertThat(rememberedBoundsFound?.rememberedBoundsRatio?.bottom).isEqualTo(1f)
        }
    }

    private companion object {
        const val DESKTOP_REPOSITORY_STATES_DATASTORE_TEST_FILE = "desktop_repo_test.pb"
        const val DEFAULT_USER_ID = 1000
        const val USER_ID_2 = 2000
        const val DEFAULT_DESKTOP_ID = 1
        const val UNIQUE_DISPLAY_ID = "unique_display_id_1"
        const val UNIQUE_DISPLAY_ID_2 = "unique_display_id_2"

        fun createRepositoryWithOneDesk(
            tasks: ArrayList<DesktopTask>
        ): DesktopPersistentRepositories {
            val desk = createDesktop(tasks)
            val repositoryState =
                DesktopRepositoryState.newBuilder().putDesktop(DEFAULT_DESKTOP_ID, desk)
            val desktopPersistentRepositories =
                DesktopPersistentRepositories.newBuilder()
                    .putDesktopRepoByUser(DEFAULT_USER_ID, repositoryState.build())
                    .build()
            return desktopPersistentRepositories
        }

        fun createDesktop(tasks: ArrayList<DesktopTask>): Desktop? {
            val desktopBuilder =
                Desktop.newBuilder().setDisplayId(DEFAULT_DISPLAY).setDesktopId(DEFAULT_DESKTOP_ID)
            tasks.forEach { task ->
                desktopBuilder.addZOrderedTasks(task.taskId).putTasksByTaskId(task.taskId, task)
            }
            return desktopBuilder.build()
        }

        fun createDesktopTask(
            taskId: Int,
            state: DesktopTaskState = DesktopTaskState.VISIBLE,
        ): DesktopTask =
            DesktopTask.newBuilder().setTaskId(taskId).setDesktopTaskState(state).build()
    }
}
