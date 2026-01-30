/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.systemui.notetask

import android.app.role.RoleManager
import android.app.role.RoleManager.ROLE_NOTES
import android.hardware.input.InputManager
import android.hardware.input.KeyGestureEvent
import android.os.UserHandle
import android.os.UserManager
import android.view.KeyEvent.KEYCODE_STYLUS_BUTTON_TAIL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.keyguard.KeyguardUpdateMonitor
import com.android.systemui.SysuiTestCase
import com.android.systemui.notetask.NoteTaskEntryPoint.KEYBOARD_SHORTCUT
import com.android.systemui.notetask.NoteTaskEntryPoint.TAIL_BUTTON
import com.android.systemui.settings.FakeUserTracker
import com.android.systemui.util.concurrency.FakeExecutor
import com.android.systemui.util.mockito.mock
import com.android.systemui.util.mockito.whenever
import com.android.systemui.util.mockito.withArgCaptor
import com.android.systemui.util.time.FakeSystemClock
import com.android.wm.shell.bubbles.Bubbles
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.anyList
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.MockitoAnnotations.initMocks
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

/** atest SystemUITests:NoteTaskInitializerTest */
@SmallTest
@RunWith(AndroidJUnit4::class)
internal class NoteTaskInitializerTest : SysuiTestCase() {

    @Mock lateinit var inputManager: InputManager
    @Mock lateinit var bubbles: Bubbles
    @Mock lateinit var controller: NoteTaskController
    @Mock lateinit var roleManager: RoleManager
    @Mock lateinit var userManager: UserManager
    @Mock lateinit var keyguardMonitor: KeyguardUpdateMonitor

    private val executor = FakeExecutor(FakeSystemClock())
    private val userTracker = FakeUserTracker()

    @Before
    fun setUp() {
        initMocks(this)
        whenever(keyguardMonitor.isUserUnlocked(userTracker.userId)).thenReturn(true)
    }

    private fun createUnderTest(isEnabled: Boolean, bubbles: Bubbles?): NoteTaskInitializer =
        NoteTaskInitializer(
            controller = controller,
            optionalBubbles = Optional.ofNullable(bubbles),
            isEnabled = isEnabled,
            roleManager = roleManager,
            userTracker = userTracker,
            keyguardUpdateMonitor = keyguardMonitor,
            inputManager = inputManager,
            backgroundExecutor = executor,
        )

    @Test
    fun initialize_withUserUnlocked() {
        whenever(keyguardMonitor.isUserUnlocked(userTracker.userId)).thenReturn(true)

        createUnderTest(isEnabled = true, bubbles = bubbles).initialize()

        verify(roleManager).addOnRoleHoldersChangedListenerAsUser(any(), any(), any())
        verify(controller).updateNoteTaskForCurrentUserAndManagedProfiles()
        verify(keyguardMonitor).registerCallback(any())
    }

    @Test
    fun initialize_withUserLocked() {
        whenever(keyguardMonitor.isUserUnlocked(userTracker.userId)).thenReturn(false)

        createUnderTest(isEnabled = true, bubbles = bubbles).initialize()

        verify(roleManager).addOnRoleHoldersChangedListenerAsUser(any(), any(), any())
        verify(controller, never()).setNoteTaskShortcutEnabled(any(), any())
        verify(keyguardMonitor).registerCallback(any())
        assertThat(userTracker.callbacks).isNotEmpty()
    }

    @Test
    fun initialize_flagDisabled() {
        val underTest = createUnderTest(isEnabled = false, bubbles = bubbles)

        underTest.initialize()

        verifyNoMoreInteractions(bubbles, controller, roleManager, userManager, keyguardMonitor)
    }

    @Test
    fun initialize_bubblesNotPresent() {
        val underTest = createUnderTest(isEnabled = true, bubbles = null)

        underTest.initialize()

        verifyNoMoreInteractions(bubbles, controller, roleManager, userManager, keyguardMonitor)
    }

    @Test
    fun initialize_keyGestureTypeOpenNotes_isRegistered() {
        val underTest = createUnderTest(isEnabled = true, bubbles = bubbles)
        underTest.initialize()
        verify(inputManager)
            .registerKeyGestureEventHandler(
                eq(listOf(KeyGestureEvent.KEY_GESTURE_TYPE_OPEN_NOTES)),
                any(),
            )
    }

    @Test
    fun handlesShortcut_keyGestureTypeOpenNotes() {
        val gestureEvent =
            KeyGestureEvent.Builder()
                .setKeyGestureType(KeyGestureEvent.KEY_GESTURE_TYPE_OPEN_NOTES)
                .setAction(KeyGestureEvent.ACTION_GESTURE_COMPLETE)
                .build()
        val underTest = createUnderTest(isEnabled = true, bubbles = bubbles)
        underTest.initialize()
        val callback = withArgCaptor {
            verify(inputManager).registerKeyGestureEventHandler(anyList(), capture())
        }

        callback.handleKeyGestureEvent(gestureEvent, null)
        executor.runAllReady()

        verify(controller).showNoteTask(eq(KEYBOARD_SHORTCUT))
    }

    @Test
    fun handlesShortcut_stylusTailButton() {
        val gestureEvent =
            KeyGestureEvent.Builder()
                .setKeycodes(intArrayOf(KEYCODE_STYLUS_BUTTON_TAIL))
                .setKeyGestureType(KeyGestureEvent.KEY_GESTURE_TYPE_OPEN_NOTES)
                .setAction(KeyGestureEvent.ACTION_GESTURE_COMPLETE)
                .build()
        val underTest = createUnderTest(isEnabled = true, bubbles = bubbles)
        underTest.initialize()
        val callback = withArgCaptor {
            verify(inputManager).registerKeyGestureEventHandler(anyList(), capture())
        }

        callback.handleKeyGestureEvent(gestureEvent, null)
        executor.runAllReady()

        verify(controller).showNoteTask(eq(TAIL_BUTTON))
    }

    @Test
    fun ignoresUnrelatedShortcuts() {
        val gestureEvent =
            KeyGestureEvent.Builder()
                .setKeycodes(intArrayOf(KEYCODE_STYLUS_BUTTON_TAIL))
                .setKeyGestureType(KeyGestureEvent.KEY_GESTURE_TYPE_HOME)
                .setAction(KeyGestureEvent.ACTION_GESTURE_COMPLETE)
                .build()
        val underTest = createUnderTest(isEnabled = true, bubbles = bubbles)
        underTest.initialize()
        val callback = withArgCaptor {
            verify(inputManager).registerKeyGestureEventHandler(anyList(), capture())
        }

        callback.handleKeyGestureEvent(gestureEvent, null)
        executor.runAllReady()

        verify(controller, never()).showNoteTask(any())
    }

    @Test
    fun initialize_userUnlocked_shouldUpdateNoteTask() {
        whenever(keyguardMonitor.isUserUnlocked(userTracker.userId)).thenReturn(false)
        val underTest = createUnderTest(isEnabled = true, bubbles = bubbles)
        underTest.initialize()
        val callback = withArgCaptor { verify(keyguardMonitor).registerCallback(capture()) }
        whenever(keyguardMonitor.isUserUnlocked(userTracker.userId)).thenReturn(true)

        callback.onUserUnlocked()

        verify(controller).updateNoteTaskForCurrentUserAndManagedProfiles()
    }

    @Test
    fun initialize_onRoleHoldersChanged_shouldRunOnRoleHoldersChanged() {
        val underTest = createUnderTest(isEnabled = true, bubbles = bubbles)
        underTest.initialize()
        val callback = withArgCaptor {
            verify(roleManager)
                .addOnRoleHoldersChangedListenerAsUser(any(), capture(), eq(UserHandle.ALL))
        }

        callback.onRoleHoldersChanged(ROLE_NOTES, userTracker.userHandle)

        verify(controller).onRoleHoldersChanged(ROLE_NOTES, userTracker.userHandle)
    }

    @Test
    fun initialize_onProfilesChanged_shouldUpdateNoteTask() {
        val underTest = createUnderTest(isEnabled = true, bubbles = bubbles)
        underTest.initialize()

        userTracker.callbacks.first().onProfilesChanged(emptyList())

        verify(controller, times(2)).updateNoteTaskForCurrentUserAndManagedProfiles()
    }

    @Test
    fun initialize_onUserChanged_shouldUpdateNoteTask() {
        val underTest = createUnderTest(isEnabled = true, bubbles = bubbles)
        underTest.initialize()

        userTracker.callbacks.first().onUserChanged(0, mock())

        verify(controller, times(2)).updateNoteTaskForCurrentUserAndManagedProfiles()
    }
}
