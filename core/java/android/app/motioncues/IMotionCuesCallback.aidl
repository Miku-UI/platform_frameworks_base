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
package android.app.motioncues;

import android.app.motioncues.MotionCuesVisualStyle;
import android.app.motioncues.MotionCuesSettings;

/**
 * Callback interface used by the Motion Cues service to send data back to the
 * rendering component within the System UI.
 *
 * <p>The rendering component that initiates a motion cues session by binding to the
 * {@link MotionCuesService} must provide an implementation of this interface. The
 * client, running in a separate system process, will invoke these methods to
 * provide real-time updates for rendering motion cues.
 *
 * @hide
 */
oneway interface IMotionCuesCallback {
    /**
         * Called frequently when the motion cue's target position changes.
         * This provides the delta in screen pixels from the last known position.
         * The implementing component should use these deltas to animate the cues.
         * This method is expected to be called at a high frequency (e.g., 30-60fps)
         * during active cue movement.
         *
         * @param dx The change in position along the X-axis in pixels.
         * @param dy The change in position along the Y-axis in pixels.
         */
        void updateBubblePixelPos(in float dx, in float dy);

        /**
         * Called to update the visual properties of all active motion cues.
         * This can be used to change appearance aspects like color or shape
         * after the session has been initialized.
         *
         * @param motionCuesVisualStyle An object containing the new visual properties to apply.
         */
        void updateMotionCuesVisualStyle(in MotionCuesVisualStyle motionCuesVisualStyle);
}
