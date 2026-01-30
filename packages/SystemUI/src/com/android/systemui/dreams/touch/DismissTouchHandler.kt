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
package com.android.systemui.dreams.touch

import android.view.GestureDetector.SimpleOnGestureListener
import android.view.InputEvent
import android.view.MotionEvent
import com.android.systemui.ambient.touch.TouchHandler
import com.android.systemui.ambient.touch.TouchHandler.TouchSession

/** A simple {@link TouchHandler} that consumes touch down and informs callback on touch up. */
class DismissTouchHandler(val dismissCallback: DismissCallback) : TouchHandler {
    override fun onSessionStart(session: TouchSession) {
        session.registerGestureListener(
            object : SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean {
                    session.registerInputListener { ev: InputEvent? ->
                        if (ev !is MotionEvent) {
                            return@registerInputListener
                        }

                        if (
                            ev.action == MotionEvent.ACTION_CANCEL ||
                                ev.action == MotionEvent.ACTION_UP
                        ) {
                            session.pop()

                            if (ev.action == MotionEvent.ACTION_UP) {
                                dismissCallback.onDismissed()
                            }
                        }
                    }
                    return true
                }
            }
        )
    }

    /** Callback to be informed of dismiss event. */
    interface DismissCallback {
        /** Invoked on dismiss action. */
        fun onDismissed()
    }
}
