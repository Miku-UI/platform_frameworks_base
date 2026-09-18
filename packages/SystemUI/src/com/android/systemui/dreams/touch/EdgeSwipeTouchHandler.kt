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

package com.android.systemui.dreams.touch

import android.content.Context
import android.os.Handler
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import com.android.systemui.ambient.touch.TouchHandler
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.dreams.DreamOverlayContainerView
import com.android.systemui.navigationbar.gestural.GestureNavigationSettingsObserverFactory
import com.android.systemui.res.R
import com.android.systemui.settings.UserContextProvider
import com.android.systemui.statusbar.VibratorHelper
import javax.inject.Inject

/** A [TouchHandler] that intercepts edge swipes and forwards them to a [DreamSwipeDelegate]. */
class EdgeSwipeTouchHandler
@Inject
constructor(
    private val swipeDelegate: DreamSwipeDelegate,
    private val containerView: DreamOverlayContainerView,
    @param:Main private val mainHandler: Handler,
    @param:Background private val backgroundHandler: Handler,
    @Application context: Context,
    private val userContextProvider: UserContextProvider,
    private val vibratorHelper: VibratorHelper,
    private val logger: DreamTouchHandlerLogger,
    gestureNavigationSettingsObserverFactory: GestureNavigationSettingsObserverFactory,
) : TouchHandler {

    private var edgeWidthLeft: Float = 0f
    private var edgeWidthRight: Float = 0f
    private var velocityTracker: VelocityTracker? = null

    private val gestureNavigationSettingsObserver =
        gestureNavigationSettingsObserverFactory.create(
            mainHandler,
            backgroundHandler,
            context,
            this::updateEdgeWidths,
        )

    init {
        updateEdgeWidths()
        gestureNavigationSettingsObserver.register()
    }

    override fun onDestroy() {
        gestureNavigationSettingsObserver.unregister()
        velocityTracker?.recycle()
        velocityTracker = null
    }

    override fun onSessionStart(session: TouchHandler.TouchSession) {
        var startX = 0f
        var isLeftEdge = false
        var isRightEdge = false
        var swipeThreshold = 0f
        var hapticPerformed = false
        var gestureAccepted = false
        var activePointerId = MotionEvent.INVALID_POINTER_ID

        session.registerGestureListener(
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean {
                    gestureAccepted = false
                    isLeftEdge = e.x < edgeWidthLeft
                    isRightEdge = e.x > containerView.width - edgeWidthRight

                    if (!isLeftEdge && !isRightEdge) {
                        logger.logEdgeSwipeIgnoredNonEdge(
                            e.x,
                            e.y,
                            edgeWidthLeft,
                            edgeWidthRight,
                            containerView.width,
                        )
                        return false
                    }

                    val isClaimed =
                        swipeDelegate.onSwipeStarted(isFromLeft = isLeftEdge, startY = e.y)
                    if (!isClaimed) {
                        logger.logEdgeSwipeRefused(e.x, e.y)
                        return false
                    }

                    gestureAccepted = true
                    startX = e.x
                    activePointerId = e.getPointerId(0)
                    swipeThreshold =
                        if (isLeftEdge) {
                            edgeWidthLeft * 2f
                        } else {
                            edgeWidthRight * 2f
                        }
                    hapticPerformed = false

                    velocityTracker?.recycle()
                    velocityTracker = VelocityTracker.obtain()
                    velocityTracker?.addMovement(e)

                    logger.logEdgeSwipeDown(e.x, e.y, isLeftEdge, isRightEdge, swipeThreshold)

                    return true
                }

                override fun onScroll(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    distanceX: Float,
                    distanceY: Float,
                ): Boolean {
                    if (!isLeftEdge && !isRightEdge || !gestureAccepted || e1 == null) {
                        return false
                    }

                    velocityTracker?.addMovement(e2)

                    val dx = e2.x - startX
                    swipeDelegate.onSwipeProgress(dx, swipeThreshold)

                    // The distance towards the center of the screen
                    val distance = if (isLeftEdge) dx else -dx

                    if (distance > swipeThreshold && !hapticPerformed) {
                        logger.logEdgeSwipeHapticPerformed()
                        vibratorHelper.performHapticFeedback(
                            containerView,
                            HapticFeedbackConstants.SEGMENT_TICK,
                        )
                        hapticPerformed = true
                    }

                    return true
                }
            }
        )

        session.registerInputListener { ev ->
            if (
                ev is MotionEvent &&
                    (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL)
            ) {
                velocityTracker?.addMovement(ev)
                if ((isLeftEdge || isRightEdge) && gestureAccepted) {
                    val dx = ev.x - startX
                    val distance = if (isLeftEdge) dx else -dx
                    val committed = ev.action == MotionEvent.ACTION_UP && distance > swipeThreshold

                    velocityTracker?.computeCurrentVelocity(1000)
                    val velocityX = velocityTracker?.getXVelocity(activePointerId) ?: 0f

                    logger.logEdgeSwipeEnded(ev.action, dx, committed)
                    swipeDelegate.onSwipeEnded(committed, velocityX)

                    isLeftEdge = false
                    isRightEdge = false
                    hapticPerformed = false
                    gestureAccepted = false
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                }

                velocityTracker?.recycle()
                velocityTracker = null
            }
        }
    }

    private fun updateEdgeWidths() {
        val userResources = userContextProvider.userContext.resources
        val defaultEdgeWidth =
            userResources
                .getDimensionPixelSize(R.dimen.dream_switcher_swipe_edge_width_default)
                .toFloat()

        edgeWidthLeft =
            gestureNavigationSettingsObserver.getLeftSensitivity(userResources).toFloat()
        if (edgeWidthLeft == 0f) {
            edgeWidthLeft = defaultEdgeWidth
        }

        edgeWidthRight =
            gestureNavigationSettingsObserver.getRightSensitivity(userResources).toFloat()
        if (edgeWidthRight == 0f) {
            edgeWidthRight = defaultEdgeWidth
        }
    }
}
