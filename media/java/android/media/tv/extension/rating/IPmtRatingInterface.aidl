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

import android.media.tv.extension.rating.IPmtRatingListener;

/**
 * @hide
 */
interface IPmtRatingInterface {
    /**
     * Gets the PMT (Program Map Table) rating information for a given session.
     *
     * @param sessionToken A token used to identify the related session to retrieve the PMT rating.
     * @return A flattened string representation of the TV content rating via
     *         {@link android.media.tv.TvContentRating#flattenToString()}.
     */
    String getPmtRating(String sessionToken);
    /**
     * Registers a listener to receive updates for PMT rating changes.
     *
     * @param clientToken A token to uniquely identify the client registering the listener.
     * @param listener The IPmtRatingListener to be called when the PMT rating is updated.
     */
    void addPmtRatingListener(String clientToken, in IPmtRatingListener listener);
    /**
     * Removes a previously registered listener for PMT rating change events.
     *
     * @param listener The IPmtRatingListener that was previously added and should now be removed.
     */
    void removePmtRatingListener(in IPmtRatingListener listener);
}
