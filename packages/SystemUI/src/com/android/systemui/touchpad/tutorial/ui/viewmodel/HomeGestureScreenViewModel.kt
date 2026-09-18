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

package com.android.systemui.touchpad.tutorial.ui.viewmodel

import android.view.MotionEvent
import com.android.systemui.Flags
import com.android.systemui.inputdevice.tutorial.ui.composable.TutorialActionState
import com.android.systemui.res.R
import com.android.systemui.touchpad.tutorial.ui.gesture.GestureState.NotStarted
import com.android.systemui.touchpad.tutorial.ui.gesture.GestureState.PartialSuccess
import com.android.systemui.touchpad.tutorial.ui.gesture.handleTouchpadMotionEvent
import com.android.systemui.util.kotlin.pairwiseBy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HomeGestureScreenViewModel(private val gestureRecognizer: GestureRecognizerAdapter) :
    TouchpadTutorialScreenViewModel {
    // Keeps track of whether a partial success has happened.
    private var hasPartialSuccessOccurred = false

    override val tutorialState: Flow<TutorialActionState> =
        if (Flags.touchpadGestureTutorialBugFixes()) {
            gestureRecognizer.gestureState
                .pairwiseBy(NotStarted) { previous, current ->
                    if (previous is NotStarted) {
                        // Resets the flag each time the tutorial is opened.
                        hasPartialSuccessOccurred = false
                    }
                    if (current is PartialSuccess) {
                        hasPartialSuccessOccurred = true
                    }
                    current to toAnimationProperties()
                }
                .mapToTutorialState()
        } else {
            gestureRecognizer.gestureState
                .map {
                    it to
                        TutorialAnimationProperties(
                            progressStartMarker = "drag with gesture",
                            progressEndMarker = "release playback realtime",
                            successAnimation = R.raw.trackpad_home_success,
                        )
                }
                .mapToTutorialState()
        }

    override fun handleEvent(event: MotionEvent): Boolean {
        return gestureRecognizer.handleTouchpadMotionEvent(event)
    }

    private fun toAnimationProperties(): TutorialAnimationProperties {
        val startMarker =
            if (Flags.touchpadGestureTutorialBugFixes()) {
                if (hasPartialSuccessOccurred) "drag with gesture 2" else "drag with gesture 1"
            } else {
                "drag with gesture"
            }
        return TutorialAnimationProperties(
            progressStartMarker = startMarker,
            progressEndMarker = "release playback realtime",
            successAnimation = R.raw.trackpad_recent_then_home_success,
        )
    }
}
