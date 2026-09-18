/*
 * Copyright (C) 2025 The Android Open Source Project
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

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link ActionableInsight}. */
@RunWith(AndroidJUnit4.class)
public class InsightDisplayDetailsTest {
    @Test
    public void testParcelUnparcel() {
        final InsightDisplayDetails originalDisplayDetails =
                new InsightDisplayDetails.Builder("title")
                        .setContentDescription("content description")
                        .setSubtitle("subtitle")
                        .build();

        final Parcel parcel = Parcel.obtain();
        parcel.writeParcelable(originalDisplayDetails, 0);

        parcel.setDataPosition(0);
        InsightDisplayDetails fromParcel = parcel.readParcelable(null, InsightDisplayDetails.class);

        assertThat(fromParcel).isEqualTo(originalDisplayDetails);
    }
}
