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

package com.android.systemui.topwindoweffects.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.input.InputManager
import android.hardware.input.KeyGestureEvent
import android.os.Bundle
import android.os.SystemProperties
import android.provider.Settings.Global.POWER_BUTTON_LONG_PRESS_DURATION_MS
import com.android.internal.annotations.VisibleForTesting
import com.android.systemui.assist.AssistManager
import com.android.systemui.common.coroutine.ChannelExt.trySendWithFailureLogging
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.shared.Flags
import com.android.systemui.topwindoweffects.data.repository.InvocationEffectPreferencesImpl.Companion.DEFAULT_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT_PREFERENCE
import com.android.systemui.topwindoweffects.data.repository.InvocationEffectPreferencesImpl.Companion.DEFAULT_INWARD_EFFECT_PADDING_DURATION_MS
import com.android.systemui.topwindoweffects.data.repository.InvocationEffectPreferencesImpl.Companion.DEFAULT_OUTWARD_EFFECT_DURATION_MS
import com.android.systemui.topwindoweffects.data.repository.InvocationEffectPreferencesImpl.Companion.INVOCATION_EFFECT_ANIMATION_IN_DURATION_PADDING_MS
import com.android.systemui.topwindoweffects.data.repository.InvocationEffectPreferencesImpl.Companion.INVOCATION_EFFECT_ANIMATION_OUT_DURATION_MS
import com.android.systemui.topwindoweffects.data.repository.InvocationEffectPreferencesImpl.Companion.IS_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT
import com.android.systemui.util.settings.GlobalSettings
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import java.io.PrintWriter
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.math.max
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@SysUISingleton
class SqueezeEffectRepositoryImpl
@Inject
constructor(
    @Application private val context: Context,
    private val globalSettings: GlobalSettings,
    private val inputManager: InputManager,
    @Background coroutineContext: CoroutineContext,
    @Background executor: Executor,
    private val preferences: InvocationEffectPreferences,
) : SqueezeEffectRepository, InvocationEffectSetUiHintsHandler, InvocationEffectEnabler {

    override val isSqueezeEffectHapticEnabled = Flags.enableLppAssistInvocationHapticEffect()

    private fun getLongPressPowerDurationFromSettings() =
        globalSettings
            .getInt(
                POWER_BUTTON_LONG_PRESS_DURATION_MS,
                context.resources.getInteger(
                    com.android.internal.R.integer.config_longPressOnPowerDurationMs
                ),
            )
            .toLong()

    override fun getInvocationEffectInitialDelayMillis(): Long {
        return DEFAULT_INITIAL_DELAY_MILLIS +
            max(
                0,
                getLongPressPowerDurationFromSettings() - DEFAULT_LONG_PRESS_POWER_DURATION_MILLIS,
            )
    }

    override fun getInvocationEffectInAnimationDurationMillis(): Long {
        return preferences.getInwardAnimationPaddingDurationMillis() +
            getLongPressPowerDurationFromSettings() - getInvocationEffectInitialDelayMillis()
    }

    override fun getInvocationEffectOutAnimationDurationMillis(): Long {
        return preferences.getOutwardAnimationDurationMillis()
    }

    override fun useHapticRumble(): Boolean {
        val hapticsOption =
            SystemProperties.get(
                /*key=*/ "persist.lpp_invocation.haptics",
                /*def=*/ "no_rumble",
            )
        return hapticsOption == "with_rumble"
    }

    private fun setInvocationEffectPreferences(
        isEnabled: Boolean? = null,
        inwardsEffectDurationPadding: Long? = null,
        outwardsEffectDuration: Long? = null,
    ) {

        preferences.setInvocationEffectConfig(
            config =
                InvocationEffectPreferences.Config(
                    isEnabled = isEnabled ?: preferences.isInvocationEffectEnabledInPreferences(),
                    inwardsEffectDurationPadding =
                        inwardsEffectDurationPadding
                            ?: preferences.getInwardAnimationPaddingDurationMillis(),
                    outwardsEffectDuration =
                        outwardsEffectDuration ?: preferences.getOutwardAnimationDurationMillis(),
                ),
            saveActiveUserAndAssistant = !preferences.isCurrentUserAndAssistantPersisted(),
        )
    }

    override fun tryHandleSetUiHints(hints: Bundle): Boolean {
        return when (hints.getString(AssistManager.ACTION_KEY)) {
            SET_INVOCATION_EFFECT_PARAMETERS_ACTION -> {

                val isEnabled: Boolean? =
                    if (hints.containsKey(IS_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT)) {
                        hints.getBoolean(
                            IS_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT,
                            DEFAULT_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT_PREFERENCE,
                        )
                    } else {
                        null
                    }

                val inwardsEffectDurationPadding: Long? =
                    if (hints.containsKey(INVOCATION_EFFECT_ANIMATION_IN_DURATION_PADDING_MS)) {
                        hints.getLong(
                            INVOCATION_EFFECT_ANIMATION_IN_DURATION_PADDING_MS,
                            DEFAULT_INWARD_EFFECT_PADDING_DURATION_MS,
                        )
                    } else {
                        null
                    }

                val outwardsEffectDuration: Long? =
                    if (hints.containsKey(INVOCATION_EFFECT_ANIMATION_OUT_DURATION_MS)) {
                        hints.getLong(
                            INVOCATION_EFFECT_ANIMATION_OUT_DURATION_MS,
                            DEFAULT_OUTWARD_EFFECT_DURATION_MS,
                        )
                    } else {
                        null
                    }

                setInvocationEffectPreferences(
                    isEnabled = isEnabled,
                    inwardsEffectDurationPadding = inwardsEffectDurationPadding,
                    outwardsEffectDuration = outwardsEffectDuration,
                )

                true
            }
            else -> false
        }
    }

    override fun setEnabled(enabled: Boolean) {
        setInvocationEffectPreferences(isEnabled = enabled)
    }

    private val _isPowerButtonLongPressed = MutableStateFlow(false)
    override val isPowerButtonLongPressed = _isPowerButtonLongPressed.asStateFlow()

    private var isPowerButtonDownAndPowerKeySingleGestureActive = false

    override val isEffectEnabled: Flow<Boolean> =
        preferences.isInvocationEffectEnabledByAssistant
            .map { it && Flags.enableLppAssistInvocationEffect() }
            .flowOn(coroutineContext)
            .distinctUntilChanged()

    @SuppressLint("MissingPermission")
    override val isPowerButtonPressedAsSingleGesture: Flow<Boolean> =
        conflatedCallbackFlow {
                val listener =
                    InputManager.KeyGestureEventListener { event ->
                        updateIsPowerButtonDownAndSingleGestureActive(event)
                        trySendWithFailureLogging(
                            isPowerButtonDownAndPowerKeySingleGestureActive,
                            TAG,
                            "updated showInvocationEffect",
                        )
                    }
                trySendWithFailureLogging(false, TAG, "init showInvocationEffect")
                inputManager.registerKeyGestureEventListener(executor, listener)
                awaitClose { inputManager.unregisterKeyGestureEventListener(listener) }
            }
            .flowOn(coroutineContext)
            .distinctUntilChanged()

    private fun updateIsPowerButtonDownAndSingleGestureActive(event: KeyGestureEvent) {
        _isPowerButtonLongPressed.value =
            if (event.isGestureTypeAssistant()) {
                event.isGestureComplete()
            } else {
                _isPowerButtonLongPressed.value && isPowerButtonDownAndPowerKeySingleGestureActive
            }

        isPowerButtonDownAndPowerKeySingleGestureActive =
            if (isPowerButtonDownAndPowerKeySingleGestureActive) {
                !(event.isGestureTypeAssistant() &&
                    (event.isGestureCancelled() || event.isGestureComplete()))
            } else {
                event.isGestureTypeAssistant() && event.isGestureStart()
            }
    }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        pw.println("$TAG:")
        pw.println("  isPowerButtonLongPressed=${_isPowerButtonLongPressed.value}")
        pw.println(
            "  isPowerButtonDownAndPowerKeySingleGestureActive=$isPowerButtonDownAndPowerKeySingleGestureActive"
        )
        pw.println("  isSqueezeEffectHapticEnabled=$isSqueezeEffectHapticEnabled")
        pw.println(
            "  longPressPowerDurationFromSettings=${getLongPressPowerDurationFromSettings()}"
        )
        pw.println(
            "  invocationEffectInitialDelayMillis=${getInvocationEffectInitialDelayMillis()}"
        )
        pw.println(
            "  invocationEffectInAnimationDurationMillis=${getInvocationEffectInAnimationDurationMillis()}"
        )
        pw.println(
            "  invocationEffectOutAnimationDurationMillis=${getInvocationEffectOutAnimationDurationMillis()}"
        )
        preferences.dump(pw, args)
    }

    companion object {
        private const val TAG = "SqueezeEffectRepository"

        /**
         * Current default timeout for detecting key combination is 150ms (as mentioned in
         * [KeyCombinationManager.COMBINE_KEY_DELAY_MILLIS]). Power key combinations don't have any
         * specific value defined yet for this timeout and they use this default timeout 150ms.
         * We're keeping this value of initial delay as 150ms because:
         * 1. Invocation effect doesn't show up in screenshots
         * 2. [TopLevelWindowEffects] window isn't created if power key combination is detected
         */
        @VisibleForTesting const val DEFAULT_INITIAL_DELAY_MILLIS = 150L
        @VisibleForTesting const val DEFAULT_LONG_PRESS_POWER_DURATION_MILLIS = 500L
        @VisibleForTesting const val DEFAULT_INWARD_EFFECT_DURATION_MILLIS = 800L // in milliseconds

        @VisibleForTesting
        const val SET_INVOCATION_EFFECT_PARAMETERS_ACTION = "set_invocation_effect_parameters"
    }
}

private fun KeyGestureEvent.isGestureTypeAssistant() =
    this.keyGestureType == KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_ASSISTANT

private fun KeyGestureEvent.isGestureComplete() =
    this.action == KeyGestureEvent.ACTION_GESTURE_COMPLETE && !this.isCancelled

private fun KeyGestureEvent.isGestureCancelled() =
    this.action == KeyGestureEvent.ACTION_GESTURE_COMPLETE && this.isCancelled

private fun KeyGestureEvent.isGestureStart() = this.action == KeyGestureEvent.ACTION_GESTURE_START
