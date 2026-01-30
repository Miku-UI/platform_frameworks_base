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

package com.android.systemui.keyboard.shortcut.data.source

import android.content.res.mainResources
import android.platform.test.annotations.EnableFlags
import android.view.KeyEvent.KEYCODE_D
import android.view.KeyEvent.KEYCODE_DPAD_DOWN
import android.view.KeyEvent.KEYCODE_EQUALS
import android.view.KeyEvent.KEYCODE_LEFT_BRACKET
import android.view.KeyEvent.KEYCODE_MINUS
import android.view.KeyEvent.KEYCODE_RIGHT_BRACKET
import android.view.KeyEvent.KEYCODE_TAB
import android.view.KeyEvent.KEYCODE_W
import android.view.KeyEvent.META_ALT_ON
import android.view.KeyEvent.META_CTRL_ON
import android.view.KeyEvent.META_META_ON
import android.view.KeyEvent.META_SHIFT_ON
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.testScope
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import com.android.window.flags.Flags
import com.android.window.flags.Flags.FLAG_ENABLE_MOVE_TO_NEXT_DISPLAY_SHORTCUT
import com.android.window.flags.Flags.FLAG_ENABLE_TASK_RESIZING_KEYBOARD_SHORTCUTS
import com.android.window.flags.Flags.FLAG_KEYBOARD_SHORTCUTS_TO_SWITCH_DESKS
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.google.common.truth.Truth.assertThat
import kotlin.Triple
import kotlin.collections.flatMap
import kotlin.collections.listOf
import kotlin.collections.map
import kotlin.map
import kotlin.sequences.flatMap
import kotlin.sequences.map
import kotlin.text.flatMap
import kotlin.text.map
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class MultitaskingShortcutsSourceTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val desktopState = FakeDesktopState()
    private val source = MultitaskingShortcutsSource(kosmos.mainResources, context, desktopState)

    private val expectedDesktopShortcuts =
        listOf(
            Triple(
                context.getString(R.string.system_multitasking_desktop_view),
                META_META_ON or META_CTRL_ON,
                KEYCODE_DPAD_DOWN,
            ),
            Triple(
                context.getString(R.string.system_multitasking_move_to_next_display),
                META_META_ON or META_CTRL_ON,
                KEYCODE_D,
            ),
            Triple(
                context.getString(R.string.system_desktop_mode_snap_left_window),
                META_META_ON,
                KEYCODE_LEFT_BRACKET,
            ),
            Triple(
                context.getString(R.string.system_desktop_mode_snap_right_window),
                META_META_ON,
                KEYCODE_RIGHT_BRACKET,
            ),
            Triple(
                context.getString(R.string.system_desktop_mode_toggle_maximize_window),
                META_META_ON,
                KEYCODE_EQUALS,
            ),
            Triple(
                context.getString(R.string.system_desktop_mode_minimize_window),
                META_META_ON,
                KEYCODE_MINUS,
            ),
            Triple(
                context.getString(R.string.system_desktop_mode_close_window),
                META_META_ON or META_CTRL_ON,
                KEYCODE_W,
            ),
            Triple(
                context.getString(R.string.system_multiple_desktop_mode_switch_between_desks),
                META_META_ON or META_CTRL_ON,
                KEYCODE_LEFT_BRACKET,
            ),
            Triple(
                context.getString(R.string.system_multiple_desktop_mode_switch_between_desks),
                META_META_ON or META_CTRL_ON,
                KEYCODE_RIGHT_BRACKET,
            ),
        )

    @Before
    fun setup() {
        desktopState.canEnterDesktopMode = true
    }

    @Test
    fun shortcutGroups_doesNotContainCycleThroughRecentAppsShortcuts() {
        testScope.runTest {
            val groups = source.shortcutGroups(TEST_DEVICE_ID)

            val shortcuts =
                groups.flatMap { it.items }.map { c -> Triple(c.label, c.modifiers, c.keycode) }

            val cycleThroughRecentAppsShortcuts =
                listOf(
                    Triple(
                        context.getString(R.string.group_system_cycle_forward),
                        META_ALT_ON,
                        KEYCODE_TAB,
                    ),
                    Triple(
                        context.getString(R.string.group_system_cycle_back),
                        META_SHIFT_ON or META_ALT_ON,
                        KEYCODE_TAB,
                    ),
                )

            assertThat(shortcuts).containsNoneIn(cycleThroughRecentAppsShortcuts)
        }
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_MOVE_TO_NEXT_DISPLAY_SHORTCUT,
        Flags.FLAG_ENABLE_TASK_RESIZING_KEYBOARD_SHORTCUTS,
        Flags.FLAG_KEYBOARD_SHORTCUTS_TO_SWITCH_DESKS,
    )
    fun shortcutGroups_containsDesktopShortcuts() {
        testScope.runTest {
            val groups = source.shortcutGroups(TEST_DEVICE_ID)
            val shortcuts =
                groups.flatMap { it.items }.map { c -> Triple(c.label, c.modifiers, c.keycode) }

            assertThat(shortcuts).containsAtLeastElementsIn(expectedDesktopShortcuts)
        }
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_MOVE_TO_NEXT_DISPLAY_SHORTCUT,
        Flags.FLAG_ENABLE_TASK_RESIZING_KEYBOARD_SHORTCUTS,
        Flags.FLAG_KEYBOARD_SHORTCUTS_TO_SWITCH_DESKS,
        Flags.FLAG_CLOSE_TASK_KEYBOARD_SHORTCUT,
    )
    fun shortcutGroups_desktopDisabled_doesNotContainDesktopShortcuts() {
        testScope.runTest {
            desktopState.canEnterDesktopMode = false
            val groups = source.shortcutGroups(TEST_DEVICE_ID)
            val shortcuts =
                groups.flatMap { it.items }.map { c -> Triple(c.label, c.modifiers, c.keycode) }

            assertThat(shortcuts).containsNoneIn(expectedDesktopShortcuts)
        }
    }

    private companion object {
        private const val TEST_DEVICE_ID = 1234
    }
}
