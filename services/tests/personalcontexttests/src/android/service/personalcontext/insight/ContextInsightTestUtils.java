/*
 * Copyright 2025 The Android Open Source Project
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

package android.service.personalcontext.insight;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;

/** Utility class for personal context tests. */
public class ContextInsightTestUtils {
    /**
     * Parcels and unparcels the given {@link ContextInsight} and asserts the same amount of data
     * written to and read from the {@link Parcel}.
     */
    public static ContextInsight assertParcelUnparcel(ContextInsight insight) {
        final ContextInsightWrapper wrapper = new ContextInsightWrapper(insight);

        Parcel parcel = Parcel.obtain();
        wrapper.writeToParcel(parcel, 0);
        final int dataSize = parcel.dataPosition();

        // Reset data position for read.
        parcel.setDataPosition(0);
        ContextInsightWrapper fromParcel = ContextInsightWrapper.CREATOR.createFromParcel(parcel);

        // Same size of data is written and read.
        assertThat(dataSize).isEqualTo(parcel.dataPosition());
        parcel.recycle();
        return fromParcel.getContextInsight();
    }
}
