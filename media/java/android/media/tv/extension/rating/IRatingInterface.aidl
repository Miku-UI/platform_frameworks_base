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

import android.os.Bundle;
import android.media.tv.extension.rating.IRatingUpdateListener;
import android.media.tv.extension.rating.RatingRegionInfo;

/**
 * @hide
 */
interface IRatingInterface {
    /**
     * Gets the current RRT (Regional Rating Table) information. This should be called after the
     * listener notified that there is an update to the RRT table.
     *
     * @return The RRT Rating list.
     */
    List<RatingRegionInfo> getRRTRatingInfo();
    /**
     * Sets the RRT rating list based on a user's selection.
     *
     * @param regions The RRT Rating list user selected.
     * @return true if the rating information was set successfully, false otherwise.
     */
    boolean setRRTRatingInfo(in List<RatingRegionInfo> regions);
    /**
     * Resets and clears all stored RRT5 rating information.
     *
     * @return true if all RRT5 information was cleared successfully, false otherwise.
     */
    boolean setResetRrt5();
    /**
     * Registers a listener to be notified of RRT table changes.
     */
    void registerRrtUpdateListener(IRatingUpdateListener listener);
    /**
     * Unregisters an existing listener.
     */
    void unregisterRrtUpdateListener(IRatingUpdateListener listener);
}
