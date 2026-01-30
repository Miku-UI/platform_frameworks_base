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

package com.android.keyguard

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Display
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.android.systemui.keyevent.domain.interactor.SysUIKeyEventHandler
import com.android.systemui.plugins.keyguard.ui.clocks.ClockController
import com.android.systemui.plugins.keyguard.ui.clocks.ClockFaceController
import com.android.systemui.plugins.keyguard.ui.clocks.ClockFaceLayout
import com.android.systemui.res.R
import com.android.systemui.shared.clocks.ClockRegistry
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.DisposableHandle

/** Creates a keyguard presentation for a [Display]. */
fun interface ConnectedDisplayKeyguardPresentationFactory {
    fun create(display: Display): Presentation
}

/** [Presentation] shown in connected displays while on keyguard. */
class ConnectedDisplayConstraintLayoutKeyguardPresentation
@AssistedInject
constructor(
    @Assisted display: Display,
    context: Context,
    private val clockRegistry: ClockRegistry,
    private val clockEventController: ClockEventController,
    private val sysuiKeyEventHandler: SysUIKeyEventHandler,
) :
    Presentation(
        context,
        display,
        R.style.Theme_SystemUI_KeyguardPresentation,
        WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG,
    ) {

    private lateinit var constraintLayoutRootView: ConstraintLayout
    private lateinit var faceController: ClockFaceController
    private var bindHandle: DisposableHandle? = null

    private val clockChangedListener =
        object : ClockRegistry.ClockChangeListener {
            override fun onCurrentClockChanged() {
                setClock(constraintLayoutRootView, clockRegistry.createCurrentClock(context))
            }

            override fun onAvailableClocksChanged() {}
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onCreateInternal()

        setShowWallpaperFlagOnWindow()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return sysuiKeyEventHandler.dispatchKeyEvent(event)
    }

    private fun onCreateInternal() {
        constraintLayoutRootView =
            ConstraintLayout(context).apply {
                layoutParams =
                    ConstraintLayout.LayoutParams(
                        ConstraintLayout.LayoutParams.MATCH_PARENT,
                        ConstraintLayout.LayoutParams.MATCH_PARENT,
                    )
                clipChildren = false
            }

        setContentView(constraintLayoutRootView)

        setFullscreen()

        setClock(constraintLayoutRootView, clockRegistry.createCurrentClock(context))
    }

    private fun setShowWallpaperFlagOnWindow() {
        val window = window ?: error("No window available")
        window.attributes =
            window.attributes.apply {
                flags = flags or WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER
            }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        clockRegistry.registerClockChangeListener(clockChangedListener)
        clockEventController.registerListeners()
        bindHandle = clockEventController.bind(constraintLayoutRootView)
        faceController.animations.enter()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        clockEventController.unregisterListeners()
        bindHandle?.dispose()
        clockRegistry.unregisterClockChangeListener(clockChangedListener)
    }

    override fun onDisplayChanged() {
        val window = window ?: error("no window available.")
        window.decorView.requestLayout()
    }

    private fun setClock(rootView: ConstraintLayout, clockController: ClockController) {
        clockEventController.clock = clockController
        clockEventController.setLargeClockOnSecondaryDisplay(true)
        faceController = clockController.largeClock
        faceController.events.onSecondaryDisplayChanged(true)

        rootView.removeAllViews()
        addClockLayoutConstraints(rootView, clockController.largeClock.layout)
    }

    private fun addClockLayoutConstraints(
        rootView: ConstraintLayout,
        clockLayout: ClockFaceLayout,
    ) {
        val cs = ConstraintSet().apply { clone(rootView) }
        clockLayout.views.forEach { view ->
            rootView.addView(view)
            cs.setVisibility(view.id, View.VISIBLE)
        }
        clockLayout.applyExternalDisplayPresentationConstraints(cs)
        cs.applyTo(rootView)
    }

    private fun setFullscreen() {
        window?.apply {
            // Logic to make the lock screen fullscreen
            decorView.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
            attributes.fitInsetsTypes = 0
            isNavigationBarContrastEnforced = false
            navigationBarColor = Color.TRANSPARENT
        } ?: error("No window available")
    }

    /** [ConnectedDisplayConstraintLayoutKeyguardPresentation] factory. */
    @AssistedFactory
    interface Factory {
        /** Creates a new [Presentation] for the given [display]. */
        fun create(display: Display): ConnectedDisplayConstraintLayoutKeyguardPresentation
    }
}
