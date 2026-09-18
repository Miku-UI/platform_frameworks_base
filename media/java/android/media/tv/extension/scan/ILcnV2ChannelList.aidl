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

import android.media.tv.extension.scan.ILcnV2ChannelListListener;

import android.os.Bundle;

/**
 * @hide
 */
interface ILcnV2ChannelList {
    /**
     * Get the LCN V2 channel list information.
     * If there are no conflicts, the array of Bundle is empty.
     *
     * @return an array of bundle of LCN V2 channel list information, each bundle must at least
     *         contain keys defined in @ScanConstants.LcnV2ChannelListInfoBundleKey.
     */
    Bundle[] getLcnV2ChannelLists();
    /**
     * Select and set one of two or more LCN V2 channel list detected by the service scan.
     *
     * @param lcnV2ChannelListSettings Bundle, each bundle must at least
     *         contain keys defined in @ScanConstants.LcnV2ChannelListInfoBundleKey.
     * @return @ScanConstants.OpResult.RESULT_SUCCESS if set successfully else RESULT_FAILED.
     */
    int setLcnV2ChannelList(in Bundle lcnV2ChannelListSettings);
    /**
     * Set the listener to be invoked when two or more LCN V2 channel list are detected.
     *
     * @param listener ILcnV2ChannelListListener.
     * @return @ScanConstants.OpResult.RESULT_SUCCESS if set successfully else RESULT_FAILED.
     */
    int setListener(in ILcnV2ChannelListListener listener);
}
