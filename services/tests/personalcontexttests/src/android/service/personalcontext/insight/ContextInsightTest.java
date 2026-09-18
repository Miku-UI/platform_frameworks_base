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

import android.os.Bundle;
import android.os.Parcel;
import android.service.personalcontext.hint.BundleHint;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ContextInsightTest {

    @Test
    public void testContextInsightWrapperParcelUnparcel() {
        final int inputValue = 1234;
        final String dataKey = "test-key";
        final BundleHint hint = new BundleHint.Builder().build();
        hint.getDataBundle().putInt(dataKey, inputValue);

        final BundleInsight insight = new BundleInsight.Builder()
                .build();

        ContextInsight outputInsight = assertParcelUnparcel(insight);
        assertThat(insight.getInsightId()).isEqualTo(outputInsight.getInsightId());
        assertThat(insight.getCreationTime()).isGreaterThan(Instant.ofEpochMilli(0));
        assertThat(insight.getCreationTime().toEpochMilli())
                .isEqualTo(outputInsight.getCreationTime().toEpochMilli());
    }

    @Test
    public void testBundleInsightParcelUnparcel() {
        final int inputValue = 1234;
        final String dataKey = "test-key";
        final Bundle data = new Bundle();
        data.putInt(dataKey, inputValue);

        final BundleInsight insight = new BundleInsight.Builder()
                .setDataBundle(data).build();

        ContextInsight outputInsight = assertParcelUnparcel(insight);
        assertThat(outputInsight).isInstanceOf(BundleInsight.class);
        final int outputValue = ((BundleInsight) outputInsight).getDataBundle().getInt(dataKey);

        assertThat(outputValue).isEqualTo(inputValue);
    }

    /**
     * Parcels and unparcels the given {@link ContextInsight} and asserts the same amount of data
     * written to and read from the {@link Parcel}.
     */
    public ContextInsight assertParcelUnparcel(ContextInsight insight) {
        final ContextInsightWrapper wrapper = new ContextInsightWrapper(insight);

        Parcel parcel = Parcel.obtain();
        wrapper.writeToParcel(parcel, 0);
        final int dataSize = parcel.dataPosition();

        // Reset data position for read.
        parcel.setDataPosition(0);
        ContextInsightWrapper fromParcel = ContextInsightWrapper.CREATOR.createFromParcel(parcel);

        // Same size of data is written and read.
        assertThat(dataSize).isEqualTo(parcel.dataPosition());
        return fromParcel.getContextInsight();
    }
}
