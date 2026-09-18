/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.keyguard.domain.interactor

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.provider.Settings.Secure
import android.util.Log
import com.android.internal.annotations.VisibleForTesting
import com.android.systemui.authentication.domain.interactor.AuthenticationInteractor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.keyguard.data.repository.KeyguardRepository
import com.android.systemui.keyguard.shared.model.LockAfterDelayTimerState
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.power.shared.model.WakeSleepReason
import com.android.systemui.power.shared.model.WakefulnessModel
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.shared.settings.data.repository.SecureSettingsRepository
import com.android.systemui.shared.settings.data.repository.SystemSettingsRepository
import com.android.systemui.util.PendingIntentCreator
import com.android.systemui.util.time.SystemClock
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/**
 * Monitors for events that should lead to "delayed lock" (e.g. screen off due to timeout) and
 * manages the corresponding timer in a deep-suspend-immune way (using
 * AlarmManager#setExactAndAllowWhileIdle). Reports the state of the "delayed lock" timer as
 * [LockAfterDelayTimerState].
 */
@SysUISingleton
class LockAfterDelayInteractor
@Inject
constructor(
    @param:Application private val scope: CoroutineScope,
    @param:ShadeDisplayAware private val context: Context,
    private val pendingIntentCreator: PendingIntentCreator,
    private val repository: KeyguardRepository,
    private val authenticationInteractor: AuthenticationInteractor,
    private val keyguardInteractor: KeyguardInteractor,
    private val systemClock: SystemClock,
    private val alarmManager: AlarmManager,
    private val secureSettingsRepository: SecureSettingsRepository,
    private val systemSettingsRepository: SystemSettingsRepository,
    private val powerInteractor: PowerInteractor,
) {

    val lockAfterDelayState: StateFlow<LockAfterDelayTimerState> = repository.lockAfterDelayState

    /**
     * Counter that is incremented every time we wake up or stop dreaming. Upon sleeping/dreaming,
     * we put the current value of this counter into the intent extras of the timeout alarm intent.
     * If this value has changed by the time we receive the intent, it is discarded since it's out
     * of date.
     */
    private val timeoutCounter = AtomicInteger(0)

    private val broadcastReceiver: BroadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (DELAYED_KEYGUARD_ACTION == intent.action) {
                    val sequence = intent.getIntExtra(SEQ_EXTRA_KEY, 0)
                    // If the sequence # matches, we have not woken up or stopped dreaming since the
                    // alarm was set. That means this is still relevant - the lock timeout has now
                    // elapsed.
                    var relevant = (timeoutCounter.get() == sequence)
                    if (relevant) {
                        repository.lockAfterDelayState.value = LockAfterDelayTimerState.ELAPSED
                    }
                }
            }
        }

    @VisibleForTesting
    fun timeoutElapsedForTesting() {
        repository.lockAfterDelayState.value = LockAfterDelayTimerState.ELAPSED
    }

    init {
        registerBroadcastReceiver()
        handleWakefulness()
        handleDreamingWhileAwake()
    }

    private fun handleWakefulness() {
        if (!SceneContainerFlag.isEnabled) {
            return
        }
        scope.launch {
            powerInteractor.detailedWakefulness
                .distinctUntilChangedBy { it.isAsleep() }
                .collect {
                    if (it.isAwake()) {
                        ensureTimerStopped("awake")
                    } else {
                        if (wakefulnessStateTriggersDelayedLock(it) && !delayLockIsImmediate()) {
                            ensureTimerRunning("falling asleep")
                        } else {
                            ensureTimerStopped("not relevant")
                        }
                    }
                }
        }
    }

    /**
     * While [handleWakefulness] schedules a lock when the device goes to sleep,
     * [handleDreamingWhileAwake] schedules a lock when a dream is entered from awake. This can
     * occur when the user manually starts a dream (also known as screensaver), via quick settings
     * or settings.
     */
    private fun handleDreamingWhileAwake() {
        if (!SceneContainerFlag.isEnabled) {
            return
        }

        scope.launch {
            keyguardInteractor.isDreamingAny.distinctUntilChanged().collect { isDreaming ->
                if (powerInteractor.detailedWakefulness.value.isAwake()) {
                    if (isDreaming) {
                        ensureTimerRunning("started dreaming while awake")
                    } else {
                        ensureTimerStopped("stopped dreaming while awake")
                    }
                }
            }
        }
    }

    private suspend fun ensureTimerRunning(reason: String) {
        var shouldScheduleAlarm = false
        synchronized(this) {
            if (repository.lockAfterDelayState.value == LockAfterDelayTimerState.INACTIVE) {
                shouldScheduleAlarm = true
                repository.lockAfterDelayState.value = LockAfterDelayTimerState.RUNNING
            }
        }

        if (!shouldScheduleAlarm) {
            Log.d(TAG, "ignored duplicate timer start (reason=$reason)")
            return
        }

        Log.i(TAG, "timer started due to $reason")
        scheduleAlarm()
    }

    private suspend fun ensureTimerStopped(reason: String) {
        var shouldCancelAlarm = false
        synchronized(this) {
            if (repository.lockAfterDelayState.value == LockAfterDelayTimerState.RUNNING) {
                shouldCancelAlarm = true
            }

            repository.lockAfterDelayState.value = LockAfterDelayTimerState.INACTIVE
        }

        if (shouldCancelAlarm) {
            Log.i(TAG, "timer stopped due to $reason")
            cancelAlarm()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun scheduleAlarm() {
        var counter = timeoutCounter.get()

        val intent =
            Intent(DELAYED_KEYGUARD_ACTION).apply {
                setPackage(context.packageName)
                putExtra(SEQ_EXTRA_KEY, counter)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }

        val sender =
            pendingIntentCreator.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val delay = lockDelay()
        Log.i(TAG, "scheduling lock after screen timeout alarm seq=$counter in ${delay}ms")
        val time = systemClock.elapsedRealtime() + delay
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, time, sender)

        // TODO(b/346803756): Migrate support for child profiles.
    }

    /**
     * Cancel the timeout by incrementing the counter so that we ignore the intent when it's
     * received.
     */
    private fun cancelAlarm() {
        var canceledCounter = timeoutCounter.getAndIncrement()
        Log.i(TAG, "canceled lock after screen timeout alarm seq=$canceledCounter")
    }

    /**
     * Registers the broadcast receiver to receive the alarm intent.
     *
     * TODO(b/351817381): Investigate using BroadcastDispatcher vs. ignoring this lint warning.
     */
    @SuppressLint("WrongConstant", "RegisterReceiverViaContext")
    private fun registerBroadcastReceiver() {
        if (!SceneContainerFlag.isEnabled) {
            return
        }

        val delayedActionFilter = IntentFilter()
        delayedActionFilter.addAction(DELAYED_KEYGUARD_ACTION)
        // TODO(b/346803756): Listen for DELAYED_LOCK_PROFILE_ACTION.
        delayedActionFilter.priority = IntentFilter.SYSTEM_HIGH_PRIORITY
        context.registerReceiver(
            broadcastReceiver,
            delayedActionFilter,
            SYSTEMUI_PERMISSION,
            null /* scheduler */,
            Context.RECEIVER_EXPORTED,
        )
    }

    /**
     * Returns the amount of time to wait before locking down the device after the device has been
     * put to sleep by the user, in milliseconds.
     */
    suspend fun lockDelay(): Long {
        val isSecure = authenticationInteractor.authenticationMethod.value.isSecure
        val lockAfterDelaySetting =
            if (isSecure) {
                secureSettingsRepository
                    .getInt(
                        Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT,
                        KEYGUARD_LOCK_AFTER_DELAY_DEFAULT,
                    )
                    .toLong()
            } else {
                // Without a secure auth method, Settings provides no ability to change the lock
                // timeout. Ignore a possibly-set prior value.
                KEYGUARD_LOCK_AFTER_DELAY_DEFAULT.toLong()
            }
        Log.d(TAG, "Lock after delay with isSecure=$isSecure: ${lockAfterDelaySetting}ms")

        val maxTimeToLockDevicePolicy = authenticationInteractor.getMaximumTimeToLock()

        if (maxTimeToLockDevicePolicy <= 0) {
            // No device policy enforced maximum.
            Log.d(TAG, "No device policy max, delay is ${lockAfterDelaySetting}ms")
            return lockAfterDelaySetting
        }

        val screenOffTimeoutSetting =
            systemSettingsRepository
                .getInt(Settings.System.SCREEN_OFF_TIMEOUT, KEYGUARD_DISPLAY_TIMEOUT_DELAY_DEFAULT)
                .coerceAtLeast(0)
                .toLong()
        Log.d(
            TAG,
            "Device policy max set to ${maxTimeToLockDevicePolicy}ms, screen off timeout setting set to ${screenOffTimeoutSetting}ms",
        )

        return (maxTimeToLockDevicePolicy - screenOffTimeoutSetting)
            .coerceIn(minimumValue = 0, maximumValue = lockAfterDelaySetting)
            .also { Log.d(TAG, "Device policy max enforced, delay is ${it}ms") }
    }

    /**
     * Returns true if the lock delay is actually 0, so a delayed lock transforms to an immediate
     * lock. When this is the case, the "lock after screen timeout" timer never starts.
     */
    suspend fun delayLockIsImmediate(): Boolean {
        return lockDelay() <= 0
    }

    /**
     * Determines whether a wakefulness event should start the "lock after screen timeout" timer.
     */
    suspend fun wakefulnessStateTriggersDelayedLock(wakefulness: WakefulnessModel): Boolean {
        if (!wakefulness.isAsleep()) {
            return false
        }

        if (wakefulness.lastSleepReason == WakeSleepReason.TIMEOUT) {
            return true
        }

        if (wakefulness.lastSleepReason == WakeSleepReason.POWER_BUTTON) {
            if (wakefulness.powerButtonLaunchGestureTriggered) {
                // Waking up soon - no need to delay lock.
                return false
            }

            if (authenticationInteractor.getPowerButtonInstantlyLocks()) {
                // Instant lock - no need to delay lock.
                return false
            }
            return true
        }

        return false
    }

    companion object {
        private val TAG = "LockAfterDelayInteractor"

        private const val DELAYED_KEYGUARD_ACTION =
            "com.android.internal.policy.impl.PhoneWindowManager.DELAYED_KEYGUARD"
        private const val DELAYED_LOCK_PROFILE_ACTION =
            "com.android.internal.policy.impl.PhoneWindowManager.DELAYED_LOCK"
        private const val SYSTEMUI_PERMISSION = "com.android.systemui.permission.SELF"
        private const val SEQ_EXTRA_KEY = "count"

        private const val KEYGUARD_LOCK_AFTER_DELAY_DEFAULT = 5000
        private const val KEYGUARD_DISPLAY_TIMEOUT_DELAY_DEFAULT = 30000
    }
}
