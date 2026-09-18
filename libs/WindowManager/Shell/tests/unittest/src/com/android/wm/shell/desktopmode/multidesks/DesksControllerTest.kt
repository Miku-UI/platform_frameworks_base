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

package com.android.wm.shell.desktopmode.multidesks

import android.os.UserHandle
import android.os.UserManager
import android.testing.AndroidTestingRunner
import android.view.Display.DEFAULT_DISPLAY
import android.view.Display.INVALID_DISPLAY
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito.never
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.desktopmode.data.DesktopRepositoryInitializer.DeskRootHelper.DeskRootRemovalRequest
import com.android.wm.shell.shared.desktopmode.FakeDesktopConfig
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Test class for [DesksController].
 *
 * Usage: atest WMShellUnitTests:DesksControllerTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class DesksControllerTest : ShellTestCase() {

    private val mockShellController = mock<ShellController>()
    private val mockDisplayController = mock<DisplayController>()
    private val mockDesksOrganizer = mock<DesksOrganizer>()
    private val mockShellTaskOrganizer = mock<ShellTaskOrganizer>()
    private lateinit var desktopState: FakeDesktopState
    private lateinit var desktopConfig: FakeDesktopConfig
    private lateinit var desktopUserRepositories: DesktopUserRepositories
    private val repository: DesktopRepository
        get() = desktopUserRepositories.current

    private val testScope = TestScope()
    private lateinit var controller: DesksController

    @Before
    fun setUp() {
        desktopState = FakeDesktopState()
        desktopConfig = FakeDesktopConfig()
        whenever(mockShellController.currentUserId).thenReturn(USER_ID_1)
        desktopUserRepositories =
            DesktopUserRepositories(
                ShellInit(TestShellExecutor()),
                /* shellController= */ mockShellController,
                /* persistentRepository= */ mock(),
                /* repositoryInitializer= */ mock(),
                testScope.backgroundScope,
                testScope.backgroundScope,
                /* userManager= */ mock(),
                desktopState,
                desktopConfig,
            )

        controller =
            DesksController(
                mockShellController,
                desktopUserRepositories,
                desktopConfig,
                desktopState,
                mockDisplayController,
                mockDesksOrganizer,
                mockShellTaskOrganizer,
            )
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun testRecreateDesk() =
        testScope.runTest {
            val userId = 1
            val displayId = 2
            val deskId = 3
            val newDeskId = 4
            whenever(mockDesksOrganizer.createDesk(eq(displayId), eq(userId), any())).thenAnswer {
                val callback = it.getArgument<DesksOrganizer.OnCreateCallback>(2)
                callback.onCreated(newDeskId)
            }

            val result = controller.recreateDeskRoot(userId, displayId, deskId)

            assertThat(result).isEqualTo(newDeskId)
        }

    @Test
    fun testRemoveDeskRoots() =
        testScope.runTest {
            val requests =
                listOf(
                    DeskRootRemovalRequest(deskId = 1, userId = 2),
                    DeskRootRemovalRequest(deskId = 3, userId = 4),
                )

            controller.removeDeskRoots(requests)

            verify(mockDesksOrganizer).removeDesk(any(), eq(1), eq(2))
            verify(mockDesksOrganizer).removeDesk(any(), eq(3), eq(4))
        }

    @Test
    fun testCanCreateDesks_noLimit_returnsTrue() {
        desktopConfig.maxDeskLimit = DESK_LIMIT_NO_LIMIT

        val canCreate = controller.canCreateDesks(USER_ID_1)

        assertThat(canCreate).isTrue()
    }

    @Test
    fun testCanCreateDesks_atLimit_returnsFalse() {
        desktopConfig.maxDeskLimit = 2
        // Add two desks to bring the number up to the limit.
        repository.addDesk(displayId = DEFAULT_DISPLAY, deskId = 2)
        repository.addDesk(displayId = DEFAULT_DISPLAY, deskId = 3)

        val canCreate = controller.canCreateDesks(USER_ID_1)

        assertThat(canCreate).isFalse()
    }

    @Test
    fun testCanCreateDesksInDisplay_desktopModeUnsupported_returnsFalse() {
        desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = false
        desktopConfig.maxDeskLimit = DESK_LIMIT_NO_LIMIT

        val canCreate = controller.canCreateDeskInDisplay(DEFAULT_DISPLAY, USER_ID_1)

        assertThat(canCreate).isFalse()
    }

    @Test
    fun testCanCreateDesksInDisplay_atLimit_limitEnforced_returnsFalse() {
        desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
        desktopConfig.maxDeskLimit = 2
        // Add two desks to bring the number up to the limit.
        repository.addDesk(displayId = DEFAULT_DISPLAY, deskId = 2)
        repository.addDesk(displayId = DEFAULT_DISPLAY, deskId = 3)

        val canCreate =
            controller.canCreateDeskInDisplay(
                displayId = DEFAULT_DISPLAY,
                userId = USER_ID_1,
                enforceDeskLimit = true,
            )

        assertThat(canCreate).isFalse()
    }

    @Test
    fun testCanCreateDesksInDisplay_atLimit_limitNotEnforced_returnsTrue() {
        desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
        desktopConfig.maxDeskLimit = 2
        // Add two desks to bring the number up to the limit.
        repository.addDesk(displayId = DEFAULT_DISPLAY, deskId = 2)
        repository.addDesk(displayId = DEFAULT_DISPLAY, deskId = 3)

        val canCreate =
            controller.canCreateDeskInDisplay(
                displayId = DEFAULT_DISPLAY,
                userId = USER_ID_1,
                enforceDeskLimit = false,
            )

        assertThat(canCreate).isTrue()
    }

    @Test
    fun testCreateDesk_createsDesk() {
        desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
        val currentDeskCount = repository.getNumberOfDesks(DEFAULT_DISPLAY)
        whenever(mockDesksOrganizer.createDesk(eq(DEFAULT_DISPLAY), any(), any())).thenAnswer {
            invocation ->
            (invocation.arguments[2] as DesksOrganizer.OnCreateCallback).onCreated(deskId = 5)
        }

        controller.createDesk(DEFAULT_DISPLAY, repository.userId)

        assertThat(repository.getNumberOfDesks(DEFAULT_DISPLAY)).isEqualTo(currentDeskCount + 1)
    }

    @Test
    fun testCreateDesk_invalidDisplay_dropsRequest() {
        desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
        controller.createDesk(INVALID_DISPLAY)

        verify(mockDesksOrganizer, never()).createDesk(any(), any(), any())
    }

    @Test
    fun testCreateDesk_systemUser_dropsRequest() {
        desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
        assumeTrue(UserManager.isHeadlessSystemUserMode())

        controller.createDesk(DEFAULT_DISPLAY, UserHandle.USER_SYSTEM)

        verify(mockDesksOrganizer, never()).createDesk(any(), any(), any())
    }

    @Test
    fun testCreateDesk_atLimit_limitNotEnforced_createsDesk() {
        desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
        desktopConfig.maxDeskLimit = 2
        // Add two desks to bring the number up to the limit.
        repository.addDesk(displayId = DEFAULT_DISPLAY, deskId = 2)
        repository.addDesk(displayId = DEFAULT_DISPLAY, deskId = 3)
        val currentDeskCount = repository.getNumberOfDesks(DEFAULT_DISPLAY)
        whenever(mockDesksOrganizer.createDesk(eq(DEFAULT_DISPLAY), any(), any())).thenAnswer {
            invocation ->
            (invocation.arguments[2] as DesksOrganizer.OnCreateCallback).onCreated(deskId = 5)
        }

        controller.createDesk(
            displayId = DEFAULT_DISPLAY,
            userId = repository.userId,
            enforceDeskLimit = false,
        )

        assertThat(repository.getNumberOfDesks(DEFAULT_DISPLAY)).isEqualTo(currentDeskCount + 1)
    }

    @Test
    fun testCreateDeskSuspending_createsDeskReturningId() =
        testScope.runTest {
            val newDeskId = 5
            desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
            val currentDeskCount = repository.getNumberOfDesks(DEFAULT_DISPLAY)
            whenever(mockDesksOrganizer.createDesk(eq(DEFAULT_DISPLAY), any(), any())).thenAnswer {
                invocation ->
                (invocation.arguments[2] as DesksOrganizer.OnCreateCallback).onCreated(newDeskId)
            }

            assertThat(controller.createDeskSuspending(DEFAULT_DISPLAY, repository.userId))
                .isEqualTo(newDeskId)

            assertThat(repository.getNumberOfDesks(DEFAULT_DISPLAY)).isEqualTo(currentDeskCount + 1)
        }

    @Test
    fun testCreateDeskSuspending_displayNotSupported_returnsNull() =
        testScope.runTest {
            desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = false

            assertThat(controller.createDeskSuspending(DEFAULT_DISPLAY)).isNull()
        }

    @Test
    fun testCreateDeskSuspending_invalidDisplay_returnsNull() =
        testScope.runTest {
            desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true

            assertThat(controller.createDeskSuspending(INVALID_DISPLAY)).isNull()
        }

    @Test
    fun testCreateDeskSuspending_systemUser_returnsNull() =
        testScope.runTest {
            desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
            assumeTrue(UserManager.isHeadlessSystemUserMode())

            assertThat(controller.createDeskSuspending(DEFAULT_DISPLAY, UserHandle.USER_SYSTEM))
                .isNull()
        }

    @Test
    fun testCreateDeskSuspending_atLimit_limitNotEnforced_createsDeskReturningId() =
        testScope.runTest {
            val newDeskId = 5
            desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
            desktopConfig.maxDeskLimit = 2
            // Add two desks to bring the number up to the limit.
            repository.addDesk(displayId = DEFAULT_DISPLAY, deskId = 2)
            repository.addDesk(displayId = DEFAULT_DISPLAY, deskId = 3)
            val currentDeskCount = repository.getNumberOfDesks(DEFAULT_DISPLAY)
            whenever(mockDesksOrganizer.createDesk(eq(DEFAULT_DISPLAY), any(), any())).thenAnswer {
                invocation ->
                (invocation.arguments[2] as DesksOrganizer.OnCreateCallback).onCreated(newDeskId)
            }

            assertThat(
                    controller.createDeskSuspending(
                        displayId = DEFAULT_DISPLAY,
                        userId = repository.userId,
                        enforceDeskLimit = false,
                    )
                )
                .isEqualTo(newDeskId)

            assertThat(repository.getNumberOfDesks(DEFAULT_DISPLAY)).isEqualTo(currentDeskCount + 1)
        }

    private companion object {
        private const val USER_ID_1 = 6
        private const val DESK_LIMIT_NO_LIMIT = 0
    }
}
