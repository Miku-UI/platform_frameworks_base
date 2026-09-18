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

package android.media.tv.extension.scan;

import android.media.tv.extension.scan.IFavoriteNetworkListener;
import android.os.Bundle;

/**
 * @hide
 */
interface IFavoriteNetwork {
    /**
     * Get the favorite network information.
     * If there are no conflicts, the array of Bundle is empty.
     *
     * @return an array of bundle of favorite network information, each bundle key should be at
     *         least contain keys defined in @ScanConstants.FavoriteNetworkBundleKey.
     */
    Bundle[] getFavoriteNetworks();
    /**
     * Select and set one of two or more favorite networks detected by the service scan.
     *
     * @param favoriteNetworkSettings, each bundle key should be at
     *        least contain keys defined in @ScanConstants.FavoriteNetworkBundleKey.
     * @return @ScanConstants.OpResult.RESULT_SUCCESS if set successfully else RESULT_FAILED.
     */
    int setFavoriteNetwork(in Bundle favoriteNetworkSettings);
    /**
     * Set the listener to be invoked when two or more favorite networks are detected.
     *
     * @param listener IFavoriteNetworkListener.
     * @return @ScanConstants.OpResult.RESULT_SUCCESS if set successfully else RESULT_FAILED.
     */
    int setListener(in IFavoriteNetworkListener listener);
}
