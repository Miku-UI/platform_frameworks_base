/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.motioncues

import android.app.motioncues.IMotionCuesCallback
import android.app.motioncues.MotionCuesVisualStyle
import android.app.motioncues.MotionCuesService.SERVICE_INTERFACE
import android.app.motioncues.MotionCuesService.EXTRA_API_CALLBACK
import android.app.motioncues.MotionCuesSettings
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.settings.UserTracker
import com.android.systemui.statusbar.CommandQueue
import java.util.concurrent.Executor
import javax.inject.Inject

/**
 * Manages the connection to the MotionCuesService and implements the CommandQueue callbacks to
 * start and end the motion cues session.
 */
@SysUISingleton
class MotionCuesManager @Inject constructor(
    private val context: Context,
    private val commandQueue: CommandQueue,
    val motionCuesUi: MotionCuesUi,
    private val userTracker: UserTracker,
    @Main private val mainExecutor: Executor
) : CoreStartable, CommandQueue.Callbacks, UserTracker.Callback {
    companion object {
        private const val TAG = "MotionCuesManager"
    }
    private val motionCuesCallback = MotionCuesCallbackImpl()

    private val motionCuesConnection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                Log.i(TAG, "Connected to bound service: $name")
            }

            override fun onServiceDisconnected(name: ComponentName) {
                Log.w(TAG, "Service disconnected unexpectedly: $name")
                resetSession()
            }

            override fun onBindingDied(name: ComponentName?) {
                Log.e(TAG, "Binding died for service: $name")
                endMotionCuesSession()
            }

            override fun onNullBinding(name: ComponentName?) {
                Log.e(TAG, "Service returned null from onBind: $name")
                endMotionCuesSession()
            }
        }

    /**
     * Starts a new motion cues session.
     *
     * This method initiates a connection to the specified {@link MotionCuesService}. The UI will only
     * be displayed after the service connection is successfully established.
     *
     * If a session is already active when this method is called, the request will be ignored and a
     * warning will be logged.
     *
     * @param componentName The {@link ComponentName} of the service to bind to.
     * @param userId The user to bind to.
     * @param motionCuesSettings The initial settings for the motion cues UI.
     */
    override fun startMotionCuesSession(
        componentName: ComponentName,
        userId: Int,
        motionCuesSettings: MotionCuesSettings,
    ) {
        if (motionCuesUi.isStarted) {
            Log.w(TAG, "startMotionCuesSession called while a session is already active. Ignoring.")
            return
        }

        val motionCuesIntent =
            Intent(SERVICE_INTERFACE).apply {
                component = componentName
                putExtra(EXTRA_API_CALLBACK, motionCuesCallback.asBinder())
            }


        Log.i(TAG, "Starting Motion Cues Session.")
        val success =
            context.bindService(motionCuesIntent, motionCuesConnection, Context.BIND_AUTO_CREATE)

        if (success) {
            // Start the UI immediately after a successful bind.
            // Otherwise if we start it in the onServiceConnected callback,
            // the service may send updates before the UI is ready to receive them.
            motionCuesUi.start(motionCuesSettings, userId, componentName.packageName)
        } else {
            Log.e(TAG, "Failed to bind to MotionCuesService: $componentName")
            endMotionCuesSession()
        }
    }

    /**
     * Ends the currently active motion cues session.
     *
     * This method unbinds from the service and removes the motion cues UI from the screen. If no
     * session is currently active, this method does nothing.
     */
    override fun endMotionCuesSession() {
        try {
            Log.i(TAG, "Ending Motion Cues Session.")
            context.unbindService(motionCuesConnection)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Failed to unbind service; it may have already been unbound.", e)
        }
        resetSession()
    }

    private fun resetSession() {
        motionCuesUi.stop()
    }

    override fun start() {
        commandQueue.addCallback(this)
        userTracker.addCallback(this, mainExecutor)
    }

    override fun onUserChanged(newUser: Int, userContext: Context) {
        Log.i(TAG, "Ending Motion Cues Session on user change.")
        endMotionCuesSession()
    }

    private inner class MotionCuesCallbackImpl : IMotionCuesCallback.Stub() {
        override fun updateBubblePixelPos(dx: Float, dy: Float) {
            motionCuesUi.updateBubblePos(dx, dy)
        }

        override fun updateMotionCuesVisualStyle(motionCuesVisualStyle: MotionCuesVisualStyle) {
            motionCuesUi.updateMotionCuesVisualStyle(motionCuesVisualStyle)
        }
    }
}
