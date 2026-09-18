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

package android.media.tv.extension.rating;

import android.media.tv.extension.rating.IProgramRatingInfoListener;
import android.os.Bundle;

/**
 * @hide
 */
interface IProgramRatingInfo {
    /**
     * Registers a listener to receive notifications when ProgramInfo is updated.
     *
     * @param clientToken A token used to uniquely identify the client registering the listener.
     * @param listener    The listener to be called when ProgramInfo's content rating updated.
     */
    void addProgramRatingInfoListener(String clientToken, in IProgramRatingInfoListener listener);
    /**
     * Removes a previously registered listener for ProgramInfo update notifications.
     *
     * @param listener The listener that was previously registered to monitor ProgramInfo updates
     *                 and should now be removed.
     */
    void removeProgramRatingInfoListener(in IProgramRatingInfoListener listener);
    /**
     * Gets the ProgramInfo, which contains content rating information for the current program.
     * This information can only be retrieved during an active viewing session.
     *
     * @param sessionToken The token that associates this request with a specific viewing session.
     * @return A Bundle containing the program's rating information, or null if it is not available.
     *         The Bundle must contain the following key:
     *         <ul><li>
     *         KEY_CONTENT_RATINGS: The content ratings applicable to the current channel.
     *         </li></ul>
     */
    Bundle getProgramRatingInfo(String sessionToken);
}
