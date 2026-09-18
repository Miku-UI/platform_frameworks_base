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

package android.telephony.satellite.stub;

import android.telephony.satellite.stub.NetworkInfo;

/**
 * @hide
 */
parcelable PrioritizedNetworkScanRequest {
    /**
     * A list of NetworkInfo objects defining the preferred networks for the scan.
     * The modem must give higher priority to these networks when scanning.
     */
    NetworkInfo[] networkInfos;

    /**
     * The wait interval between two adjacent scans in milliseconds.
     * This field is optional. Android might not fill this info, in which the default value will
     * be -1. If Android provides this, modem shall use it.
     */
    int searchIntervalMs;

    /**
     * The valid duration in seconds for the preferred networks from the time it is received.
     * This field is optional. Android might not fill this info in which the default value will
     * be -2. If Android provides a valid value, model shall use this value.
     * <ul>
     * <li>0: The modem will perform the scan sequence exactly once and then automatically
     *     disables the prioritized scan mode.</li>
     * <li>-1: means no expiration until modem reboot or explicitly disabled by Android.</li>
     * </ul>
     */
    int validDurationSec;
}
