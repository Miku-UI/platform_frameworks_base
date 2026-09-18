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
package android.service.autofill;

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;
import android.os.Parcel;
import android.util.ArraySet;
import android.view.autofill.AutofillId;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class FillEventHistoryTest {
    private final FillEventHistory.Event mEvent1 =
            new FillEventHistory.Event.Builder(FillEventHistory.Event.TYPE_DATASET_SELECTED)
                    .setDatasetId("123")
                    .setSelectedDatasetIds(Arrays.asList("123", "234"))
                    .setIgnoredDatasetIds(new ArraySet<>(Arrays.asList("345", "456")))
                    .setChangedFieldIds(
                            new ArrayList<>(Arrays.asList(new AutofillId(1), new AutofillId(2))))
                    .setChangedDatasetIds(new ArrayList<>(Arrays.asList("234", "345")))
                    .setSaveDialogNotShowReason(FillEventHistory.Event.NO_SAVE_UI_REASON_NONE)
                    .setUiType(FillEventHistory.Event.UI_TYPE_INLINE)
                    .setFocusedId(new AutofillId(1))
                    .build();

    // Same as Event1.
    private final FillEventHistory.Event mEvent2 =
            new FillEventHistory.Event.Builder(FillEventHistory.Event.TYPE_DATASET_SELECTED)
                    .setDatasetId("123")
                    .setSelectedDatasetIds(Arrays.asList("123", "234"))
                    .setIgnoredDatasetIds(new ArraySet<>(Arrays.asList("345", "456")))
                    .setChangedFieldIds(
                            new ArrayList<>(Arrays.asList(new AutofillId(1), new AutofillId(2))))
                    .setChangedDatasetIds(new ArrayList<>(Arrays.asList("234", "345")))
                    .setSaveDialogNotShowReason(FillEventHistory.Event.NO_SAVE_UI_REASON_NONE)
                    .setUiType(FillEventHistory.Event.UI_TYPE_INLINE)
                    .setFocusedId(new AutofillId(1))
                    .build();

    // Different from Event1.
    private final FillEventHistory.Event mEvent3 =
            new FillEventHistory.Event.Builder(FillEventHistory.Event.TYPE_DATASET_SELECTED)
                    .setDatasetId("123")
                    .setSelectedDatasetIds(Arrays.asList("123", "234"))
                    .setIgnoredDatasetIds(new ArraySet<>(Arrays.asList("456", "567")))
                    .setChangedFieldIds(
                            new ArrayList<>(Arrays.asList(new AutofillId(2), new AutofillId(3))))
                    .setChangedDatasetIds(new ArrayList<>(Arrays.asList("234", "345")))
                    .setSaveDialogNotShowReason(FillEventHistory.Event.NO_SAVE_UI_REASON_NONE)
                    .setUiType(FillEventHistory.Event.UI_TYPE_INLINE)
                    .setFocusedId(new AutofillId(1))
                    .build();

    // Different from Event1.
    private final FillEventHistory.Event mEvent4 =
            new FillEventHistory.Event.Builder(FillEventHistory.Event.TYPE_DATASET_SELECTED)
                    .setSaveDialogNotShowReason(FillEventHistory.Event.NO_SAVE_UI_REASON_NONE)
                    .setUiType(FillEventHistory.Event.UI_TYPE_INLINE)
                    .build();

    @Test
    public void event_hashCodeAndEquals() throws Exception {
        assertThat(mEvent1.hashCode()).isEqualTo(mEvent2.hashCode());
        assertThat(mEvent1.hashCode()).isNotEqualTo(mEvent3.hashCode());
        assertThat(mEvent1.hashCode()).isNotEqualTo(mEvent4.hashCode());

        assertThat(mEvent1).isEqualTo(mEvent2);
        assertThat(mEvent1).isNotEqualTo(mEvent3);
        assertThat(mEvent1).isNotEqualTo(mEvent4);
    }
    @Test
    public void fillEventHistory_hashCodeAndEquals() throws Exception {
        FillEventHistory mHistory1 = new FillEventHistory(123, new Bundle());
        mHistory1.addEvent(mEvent1);
        mHistory1.addEvent(mEvent3);
        // Same as history1.
        FillEventHistory mHistory2 = new FillEventHistory(123, new Bundle());
        mHistory2.addEvent(mEvent2);
        mHistory2.addEvent(mEvent3);
        // Different from history1.
        FillEventHistory mHistory3 = new FillEventHistory(123, new Bundle());
        mHistory3.addEvent(mEvent1);
        mHistory3.addEvent(mEvent2);
        mHistory3.addEvent(mEvent4);

        assertThat(mHistory1.hashCode()).isEqualTo(mHistory2.hashCode());
        assertThat(mHistory1.hashCode()).isNotEqualTo(mHistory3.hashCode());

        assertThat(mHistory1).isEqualTo(mHistory2);
        assertThat(mHistory1).isNotEqualTo(mHistory3);
    }

    @Test
    public void fillEventHistory_parcelUnparcel_hashCodeAndEquals() throws Exception {
        FillEventHistory mHistory1 = new FillEventHistory(123, new Bundle());
        mHistory1.addEvent(mEvent1);
        mHistory1.addEvent(mEvent3);

        final Parcel parcel = Parcel.obtain();
        try {
            mHistory1.writeToParcel(parcel, 0);
            final int dataSize = parcel.dataPosition();
            // Reset data position for read.
            parcel.setDataPosition(0);
            final FillEventHistory fromParcel = mHistory1.CREATOR.createFromParcel(parcel);

            // Same size of data is written and read.
            assertThat(dataSize).isEqualTo(parcel.dataPosition());

            // Unparceled object is equal to and has same hashCode as original.
            assertThat(mHistory1).isEqualTo(fromParcel);
            assertThat(mHistory1.hashCode()).isEqualTo(fromParcel.hashCode());
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void fillEventHistory_builderMatchesEquals() {
        final FillEventHistory.Event constructed =
                new FillEventHistory.Event(
                        FillEventHistory.Event.TYPE_DATASET_SELECTED, // eventType
                        "123", // datasetId
                        null, // clientState
                        Arrays.asList("123", "234"), // selectedDatasetIds
                        new ArraySet<>(Arrays.asList("345", "456")), // ignoredDatasetIds
                        new ArrayList<>(
                                Arrays.asList(
                                        new AutofillId(1), new AutofillId(2))), // changedFieldIds
                        new ArrayList<>(Arrays.asList("234", "345")), // changedDatasetIds
                        new ArrayList<>(Arrays.asList(new AutofillId(5))), // manuallyFilledFieldIds
                        new ArrayList<>(
                                List.of(
                                        new ArrayList<>(
                                                Arrays.asList(
                                                        "6", "7")))), // manuallyFilledDatasetIds
                        new AutofillId[] {new AutofillId(3), new AutofillId(4)}, // detectedFieldIds
                        null, // detectedFieldClassifications
                        FillEventHistory.Event.NO_SAVE_UI_REASON_NONE, // saveDialogNotShowReason
                        FillEventHistory.Event.UI_TYPE_INLINE, // uiType
                        new AutofillId(1) // focusedId
                        );

        final FillEventHistory.Event build =
                new FillEventHistory.Event.Builder(FillEventHistory.Event.TYPE_DATASET_SELECTED)
                        .setDatasetId("123")
                        .setSelectedDatasetIds(Arrays.asList("123", "234"))
                        .setIgnoredDatasetIds(new ArraySet<>(Arrays.asList("345", "456")))
                        .setChangedFieldIds(
                                new ArrayList<>(
                                        Arrays.asList(new AutofillId(1), new AutofillId(2))))
                        .setChangedDatasetIds(new ArrayList<>(Arrays.asList("234", "345")))
                        .setManuallyFilledFieldIds(
                                new ArrayList<>(Arrays.asList(new AutofillId(5))))
                        .setManuallyFilledDatasetIds(
                                new ArrayList<>(List.of(new ArrayList<>(Arrays.asList("6", "7")))))
                        .setDetectedFieldIds(
                                new AutofillId[] {new AutofillId(3), new AutofillId(4)})
                        .setSaveDialogNotShowReason(FillEventHistory.Event.NO_SAVE_UI_REASON_NONE)
                        .setUiType(FillEventHistory.Event.UI_TYPE_INLINE)
                        .setFocusedId(new AutofillId(1))
                        .build();

        assertThat(constructed).isEqualTo(build);
    }
}
