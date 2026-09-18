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

package android.telephony.satellite.stub;

import android.telephony.satellite.stub.SatelliteInfo;

/**
 * @hide
 */
parcelable SystemSelectionSpecifier {
    /**
     * Network plmn associated with channel information.
     * This value is populated from the first item in the {@link #mMccMncs} field if provided,
     * for backward compatibility.
     * This field is optional. The default value is an empty string.
     * @deprecated Use the {@link #mMccMncs} field instead.
     */
    String mMccMnc;

    /**
     * The frequency bands to scan.
     * Bands will be filled only if the whole band is needed.
     * Maximum length of the vector is 8.
     * The values are populated from the mBands array within the SatelliteInfo[] array, which is
     * included in the SystemSelectionSpecifier, for backward compatibility.
     * This field is optional. The default value is an empty array.
     * @deprecated Use the bands field inside {@link #satelliteInfos} instead.
     */
    int[] mBands;

    /**
     * The radio channels to scan as defined in 3GPP TS 25.101 and 36.101.
     * The values are populated from the earfcns defined in the EarfcnRange[] array inside
     * SatelliteInfo[], which is included in the SystemSelectionSpecifier, for backward
     * compatibility.
     * Maximum length of the vector is 32.
     * This field is optional. The default value is an empty array.
     */
    int[] mEarfcs;

    /*
     * The list of satellites configured for the current location.
     * This field is optional. The default value is an empty array.
     */
    SatelliteInfo[] satelliteInfos;

    /**
     * The list of tag IDs associated with the current location.
     * This field is optional and empty by default.
     */
    int[] tagIds;

    /**
     * The ICC ID of the satellite subscription.
     * This field is optional. The default value is an empty string.
     */
    String mIccId;

    /**
     * The list of satellite PLMNs supported by the carrier associated with the satellite
     * subscription.
     * <p>
     * These PLMNs support manual-connect {@code NTRadioTechnology} types, such as
     * {@code NTRadioTechnology#NB_IOT_NTN}.
     * <p>
     * This field is optional. The default value is an empty array.
     */
    String[] mMccMncs;
}
