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

package android.view.autofill;

import static android.service.autofill.Flags.FLAG_STRING_REBUILD_API;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;

import android.os.Parcel;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AutofillNoiseInjectedDataTest {
    @Rule
    public final SetFlagsRule mSetFlagsRule =
            new SetFlagsRule(SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT);

    private final byte mTestBitMask = (byte) ((1 << 0) | (1 << 3) | (1 << 4));

    private AutofillNoiseInjectedData writeToParcelAndRecycle(AutofillNoiseInjectedData value) {
        Parcel parcel = Parcel.obtain();
        try {
            value.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            return AutofillNoiseInjectedData.CREATOR.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }

    @Test
    @EnableFlags(FLAG_STRING_REBUILD_API)
    public void testGetters() {
        byte[] payload = new byte[] {1, 2, 3, 4, 5};
        AutofillNoiseInjectedData injectedText =
                new AutofillNoiseInjectedData(mTestBitMask, payload);

        assertThat(injectedText.getRetainedBitMask()).isEqualTo(mTestBitMask);
        assertArrayEquals(payload, injectedText.getNoiseInjectedPayload());
    }

    @Test
    @EnableFlags(FLAG_STRING_REBUILD_API)
    public void testWriteToParcelAndCreateFromParcel() {
        byte[] payload = new byte[] {1, 2, 3, 4, 5};
        AutofillNoiseInjectedData original = new AutofillNoiseInjectedData(mTestBitMask, payload);

        AutofillNoiseInjectedData reconstructed = writeToParcelAndRecycle(original);

        assertThat(reconstructed.getRetainedBitMask()).isEqualTo(mTestBitMask);
        assertArrayEquals(payload, reconstructed.getNoiseInjectedPayload());
    }

    @Test
    @EnableFlags(FLAG_STRING_REBUILD_API)
    public void testWriteToParcelAndCreateFromParcel_emptyPayload() {
        byte[] payload = new byte[0];
        AutofillNoiseInjectedData original = new AutofillNoiseInjectedData(mTestBitMask, payload);

        AutofillNoiseInjectedData reconstructed = writeToParcelAndRecycle(original);

        assertThat(reconstructed.getRetainedBitMask()).isEqualTo(mTestBitMask);
        assertArrayEquals(payload, reconstructed.getNoiseInjectedPayload());
    }

    @Test
    @EnableFlags(FLAG_STRING_REBUILD_API)
    public void testDescribeContents() {
        byte[] payload = new byte[] {1};
        AutofillNoiseInjectedData injectedText =
                new AutofillNoiseInjectedData(mTestBitMask, payload);
        assertThat(injectedText.describeContents()).isEqualTo(0);
    }
}
