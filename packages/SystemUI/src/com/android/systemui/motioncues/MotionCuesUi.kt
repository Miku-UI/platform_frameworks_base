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

package com.android.systemui.motioncues

import android.annotation.MainThread;
import android.annotation.SuppressLint
import android.app.motioncues.MotionCuesVisualStyle
import android.app.motioncues.MotionCuesSettings
import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.graphics.drawable.DrawableCompat
import com.android.internal.annotations.GuardedBy
import com.android.internal.annotations.VisibleForTesting
import com.android.systemui.motioncues.nano.MotionCueState
import com.android.systemui.motioncues.nano.MotionBubble
import com.android.systemui.statusbar.policy.ConfigurationController
import kotlin.math.abs
import kotlin.math.min

/**
 * Manages the UI for motion cues, including creating, displaying, and animating a screen overlay.
 *
 * This class is responsible for all aspects of the visual presentation, such as:
 * - Creating a system overlay window to draw on.
 * - Rendering the motion cues (e.g., bubbles) based on provided settings.
 * - Handling updates to the cues' position and appearance based on data from the client service.
 * - Managing the lifecycle of the overlay view.
 *
 * It is designed to be controlled by [MotionCuesManager], which handles the service
 * connection and session logic.
 */
@SuppressLint("ClickableViewAccessibility")
class MotionCuesUi(
    private val context: Context,
    private val windowManager: WindowManager,
    private val configurationController: ConfigurationController
) : ConfigurationController.ConfigurationListener {

    private var clientPackageName: String? = null
    private var userId: Int = 0
    private val paint = Paint()
    private val lock = Any()
    private lateinit var motionCuesSettings: MotionCuesSettings
    private var bubbleShape: Drawable? = null
    private var bubbleShapeResId: Int = 0
    private lateinit var lastConfiguration: Configuration

    @GuardedBy("lock")
    private val motionCues = mutableListOf<MotionCue>()
    var isStarted: Boolean = false
        private set
    private var overlayView: OverlayView? = null
    private lateinit var screenDimensions: Point
    private var bubbleGridWidth = 0f
    private var bubbleGridHeight = 0f
    private var marginLeft = 0
    private var marginRight = 0

    init {
        reset()
    }

    /** Starts the motion cue overlay, updating screen dimensions and creating bubbles. */
    @MainThread
    fun start(settings: MotionCuesSettings, userId: Int, clientPackageName: String) {
        if (isStarted) {
            Log.w(TAG, "MotionBubbles already started. Ignoring call to start.")
            return
        }
        Log.i(TAG, "Starting motion bubbles overlay.")
        isStarted = true
        motionCuesSettings = settings
        this.userId = userId
        this.clientPackageName = clientPackageName
        updateScreenDimensions()
        makeOverlayView()
        makeBubbles()
        lastConfiguration = Configuration(context.resources.configuration)
        configurationController.addCallback(this)
    }

    /** Stops the motion cue overlay and removes the view. */
    @MainThread
    fun stop() {
        if (!isStarted) {
            Log.w(TAG, "Motionbubbles not started. Ignoring call to stop.")
            return
        }
        Log.i(TAG, "Stopping motion bubbles overlay.")
        stopDrawingAndRemoveView()
        reset()
    }

    private fun reset() {
        isStarted = false
        clientPackageName = null
        userId =  UserHandle.USER_SYSTEM
        motionCuesSettings = DEFAULT_MOTION_CUES_SETTINGS
        bubbleShape = null
        bubbleShapeResId = 0
        synchronized(lock) {
            motionCues.clear()
        }
        paint.color = DEFAULT_BUBBLE_COLOR
        configurationController.removeCallback(this)
    }

    /** Updates the position of all motion cues. */
    @MainThread
    fun updateBubblePos(dx: Float, dy: Float) {
        if (!isStarted) {
            Log.w(TAG, "Ignoring updateBubblePos call before UI is started.")
            return
        }
        synchronized(lock) {
            for (motionCue in motionCues) {
                motionCue.x += dx
                motionCue.y += dy
            }
        }
        overlayView?.postInvalidate()
    }

    /** Updates the data used to control the appearance of the motion cues. */
    @MainThread
    fun updateMotionCuesVisualStyle(data: MotionCuesVisualStyle) {
        if (!isStarted) {
            Log.w(TAG, "Ignoring updateMotionCuesVisualStyle call before UI is started.")
            return
        }
        Log.i(TAG, "Updating motion cues data")
        synchronized(lock) {
            paint.color = data.color
            bubbleShape = loadDrawableFromClient(data.shapeRes)
            bubbleShape?.let {
                DrawableCompat.setTint(it, data.color)
                bubbleShapeResId = data.shapeRes
            } ?: run {
                Log.w(
                    TAG,
                    "Failed to load provided fetch drawable ${data.shapeRes}. Using default."
                )
                bubbleShapeResId = 0
            }
        }
        overlayView?.postInvalidate()
    }

    private fun loadDrawableFromClient(drawableResId: Int): Drawable? {
        val pkgName = clientPackageName ?: return null
        return try {
            val clientContext = context.createPackageContextAsUser(pkgName, 0, UserHandle.of(userId))
            clientContext.getDrawable(drawableResId)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Client package not found: $pkgName", e)
            null
        } catch (e: Resources.NotFoundException) {
            Log.e(TAG, "Drawable resource not found in client package: $drawableResId", e)
            null
        }
    }

    private fun stopDrawingAndRemoveView() {
        overlayView?.let {
            try {
                windowManager.removeViewImmediate(it)
                Log.d(TAG, "Overlay view removed.")
            } catch (e: Exception) {
                when (e) {
                    is IllegalStateException, is IllegalArgumentException -> {
                        Log.e(
                            TAG,
                            "Error removing overlay view. isViewAttached: ${it.isAttachedToWindow}",
                            e
                        )
                    }
                    else -> throw e
                }
            } finally {
                overlayView = null
            }
        } ?: Log.d(TAG, "OverlayView is null. Ignoring attempt to stop.")
    }

    private fun updateScreenDimensions() {
        val windowMetrics = windowManager.currentWindowMetrics
        screenDimensions = Point(windowMetrics.bounds.width(), windowMetrics.bounds.height())
    }

    private fun Int.dpToPx(): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    private fun makeBubbles() {
        synchronized(lock) {
            motionCues.clear()
            val bubbleSpacingX = motionCuesSettings.horizontalSpacingDp.dpToPx()
            val bubbleSpacingY = motionCuesSettings.verticalSpacingDp.dpToPx()
            var rowCount = (screenDimensions.y / bubbleSpacingY) + 1
            val colCount = (screenDimensions.x / bubbleSpacingX) + 1
            // Since every odd # row is offset (calculated in the loop below), if we end up with an
            // odd number of rows, the first row and the last row will be an offset row. In the case
            // when the last row needs to be wrapped to the start, we will end up will two offset
            // rows side-by-side, which breaks the expected grid pattern. Therefore, we need to
            // ensure that there are an even number of rows.
            // Columns will not have this issue since we don't offset the columns.
            if (rowCount % 2 != 0) {
                rowCount++
            }
            bubbleGridWidth = (colCount * bubbleSpacingX).toFloat()
            bubbleGridHeight = (rowCount * bubbleSpacingY).toFloat()
            val xOffset = ((bubbleGridWidth - screenDimensions.x) / 2).toInt()
            for (row in 0 until rowCount) {
                for (col in 0 until colCount) {
                    val rowOffset = if (row % 2 == 1) bubbleSpacingX / 2 else 0
                    motionCues.add(
                        MotionCue(
                            (col * bubbleSpacingX + rowOffset - xOffset).toFloat(),
                            (row * bubbleSpacingY).toFloat(),
                            motionCuesSettings.radiusDp.toFloat()
                        )
                    )
                }
            }

            val maxMarginSize = motionCuesSettings.marginSizeDp / 100f
            marginLeft = (screenDimensions.x * maxMarginSize).toInt()
            marginRight = screenDimensions.x - marginLeft
        }
    }

    /** Creates an overlay that renders the motion bubbles over other apps. */
    private fun makeOverlayView() {
        if (overlayView?.isAttachedToWindow == true) {
            // If everything flows correctly, we should never reach here.
            // However, in cases where it does, no-op.
            // Otherwise, we'll end up with multiple overlayview instances (very bad)
            Log.e(TAG, "Skipping attempt to create overlay. One already exists.")
            return
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_VOLUME_OVERLAY,
            // Overlay permission needed
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSPARENT
        ).apply {
            setTrustedOverlay()
            alpha = OVERLAY_WINDOW_ALPHA
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            setFrameRateBoostOnTouchEnabled(false)
            setFrameRatePowerSavingsBalanced(true)
        }
        overlayView = OverlayView(context).also {
            it.setRequestedFrameRate(DRAW_FPS.toFloat())
            windowManager.addView(it, params)
        }
    }

    /**
     * Draws the motion bubbles on the edges of the screen. The bubbles shrink as they get close to
     * the edge of the drawing margin or the top/bottom edges of the screen.
     */
    private fun drawBubbles(canvas: Canvas) {
        synchronized(lock) {
            motionCues.forEach { motionCue ->
                // Wrap around screen edges
                var x = (motionCue.x + bubbleGridWidth) % bubbleGridWidth
                var y = (motionCue.y + bubbleGridHeight) % bubbleGridHeight

                if (x < 0) x += bubbleGridWidth
                if (y < 0) y += bubbleGridHeight

                // Only draw in the margins
                if (x > marginLeft && x < marginRight) {
                    return@forEach
                }

                // Calculate distance from edges
                val distanceFromEdgeX = min(abs(marginRight - x), abs(marginLeft - x))
                val distanceFromEdgeY = min(y, screenDimensions.y - y)
                val distanceFromEdge = min(distanceFromEdgeX, distanceFromEdgeY)

                // Adjust radius based on distance from edge
                var adjustedRadius = motionCuesSettings.radiusDp.toFloat()
                if (distanceFromEdge < EDGE_SHRINK_THRESHOLD) {
                    val shrinkFactor = distanceFromEdge / EDGE_SHRINK_THRESHOLD
                    adjustedRadius *= shrinkFactor
                }

                bubbleShape?.let {
                    val radiusPx = adjustedRadius.toInt()
                    it.setBounds(
                        (x - radiusPx).toInt(),
                        (y - radiusPx).toInt(),
                        (x + radiusPx).toInt(),
                        (y + radiusPx).toInt()
                    )
                    it.draw(canvas)
                } ?: canvas.drawCircle(x, y, adjustedRadius, paint) // Fallback
            }
        }
    }

    override fun onConfigChanged(newConfig: Configuration?) {
        if (newConfig == null) return

        if (lastConfiguration.orientation != newConfig.orientation ||
            lastConfiguration.screenLayout != newConfig.screenLayout
        ) {
            if (isStarted) {
                Log.i(TAG, "Recreating overlay due to orientation/layout change.")
                stopDrawingAndRemoveView()
                updateScreenDimensions()
                makeOverlayView()
                makeBubbles()
            }
        }
        lastConfiguration.updateFrom(newConfig)
    }

    /**
    * Returns a snapshot of the current state of the Motion Cues UI.
    *
    * @return A [MotionCueState] object containing the current debug state
    */
    @VisibleForTesting
    fun getState(): MotionCueState {
        val motionBubbleProtos = synchronized(lock) {
            motionCues.map { cue ->
                val proto = MotionBubble()
                proto.x = cue.x
                proto.y = cue.y
                proto
            }.toTypedArray()
        }

        val state = MotionCueState()
        state.isStarted = this@MotionCuesUi.isStarted
        state.paintColor = String.format("#%08X", paint.color)
        state.bubbleShapeResId = this@MotionCuesUi.bubbleShapeResId
        state.horizontalSpacingDp = motionCuesSettings.horizontalSpacingDp
        state.verticalSpacingDp = motionCuesSettings.verticalSpacingDp
        state.marginSizeDp = motionCuesSettings.marginSizeDp
        state.radiusDp = motionCuesSettings.radiusDp
        state.motionBubbles = motionBubbleProtos
        state.clientPackageName = this@MotionCuesUi.clientPackageName?: ""
        return state
    }

    /**
     * Sets the isStarted property of the Motion Cues UI.
     *
     * @param isStarted The value to set the isStarted property to.
     */
    @VisibleForTesting
    fun setIsStarted(isStarted: Boolean) {
        this.isStarted = isStarted
    }

    /**
     * Sets the clientPackageName property of the Motion Cues UI.
     *
     * @param clientPackageName The value to set the clientPackageName property to.
     */
    @VisibleForTesting
    fun setClientPackageName(clientPackageName: String) {
        this.clientPackageName = clientPackageName
    }

    /** A view that can draw on a given canvas. */
    private inner class OverlayView(context: Context) : View(context) {
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            drawBubbles(canvas)
        }
    }

    companion object {
        private const val TAG = "MotionCuesUi"
        // In sdk = 31, touches are blocked as part of anything above 0.8
        // alpha. To resolve this, we need to ensure we're below that threshold. See ag/17071470 for
        // more info.
        const val OVERLAY_WINDOW_ALPHA = 0.8f
        const val EDGE_SHRINK_THRESHOLD = 50
        private const val DRAW_FPS = 30L
        private const val DEFAULT_BUBBLE_COLOR = Color.GRAY
        private val DEFAULT_MOTION_CUES_SETTINGS =
            MotionCuesSettings.Builder()
                .setHorizontalSpacingDp(60)
                .setVerticalSpacingDp(140)
                .setMarginSizeDp(20)
                .setRadiusDp(15)
                .build()
    }
}
