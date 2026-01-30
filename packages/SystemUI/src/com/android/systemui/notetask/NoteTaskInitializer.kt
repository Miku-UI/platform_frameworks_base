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

import android.app.role.OnRoleHoldersChangedListener
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.UserInfo
import android.hardware.input.InputManager
import android.hardware.input.KeyGestureEvent
import android.os.IBinder
import android.os.UserHandle
import android.view.KeyEvent.KEYCODE_STYLUS_BUTTON_TAIL
import com.android.keyguard.KeyguardUpdateMonitor
import com.android.keyguard.KeyguardUpdateMonitorCallback
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.log.DebugLogger.debugLog
import com.android.systemui.notetask.NoteTaskEntryPoint.KEYBOARD_SHORTCUT
import com.android.systemui.notetask.NoteTaskEntryPoint.TAIL_BUTTON
import com.android.systemui.settings.UserTracker
import com.android.wm.shell.bubbles.Bubbles
import java.util.Optional
import java.util.concurrent.Executor
import javax.inject.Inject

/** Class responsible to "glue" all note task dependencies. */
class NoteTaskInitializer
@Inject
constructor(
    private val controller: NoteTaskController,
    private val roleManager: RoleManager,
    private val optionalBubbles: Optional<Bubbles>,
    private val userTracker: UserTracker,
    private val keyguardUpdateMonitor: KeyguardUpdateMonitor,
    private val inputManager: InputManager,
    @Background private val backgroundExecutor: Executor,
    @NoteTaskEnabledKey private val isEnabled: Boolean,
) {

    /** Initializes note task related features and glue it with other parts of the SystemUI. */
    fun initialize() {
        debugLog { "initialize: isEnabled=$isEnabled, hasBubbles=${optionalBubbles.isEmpty}" }

        // Guard against feature not being enabled or mandatory dependencies aren't available.
        if (!isEnabled || optionalBubbles.isEmpty) return

        initializeKeyGestureEventHandler()
        initializeOnRoleHoldersChanged()
        initializeOnUserUnlocked()
        initializeUserTracker()
    }

    /**
     * Initializes a [InputManager.KeyGestureEventHandler] which will handle shortcuts for opening
     * the notes role via [NoteTaskController].
     */
    private fun initializeKeyGestureEventHandler() {
        inputManager.registerKeyGestureEventHandler(
            listOf(KeyGestureEvent.KEY_GESTURE_TYPE_OPEN_NOTES),
            callbacks,
        )
    }

    /**
     * Initializes the [RoleManager] role holder changed listener to ensure [NoteTaskController]
     * will always update whenever the role holder app changes. Keep in mind that a role may change
     * by direct user interaction (i.e., user goes to settings and change it) or by indirect
     * interaction (i.e., the current role holder app is uninstalled).
     */
    private fun initializeOnRoleHoldersChanged() {
        roleManager.addOnRoleHoldersChangedListenerAsUser(
            backgroundExecutor,
            callbacks,
            UserHandle.ALL,
        )
    }

    /**
     * Initializes a [KeyguardUpdateMonitor] listener that will ensure [NoteTaskController] is in
     * correct state during system initialization (after a direct boot user unlocked event).
     *
     * Once the system is unlocked, we will force trigger [NoteTaskController.onRoleHoldersChanged]
     * with a hardcoded [RoleManager.ROLE_NOTES] for the current user.
     */
    private fun initializeOnUserUnlocked() {
        if (keyguardUpdateMonitor.isUserUnlocked(userTracker.userId)) {
            controller.updateNoteTaskForCurrentUserAndManagedProfiles()
        }
        keyguardUpdateMonitor.registerCallback(callbacks)
    }

    private fun initializeUserTracker() {
        userTracker.addCallback(callbacks, backgroundExecutor)
    }

    // Some callbacks use a weak reference, so we play safe and keep a hard reference to them all.
    private val callbacks =
        object :
            KeyguardUpdateMonitorCallback(),
            UserTracker.Callback,
            OnRoleHoldersChangedListener,
            InputManager.KeyGestureEventHandler {

            override fun onRoleHoldersChanged(roleName: String, user: UserHandle) {
                controller.onRoleHoldersChanged(roleName, user)
            }

            override fun onUserUnlocked() {
                controller.updateNoteTaskForCurrentUserAndManagedProfiles()
            }

            override fun onUserChanged(newUser: Int, userContext: Context) {
                controller.updateNoteTaskForCurrentUserAndManagedProfiles()
            }

            override fun onProfilesChanged(profiles: List<UserInfo>) {
                controller.updateNoteTaskForCurrentUserAndManagedProfiles()
            }

            override fun handleKeyGestureEvent(event: KeyGestureEvent, focusedToken: IBinder?) {
                this@NoteTaskInitializer.handleKeyGestureEvent(event)
            }
        }

    private fun handleKeyGestureEvent(event: KeyGestureEvent) {
        if (event.keyGestureType != KeyGestureEvent.KEY_GESTURE_TYPE_OPEN_NOTES) {
            return
        }
        debugLog {
            "handleKeyGestureEvent: Received OPEN_NOTES gesture event from keycodes: " +
                event.keycodes.contentToString()
        }
        if (event.keycodes.size == 1 && event.keycodes[0] == KEYCODE_STYLUS_BUTTON_TAIL) {
            backgroundExecutor.execute { controller.showNoteTask(TAIL_BUTTON) }
        } else {
            backgroundExecutor.execute { controller.showNoteTask(KEYBOARD_SHORTCUT) }
        }
    }
}
