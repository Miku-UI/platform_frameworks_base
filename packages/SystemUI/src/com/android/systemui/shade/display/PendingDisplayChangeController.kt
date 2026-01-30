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

package com.android.systemui.shade.display

import android.annotation.BinderThread
import android.annotation.SuppressLint
import android.os.Trace
import android.util.Log
import com.android.internal.policy.IKeyguardService.SCREEN_TURNING_ON_REASON_DISPLAY_SWITCH
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.power.shared.model.ScreenPowerState
import com.android.systemui.statusbar.NotificationShadeWindowController
import java.lang.Runnable
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext

/**
 * Manages pending display change events from WM. It receives onScreenTurningOn callback,
 * and whenever the reason of the display turning on is a display switch (for example,
 * unfolding a display on foldable devices) it will enter 'pending display change' state
 * until it will receive onScreenTurnedOn callback.
 *
 * Whenever pending display change state changes, this class will notify
 * NotificationShadeWindowController about the change, so it could update its appearance like
 * the wallpaper visibility flag. This is to ensure that we applied all the necessary
 * changes before we turned on the screen, so the user won't see inconsistent state.
 *
 * Suppressing SharedFlowCreation: we have to process each Runnable sent to this flow, so we need to
 * use MutableSharedFlow as it supports buffering. This flow is not exposed publicly and collected
 * on a background thread, so it shouldn't significantly affect performance.
 */
@SuppressLint("SharedFlowCreation")
@OptIn(ExperimentalCoroutinesApi::class)
@SysUISingleton
class PendingDisplayChangeController @Inject constructor(
    @Background private val backgroundScope: CoroutineScope,
    @Application private val mainScope: CoroutineScope,
    private val notificationShadeWindowController: Lazy<NotificationShadeWindowController>,
    private val powerInteractor: PowerInteractor
) : CoreStartable {

    private val onPendingScreenTurningOn: MutableSharedFlow<Runnable> by lazy {
        EnsureWallpaperDrawnOnDisplaySwitch.expectInNewMode()

        /**
         * Extra buffer is required for tryEmit() to work, otherwise tryEmit returns 'false'
         * and doesn't emit the value even if there are no past pending emissions.
         */
        MutableSharedFlow(extraBufferCapacity = 1)
    }

    override fun start() {
        if (!EnsureWallpaperDrawnOnDisplaySwitch.isEnabled) {
            return
        }

        backgroundScope.launch {
            onPendingScreenTurningOn
                .flatMapLatest { reportScreenOnTaskComplete ->
                    flow<Unit> {
                        Trace.beginAsyncSection(
                            "$TAG#pendingDisplayChange",
                            reportScreenOnTaskComplete.hashCode()
                        )

                        updateShadeWindowState(pendingDisplayChange = true)
                        reportScreenOnTaskComplete.run()
                        waitForScreenTurnedOn()
                    }.onCompletion {
                        // Call again in case if the coroutine was interrupted
                        reportScreenOnTaskComplete.run()

                        // Reset pending display change property as now the screen is turned ON
                        updateShadeWindowState(pendingDisplayChange = false)

                        Trace.endAsyncSection(
                            "$TAG#pendingDisplayChange",
                            reportScreenOnTaskComplete.hashCode()
                        )
                    }
                }
                .collect()
        }
    }

    private suspend fun updateShadeWindowState(pendingDisplayChange: Boolean) {
        withContext(mainScope.coroutineContext) {
            notificationShadeWindowController.get().setPendingDisplayChange(pendingDisplayChange)
        }
    }

    private suspend fun waitForScreenTurnedOn() {
        try {
            withTimeout(SCREEN_TURNED_ON_TIMEOUT_MS) {
                powerInteractor.screenPowerState
                    .filter { it == ScreenPowerState.SCREEN_ON }
                    .first()
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Timeout waiting for screen on event", e)
        }
    }

    @BinderThread
    fun onScreenTurningOn(reason: Int, onComplete: Runnable) {
        EnsureWallpaperDrawnOnDisplaySwitch.expectInNewMode()

        if (reason != SCREEN_TURNING_ON_REASON_DISPLAY_SWITCH) {
            onComplete.run()
            return
        }

        val runnableSent = onPendingScreenTurningOn.tryEmit(onComplete.toSingleShot())
        if (!runnableSent) {
            Log.e(TAG, "Could not send onComplete runnable to the flow")
            onComplete.run()
        }
    }

    /**
     * Makes the runnable safe to call multiple times, only the first call will be propagated
     * to the original runnable, the rest will be ignored
     */
    private fun Runnable.toSingleShot(): Runnable =
        object : Runnable {
            private var executed = AtomicBoolean(false)

            override fun run() {
                if (executed.compareAndSet(/* expectedValue= */ false, /* newValue= */ true)) {
                    this@toSingleShot.run()
            }
        }
    }

    private companion object {
        private const val TAG = "PendingDispChangeContr"
        private const val SCREEN_TURNED_ON_TIMEOUT_MS = 1000L
    }
}
