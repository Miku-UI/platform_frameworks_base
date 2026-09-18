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
package com.android.systemui.lowlightclock

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.util.Log
import android.view.View
import android.widget.TextClock
import android.widget.TextView
import com.android.dream.lowlight.LowLightTransitionCoordinator
import com.android.systemui.res.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.Optional
import javax.inject.Provider

/** Implementation of [LowLightClockDreamService]. */
class LowLightClockDreamServiceImpl
@AssistedInject
constructor(
    @Assisted private val delegate: DreamServiceDelegate,
    private val chargingStatusProvider: ChargingStatusProvider,
    private val animationProvider: LowLightClockAnimationProvider,
    private val lowLightTransitionCoordinator: LowLightTransitionCoordinator,
    optionalDisplayController: Optional<Provider<LowLightDisplayController>>,
) : LowLightTransitionCoordinator.LowLightExitListener {

    private val displayController: LowLightDisplayController? by lazy {
        optionalDisplayController.map { it.get() }.orElse(null)
    }

    private var isDimBrightnessSupported = false
    private lateinit var chargingStatusTextView: TextView
    private lateinit var textClock: TextClock
    private var animationIn: Animator? = null
    private var animationOut: Animator? = null

    fun onAttachedToWindow() {
        delegate.isInteractive = false
        delegate.isFullscreen = true

        delegate.setContentView(R.layout.low_light_clock_dream)

        textClock = delegate.findViewById(R.id.low_light_text_clock)!!
        chargingStatusTextView = delegate.findViewById(R.id.charging_status_text_view)!!

        chargingStatusProvider.startUsing(this::updateChargingMessage)
        lowLightTransitionCoordinator.setLowLightExitListener(this)
    }

    fun onDreamingStarted() {
        animationIn = animationProvider.provideAnimationIn(textClock, chargingStatusTextView)
        animationIn?.start()

        isDimBrightnessSupported = displayController?.isDisplayBrightnessModeSupported() == true
        if (isDimBrightnessSupported) {
            Log.v(TAG, "setting dim brightness state")
            displayController?.setDisplayBrightnessModeEnabled(true)
        } else {
            Log.v(TAG, "dim brightness not supported. setting screen brightness to minimum")
            delegate.setScreenBrightness(0f)
        }
    }

    fun onDreamingStopped() {
        if (isDimBrightnessSupported) {
            Log.v(TAG, "clearing dim brightness state")
            displayController?.setDisplayBrightnessModeEnabled(false)
        }
    }

    fun onWakeUp() {
        animationIn?.cancel()
        animationOut = animationProvider.provideAnimationOut(textClock, chargingStatusTextView)
        animationOut?.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animator: Animator) {
                    delegate.finish()
                }
            }
        )
        animationOut?.start()
    }

    fun onDetachedFromWindow() {
        animationOut?.cancel()
        chargingStatusProvider.stopUsing()
        lowLightTransitionCoordinator.setLowLightExitListener(null)
    }

    private fun updateChargingMessage(showChargingStatus: Boolean, chargingStatusMessage: String) {
        chargingStatusTextView.text = chargingStatusMessage
        chargingStatusTextView.visibility = if (showChargingStatus) View.VISIBLE else View.INVISIBLE
    }

    override fun onBeforeExitLowLight(): Animator? {
        animationOut = animationProvider.provideAnimationOut(textClock, chargingStatusTextView)
        animationOut?.start()

        // Return the animator so that the transition coordinator waits for the low light exit
        // animations to finish before entering low light, as otherwise the default DreamActivity
        // animation plays immediately and there's no time for this animation to play.
        return animationOut
    }

    @AssistedFactory
    interface Factory {
        fun create(delegate: DreamServiceDelegate): LowLightClockDreamServiceImpl
    }

    companion object {
        private const val TAG = "LowLightClockDreamService"
    }
}
