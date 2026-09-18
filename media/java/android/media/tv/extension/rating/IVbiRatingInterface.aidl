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

import android.media.tv.extension.rating.IVbiRatingListener;

/**
 * @hide
 */
interface IVbiRatingInterface {
    /**
     * Gets the VBI rating for a given session.
     *
     * @param sessionToken A token used to identify the related session to retrieve the VBI rating.
     * @return A flattened string representation of the TV content rating via
     *        {@link android.media.tv.TvContentRating#flattenToString()}.
     */
    String getVbiRating(String sessionToken);
    /**
     * Registers a listener to receive updates for VBI rating changes.
     *
     * @param clientToken A token to uniquely identify the client registering the listener.
     * @param listener    The VbiRatingListener to be called when the VBI rating is updated.
     */
    void addVbiRatingListener(String clientToken, in IVbiRatingListener listener);
    /**
     * Removes a previously registered listener for VBI rating change events.
     *
     * @param listener The VbiRatingListener that was previously added and should now be removed.
     */
    void removeVbiRatingListener(in IVbiRatingListener listener);
}
