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
parcelable SatelliteNetworkInfo {
    /**
     * List of roaming PLMNs used for connecting to satellite networks supported by the
     * user subscription. When modem camps on a satellite network whose satellite technology is NTN
     * but modem reports NTRadioTechnology#UNKNOWN in the AccessTechnologySpecificInfo of the camped
     * cell, Android will consider this network as a terrestrial network. To make Android consider
     * this as a NTN network, modem must report NTRadioTechnology#NR_NTN for such cases.
     *
     * <p>The order of PLMNs does not imply priority.
     */
    NetworkInfo[] allowedPlmns;

    /**
     * List of satellite PLMNs that are not supported by the carrier. Modem should use this list
     * to identify satellite PLMNs that are not supported by the carrier and make sure not to
     * attach to them.
     *
     * <p>The order of PLMNs does not imply priority.
     *
     */
    NetworkInfo[] disallowedPlmns;
}
