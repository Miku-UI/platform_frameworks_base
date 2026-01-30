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

package com.android.systemui.statusbar.events

import android.util.Log
import android.view.Display
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.InvalidDisplayException
import android.view.WindowManager.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import com.android.systemui.ScreenDecorations
import com.android.systemui.ScreenDecorationsThread
import com.android.systemui.decor.DecorProvider
import com.android.systemui.decor.PrivacyDotCornerDecorProviderImpl
import com.android.systemui.decor.PrivacyDotDecorProviderFactory
import com.android.systemui.statusbar.events.PrivacyDotCorner.BottomLeft
import com.android.systemui.statusbar.events.PrivacyDotCorner.BottomRight
import com.android.systemui.statusbar.events.PrivacyDotCorner.TopLeft
import com.android.systemui.statusbar.events.PrivacyDotCorner.TopRight
import com.android.systemui.util.containsExactly
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.concurrent.Executor

/**
 * Responsible for adding the privacy dot to a window.
 *
 * It will create one window per corner (top left, top right, bottom left, bottom right), which are
 * used dependant on the display's rotation.
 */
class PrivacyDotWindowController
@AssistedInject
constructor(
    @Assisted private val displayId: Int,
    @Assisted private val privacyDotViewController: PrivacyDotViewController,
    @Assisted private val windowManager: WindowManager,
    @Assisted private val inflater: LayoutInflater,
    @ScreenDecorationsThread private val uiExecutor: Executor,
    private val dotFactory: PrivacyDotDecorProviderFactory,
) {
    private val dotWindowViewsByCorner = mutableMapOf<PrivacyDotCorner, View>()
    private var displayRotationOnStartup = 0

    fun start() {
        uiExecutor.execute { startOnUiThread() }
    }

    private fun startOnUiThread() {
        displayRotationOnStartup = inflater.context.display.rotation

        val providers = dotFactory.providers

        val topLeftContainer = providers.inflate(TopLeft)
        val topRightContainer = providers.inflate(TopRight)
        val bottomLeftContainer = providers.inflate(BottomLeft)
        val bottomRightContainer = providers.inflate(BottomRight)

        val dotViewContainersByView =
            mapOf(
                topLeftContainer.dotView to topLeftContainer,
                topRightContainer.dotView to topRightContainer,
                bottomLeftContainer.dotView to bottomLeftContainer,
                bottomRightContainer.dotView to bottomRightContainer,
            )

        privacyDotViewController.showingListener =
            object : PrivacyDotViewController.ShowingListener {

                override fun onPrivacyDotShown(v: View?) {
                    val dotViewContainer = dotViewContainersByView[v]
                    if (v == null || dotViewContainer == null) {
                        return
                    }
                    v.addToWindow(dotViewContainer.corner)
                    dotWindowViewsByCorner[dotViewContainer.corner] = dotViewContainer.windowView
                }

                override fun onPrivacyDotHidden(v: View?) {
                    val dotViewContainer = dotViewContainersByView[v]
                    val windowView = dotWindowViewsByCorner.remove(dotViewContainer?.corner)
                    if (windowView != null) {
                        windowManager.removeViewSafely(windowView)
                    }
                }
            }
        privacyDotViewController.initialize(
            topLeftContainer.dotView,
            topRightContainer.dotView,
            bottomLeftContainer.dotView,
            bottomRightContainer.dotView,
        )
    }

    private fun List<DecorProvider>.inflate(corner: PrivacyDotCorner): DotViewContainer {
        val provider =
            first { it.alignedBounds.containsExactly(corner.alignedBound1, corner.alignedBound2) }
                as PrivacyDotCornerDecorProviderImpl
        val dotView = inflater.inflate(/* resource= */ provider.layoutId, /* root= */ null)
        // PrivacyDotViewController expects the dot view to have a FrameLayout parent.
        val windowView = FrameLayout(dotView.context)
        windowView.addView(dotView)
        return DotViewContainer(windowView, dotView, corner)
    }

    private fun View.addToWindow(corner: PrivacyDotCorner) {
        val excludeFromScreenshots = displayId == Display.DEFAULT_DISPLAY
        val params =
            ScreenDecorations.getWindowLayoutBaseParams(excludeFromScreenshots).apply {
                width = WRAP_CONTENT
                height = WRAP_CONTENT
                gravity = corner.rotatedCorner(displayRotationOnStartup).gravity
                title = "PrivacyDot${corner.title}$displayId"
            }
        try {
            // Wrapping this in a try/catch to avoid crashes when a display is instantly removed
            // after being added, and initialization hasn't finished yet.
            windowManager.addView(rootView, params)
        } catch (e: InvalidDisplayException) {
            Log.e(
                TAG,
                "Unable to add view to WM. Display with id $displayId does not exist anymore",
                e,
            )
        }
        return
    }

    fun stop() {
        uiExecutor.execute {
            dotWindowViewsByCorner.forEach { windowManager.removeViewSafely(it.value) }
        }
    }

    private data class DotViewContainer(
        val windowView: View,
        val dotView: View,
        val corner: PrivacyDotCorner,
    )

    @AssistedFactory
    fun interface Factory {
        fun create(
            displayId: Int,
            privacyDotViewController: PrivacyDotViewController,
            windowManager: WindowManager,
            inflater: LayoutInflater,
        ): PrivacyDotWindowController
    }

    private fun WindowManager.removeViewSafely(view: View) {
        try {
            removeView(view)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Failed to remove view from window manager.")
        }
    }

    private companion object {
        const val TAG = "PrivacyDotWindowController"
    }
}
