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

import android.telephony.satellite.stub.NTRadioTechnology;

/**
 * @hide
 */
parcelable NetworkInfo {
    /**
     * Public Land Mobile Network ID (MCC + MNC) as a 5 or 6 digit string. This field is mandatory.
     */
    String plmn;

    /**
     * List of Absolute Radio Frequency Channel Numbers. This list is optional
     * and thus might not be filled by framework.
     *
     * For GSM: List of ARFCN values.
     * For LTE: List of EARFCN values.
     * For NR: List of NRARFCN values.
     *
     * The default value is an empty array.
     */
    int[] arfcns;

    /**
     * Access network type. This field is optional and thus might not be filled by framework.
     */
    int accessNetwork;

    /**
     * Type of satellite technology if the target network is a satellite network.
     * This field is optional and thus might not be filled by framework.
     */
    NTRadioTechnology satelliteTechnology;

    /**
     * When satelliteTechnology is different from NTRadioTechnology#UNKNOWN,
     * the expected behavior is as follows:
     * {@code true} Modem shall treat the satellite network the same priority as other terrestrial
     * networks in cell reselection.
     * {@code false} Modem shall treat the satellite network with less priority than terrestrial
     * networks in cell reselection.
     */
    boolean hasSamePriorityAsTn;
}
