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

package android.content.pm.verify.developer;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import android.os.PersistableBundle;
import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@Presubmit
@RunWith(AndroidJUnit4.class)
@SmallTest
public class DeveloperVerificationStatusTest {
    private static final boolean TEST_VERIFIED = true;
    private static final boolean TEST_LITE = true;
    private static final int TEST_APP_METADATA_VERIFICATION_STATUS =
            DeveloperVerificationStatus.APP_METADATA_VERIFICATION_STATUS_GOOD;
    private static final String TEST_FAILURE_MESSAGE = "test test";
    private static final String TEST_KEY = "test key";
    private static final String TEST_VALUE = "test value";
    private final PersistableBundle mTestExtras = new PersistableBundle();
    private DeveloperVerificationStatus mStatus;

    @Before
    public void setUpWithBuilder() {
        mTestExtras.putString(TEST_KEY, TEST_VALUE);
        mStatus = new DeveloperVerificationStatus.Builder()
                .setAppMetadataVerificationStatus(TEST_APP_METADATA_VERIFICATION_STATUS)
                .setFailureMessage(TEST_FAILURE_MESSAGE)
                .setVerified(TEST_VERIFIED)
                .setLiteVerification(TEST_LITE)
                .build();
    }

    @Test
    public void testGetters() {
        assertThat(mStatus.isVerified()).isEqualTo(TEST_VERIFIED);
        assertThat(mStatus.getAppMetadataVerificationStatus()).isEqualTo(
                TEST_APP_METADATA_VERIFICATION_STATUS);
        assertThat(mStatus.getFailureMessage()).isEqualTo(TEST_FAILURE_MESSAGE);
        assertThat(mStatus.isLiteVerification()).isEqualTo(TEST_LITE);
    }

    @Test
    public void testParcel() {
        Parcel parcel = Parcel.obtain();
        mStatus.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        DeveloperVerificationStatus statusFromParcel =
                DeveloperVerificationStatus.CREATOR.createFromParcel(parcel);
        assertThat(statusFromParcel.isVerified()).isEqualTo(TEST_VERIFIED);
        assertThat(statusFromParcel.getAppMetadataVerificationStatus()).isEqualTo(
                TEST_APP_METADATA_VERIFICATION_STATUS);
        assertThat(statusFromParcel.getFailureMessage()).isEqualTo(TEST_FAILURE_MESSAGE);
        assertThat(statusFromParcel.isLiteVerification()).isEqualTo(TEST_LITE);
    }

    @Test
    public void testParcelWithNullFailureMessage() {
        DeveloperVerificationStatus status = new DeveloperVerificationStatus.Builder()
                .setFailureMessage(null)
                .build();
        Parcel parcel = Parcel.obtain();
        status.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        DeveloperVerificationStatus statusFromParcel =
                DeveloperVerificationStatus.CREATOR.createFromParcel(parcel);
        assertThat(statusFromParcel.getFailureMessage()).isEqualTo(null);
    }
}
