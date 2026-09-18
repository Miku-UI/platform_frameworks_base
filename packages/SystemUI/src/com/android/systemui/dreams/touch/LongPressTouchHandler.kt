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

import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import com.android.systemui.ambient.touch.TouchHandler
import com.android.systemui.ambient.touch.TouchHandler.TouchSession
import com.android.systemui.dreams.DreamOverlayContainerView
import com.android.systemui.dreams.ui.viewmodel.DreamDialogController
import com.android.systemui.statusbar.VibratorHelper
import javax.inject.Inject

/** [TouchHandler] for long press gestures. */
class LongPressTouchHandler
@Inject
constructor(
    private val vibratorHelper: VibratorHelper,
    private val containerView: DreamOverlayContainerView,
    private val dialogController: DreamDialogController,
    private val logger: DreamTouchHandlerLogger,
) : TouchHandler {
    override fun onSessionStart(session: TouchSession) {
        session.registerGestureListener {
            if (dialogController.showDialog()) {
                session.pilfer()
                logger.logLongPressDetected()
                vibratorHelper.performHapticFeedback(
                    containerView,
                    HapticFeedbackConstants.LONG_PRESS,
                )
            } else {
                logger.logLongPressIgnored()
            }
        }
    }
}

private fun TouchSession.registerGestureListener(onLongPress: (MotionEvent) -> Unit) {
    registerGestureListener(
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) = onLongPress(e)
        }
    )
}
