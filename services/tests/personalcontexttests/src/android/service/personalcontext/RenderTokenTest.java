/*
 * Copyright 2026 The Android Open Source Project
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

package android.service.personalcontext;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class RenderTokenTest {

    @Test
    public void testParcelUnparcel() {
        final UUID componentId = UUID.randomUUID();
        String tag = "testTag";
        final RenderToken token = new RenderToken(componentId, tag);

        final RenderToken fromParcel = parcelUnparcel(token);

        assertThat(fromParcel.getTokenId()).isEqualTo(token.getTokenId());
        assertThat(fromParcel.getRendererComponentId()).isEqualTo(token.getRendererComponentId());
        assertThat(fromParcel.getTag()).isEqualTo(token.getTag());

        assertThat(fromParcel).isEqualTo(token);
        assertThat(fromParcel.hashCode()).isEqualTo(token.hashCode());
    }

    @Test
    public void testParcelUnparcel_nullTag() {
        final UUID componentId = UUID.randomUUID();
        final RenderToken token = new RenderToken(componentId, null);

        final RenderToken fromParcel = parcelUnparcel(token);

        assertThat(fromParcel.getTokenId()).isEqualTo(token.getTokenId());
        assertThat(fromParcel.getRendererComponentId()).isEqualTo(token.getRendererComponentId());
        assertThat(fromParcel.getTag()).isNull();

        assertThat(fromParcel).isEqualTo(token);
        assertThat(fromParcel.hashCode()).isEqualTo(token.hashCode());
    }

    @Test
    public void testComparable() {
        final RenderToken token1 = new RenderToken(UUID.randomUUID(), null);
        final RenderToken token2 = new RenderToken(UUID.randomUUID(), null);

        assertThat(token1.compareTo(token2))
                .isEqualTo(token1.getTokenId().compareTo(token2.getTokenId()));
    }

    private RenderToken parcelUnparcel(RenderToken in) {
        final Parcel parcel = Parcel.obtain();
        try {
            in.writeToParcel(parcel, 0);
            final int dataSize = parcel.dataPosition();
            parcel.setDataPosition(0);

            final RenderToken fromParcel = RenderToken.CREATOR.createFromParcel(parcel);
            // Same size of data is written and read.
            assertThat(dataSize).isEqualTo(parcel.dataPosition());
            return fromParcel;
        } finally {
            parcel.recycle();
        }
    }
}
